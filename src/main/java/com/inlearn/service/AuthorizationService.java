package com.inlearn.service;

import com.inlearn.dao.CourseDao;
import com.inlearn.dao.EnrollmentDao;
import com.inlearn.dao.UserDao;
import com.inlearn.exception.AccessDeniedException;
import com.inlearn.exception.AccountUnavailableException;
import com.inlearn.exception.ConflictException;
import com.inlearn.exception.NotFoundException;
import com.inlearn.model.Course;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.Role;
import com.inlearn.model.User;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Central permission checks used by every service before it reads or changes protected data.
 *
 * <p>Each check reloads the signed-in account from the database, so a user who was deactivated
 * or given a different role while signed in loses access immediately. Hiding buttons in the
 * interface is only a convenience; these checks are what actually enforce the rules.
 *
 * <p>Rules:
 * <ul>
 *   <li>Administrators manage users, courses, instructor assignments and enrollments.</li>
 *   <li>Instructors manage only the courses assigned to them. Administrators do <em>not</em>
 *       automatically get instructor powers such as grading.</li>
 *   <li>Students see only courses they are actively enrolled in, and only their own work.</li>
 * </ul>
 */
public class AuthorizationService {

    private final UserDao userDao;
    private final CourseDao courseDao;
    private final EnrollmentDao enrollmentDao;

    public AuthorizationService(UserDao userDao, CourseDao courseDao, EnrollmentDao enrollmentDao) {
        this.userDao = userDao;
        this.courseDao = courseDao;
        this.enrollmentDao = enrollmentDao;
    }

    /** Reloads the account and confirms it still exists and is active. */
    public User requireActiveUser(Connection connection, CurrentUser currentUser) throws SQLException {
        if (currentUser == null) {
            throw new AccessDeniedException("Please sign in to continue.");
        }
        User user = userDao.findById(connection, currentUser.id()).orElseThrow(AccountUnavailableException::new);
        if (!user.isActive()) {
            throw new AccountUnavailableException();
        }
        return user;
    }

    public User requireAdministrator(Connection connection, CurrentUser currentUser) throws SQLException {
        User user = requireActiveUser(connection, currentUser);
        if (!user.hasRole(Role.ADMIN)) {
            throw new AccessDeniedException("Only administrators can do this.");
        }
        return user;
    }

    /** Confirms the user is an instructor and is the instructor assigned to the course. */
    public Course requireInstructorOfCourse(Connection connection, CurrentUser currentUser, long courseId)
            throws SQLException {
        User user = requireActiveUser(connection, currentUser);
        Course course = findCourse(connection, courseId);
        if (!user.hasRole(Role.INSTRUCTOR) || course.instructorId() == null || course.instructorId() != user.id()) {
            throw new AccessDeniedException("You can only manage courses that are assigned to you.");
        }
        return course;
    }

    /** Confirms the user is a student actively enrolled in the course. */
    public Course requireEnrolledStudent(Connection connection, CurrentUser currentUser, long courseId)
            throws SQLException {
        User user = requireActiveUser(connection, currentUser);
        Course course = findCourse(connection, courseId);
        if (!user.hasRole(Role.STUDENT) || !enrollmentDao.isActivelyEnrolled(connection, courseId, user.id())) {
            throw new AccessDeniedException("You can only open courses you're enrolled in.");
        }
        return course;
    }

    /**
     * Allows the course's instructor or an actively enrolled student to view course content
     * such as materials and announcements.
     */
    public CourseAccess requireCourseMember(Connection connection, CurrentUser currentUser, long courseId)
            throws SQLException {
        User user = requireActiveUser(connection, currentUser);
        Course course = findCourse(connection, courseId);
        boolean isAssignedInstructor = user.hasRole(Role.INSTRUCTOR)
                && course.instructorId() != null && course.instructorId() == user.id();
        boolean isEnrolledStudent = user.hasRole(Role.STUDENT)
                && enrollmentDao.isActivelyEnrolled(connection, courseId, user.id());
        if (!isAssignedInstructor && !isEnrolledStudent) {
            throw new AccessDeniedException("You don't have access to this course.");
        }
        return new CourseAccess(user, course, isAssignedInstructor);
    }

    /** Archived courses are read-only for everyone. */
    public static void requireEditable(Course course) {
        if (!course.isActive()) {
            throw new ConflictException(course.code() + " is archived, so it's read-only. "
                    + "An administrator can restore the course if changes are needed.");
        }
    }

    private Course findCourse(Connection connection, long courseId) throws SQLException {
        return courseDao.findById(connection, courseId)
                .orElseThrow(() -> new NotFoundException("This course no longer exists."));
    }

    /**
     * The result of a course-membership check.
     *
     * @param instructor true if the user is the course's instructor, false if an enrolled student
     */
    public record CourseAccess(User user, Course course, boolean instructor) {
    }
}
