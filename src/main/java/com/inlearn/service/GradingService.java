package com.inlearn.service;

import com.inlearn.dao.AssignmentDao;
import com.inlearn.dao.CourseDao;
import com.inlearn.dao.Database;
import com.inlearn.dao.EnrollmentDao;
import com.inlearn.dao.GradeDao;
import com.inlearn.dao.SubmissionDao;
import com.inlearn.exception.ConcurrentUpdateException;
import com.inlearn.exception.ConflictException;
import com.inlearn.exception.DuplicateRecordException;
import com.inlearn.exception.NotFoundException;
import com.inlearn.exception.StorageException;
import com.inlearn.exception.ValidationException;
import com.inlearn.model.Assignment;
import com.inlearn.model.Course;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.Enrollment;
import com.inlearn.model.Grade;
import com.inlearn.model.Gradebook;
import com.inlearn.model.GradebookRow;
import com.inlearn.model.GradebookRow.AssignmentResult;
import com.inlearn.model.StudentAssignment;
import com.inlearn.model.StudentCourseGrades;
import com.inlearn.model.Submission;
import com.inlearn.model.SubmissionReviewRow;
import com.inlearn.model.User;
import com.inlearn.util.InputValidator;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Grading submissions and building gradebooks. Only the course's assigned instructor can grade;
 * administrators deliberately do not have grading rights.
 */
public class GradingService {

    private static final Logger LOGGER = Logger.getLogger(GradingService.class.getName());

    private final Database database;
    private final CourseDao courseDao;
    private final AssignmentDao assignmentDao;
    private final SubmissionDao submissionDao;
    private final GradeDao gradeDao;
    private final EnrollmentDao enrollmentDao;
    private final SubmissionService submissionService;
    private final AuthorizationService authorization;
    private final Clock clock;

    public GradingService(Database database, CourseDao courseDao, AssignmentDao assignmentDao,
                          SubmissionDao submissionDao, GradeDao gradeDao, EnrollmentDao enrollmentDao,
                          SubmissionService submissionService, AuthorizationService authorization, Clock clock) {
        this.database = database;
        this.courseDao = courseDao;
        this.assignmentDao = assignmentDao;
        this.submissionDao = submissionDao;
        this.gradeDao = gradeDao;
        this.enrollmentDao = enrollmentDao;
        this.submissionService = submissionService;
        this.authorization = authorization;
        this.clock = clock;
    }

    /** One assignment's submissions, one row per currently enrolled student. */
    public record AssignmentReview(Course course, Assignment assignment, List<SubmissionReviewRow> rows) {
    }

    public AssignmentReview getAssignmentReview(CurrentUser currentUser, long assignmentId) {
        return database.read(connection -> {
            Assignment assignment = findAssignment(connection, assignmentId);
            Course course = authorization.requireInstructorOfCourse(connection, currentUser, assignment.courseId());
            Map<Long, Submission> submissionsByStudent = submissionDao.findByAssignment(connection, assignmentId)
                    .stream().collect(Collectors.toMap(Submission::studentId, Function.identity()));
            Map<Long, Grade> gradesBySubmission = gradeDao.findByAssignment(connection, assignmentId)
                    .stream().collect(Collectors.toMap(Grade::submissionId, Function.identity()));

            List<SubmissionReviewRow> rows = new ArrayList<>();
            for (Enrollment enrollment : enrollmentDao.findActiveByCourse(connection, course.id())) {
                Submission submission = submissionsByStudent.get(enrollment.studentId());
                Grade grade = submission == null ? null : gradesBySubmission.get(submission.id());
                rows.add(new SubmissionReviewRow(enrollment.studentId(), enrollment.studentName(),
                        enrollment.studentEmail(), submission, grade));
            }
            return new AssignmentReview(course, assignment, rows);
        });
    }

    /**
     * Records or updates the grade and feedback for a submission.
     *
     * @param expectedGradeVersion the version of the grade being edited, or {@code null} when grading for the first time
     */
    public Grade saveGrade(CurrentUser currentUser, long submissionId, BigDecimal points, String feedback,
                           Integer expectedGradeVersion) {
        String cleanedFeedback = InputValidator.optionalText(feedback, "Feedback", InputValidator.MAX_LONG_TEXT_LENGTH);
        try {
            Grade saved = database.inTransaction(connection -> {
                Submission submission = submissionDao.findById(connection, submissionId)
                        .orElseThrow(() -> new NotFoundException("This submission no longer exists."));
                Assignment assignment = findAssignment(connection, submission.assignmentId());
                Course course = authorization.requireInstructorOfCourse(connection, currentUser, assignment.courseId());
                AuthorizationService.requireEditable(course);
                User grader = authorization.requireActiveUser(connection, currentUser);
                GradeCalculator.validatePoints(points, assignment.maxPoints());
                if (points.stripTrailingZeros().scale() > InputValidator.POINTS_DECIMAL_PLACES) {
                    throw new ValidationException("Points can have at most two decimal places.");
                }
                BigDecimal storedPoints = points.setScale(InputValidator.POINTS_DECIMAL_PLACES);
                Instant gradedAt = clock.instant();

                Optional<Grade> existing = gradeDao.findBySubmission(connection, submissionId);
                // The screen tells us which grade version it showed. A mismatch means another
                // session graded or regraded the submission in the meantime.
                boolean screenShowedNoGrade = expectedGradeVersion == null;
                if (existing.isEmpty()) {
                    if (!screenShowedNoGrade) {
                        throw new ConcurrentUpdateException("grade");
                    }
                    gradeDao.insert(connection, submissionId, storedPoints, cleanedFeedback, grader.id(), gradedAt);
                } else if (screenShowedNoGrade || !gradeDao.update(connection, existing.get().id(), storedPoints,
                        cleanedFeedback, grader.id(), gradedAt, expectedGradeVersion)) {
                    throw new ConcurrentUpdateException("grade");
                }
                return gradeDao.findBySubmission(connection, submissionId).orElseThrow();
            });
            LOGGER.info("Instructor " + currentUser.id() + " graded submission " + submissionId);
            return saved;
        } catch (DuplicateRecordException e) {
            throw new ConflictException("This submission was graded by someone else just now. "
                    + "Refresh to see the latest grade.", e);
        }
    }

