package com.inlearn.integration;

import com.inlearn.exception.AccessDeniedException;
import com.inlearn.exception.ConcurrentUpdateException;
import com.inlearn.exception.ConflictException;
import com.inlearn.exception.ValidationException;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.Grade;
import com.inlearn.model.Gradebook;
import com.inlearn.model.LatePolicy;
import com.inlearn.model.Role;
import com.inlearn.model.StudentCourseGrades;
import com.inlearn.service.AssignmentService.AssignmentDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grade validation, the gradebook formula and edit restrictions that protect existing grades. */
class GradingIT extends IntegrationTestBase {

    private CurrentUser instructor;
    private CurrentUser student;
    private long course;
    private long lab;
    private long essay;
    private long labSubmission;

    @BeforeEach
    void createGradedWork() {
        instructor = createUser("i@test.edu", "Instructor", Role.INSTRUCTOR);
        student = createUser("s@test.edu", "Student", Role.STUDENT);
        course = createCourse("ENG 300", instructor);
        enroll(course, student);
        lab = createAssignment(instructor, course, "10", LatePolicy.ALLOW_LATE, Duration.ofDays(1));
        essay = createAssignment(instructor, course, "40", LatePolicy.ALLOW_LATE, Duration.ofDays(2));
        createAssignment(instructor, course, "50", LatePolicy.ALLOW_LATE, Duration.ofDays(3));
        labSubmission = app.submissionService().submit(student, lab, userFile("lab.txt", "lab")).submission().id();
        app.submissionService().submit(student, essay, userFile("essay.txt", "essay"));
    }

    @Test
    void gradebookExportsToCsvForTheCoursesOwnInstructorOnly() throws Exception {
        app.gradingService().saveGrade(instructor, labSubmission, new BigDecimal("8.5"), "Good", null);
        Path file = userFiles.resolve("grades.csv");

        assertEquals(1, app.gradingService().exportGradebookCsv(instructor, course, file));
        String csv = Files.readString(file);
        assertTrue(csv.contains("Student,s@test.edu,8.5,Not graded,Not submitted,8.5,10,85.0"));

        CurrentUser otherInstructor = createUser("other@test.edu", "Other", Role.INSTRUCTOR);
        assertThrows(AccessDeniedException.class,
                () -> app.gradingService().exportGradebookCsv(otherInstructor, course, userFiles.resolve("x.csv")));
        assertThrows(AccessDeniedException.class,
                () -> app.gradingService().exportGradebookCsv(student, course, userFiles.resolve("y.csv")));
    }

    @Test
    void gradesOutsideZeroToMaximumAreRejected() {
        assertThrows(ValidationException.class,
                () -> app.gradingService().saveGrade(instructor, labSubmission, new BigDecimal("10.5"), "", null));
        assertThrows(ValidationException.class,
                () -> app.gradingService().saveGrade(instructor, labSubmission, new BigDecimal("-1"), "", null));
        Grade grade = app.gradingService().saveGrade(instructor, labSubmission, new BigDecimal("10"), "Perfect", null);
        assertEquals(0, new BigDecimal("10").compareTo(grade.points()));
    }

    @Test
    void currentGradeIgnoresUngradedWork() {
        app.gradingService().saveGrade(instructor, labSubmission, new BigDecimal("8.5"), "Good", null);

        StudentCourseGrades grades = app.gradingService().getStudentGrades(student, course);
        // Only the lab is graded: 8.5 / 10. The ungraded essay and unsubmitted project are excluded.
        assertEquals("85.0%", grades.courseGrade().percentageLabel());
        assertEquals(1, grades.courseGrade().gradedCount());

        Gradebook gradebook = app.gradingService().getCourseGradebook(instructor, course);
        assertEquals("85.0%", gradebook.rows().getFirst().courseGrade().percentageLabel());
    }

    @Test
    void noGradedWorkShowsNotApplicable() {
        assertEquals("N/A", app.gradingService().getStudentGrades(student, course).courseGrade().percentageLabel());
    }

    @Test
    void regradingWithAStaleVersionIsDetected() {
        Grade first = app.gradingService().saveGrade(instructor, labSubmission, new BigDecimal("6"), "", null);
        app.gradingService().saveGrade(instructor, labSubmission, new BigDecimal("7"), "", first.version());
        assertThrows(ConcurrentUpdateException.class,
                () -> app.gradingService().saveGrade(instructor, labSubmission, new BigDecimal("9"), "", first.version()));
    }

    @Test
    void maximumPointsAreLockedOnceWorkIsGraded() {
        app.gradingService().saveGrade(instructor, labSubmission, new BigDecimal("8"), "", null);
        var context = app.assignmentService().getEditContext(instructor, lab);
        AssignmentDraft changedMaximum = new AssignmentDraft("Lab", "Instructions", context.assignment().dueAt(),
                new BigDecimal("20"), LatePolicy.ALLOW_LATE, null);
        assertThrows(ConflictException.class, () -> app.assignmentService().updateAssignment(instructor, lab,
                changedMaximum, context.assignment().version()));
    }

    @Test
    void movingTheDeadlineEarlierRecalculatesLateStatus() {
        var context = app.assignmentService().getEditContext(instructor, lab);
        AssignmentDraft earlierDeadline = new AssignmentDraft("Lab", "Instructions", START.minus(Duration.ofHours(1)),
                new BigDecimal("10"), LatePolicy.ALLOW_LATE, null);
        var result = app.assignmentService().updateAssignment(instructor, lab, earlierDeadline,
                context.assignment().version());
        assertEquals(1, result.lateStatusChanges());
    }

    @Test
    void disallowingLateWorkIsBlockedWhenLateWorkWasAccepted() {
        clock.advance(Duration.ofDays(5));
        long late = createAssignment(instructor, course, "10", LatePolicy.ALLOW_LATE, Duration.ofHours(1));
        clock.advance(Duration.ofHours(3));
        app.submissionService().submit(student, late, userFile("late.txt", "late"));
        var context = app.assignmentService().getEditContext(instructor, late);
        AssignmentDraft noLate = new AssignmentDraft("Lab", "Instructions", context.assignment().dueAt(),
                new BigDecimal("10"), LatePolicy.NO_LATE, null);
        assertThrows(ConflictException.class, () -> app.assignmentService().updateAssignment(instructor, late, noLate,
                context.assignment().version()));
    }
}
