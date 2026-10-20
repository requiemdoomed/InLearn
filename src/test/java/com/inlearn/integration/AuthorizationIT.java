package com.inlearn.integration;

import com.inlearn.exception.AccessDeniedException;
import com.inlearn.exception.AccountUnavailableException;
import com.inlearn.model.AccountStatus;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.LatePolicy;
import com.inlearn.model.Role;
import com.inlearn.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Role and ownership rules enforced in the service layer. */
class AuthorizationIT extends IntegrationTestBase {

    private CurrentUser instructorA;
    private CurrentUser instructorB;
    private CurrentUser studentInA;
    private CurrentUser studentInB;
    private long courseA;
    private long courseB;
    private long submissionInA;

    @BeforeEach
    void createTwoCourses() {
        instructorA = createUser("a@test.edu", "Instructor A", Role.INSTRUCTOR);
        instructorB = createUser("b@test.edu", "Instructor B", Role.INSTRUCTOR);
        studentInA = createUser("sa@test.edu", "Student A", Role.STUDENT);
        studentInB = createUser("sb@test.edu", "Student B", Role.STUDENT);
        courseA = createCourse("CS 101", instructorA);
        courseB = createCourse("CS 102", instructorB);
        enroll(courseA, studentInA);
        enroll(courseB, studentInB);
        long assignment = createAssignment(instructorA, courseA, "10", LatePolicy.NO_LATE, Duration.ofDays(2));
        submissionInA = app.submissionService().submit(studentInA, assignment, userFile("work.txt", "answer"))
                .submission().id();
    }

    @Test
    void instructorsCannotManageOtherInstructorsCourses() {
        assertThrows(AccessDeniedException.class, () -> app.courseService().getRoster(instructorB, courseA));
        assertThrows(AccessDeniedException.class,
                () -> app.announcementService().postAnnouncement(instructorB, courseA, "Hi", "Not my course"));
        assertThrows(AccessDeniedException.class, () -> app.gradingService().getCourseGradebook(instructorB, courseA));
        assertThrows(AccessDeniedException.class,
                () -> app.gradingService().saveGrade(instructorB, submissionInA, BigDecimal.ONE, "", null));
        assertDoesNotThrow(() -> app.courseService().getRoster(instructorA, courseA));
    }

    @Test
    void studentsSeeOnlyCoursesTheyAreEnrolledIn() {
        assertThrows(AccessDeniedException.class, () -> app.courseService().getCourse(studentInB, courseA));
        assertThrows(AccessDeniedException.class, () -> app.materialService().listMaterials(studentInB, courseA));
        assertThrows(AccessDeniedException.class, () -> app.gradingService().getStudentGrades(studentInB, courseA));
        assertDoesNotThrow(() -> app.courseService().getCourse(studentInA, courseA));
    }

    @Test
    void studentsCannotDownloadAnotherStudentsSubmission() {
        Path destination = userFiles.resolve("stolen.txt");
        assertThrows(AccessDeniedException.class,
                () -> app.submissionService().downloadSubmission(studentInB, submissionInA, destination));
        assertDoesNotThrow(() -> app.submissionService().downloadSubmission(studentInA, submissionInA,
                userFiles.resolve("mine.txt")));
        assertDoesNotThrow(() -> app.submissionService().downloadSubmission(instructorA, submissionInA,
                userFiles.resolve("review.txt")));
    }

    @Test
    void administratorsDoNotGetGradingPrivileges() {
        assertThrows(AccessDeniedException.class,
                () -> app.gradingService().saveGrade(admin, submissionInA, BigDecimal.TEN, "Looks fine", null));
        assertThrows(AccessDeniedException.class,
                () -> app.submissionService().downloadSubmission(admin, submissionInA, userFiles.resolve("x.txt")));
    }

    @Test
    void nonAdministratorsCannotManageUsers() {
        assertThrows(AccessDeniedException.class, () -> app.userService().listUsers(instructorA));
        assertThrows(AccessDeniedException.class, () -> app.enrollmentService().enrollStudents(studentInA, courseB,
                List.of(studentInA.id())));
    }

    @Test
    void deactivatedAccountsLoseAccessImmediately() {
        User student = app.userService().listUsers(admin).stream()
                .filter(user -> user.id() == studentInA.id()).findFirst().orElseThrow();
        app.userService().setStatus(admin, student.id(), AccountStatus.INACTIVE, student.version());
        assertThrows(AccountUnavailableException.class, () -> app.courseService().getCourse(studentInA, courseA));
    }

    @Test
    void withdrawnStudentsLoseCourseAccess() {
        int version = app.enrollmentService().listEnrollments(admin, courseA).getFirst().version();
        app.enrollmentService().withdrawStudent(admin, courseA, studentInA.id(), version);
        assertThrows(AccessDeniedException.class, () -> app.courseService().getCourse(studentInA, courseA));
    }
}
