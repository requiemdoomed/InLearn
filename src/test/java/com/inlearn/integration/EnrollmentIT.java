package com.inlearn.integration;

import com.inlearn.exception.ConflictException;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.Enrollment;
import com.inlearn.model.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Duplicate enrollment prevention and transaction rollback. */
class EnrollmentIT extends IntegrationTestBase {

    @Test
    void enrollingTheSameStudentTwiceIsRejected() {
        CurrentUser instructor = createUser("i@test.edu", "Instructor", Role.INSTRUCTOR);
        CurrentUser student = createUser("s@test.edu", "Student", Role.STUDENT);
        long course = createCourse("BIO 100", instructor);
        enroll(course, student);

        ConflictException error = assertThrows(ConflictException.class, () -> enroll(course, student));
        assertTrue(error.getMessage().contains("already enrolled"));
        assertEquals(1, app.enrollmentService().listEnrollments(admin, course).size());
    }

    @Test
    void aFailedBatchEnrollmentRollsBackEveryStudent() {
        CurrentUser instructor = createUser("i@test.edu", "Instructor", Role.INSTRUCTOR);
        CurrentUser alreadyEnrolled = createUser("a@test.edu", "Already Enrolled", Role.STUDENT);
        CurrentUser newStudent = createUser("n@test.edu", "New Student", Role.STUDENT);
        CurrentUser anotherNewStudent = createUser("m@test.edu", "Another New", Role.STUDENT);
        long course = createCourse("CHEM 100", instructor);
        enroll(course, alreadyEnrolled);

        // The duplicate is last, so two inserts have already run when the batch fails.
        assertThrows(ConflictException.class, () -> app.enrollmentService().enrollStudents(admin, course,
                List.of(newStudent.id(), anotherNewStudent.id(), alreadyEnrolled.id())));

        List<Enrollment> enrollments = app.enrollmentService().listEnrollments(admin, course);
        assertEquals(1, enrollments.size(), "the partial batch must have been rolled back");
        assertEquals(alreadyEnrolled.id(), enrollments.getFirst().studentId());
    }

    @Test
    void reEnrollingAWithdrawnStudentReactivatesTheSameRecord() {
        CurrentUser instructor = createUser("i@test.edu", "Instructor", Role.INSTRUCTOR);
        CurrentUser student = createUser("s@test.edu", "Student", Role.STUDENT);
        long course = createCourse("ART 100", instructor);
        enroll(course, student);
        Enrollment original = app.enrollmentService().listEnrollments(admin, course).getFirst();
        app.enrollmentService().withdrawStudent(admin, course, student.id(), original.version());

        enroll(course, student);

        List<Enrollment> enrollments = app.enrollmentService().listEnrollments(admin, course);
        assertEquals(1, enrollments.size());
        assertEquals(original.id(), enrollments.getFirst().id());
        assertTrue(enrollments.getFirst().isActive());
    }

    @Test
    void instructorsCannotBeEnrolledAsStudents() {
        CurrentUser instructor = createUser("i@test.edu", "Instructor", Role.INSTRUCTOR);
        long course = createCourse("PHYS 100", instructor);
        assertThrows(RuntimeException.class, () -> enroll(course, instructor));
    }
}
