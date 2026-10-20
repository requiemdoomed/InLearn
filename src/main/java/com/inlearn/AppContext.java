package com.inlearn;

import com.inlearn.config.AppConfig;
import com.inlearn.dao.AnnouncementDao;
import com.inlearn.dao.AssignmentDao;
import com.inlearn.dao.CourseDao;
import com.inlearn.dao.Database;
import com.inlearn.dao.EnrollmentDao;
import com.inlearn.dao.GradeDao;
import com.inlearn.dao.MaterialDao;
import com.inlearn.dao.SubmissionDao;
import com.inlearn.dao.UserDao;
import com.inlearn.service.AnnouncementService;
import com.inlearn.service.AssignmentService;
import com.inlearn.service.AuthService;
import com.inlearn.service.AuthorizationService;
import com.inlearn.service.CourseService;
import com.inlearn.service.DashboardService;
import com.inlearn.service.EnrollmentService;
import com.inlearn.service.GradingService;
import com.inlearn.service.MaterialService;
import com.inlearn.service.ProfileService;
import com.inlearn.service.SubmissionService;
import com.inlearn.service.UserService;
import com.inlearn.util.FileStorage;
import com.inlearn.util.PasswordHasher;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Creates and connects the application's objects in one place ("composition root").
 * Keeping the wiring here means each class just receives what it needs through its constructor,
 * which also makes the services easy to construct in tests with a fixed {@link Clock}.
 */
public final class AppContext {

    private final ZoneId displayZone;
    private final FileStorage fileStorage;
    private final Database database;

    private final AuthService authService;
    private final ProfileService profileService;
    private final UserService userService;
    private final CourseService courseService;
    private final EnrollmentService enrollmentService;
    private final MaterialService materialService;
    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;
    private final GradingService gradingService;
    private final AnnouncementService announcementService;
    private final DashboardService dashboardService;

    public AppContext(Database database, FileStorage fileStorage, PasswordHasher passwordHasher, Clock clock,
                      ZoneId displayZone) {
        this.database = database;
        this.fileStorage = fileStorage;
        this.displayZone = displayZone;

        UserDao userDao = new UserDao();
        CourseDao courseDao = new CourseDao();
        EnrollmentDao enrollmentDao = new EnrollmentDao();
        MaterialDao materialDao = new MaterialDao();
        AssignmentDao assignmentDao = new AssignmentDao();
        SubmissionDao submissionDao = new SubmissionDao();
        GradeDao gradeDao = new GradeDao();
        AnnouncementDao announcementDao = new AnnouncementDao();

        AuthorizationService authorization = new AuthorizationService(userDao, courseDao, enrollmentDao);
        this.authService = new AuthService(database, userDao, authorization, passwordHasher, clock);
        this.profileService = new ProfileService(database, userDao, authorization);
        this.userService = new UserService(database, userDao, authorization, passwordHasher);
        this.courseService = new CourseService(database, courseDao, userDao, enrollmentDao, authorization, clock);
        this.enrollmentService = new EnrollmentService(database, enrollmentDao, courseDao, userDao, authorization, clock);
        this.materialService = new MaterialService(database, materialDao, authorization, fileStorage);
        this.assignmentService = new AssignmentService(database, assignmentDao, submissionDao, gradeDao, enrollmentDao,
                authorization, clock);
        this.submissionService = new SubmissionService(database, assignmentDao, submissionDao, gradeDao, authorization,
                fileStorage, clock);
        this.gradingService = new GradingService(database, courseDao, assignmentDao, submissionDao, gradeDao,
                enrollmentDao, submissionService, authorization, clock);
        this.announcementService = new AnnouncementService(database, announcementDao, authorization);
        this.dashboardService = new DashboardService(database, userDao, courseDao, enrollmentDao, assignmentDao,
                submissionDao, gradeDao, announcementDao, submissionService, authorization, clock);
    }

    /** Builds the production context from the configuration file. */
    public static AppContext fromConfig(AppConfig config) {
        FileStorage fileStorage = new FileStorage(config.storageDirectory(), config.maxUploadBytes(),
                config.allowedFileExtensions());
        return new AppContext(Database.fromConfig(config), fileStorage, new PasswordHasher(), Clock.systemUTC(),
                config.displayZone());
    }

    public ZoneId displayZone() {
        return displayZone;
    }

    public FileStorage fileStorage() {
        return fileStorage;
    }

    public Database database() {
        return database;
    }

    public AuthService authService() {
        return authService;
    }

    public ProfileService profileService() {
        return profileService;
    }

    public UserService userService() {
        return userService;
    }

    public CourseService courseService() {
        return courseService;
    }

    public EnrollmentService enrollmentService() {
        return enrollmentService;
    }

    public MaterialService materialService() {
        return materialService;
    }

    public AssignmentService assignmentService() {
        return assignmentService;
    }

    public SubmissionService submissionService() {
        return submissionService;
    }

    public GradingService gradingService() {
        return gradingService;
    }

    public AnnouncementService announcementService() {
        return announcementService;
    }

    public DashboardService dashboardService() {
        return dashboardService;
    }
}
