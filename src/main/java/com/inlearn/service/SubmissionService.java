package com.inlearn.service;

import com.inlearn.dao.AssignmentDao;
import com.inlearn.dao.Database;
import com.inlearn.dao.GradeDao;
import com.inlearn.dao.SubmissionDao;
import com.inlearn.exception.AccessDeniedException;
import com.inlearn.exception.ConcurrentUpdateException;
import com.inlearn.exception.ConflictException;
import com.inlearn.exception.NotFoundException;
import com.inlearn.model.Assignment;
import com.inlearn.model.Course;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.Grade;
import com.inlearn.model.Role;
import com.inlearn.model.StudentAssignment;
import com.inlearn.model.Submission;
import com.inlearn.model.User;
import com.inlearn.util.FileStorage;
import com.inlearn.util.FileStorage.StoredFile;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Student submissions: viewing assignments, uploading files and downloading submitted work.
 *
 * <p>Replacing a submission is designed so the previous valid file is never lost on failure:
 * the new file is copied into storage under a new name first, then the database row is switched
 * to it in a transaction, and only after that commit is the old file deleted. If anything fails
 * before the commit, the new copy is removed and the old submission is untouched.
 */
public class SubmissionService {

    private static final Logger LOGGER = Logger.getLogger(SubmissionService.class.getName());

    private final Database database;
    private final AssignmentDao assignmentDao;
    private final SubmissionDao submissionDao;
    private final GradeDao gradeDao;
    private final AuthorizationService authorization;
    private final FileStorage fileStorage;
    private final Clock clock;

    public SubmissionService(Database database, AssignmentDao assignmentDao, SubmissionDao submissionDao,
                             GradeDao gradeDao, AuthorizationService authorization, FileStorage fileStorage,
                             Clock clock) {
        this.database = database;
        this.assignmentDao = assignmentDao;
        this.submissionDao = submissionDao;
        this.gradeDao = gradeDao;
        this.authorization = authorization;
        this.fileStorage = fileStorage;
        this.clock = clock;
    }

    /** Everything the assignment details screen shows, including whether the student may submit now. */
    public record AssignmentDetails(Course course, StudentAssignment studentAssignment,
                                    SubmissionPolicy.Decision submissionDecision) {
    }

    /** Confirmation details shown after a successful upload. */
    public record SubmissionReceipt(Submission submission, boolean replacedPrevious) {
    }

    /** The student's view of every assignment in one of their courses. */
    public List<StudentAssignment> listForStudent(CurrentUser currentUser, long courseId) {
        return database.read(connection -> {
            authorization.requireEnrolledStudent(connection, currentUser, courseId);
            return buildStudentAssignments(connection, currentUser.id(), courseId, clock.instant());
        });
    }

    public AssignmentDetails getAssignmentDetails(CurrentUser currentUser, long assignmentId) {
        return database.read(connection -> {
            Assignment assignment = findAssignment(connection, assignmentId);
            Course course = authorization.requireEnrolledStudent(connection, currentUser, assignment.courseId());
            Submission submission = submissionDao
                    .findByAssignmentAndStudent(connection, assignmentId, currentUser.id()).orElse(null);
            Grade grade = submission == null ? null
                    : gradeDao.findBySubmission(connection, submission.id()).orElse(null);
            Instant now = clock.instant();
            StudentAssignment studentAssignment = new StudentAssignment(assignment, submission, grade,
                    SubmissionPolicy.progressOf(assignment, submission, grade, now));
            SubmissionPolicy.Decision decision = course.isActive()
                    ? SubmissionPolicy.evaluate(assignment, grade != null, now)
                    : new SubmissionPolicy.Decision(false, false, "This course is archived, so submissions are closed.");
            return new AssignmentDetails(course, studentAssignment, decision);
        });
    }

    /**
     * Uploads a new submission or replaces the student's current one, enforcing the deadline
     * and late policy at the moment of submission.
     */
    public SubmissionReceipt submit(CurrentUser currentUser, long assignmentId, Path sourceFile) {
        // Check the rules before copying, so a refused submission doesn't copy a large file for nothing.
        database.read(connection -> {
            checkSubmissionAllowed(connection, currentUser, assignmentId, false);
            return null;
        });

        StoredFile storedFile = fileStorage.store(FileStorage.Area.SUBMISSIONS, sourceFile);
        SubmissionChange change;
        try {
            change = database.inTransaction(connection -> {
                SubmissionCheck check = checkSubmissionAllowed(connection, currentUser, assignmentId, true);
                Optional<Submission> previous = check.existingSubmission();
                Instant submittedAt = check.checkedAt();
                if (previous.isEmpty()) {
                    long submissionId = submissionDao.insert(connection, assignmentId, currentUser.id(),
                            storedFile.originalFilename(), storedFile.storageKey(), storedFile.sizeBytes(),
                            submittedAt, check.decision().late());
                    return new SubmissionChange(submissionDao.findById(connection, submissionId).orElseThrow(), null);
                }
                if (!submissionDao.replaceFile(connection, previous.get().id(), storedFile.originalFilename(),
                        storedFile.storageKey(), storedFile.sizeBytes(), submittedAt, check.decision().late(),
                        previous.get().version())) {
                    throw new ConcurrentUpdateException("submission");
                }
                return new SubmissionChange(submissionDao.findById(connection, previous.get().id()).orElseThrow(),
                        previous.get());
            });
        } catch (RuntimeException e) {
            // The database still points at the previous file (if any); discard the new copy.
            fileStorage.deleteIfExists(storedFile.storageKey());
            throw e;
        }

        if (change.replaced() != null) {
            fileStorage.deleteIfExists(change.replaced().storageKey());
        }
        LOGGER.info("Student " + currentUser.id() + " submitted assignment " + assignmentId
                + " (attempt " + change.current().attemptNumber() + ")");
        return new SubmissionReceipt(change.current(), change.replaced() != null);
    }

