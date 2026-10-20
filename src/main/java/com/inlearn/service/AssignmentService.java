package com.inlearn.service;

import com.inlearn.dao.AssignmentDao;
import com.inlearn.dao.Database;
import com.inlearn.dao.EnrollmentDao;
import com.inlearn.dao.GradeDao;
import com.inlearn.dao.SubmissionDao;
import com.inlearn.exception.ConcurrentUpdateException;
import com.inlearn.exception.ConflictException;
import com.inlearn.exception.NotFoundException;
import com.inlearn.exception.ValidationException;
import com.inlearn.model.Assignment;
import com.inlearn.model.Course;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.Grade;
import com.inlearn.model.LatePolicy;
import com.inlearn.model.Submission;
import com.inlearn.model.User;
import com.inlearn.util.InputValidator;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creating, editing and deleting assignments.
 *
 * <p>Edits are checked so they never silently invalidate student work:
 * <ul>
 *   <li>Maximum points are locked once any submission has been graded, because changing them
 *       would change the meaning of grades already given.</li>
 *   <li>Changing the due date recalculates each existing submission's late status in the same
 *       transaction (the interface asks for confirmation first).</li>
 *   <li>A change is refused if it would make an already accepted submission inadmissible, such
 *       as switching to "no late submissions" when late work has been accepted.</li>
 *   <li>An assignment with submissions can't be deleted.</li>
 * </ul>
 */
public class AssignmentService {

    /** Largest allowed value for maximum points (matches the database CHECK constraint). */
    public static final BigDecimal MAX_POINTS_LIMIT = BigDecimal.valueOf(1000);

    private final Database database;
    private final AssignmentDao assignmentDao;
    private final SubmissionDao submissionDao;
    private final GradeDao gradeDao;
    private final EnrollmentDao enrollmentDao;
    private final AuthorizationService authorization;
    private final Clock clock;

    public AssignmentService(Database database, AssignmentDao assignmentDao, SubmissionDao submissionDao,
                             GradeDao gradeDao, EnrollmentDao enrollmentDao, AuthorizationService authorization,
                             Clock clock) {
        this.database = database;
        this.assignmentDao = assignmentDao;
        this.submissionDao = submissionDao;
        this.gradeDao = gradeDao;
        this.enrollmentDao = enrollmentDao;
        this.authorization = authorization;
        this.clock = clock;
    }

    /** The settings an instructor enters for an assignment. {@code lateCutoffAt} may be {@code null}. */
    public record AssignmentDraft(String title, String instructions, Instant dueAt, BigDecimal maxPoints,
                                  LatePolicy latePolicy, Instant lateCutoffAt) {
    }

    /** An assignment with counts for the instructor's assignment list. */
    public record AssignmentOverview(Assignment assignment, int submissionCount, int gradedCount, int enrolledCount) {

        public int awaitingGradingCount() {
            return submissionCount - gradedCount;
        }
    }

    /** What the editor needs to know about existing work to explain which settings are locked. */
    public record AssignmentEditContext(Course course, Assignment assignment, int submissionCount, int gradedCount) {

        public boolean isNew() {
            return assignment == null;
        }

        public boolean maxPointsLocked() {
            return gradedCount > 0;
        }
    }

    /** The saved assignment and how many submissions had their late status recalculated. */
    public record UpdateResult(Assignment assignment, int lateStatusChanges) {
    }

    /** Assignments in an instructor's course with submission and grading counts for current students. */
    public List<AssignmentOverview> listForInstructor(CurrentUser currentUser, long courseId) {
        return database.read(connection -> {
            authorization.requireInstructorOfCourse(connection, currentUser, courseId);
            Set<Long> enrolledStudentIds = enrollmentDao.findActiveByCourse(connection, courseId).stream()
                    .map(enrollment -> enrollment.studentId())
                    .collect(Collectors.toSet());
            List<Submission> submissions = submissionDao.findByCourse(connection, courseId);
            Set<Long> gradedSubmissionIds = gradeDao.findByCourse(connection, courseId).stream()
                    .map(Grade::submissionId)
                    .collect(Collectors.toSet());

            Map<Long, Integer> submissionCounts = new HashMap<>();
            Map<Long, Integer> gradedCounts = new HashMap<>();
            for (Submission submission : submissions) {
                if (!enrolledStudentIds.contains(submission.studentId())) {
                    continue; // withdrawn students' work is kept but not counted
                }
                submissionCounts.merge(submission.assignmentId(), 1, Integer::sum);
                if (gradedSubmissionIds.contains(submission.id())) {
                    gradedCounts.merge(submission.assignmentId(), 1, Integer::sum);
                }
            }
            List<AssignmentOverview> overviews = new ArrayList<>();
            for (Assignment assignment : assignmentDao.findByCourse(connection, courseId)) {
                overviews.add(new AssignmentOverview(assignment,
                        submissionCounts.getOrDefault(assignment.id(), 0),
                        gradedCounts.getOrDefault(assignment.id(), 0),
                        enrolledStudentIds.size()));
            }
            return overviews;
        });
    }

