package com.inlearn.integration;

import com.inlearn.AppContext;
import com.inlearn.dao.Database;
import com.inlearn.dao.SqlWork;
import com.inlearn.exception.ConflictException;
import com.inlearn.exception.DatabaseUnavailableException;
import com.inlearn.exception.ValidationException;
import com.inlearn.model.AssignmentProgress;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.LatePolicy;
import com.inlearn.model.Role;
import com.inlearn.model.Submission;
import com.inlearn.service.SubmissionService.SubmissionReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLNonTransientConnectionException;
import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deadlines, late policies, replacements and file safety, driven by a movable clock. */
class SubmissionIT extends IntegrationTestBase {

    private CurrentUser instructor;
    private CurrentUser student;
    private long course;

    @BeforeEach
    void createCourse() {
        instructor = createUser("i@test.edu", "Instructor", Role.INSTRUCTOR);
        student = createUser("s@test.edu", "Student", Role.STUDENT);
        course = createCourse("CS 200", instructor);
        enroll(course, student);
    }

    @Test
    void onTimeSubmissionCanBeReplacedBeforeTheDeadline() {
        long assignment = createAssignment(instructor, course, "10", LatePolicy.NO_LATE, Duration.ofDays(1));
        SubmissionReceipt first = app.submissionService().submit(student, assignment, userFile("v1.txt", "first"));
        clock.advance(Duration.ofHours(2));
        SubmissionReceipt second = app.submissionService().submit(student, assignment, userFile("v2.txt", "second"));

        assertFalse(first.replacedPrevious());
        assertTrue(second.replacedPrevious());
        assertEquals(first.submission().id(), second.submission().id(), "one current submission per student");
        assertEquals(2, second.submission().attemptNumber());
        assertEquals("v2.txt", second.submission().originalFilename());
        assertFalse(Files.exists(storageDirectory.resolve(first.submission().storageKey())), "old file cleaned up");
        assertTrue(Files.exists(storageDirectory.resolve(second.submission().storageKey())));
    }

    @Test
    void noLatePolicyRefusesSubmissionsAfterTheDeadline() {
        long assignment = createAssignment(instructor, course, "10", LatePolicy.NO_LATE, Duration.ofDays(1));
        clock.advance(Duration.ofDays(2));
        assertThrows(ConflictException.class,
                () -> app.submissionService().submit(student, assignment, userFile("late.txt", "too late")));
        assertEquals(AssignmentProgress.OVERDUE,
                app.submissionService().listForStudent(student, course).getFirst().progress());
        assertEquals(0, countStoredFiles(), "a refused submission must not leave a file behind");
    }

    @Test
    void allowLatePolicyAcceptsAndMarksLateWork() {
        long assignment = createAssignment(instructor, course, "10", LatePolicy.ALLOW_LATE, Duration.ofDays(1));
        clock.advance(Duration.ofDays(3));
        Submission submission = app.submissionService().submit(student, assignment, userFile("late.txt", "late")).submission();
        assertTrue(submission.late());
        assertEquals(AssignmentProgress.SUBMITTED_LATE,
                app.submissionService().listForStudent(student, course).getFirst().progress());
    }

    @Test
    void gradedSubmissionsCannotBeReplaced() {
        long assignment = createAssignment(instructor, course, "10", LatePolicy.ALLOW_LATE, Duration.ofDays(1));
        Submission submission = app.submissionService().submit(student, assignment, userFile("a.txt", "a")).submission();
        app.gradingService().saveGrade(instructor, submission.id(), new BigDecimal("7"), "ok", null);
        assertThrows(ConflictException.class,
                () -> app.submissionService().submit(student, assignment, userFile("b.txt", "b")));
    }

    @Test
    void invalidFilesAreRejectedBeforeAnythingIsSaved() {
        long assignment = createAssignment(instructor, course, "10", LatePolicy.NO_LATE, Duration.ofDays(1));
        assertThrows(ValidationException.class,
                () -> app.submissionService().submit(student, assignment, userFile("virus.exe", "x")));
        assertThrows(ValidationException.class,
                () -> app.submissionService().submit(student, assignment, userFile("empty.txt", "")));
        assertNull(app.submissionService().listForStudent(student, course).getFirst().submission());
    }

    @Test
    void aDatabaseFailureDuringReplacementKeepsThePreviousSubmission() throws IOException {
        long assignment = createAssignment(instructor, course, "10", LatePolicy.NO_LATE, Duration.ofDays(1));
        Submission original = app.submissionService().submit(student, assignment, userFile("good.txt", "good")).submission();

        // A context whose database drops the connection on the write transaction.
        AppContext failingApp = createContext(new FailingTransactionDatabase(testDatabase.database()));
        assertThrows(DatabaseUnavailableException.class,
                () -> failingApp.submissionService().submit(student, assignment, userFile("new.txt", "new")));

        Submission afterFailure = app.submissionService().listForStudent(student, course).getFirst().submission();
        assertEquals(original.storageKey(), afterFailure.storageKey());
        assertEquals("good", Files.readString(storageDirectory.resolve(original.storageKey())));
        assertEquals(1, countStoredFiles(), "the new copy is removed after the failed transaction");
    }

    private long countStoredFiles() {
        Path submissions = storageDirectory.resolve("submissions");
        if (!Files.isDirectory(submissions)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(submissions)) {
            return files.count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads work normally, but every transaction fails as if the network dropped. */
    private static final class FailingTransactionDatabase extends Database {
        private final Database delegate;

        FailingTransactionDatabase(Database delegate) {
            super("jdbc:unused", "", "", 1);
            this.delegate = delegate;
        }

        @Override
        public <T> T read(SqlWork<T> work) {
            return delegate.read(work);
        }

        @Override
        public <T> T inTransaction(SqlWork<T> work) {
            throw new DatabaseUnavailableException(new SQLNonTransientConnectionException("simulated outage"));
        }
    }
}
