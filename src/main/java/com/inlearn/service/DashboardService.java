package com.inlearn.service;

import com.inlearn.dao.AnnouncementDao;
import com.inlearn.dao.AssignmentDao;
import com.inlearn.dao.CourseDao;
import com.inlearn.dao.Database;
import com.inlearn.dao.EnrollmentDao;
import com.inlearn.dao.GradeDao;
import com.inlearn.dao.SubmissionDao;
import com.inlearn.dao.UserDao;
import com.inlearn.exception.AccessDeniedException;
import com.inlearn.model.AdminDashboardStats;
import com.inlearn.model.AssignmentProgress;
import com.inlearn.model.Course;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.InstructorDashboard;
import com.inlearn.model.Role;
import com.inlearn.model.StudentAssignment;
import com.inlearn.model.StudentDashboard;
import com.inlearn.model.StudentDashboard.Deadline;
import com.inlearn.model.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Builds the role-specific dashboards.
 */
public class DashboardService {

    /** How many items each dashboard list shows. */
    public static final int DASHBOARD_LIST_LIMIT = 8;

    /** Overdue work older than this drops off the student's deadline list. */
    private static final Duration OVERDUE_LOOKBACK = Duration.ofDays(14);

    private static final Set<AssignmentProgress> NEEDS_ACTION =
            Set.of(AssignmentProgress.NOT_SUBMITTED, AssignmentProgress.OVERDUE);

    private final Database database;
    private final UserDao userDao;
    private final CourseDao courseDao;
    private final EnrollmentDao enrollmentDao;
    private final AssignmentDao assignmentDao;
    private final SubmissionDao submissionDao;
    private final GradeDao gradeDao;
    private final AnnouncementDao announcementDao;
    private final SubmissionService submissionService;
    private final AuthorizationService authorization;
    private final Clock clock;

    public DashboardService(Database database, UserDao userDao, CourseDao courseDao, EnrollmentDao enrollmentDao,
                            AssignmentDao assignmentDao, SubmissionDao submissionDao, GradeDao gradeDao,
                            AnnouncementDao announcementDao, SubmissionService submissionService,
                            AuthorizationService authorization, Clock clock) {
        this.database = database;
        this.userDao = userDao;
        this.courseDao = courseDao;
        this.enrollmentDao = enrollmentDao;
        this.assignmentDao = assignmentDao;
        this.submissionDao = submissionDao;
        this.gradeDao = gradeDao;
        this.announcementDao = announcementDao;
        this.submissionService = submissionService;
        this.authorization = authorization;
        this.clock = clock;
    }

    public AdminDashboardStats getAdminStats(CurrentUser currentUser) {
        return database.read(connection -> {
            authorization.requireAdministrator(connection, currentUser);
            return new AdminDashboardStats(
                    userDao.countActiveByRole(connection, Role.STUDENT),
                    userDao.countActiveByRole(connection, Role.INSTRUCTOR),
                    courseDao.countActive(connection),
                    enrollmentDao.countActive(connection));
        });
    }

    public InstructorDashboard getInstructorDashboard(CurrentUser currentUser) {
        return database.read(connection -> {
            User instructor = requireRole(authorization.requireActiveUser(connection, currentUser), Role.INSTRUCTOR);
            return new InstructorDashboard(
                    courseDao.findByInstructor(connection, instructor.id()).stream().filter(Course::isActive).toList(),
                    enrollmentDao.countActiveStudentsForInstructor(connection, instructor.id()),
                    assignmentDao.findUpcomingForInstructor(connection, instructor.id(), clock.instant(),
                            DASHBOARD_LIST_LIMIT),
                    submissionDao.findAwaitingGrading(connection, instructor.id(), DASHBOARD_LIST_LIMIT),
                    submissionDao.countAwaitingGrading(connection, instructor.id()));
        });
    }

    /**
     * The student dashboard. Deadlines list work that still needs action: unsubmitted assignments
     * that are upcoming, or overdue within the last two weeks.
     */
    public StudentDashboard getStudentDashboard(CurrentUser currentUser) {
        return database.read(connection -> {
            User student = requireRole(authorization.requireActiveUser(connection, currentUser), Role.STUDENT);
            Instant now = clock.instant();
            List<Course> courses = courseDao.findForStudent(connection, student.id());

            List<Deadline> deadlines = new ArrayList<>();
            for (Course course : courses) {
                if (!course.isActive()) {
                    continue;
                }
                for (StudentAssignment item : submissionService.buildStudentAssignments(
                        connection, student.id(), course.id(), now)) {
                    boolean recentEnough = item.assignment().dueAt().isAfter(now.minus(OVERDUE_LOOKBACK));
                    if (NEEDS_ACTION.contains(item.progress()) && recentEnough) {
                        deadlines.add(new Deadline(item.assignment().id(), course.id(), course.code(),
                                item.assignment().title(), item.assignment().dueAt(), item.progress()));
                    }
                }
            }
            deadlines.sort(Comparator.comparing(Deadline::dueAt));
            List<Deadline> limitedDeadlines = deadlines.stream().limit(DASHBOARD_LIST_LIMIT).toList();

            return new StudentDashboard(
                    courses,
                    limitedDeadlines,
                    announcementDao.findRecentForStudent(connection, student.id(), DASHBOARD_LIST_LIMIT),
                    gradeDao.findRecentForStudent(connection, student.id(), DASHBOARD_LIST_LIMIT));
        });
    }

    private static User requireRole(User user, Role role) {
        if (!user.hasRole(role)) {
            throw new AccessDeniedException("This dashboard is only available to " + role.displayName().toLowerCase()
                    + " accounts.");
        }
        return user;
    }
}