    /** Loads an assignment and the counts that decide which settings can still change. */
    public AssignmentEditContext getEditContext(CurrentUser currentUser, long assignmentId) {
        return database.read(connection -> {
            Assignment assignment = findAssignment(connection, assignmentId);
            Course course = authorization.requireInstructorOfCourse(connection, currentUser, assignment.courseId());
            return new AssignmentEditContext(course, assignment,
                    submissionDao.countByAssignment(connection, assignmentId),
                    gradeDao.countByAssignment(connection, assignmentId));
        });
    }

    /** Context for creating a new assignment in a course. */
    public AssignmentEditContext getNewAssignmentContext(CurrentUser currentUser, long courseId) {
        return database.read(connection -> {
            Course course = authorization.requireInstructorOfCourse(connection, currentUser, courseId);
            AuthorizationService.requireEditable(course);
            return new AssignmentEditContext(course, null, 0, 0);
        });
    }

    public Assignment createAssignment(CurrentUser currentUser, long courseId, AssignmentDraft draft) {
        AssignmentDraft cleaned = validate(draft);
        if (!cleaned.dueAt().isAfter(clock.instant())) {
            throw new ValidationException("Choose a due date in the future.");
        }
        return database.inTransaction(connection -> {
            User instructor = authorization.requireActiveUser(connection, currentUser);
            Course course = authorization.requireInstructorOfCourse(connection, currentUser, courseId);
            AuthorizationService.requireEditable(course);
            long assignmentId = assignmentDao.insert(connection, courseId, cleaned.title(), cleaned.instructions(),
                    cleaned.dueAt(), cleaned.maxPoints(), cleaned.latePolicy(), cleaned.lateCutoffAt(), instructor.id());
            return assignmentDao.findById(connection, assignmentId).orElseThrow();
        });
    }

    /**
     * Saves new settings for an assignment, enforcing the rules described on this class.
     */
    public UpdateResult updateAssignment(CurrentUser currentUser, long assignmentId, AssignmentDraft draft,
                                         int expectedVersion) {
        AssignmentDraft cleaned = validate(draft);
        return database.inTransaction(connection -> {
            Assignment existing = assignmentDao.findByIdForUpdate(connection, assignmentId)
                    .orElseThrow(() -> new NotFoundException("This assignment has been deleted."));
            Course course = authorization.requireInstructorOfCourse(connection, currentUser, existing.courseId());
            AuthorizationService.requireEditable(course);
            if (existing.version() != expectedVersion) {
                throw new ConcurrentUpdateException("assignment");
            }

            int gradedCount = gradeDao.countByAssignment(connection, assignmentId);
            if (gradedCount > 0 && existing.maxPoints().compareTo(cleaned.maxPoints()) != 0) {
                throw new ConflictException("Maximum points can't change because " + gradedCount
                        + (gradedCount == 1 ? " submission has" : " submissions have")
                        + " already been graded out of " + existing.maxPoints().stripTrailingZeros().toPlainString()
                        + ". Keep the current maximum so existing grades keep their meaning.");
            }

            Assignment updatedSettings = new Assignment(existing.id(), existing.courseId(), cleaned.title(),
                    cleaned.instructions(), cleaned.dueAt(), cleaned.maxPoints(), cleaned.latePolicy(),
                    cleaned.lateCutoffAt(), existing.version(), existing.createdAt());
            List<Submission> lateStatusChanges = findLateStatusChanges(connection, updatedSettings);

            if (!assignmentDao.update(connection, assignmentId, cleaned.title(), cleaned.instructions(),
                    cleaned.dueAt(), cleaned.maxPoints(), cleaned.latePolicy(), cleaned.lateCutoffAt(),
                    expectedVersion)) {
                throw new ConcurrentUpdateException("assignment");
            }
            for (Submission submission : lateStatusChanges) {
                submissionDao.updateLateFlag(connection, submission.id(), !submission.late());
            }
            Assignment saved = assignmentDao.findById(connection, assignmentId).orElseThrow();
            return new UpdateResult(saved, lateStatusChanges.size());
        });
    }