    /**
     * Copies a submitted file to the chosen destination. Allowed for the course's instructor and
     * for the student who submitted it; nobody else.
     */
    public void downloadSubmission(CurrentUser currentUser, long submissionId, Path destination) {
        Submission submission = database.read(connection -> {
            Submission found = submissionDao.findById(connection, submissionId)
                    .orElseThrow(() -> new NotFoundException("This submission no longer exists."));
            Assignment assignment = findAssignment(connection, found.assignmentId());
            User user = authorization.requireActiveUser(connection, currentUser);
            if (user.hasRole(Role.STUDENT)) {
                if (found.studentId() != user.id()) {
                    throw new AccessDeniedException("You can only download your own submissions.");
                }
                authorization.requireEnrolledStudent(connection, currentUser, assignment.courseId());
            } else {
                authorization.requireInstructorOfCourse(connection, currentUser, assignment.courseId());
            }
            return found;
        });
        fileStorage.copyTo(submission.storageKey(), destination);
    }

    /** Builds each assignment's status for one student; shared with the gradebook and dashboard. */
    List<StudentAssignment> buildStudentAssignments(Connection connection, long studentId, long courseId,
                                                    Instant now) throws SQLException {
        Map<Long, Submission> submissionsByAssignment = submissionDao
                .findByStudentInCourse(connection, studentId, courseId).stream()
                .collect(Collectors.toMap(Submission::assignmentId, Function.identity()));
        Map<Long, Grade> gradesBySubmission = gradeDao.findByStudent(connection, studentId).stream()
                .collect(Collectors.toMap(Grade::submissionId, Function.identity()));

        List<StudentAssignment> result = new ArrayList<>();
        for (Assignment assignment : assignmentDao.findByCourse(connection, courseId)) {
            Submission submission = submissionsByAssignment.get(assignment.id());
            Grade grade = submission == null ? null : gradesBySubmission.get(submission.id());
            result.add(new StudentAssignment(assignment, submission, grade,
                    SubmissionPolicy.progressOf(assignment, submission, grade, now)));
        }
        return result;
    }

    private SubmissionCheck checkSubmissionAllowed(Connection connection, CurrentUser currentUser, long assignmentId,
                                                   boolean lockRows) throws SQLException {
        Assignment assignment = (lockRows
                ? assignmentDao.findByIdForUpdate(connection, assignmentId)
                : assignmentDao.findById(connection, assignmentId))
                .orElseThrow(() -> new NotFoundException("This assignment has been deleted."));
        Course course = authorization.requireEnrolledStudent(connection, currentUser, assignment.courseId());
        if (!course.isActive()) {
            throw new ConflictException("This course is archived, so submissions are closed.");
        }
        Optional<Submission> existing = lockRows
                ? submissionDao.findByAssignmentAndStudentForUpdate(connection, assignmentId, currentUser.id())
                : submissionDao.findByAssignmentAndStudent(connection, assignmentId, currentUser.id());
        boolean alreadyGraded = existing.isPresent()
                && gradeDao.findBySubmission(connection, existing.get().id()).isPresent();
        Instant now = clock.instant();
        SubmissionPolicy.Decision decision = SubmissionPolicy.evaluate(assignment, alreadyGraded, now);
        if (!decision.allowed()) {
            throw new ConflictException(decision.message());
        }
        return new SubmissionCheck(decision, existing, now);
    }

    private Assignment findAssignment(Connection connection, long assignmentId) throws SQLException {
        return assignmentDao.findById(connection, assignmentId)
                .orElseThrow(() -> new NotFoundException("This assignment has been deleted."));
    }

    private record SubmissionCheck(SubmissionPolicy.Decision decision, Optional<Submission> existingSubmission,
                                   Instant checkedAt) {
    }

    /** The submission after saving, and the one it replaced (or {@code null}). */
    private record SubmissionChange(Submission current, Submission replaced) {
    }
}
