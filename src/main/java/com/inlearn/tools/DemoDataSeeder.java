package com.inlearn.tools;

import com.inlearn.config.AppConfig;
import com.inlearn.config.LoggingSetup;
import com.inlearn.dao.AnnouncementDao;
import com.inlearn.dao.AssignmentDao;
import com.inlearn.dao.CourseDao;
import com.inlearn.dao.Database;
import com.inlearn.dao.DevelopmentDataDao;
import com.inlearn.dao.EnrollmentDao;
import com.inlearn.dao.GradeDao;
import com.inlearn.dao.MaterialDao;
import com.inlearn.dao.SubmissionDao;
import com.inlearn.dao.UserDao;
import com.inlearn.exception.InLearnException;
import com.inlearn.model.LatePolicy;
import com.inlearn.model.Role;
import com.inlearn.util.FileStorage;
import com.inlearn.util.FileStorage.StoredFile;
import com.inlearn.util.PasswordHasher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * DEVELOPMENT ONLY: fills an empty database with sample demo data — one administrator, two
 * instructors, six students, three courses with materials, announcements, upcoming and overdue
 * assignments, and on-time, late and graded submissions.
 *
 * <p>Passwords are hashed with the application's own {@link PasswordHasher}. Deadlines are
 * relative to the moment the seeder runs, so there is always a mix of upcoming and overdue work.
 *
 * <p>Run with {@code mvn -q compile exec:java@seed-demo}. Add {@code -Dexec.args="--reset"} to
 * delete all existing InLearn data first. The seeder refuses to run when
 * {@code app.environment=production}.
 */
public final class DemoDataSeeder {

    public static final String ADMIN_PASSWORD = "Admin#2026";
    public static final String INSTRUCTOR_PASSWORD = "Teach#2026";
    public static final String STUDENT_PASSWORD = "Learn#2026";
    private static final String RESET_FLAG = "--reset";

    private final Database database;
    private final FileStorage fileStorage;
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final UserDao userDao = new UserDao();
    private final CourseDao courseDao = new CourseDao();
    private final EnrollmentDao enrollmentDao = new EnrollmentDao();
    private final MaterialDao materialDao = new MaterialDao();
    private final AssignmentDao assignmentDao = new AssignmentDao();
    private final SubmissionDao submissionDao = new SubmissionDao();
    private final GradeDao gradeDao = new GradeDao();
    private final AnnouncementDao announcementDao = new AnnouncementDao();
    private final DevelopmentDataDao developmentDataDao = new DevelopmentDataDao();
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    private final List<String> storedFileKeys = new ArrayList<>();
    private Path sampleFileDirectory;

    public DemoDataSeeder(Database database, FileStorage fileStorage) {
        this.database = database;
        this.fileStorage = fileStorage;
    }