    /** The instructor's gradebook for a course: every enrolled student against every assignment. */
    public Gradebook getCourseGradebook(CurrentUser currentUser, long courseId) {
        return database.read(connection -> {
            Course course = authorization.requireInstructorOfCourse(connection, currentUser, courseId);
            List<Assignment> assignments = assignmentDao.findByCourse(connection, courseId);
            Map<Long, Grade> gradesBySubmission = gradeDao.findByCourse(connection, courseId).stream()
                    .collect(Collectors.toMap(Grade::submissionId, Function.identity()));
            Map<Long, List<Submission>> submissionsByStudent = submissionDao.findByCourse(connection, courseId)
                    .stream().collect(Collectors.groupingBy(Submission::studentId));

            List<GradebookRow> rows = new ArrayList<>();
            for (Enrollment enrollment : enrollmentDao.findActiveByCourse(connection, courseId)) {
                Map<Long, AssignmentResult> results = new LinkedHashMap<>();
                Map<Long, BigDecimal> earnedPoints = new HashMap<>();
                for (Submission submission : submissionsByStudent.getOrDefault(enrollment.studentId(), List.of())) {
                    Grade grade = gradesBySubmission.get(submission.id());
                    results.put(submission.assignmentId(), new AssignmentResult(submission, grade));
                    if (grade != null) {
                        earnedPoints.put(submission.assignmentId(), grade.points());
                    }
                }
                rows.add(new GradebookRow(enrollment.studentId(), enrollment.studentName(), enrollment.studentEmail(),
                        results, GradeCalculator.calculate(assignments, earnedPoints)));
            }
            return new Gradebook(course, assignments, rows);
        });
    }

    /**
     * Writes the course gradebook to a CSV file chosen by the instructor.
     *
     * @return the number of student rows written
     */
    public int exportGradebookCsv(CurrentUser currentUser, long courseId, Path destination) {
        Gradebook gradebook = getCourseGradebook(currentUser, courseId);
        try {
            Files.writeString(destination, GradebookCsvExporter.toCsv(gradebook), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not write gradebook export to " + destination, e);
            throw new StorageException("The gradebook couldn't be saved to " + destination
                    + ". Check that the folder exists, that you can write to it, and that the file isn't open "
                    + "in another program.", e);
        }
        LOGGER.info("Instructor " + currentUser.id() + " exported the gradebook of course " + courseId);
        return gradebook.rows().size();
    }

    /** The signed-in student's grades in one course. */
    public StudentCourseGrades getStudentGrades(CurrentUser currentUser, long courseId) {
        return database.read(connection -> {
            Course course = authorization.requireEnrolledStudent(connection, currentUser, courseId);
            return buildStudentCourseGrades(connection, currentUser.id(), course);
        });
    }

    /** The signed-in student's grades across all of their current courses. */
    public List<StudentCourseGrades> getStudentGradeOverview(CurrentUser currentUser) {
        return database.read(connection -> {
            User student = authorization.requireActiveUser(connection, currentUser);
            List<StudentCourseGrades> overview = new ArrayList<>();
            for (Course course : courseDao.findForStudent(connection, student.id())) {
                overview.add(buildStudentCourseGrades(connection, student.id(), course));
            }
            return overview;
        });
    }

    private StudentCourseGrades buildStudentCourseGrades(Connection connection, long studentId, Course course)
            throws SQLException {
        List<StudentAssignment> assignments = submissionService.buildStudentAssignments(
                connection, studentId, course.id(), clock.instant());
        Map<Long, BigDecimal> earnedPoints = new HashMap<>();
        for (StudentAssignment studentAssignment : assignments) {
            if (studentAssignment.isGraded()) {
                earnedPoints.put(studentAssignment.assignment().id(), studentAssignment.grade().points());
            }
        }
        List<Assignment> courseAssignments = assignments.stream().map(StudentAssignment::assignment).toList();
        return new StudentCourseGrades(course, assignments, GradeCalculator.calculate(courseAssignments, earnedPoints));
    }

    private Assignment findAssignment(Connection connection, long assignmentId) throws SQLException {
        return assignmentDao.findById(connection, assignmentId)
                .orElseThrow(() -> new NotFoundException("This assignment has been deleted."));
    }
}
