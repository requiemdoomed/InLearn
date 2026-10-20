package com.inlearn.service;

import com.inlearn.dao.CourseDao;
import com.inlearn.dao.Database;
import com.inlearn.dao.EnrollmentDao;
import com.inlearn.dao.UserDao;
import com.inlearn.exception.ConcurrentUpdateException;
import com.inlearn.exception.ConflictException;
import com.inlearn.exception.DuplicateRecordException;
import com.inlearn.exception.NotFoundException;
import com.inlearn.exception.ValidationException;
import com.inlearn.model.Course;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.Enrollment;
import com.inlearn.model.Role;
import com.inlearn.model.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Administrator enrollment management. A student has at most one enrollment row per course:
 * withdrawing marks it withdrawn and re-enrolling reactivates it, so history is never lost or
 * duplicated.
 */
public class EnrollmentService {

    private static final Logger LOGGER = Logger.getLogger(EnrollmentService.class.getName());

    private final Database database;
    private final EnrollmentDao enrollmentDao;
    private final CourseDao courseDao;
    private final UserDao userDao;
    private final AuthorizationService authorization;
    private final Clock clock;

    public EnrollmentService(Database database, EnrollmentDao enrollmentDao, CourseDao courseDao, UserDao userDao,
                             AuthorizationService authorization, Clock clock) {
        this.database = database;
        this.enrollmentDao = enrollmentDao;
        this.courseDao = courseDao;
        this.userDao = userDao;
        this.authorization = authorization;
        this.clock = clock;
    }

    /** Every enrollment in a course, including withdrawn ones. */
    public List<Enrollment> listEnrollments(CurrentUser currentUser, long courseId) {
        return database.read(connection -> {
            authorization.requireAdministrator(connection, currentUser);
            findCourse(connection, courseId);
            return enrollmentDao.findByCourse(connection, courseId);
        });
    }

    /**
     * Enrolls one or more students in a course as a single transaction: if any student cannot
     * be enrolled (for example because they are already enrolled), no one is enrolled and the
     * error explains which student caused the problem.
     *
     * @return the number of students enrolled
     */
    public int enrollStudents(CurrentUser currentUser, long courseId, List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new ValidationException("Select at least one student to enroll.");
        }
        Set<Long> uniqueStudentIds = new LinkedHashSet<>(studentIds);
        try {
            int enrolledCount = database.inTransaction(connection -> {
                authorization.requireAdministrator(connection, currentUser);
                Course course = findCourse(connection, courseId);
                AuthorizationService.requireEditable(course);
                Instant now = clock.instant();
                for (long studentId : uniqueStudentIds) {
                    enrollOne(connection, course, studentId, now);
                }
                return uniqueStudentIds.size();
            });
            LOGGER.info("Administrator " + currentUser.id() + " enrolled " + enrolledCount
                    + " student(s) in course " + courseId);
            return enrolledCount;
        } catch (DuplicateRecordException e) {
            // Another administrator enrolled one of these students at the same moment.
            throw new ConflictException("One of these students was enrolled by someone else just now. "
                    + "Refresh the list and try again.", e);
        }
    }

    /** Withdraws a student. Their submissions and grades stay in the database. */
    public Enrollment withdrawStudent(CurrentUser currentUser, long courseId, long studentId, int expectedVersion) {
        return database.inTransaction(connection -> {
            authorization.requireAdministrator(connection, currentUser);
            Course course = findCourse(connection, courseId);
            AuthorizationService.requireEditable(course);
            Enrollment enrollment = enrollmentDao.findByCourseAndStudent(connection, courseId, studentId)
                    .orElseThrow(() -> new NotFoundException("This student isn't enrolled in " + course.code() + "."));
            if (!enrollment.isActive()) {
                throw new ConflictException(enrollment.studentName() + " has already been withdrawn from "
                        + course.code() + ".");
            }
            if (!enrollmentDao.withdraw(connection, enrollment.id(), clock.instant(), expectedVersion)) {
                throw new ConcurrentUpdateException("enrollment");
            }
            LOGGER.info("Administrator " + currentUser.id() + " withdrew student " + studentId
                    + " from course " + courseId);
            return enrollmentDao.findByCourseAndStudent(connection, courseId, studentId).orElseThrow();
        });
    }

    private void enrollOne(Connection connection, Course course, long studentId, Instant now) throws SQLException {
        User student = userDao.findById(connection, studentId)
                .orElseThrow(() -> new ValidationException("One of the selected students no longer exists."));
        if (!student.hasRole(Role.STUDENT)) {
            throw new ValidationException(student.fullName() + " isn't a student account, so they can't be enrolled.");
        }
        if (!student.isActive()) {
            throw new ValidationException(student.fullName() + "'s account is inactive. Reactivate it before enrolling.");
        }
        Optional<Enrollment> existing = enrollmentDao.findByCourseAndStudent(connection, course.id(), studentId);
        if (existing.isEmpty()) {
            enrollmentDao.insert(connection, course.id(), studentId, now);
            return;
        }
        Enrollment enrollment = existing.get();
        if (enrollment.isActive()) {
            throw new ConflictException(student.fullName() + " is already enrolled in " + course.code()
                    + ". No students were enrolled; remove them from the selection and try again.");
        }
        if (!enrollmentDao.reactivate(connection, enrollment.id(), now, enrollment.version())) {
            throw new ConcurrentUpdateException("enrollment");
        }
    }

    private Course findCourse(Connection connection, long courseId) throws SQLException {
        return courseDao.findById(connection, courseId)
                .orElseThrow(() -> new NotFoundException("This course no longer exists. Refresh the course list."));
    }
}