    public static void main(String[] args) {
        boolean reset = Set.of(args).contains(RESET_FLAG);
        try {
            AppConfig config = AppConfig.load();
            if (config.isProduction()) {
                throw new InLearnException("The demo data seeder only runs in development. "
                        + "app.environment is set to production in " + config.sourceDescription() + ".");
            }
            LoggingSetup.configure(config.loggingDirectory());
            FileStorage fileStorage = new FileStorage(config.storageDirectory(), config.maxUploadBytes(),
                    config.allowedFileExtensions());
            new DemoDataSeeder(Database.fromConfig(config), fileStorage).seed(reset);
            printCredentials();
        } catch (InLearnException e) {
            System.err.println();
            System.err.println("Demo data was not loaded: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Seeds the database. Files are copied into storage first; if the database transaction then
     * fails, those files are removed again so nothing is left half-seeded.
     *
     * @param reset delete all existing InLearn data first
     */
    public void seed(boolean reset) {
        int existingUsers = database.read(developmentDataDao::countUsers);
        if (existingUsers > 0 && !reset) {
            throw new InLearnException("The database already contains " + existingUsers + " user(s). "
                    + "Run again with -Dexec.args=\"--reset\" to delete ALL existing InLearn data and reseed.");
        }
        if (reset) {
            deleteExistingData();
        }
        try {
            sampleFileDirectory = Files.createTempDirectory("inlearn-demo-files");
            database.inTransaction(connection -> {
                insertDemoData(connection);
                return null;
            });
            System.out.println("Demo data loaded: 1 administrator, 2 instructors, 6 students, 3 courses.");
        } catch (RuntimeException e) {
            storedFileKeys.forEach(fileStorage::deleteIfExists);
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create temporary demo files", e);
        } finally {
            deleteSampleFiles();
        }
    }

    private void deleteExistingData() {
        List<String> oldFiles = database.inTransaction(connection -> {
            List<String> keys = developmentDataDao.findAllStorageKeys(connection);
            developmentDataDao.deleteAllData(connection);
            return keys;
        });
        oldFiles.forEach(fileStorage::deleteIfExists);
        System.out.println("Deleted existing InLearn data and " + oldFiles.size() + " stored file(s).");
    }

    private void insertDemoData(Connection connection) throws SQLException {
        // ----- People -------------------------------------------------------------------
        long admin = user(connection, "admin@inlearn.test", "Swayam Shakya", Role.ADMIN, ADMIN_PASSWORD);
        long savyata = user(connection, "savyata.ghimire@inlearn.test", "Dr. Savyata Ghimire", Role.INSTRUCTOR, INSTRUCTOR_PASSWORD);
        long bidhan = user(connection, "bidhan.thapa@inlearn.test", "Prof. Bidhan Thapa", Role.INSTRUCTOR, INSTRUCTOR_PASSWORD);
        long krishna = user(connection, "krishna.bashyal@student.inlearn.test", "Krishna Bashyal", Role.STUDENT, STUDENT_PASSWORD);
        long felix = user(connection, "felix.montalfu@student.inlearn.test", "Felix Montalfu", Role.STUDENT, STUDENT_PASSWORD);
        long vikas = user(connection, "vikas.saahil@student.inlearn.test", "Vikas Saahil", Role.STUDENT, STUDENT_PASSWORD);
        long surakshya = user(connection, "surakshya.neupane@student.inlearn.test", "Surakshya Neupane", Role.STUDENT, STUDENT_PASSWORD);
        long aarav = user(connection, "aarav.karki@student.inlearn.test", "Aarav Karki", Role.STUDENT, STUDENT_PASSWORD);
        long prisha = user(connection, "prisha.adhikari@student.inlearn.test", "Prisha Adhikari", Role.STUDENT, STUDENT_PASSWORD);

        // ----- Courses and enrollments ---------------------------------------------------
        long cs101 = courseDao.insert(connection, "CS 101", "Introduction to Programming",
                "A first course in programming with Java. We cover variables, conditions, loops, methods and simple "
                        + "classes through weekly labs and one project. No prior experience needed.", "Fall 2026", savyata);
        long math201 = courseDao.insert(connection, "MATH 201", "Linear Algebra",
                "Vectors, matrices, linear transformations, determinants and eigenvalues, with applications to "
                        + "computer graphics and data analysis.", "Fall 2026", bidhan);
        long eng110 = courseDao.insert(connection, "ENG 110", "Academic Writing",
                "Plan, draft and revise clear academic essays. Includes peer review and an individual final essay.",
                "Fall 2026", savyata);
        enroll(connection, cs101, krishna, felix, vikas, surakshya, aarav);
        enroll(connection, math201, krishna, vikas, surakshya, prisha);
        enroll(connection, eng110, felix, aarav, prisha, krishna);

        // ----- Materials --------------------------------------------------------------------
        fileMaterial(connection, cs101, savyata, "Course syllabus", "Schedule, grading scheme and policies.",
                "cs101-syllabus.txt", "CS 101 — Introduction to Programming\nFall 2026\n\nWeekly labs: 40%\n"
                        + "Project: 30%\nFinal exam: 30%\n\nOffice hours: Tuesdays 2–4 pm.\n");
        materialDao.insertLink(connection, cs101, "Java documentation", "Official reference for the Java platform.",
                "https://docs.oracle.com/en/java/", savyata);
        fileMaterial(connection, math201, bidhan, "Lecture 1 notes", "Vectors and linear combinations.",
                "math201-lecture-01.md", "# Lecture 1: Vectors\n\nA vector is an ordered list of numbers...\n");
        materialDao.insertLink(connection, math201, "Interactive linear algebra textbook", null,
                "https://textbooks.math.gatech.edu/ila/", bidhan);
        fileMaterial(connection, eng110, savyata, "Essay rubric", "How essays are assessed.", "eng110-rubric.txt",
                "Thesis (20)\nStructure (20)\nEvidence (30)\nStyle (20)\nReferences (10)\n");

        // ----- Assignments, submissions and grades -----------------------------------------
        long lab1 = assignment(connection, cs101, savyata, "Lab 1: Hello, Java",
                "Install the JDK, write a program that prints a greeting, and submit your .java file.",
                daysFromNow(-10), "10", LatePolicy.NO_LATE, null);
        graded(connection, submit(connection, lab1, krishna, daysFromNow(-11), false, "HelloJava.java"),
                "9.5", "Clean, well-formatted code. Remember to add a comment explaining the class.", savyata);
        graded(connection, submit(connection, lab1, felix, daysFromNow(-10.5), false, "Hello.java"),
                "8", "Works correctly. Use a more descriptive class name next time.", savyata);
        submit(connection, lab1, vikas, daysFromNow(-12), false, "Greeting.java");

        long lab2 = assignment(connection, cs101, savyata, "Lab 2: Loops and Conditions",
                "Write a program that prints the FizzBuzz sequence from 1 to 100. Submit your source file.",
                daysFromNow(-2), "20", LatePolicy.ALLOW_LATE, null);
        submit(connection, lab2, krishna, daysFromNow(-3), false, "FizzBuzz.java");
        submit(connection, lab2, felix, daysFromNow(-1), true, "FizzBuzz.java");
        graded(connection, submit(connection, lab2, vikas, daysFromNow(-1.5), true, "FizzBuzz.java"),
                "15", "Correct output. Submitted late, and the loop could be simplified.", savyata);

        long project = assignment(connection, cs101, savyata, "Project 1: Text Adventure",
                "Build a small text adventure game with at least three rooms. Submit a .zip of your project.",
                daysFromNow(7), "50", LatePolicy.ALLOW_LATE, daysFromNow(10));
        submit(connection, project, krishna, daysFromNow(-0.5), false, "text-adventure-draft.txt");

        long problemSet1 = assignment(connection, math201, bidhan, "Problem Set 1",
                "Exercises 1.1–1.12 from the course notes. Show your working.", daysFromNow(-5), "25",
                LatePolicy.NO_LATE, null);
        graded(connection, submit(connection, problemSet1, krishna, daysFromNow(-6), false, "ps1-krishna.txt"),
                "22", "Strong work. Check question 7 — a sign error changed the final answer.", bidhan);
        graded(connection, submit(connection, problemSet1, vikas, daysFromNow(-5.5), false, "ps1.txt"),
                "19.5", "Good method throughout. Questions 10–12 need more justification.", bidhan);
        submit(connection, problemSet1, prisha, daysFromNow(-5.2), false, "problem-set-1.txt");

        assignment(connection, math201, bidhan, "Problem Set 2",
                "Exercises 2.1–2.10 on matrix multiplication and inverses.", daysFromNow(3), "25",
                LatePolicy.ALLOW_LATE, null);

        long outline = assignment(connection, eng110, savyata, "Essay outline",
                "Submit a one-page outline with your thesis statement and three supporting points.",
                daysFromNow(-3), "10", LatePolicy.ALLOW_LATE, null);
        graded(connection, submit(connection, outline, felix, daysFromNow(-4), false, "outline.txt"),
                "9", "A clear, arguable thesis. Point three needs a source.", savyata);
        submit(connection, outline, aarav, daysFromNow(-2), true, "essay-outline.txt");

        assignment(connection, eng110, savyata, "Final essay",
                "A 2,000-word argumentative essay developed from your outline.", daysFromNow(14), "100",
                LatePolicy.NO_LATE, null);

        // ----- Announcements ---------------------------------------------------------------
        announcementDao.insert(connection, cs101, savyata, "Welcome to CS 101",
                "Welcome! Please read the syllabus under Materials and install the JDK before our first lab.");
        announcementDao.insert(connection, cs101, savyata, "Lab 2 is now open for late submissions",
                "The deadline has passed, but you can still submit Lab 2. Late work is marked late.");
        announcementDao.insert(connection, math201, bidhan, "Office hours this week",
                "Office hours move to Thursday 3–5 pm this week only.");
        announcementDao.insert(connection, eng110, savyata, "Peer review next class",
                "Bring a printed copy of your outline for peer review.");

        System.out.println("Created administrator account id " + admin + ".");
    }

    private long user(Connection connection, String email, String fullName, Role role, String password)
            throws SQLException {
        return userDao.insert(connection, email, passwordHasher.hash(password.toCharArray()), fullName, role);
    }

    private void enroll(Connection connection, long courseId, long... studentIds) throws SQLException {
        for (long studentId : studentIds) {
            enrollmentDao.insert(connection, courseId, studentId, now.minus(Duration.ofDays(30)));
        }
    }

    private void fileMaterial(Connection connection, long courseId, long instructorId, String title, String description,
                              String filename, String content) throws SQLException {
        StoredFile stored = storeSampleFile(FileStorage.Area.MATERIALS, filename, content);
        materialDao.insertFile(connection, courseId, title, description, stored.originalFilename(), stored.storageKey(),
                stored.sizeBytes(), instructorId);
    }

    private long assignment(Connection connection, long courseId, long instructorId, String title, String instructions,
                            Instant dueAt, String maxPoints, LatePolicy latePolicy, Instant lateCutoffAt)
            throws SQLException {
        return assignmentDao.insert(connection, courseId, title, instructions, dueAt, new BigDecimal(maxPoints),
                latePolicy, lateCutoffAt, instructorId);
    }

    private long submit(Connection connection, long assignmentId, long studentId, Instant submittedAt, boolean late,
                        String filename) throws SQLException {
        StoredFile stored = storeSampleFile(FileStorage.Area.SUBMISSIONS, filename,
                "Sample submission " + filename + " for assignment " + assignmentId + ".\n");
        return submissionDao.insert(connection, assignmentId, studentId, stored.originalFilename(), stored.storageKey(),
                stored.sizeBytes(), submittedAt, late);
    }

    private void graded(Connection connection, long submissionId, String points, String feedback, long graderId)
            throws SQLException {
        gradeDao.insert(connection, submissionId, new BigDecimal(points), feedback, graderId, now.minus(Duration.ofHours(6)));
    }

    private StoredFile storeSampleFile(FileStorage.Area area, String filename, String content) {
        try {
            Path folder = Files.createTempDirectory(sampleFileDirectory, "file");
            Path sample = folder.resolve(filename);
            Files.writeString(sample, content, StandardCharsets.UTF_8);
            StoredFile stored = fileStorage.store(area, sample);
            storedFileKeys.add(stored.storageKey());
            return stored;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write a demo file", e);
        }
    }

    /** An instant a (possibly fractional) number of days from now; negative values are in the past. */
    private Instant daysFromNow(double days) {
        return now.plus(Duration.ofMinutes(Math.round(days * 24 * 60)));
    }

    private void deleteSampleFiles() {
        if (sampleFileDirectory == null) {
            return;
        }
        // Deepest paths first, so each folder is empty by the time it is deleted.
        try (Stream<Path> paths = Files.walk(sampleFileDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            System.err.println("Note: could not remove temporary demo files in " + sampleFileDirectory
                    + " (" + e.getMessage() + ")");
        }
    }

    private static void printCredentials() {
        System.out.println();
        System.out.println("Demo accounts (development only):");
        System.out.println("  Administrator  admin@inlearn.test                       " + ADMIN_PASSWORD);
        System.out.println("  Instructor     savyata.ghimire@inlearn.test             " + INSTRUCTOR_PASSWORD);
        System.out.println("  Instructor     bidhan.thapa@inlearn.test                " + INSTRUCTOR_PASSWORD);
        System.out.println("  Students       krishna.bashyal@student.inlearn.test     " + STUDENT_PASSWORD);
        System.out.println("                 (also felix.montalfu, vikas.saahil, surakshya.neupane, aarav.karki,"
                + " prisha.adhikari @student.inlearn.test)");
    }
}
