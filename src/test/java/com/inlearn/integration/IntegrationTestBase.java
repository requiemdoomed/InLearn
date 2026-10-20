package com.inlearn.integration;

import com.inlearn.AppContext;
import com.inlearn.dao.Database;
import com.inlearn.dao.UserDao;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.LatePolicy;
import com.inlearn.model.Role;
import com.inlearn.model.User;
import com.inlearn.service.AssignmentService.AssignmentDraft;
import com.inlearn.service.CourseService.CourseDetails;
import com.inlearn.service.UserService.AccountDetails;
import com.inlearn.testsupport.MutableClock;
import com.inlearn.util.FileStorage;
import com.inlearn.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;

/**
 * Shared set-up for JDBC integration tests: a fresh schema, real services wired exactly as in the
 * application (with a movable clock and temporary file storage), and an administrator account.
 */
abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Test#Pass1";
    protected static final Instant START = Instant.parse("2026-10-20T16:00:00Z");
    private static final int FAST_BCRYPT_COST = 4;
    private static final long MAX_UPLOAD_BYTES = 1024 * 1024;

    @TempDir
    protected Path storageDirectory;

    @TempDir
    protected Path userFiles;

    protected final MutableClock clock = new MutableClock(START);
    protected TestDatabase testDatabase;
    protected Database database;
    protected FileStorage fileStorage;
    protected AppContext app;
    protected CurrentUser admin;

    @BeforeEach
    void setUpDatabase() {
        testDatabase = TestDatabase.fromSettings();
        testDatabase.resetSchema();
        database = testDatabase.database();
        fileStorage = new FileStorage(storageDirectory, MAX_UPLOAD_BYTES, Set.of("txt", "pdf"));
        app = createContext(database);
        admin = bootstrapAdministrator();
    }

    /** Builds services around a given database, so a test can substitute a failing one. */
    protected AppContext createContext(Database databaseToUse) {
        return new AppContext(databaseToUse, fileStorage, new PasswordHasher(FAST_BCRYPT_COST), clock,
                ZoneId.of("America/Toronto"));
    }

    private CurrentUser bootstrapAdministrator() {
        long id = database.inTransaction(connection -> new UserDao().insert(connection,
                "admin@test.edu", new PasswordHasher(FAST_BCRYPT_COST).hash(PASSWORD.toCharArray()), "Test Admin", Role.ADMIN));
        return new CurrentUser(id, "admin@test.edu", "Test Admin", Role.ADMIN);
    }

    protected CurrentUser createUser(String email, String name, Role role) {
        User user = app.userService().createUser(admin, new AccountDetails(email, name, role), PASSWORD.toCharArray());
        return CurrentUser.from(user);
    }

    protected long createCourse(String code, CurrentUser instructor) {
        return app.courseService().createCourse(admin,
                new CourseDetails(code, code + " title", "Description", "Fall 2026", instructor.id())).id();
    }

    protected void enroll(long courseId, CurrentUser... students) {
        app.enrollmentService().enrollStudents(admin, courseId,
                Arrays.stream(students).map(CurrentUser::id).toList());
    }

    protected long createAssignment(CurrentUser instructor, long courseId, String maxPoints, LatePolicy policy,
                                    Duration dueIn) {
        return app.assignmentService().createAssignment(instructor, courseId, new AssignmentDraft("Lab", "Instructions",
                clock.instant().plus(dueIn), new BigDecimal(maxPoints), policy, null)).id();
    }

    protected Path userFile(String name, String content) {
        try {
            return Files.writeString(userFiles.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