    /** Deletes an assignment that nobody has submitted to yet. */
    public void deleteAssignment(CurrentUser currentUser, long assignmentId) {
        database.inTransaction(connection -> {
            Assignment assignment = findAssignment(connection, assignmentId);
            Course course = authorization.requireInstructorOfCourse(connection, currentUser, assignment.courseId());
            AuthorizationService.requireEditable(course);
            int submissionCount = submissionDao.countByAssignment(connection, assignmentId);
            if (submissionCount > 0) {
                throw new ConflictException("\"" + assignment.title() + "\" already has " + submissionCount
                        + (submissionCount == 1 ? " submission" : " submissions")
                        + ", so it can't be deleted. Students' work is part of their academic record.");
            }
            assignmentDao.delete(connection, assignmentId);
            return null;
        });
    }

    /**
     * Works out which existing submissions would change late status under new settings, and
     * refuses settings that would make an already accepted submission inadmissible.
     */
    private List<Submission> findLateStatusChanges(Connection connection, Assignment newSettings) throws SQLException {
        List<Submission> changed = new ArrayList<>();
        int lateUnderNewSettings = 0;
        int afterNewCutoff = 0;
        for (Submission submission : submissionDao.findByAssignment(connection, newSettings.id())) {
            boolean late = SubmissionPolicy.isLate(newSettings, submission.submittedAt());
            if (late) {
                lateUnderNewSettings++;
                if (newSettings.lateCutoffAt() != null && submission.submittedAt().isAfter(newSettings.lateCutoffAt())) {
                    afterNewCutoff++;
                }
            }
            if (late != submission.late()) {
                changed.add(submission);
            }
        }
        if (newSettings.latePolicy() == LatePolicy.NO_LATE && lateUnderNewSettings > 0) {
            throw new ConflictException(lateUnderNewSettings + " existing "
                    + (lateUnderNewSettings == 1 ? "submission was" : "submissions were")
                    + " made after this due date, so late work must stay accepted. "
                    + "Choose \"Accept late submissions\" or a later due date.");
        }
        if (afterNewCutoff > 0) {
            throw new ConflictException(afterNewCutoff + " existing "
                    + (afterNewCutoff == 1 ? "submission was" : "submissions were")
                    + " made after this late cutoff. Choose a later cutoff or remove it.");
        }
        return changed;
    }

    private Assignment findAssignment(Connection connection, long assignmentId) throws SQLException {
        return assignmentDao.findById(connection, assignmentId)
                .orElseThrow(() -> new NotFoundException("This assignment has been deleted. Refresh the list."));
    }

    private static AssignmentDraft validate(AssignmentDraft draft) {
        String title = InputValidator.requireText(draft.title(), "Title", InputValidator.MAX_TITLE_LENGTH);
        String instructions = InputValidator.requireText(draft.instructions(), "Instructions",
                InputValidator.MAX_LONG_TEXT_LENGTH);
        if (draft.dueAt() == null) {
            throw new ValidationException("Choose a due date and time.");
        }
        BigDecimal maxPoints = draft.maxPoints();
        if (maxPoints == null || maxPoints.signum() <= 0) {
            throw new ValidationException("Maximum points must be greater than zero.");
        }
        if (maxPoints.compareTo(MAX_POINTS_LIMIT) > 0) {
            throw new ValidationException("Maximum points can't be more than " + MAX_POINTS_LIMIT + ".");
        }
        if (maxPoints.stripTrailingZeros().scale() > InputValidator.POINTS_DECIMAL_PLACES) {
            throw new ValidationException("Maximum points can have at most two decimal places.");
        }
        if (draft.latePolicy() == null) {
            throw new ValidationException("Choose a late submission policy.");
        }
        Instant cutoff = draft.latePolicy() == LatePolicy.ALLOW_LATE ? draft.lateCutoffAt() : null;
        if (cutoff != null && !cutoff.isAfter(draft.dueAt())) {
            throw new ValidationException("The late submission cutoff must be after the due date.");
        }
        return new AssignmentDraft(title, instructions, draft.dueAt(),
                maxPoints.setScale(InputValidator.POINTS_DECIMAL_PLACES), draft.latePolicy(), cutoff);
    }
}
