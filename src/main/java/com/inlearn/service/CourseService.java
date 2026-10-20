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
import com.inlearn.model.CourseStatus;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.Enrollment;
import com.inlearn.model.Role;
import com.inlearn.model.User;
import com.inlearn.util.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

/**
 * Course administration (create, edit, assign instructor, archive) and course look-ups for
 * instructors and students.
 */
public class CourseService {

    private static final Logger LOGGER = Logger.getLogger(CourseService.class.getName());

    private final Database database;
    private final CourseDao courseDao;
    private final UserDao userDao;
    private final EnrollmentDao enrollmentDao;
    private final AuthorizationService authorization;
    private final Clock clock;

    public CourseService(Database database, CourseDao courseDao, UserDao userDao, EnrollmentDao enrollmentDao,
                         AuthorizationService authorization, Clock clock) {
        this.database = database;
        this.courseDao = courseDao;
        this.userDao = userDao;
        this.enrollmentDao = enrollmentDao;
        this.authorization = authorization;
        this.clock = clock;
    }

    /** The administrator-editable fields of a course. {@code instructorId} may be {@code null}. */
    public record CourseDetails(String code, String title, String description, String term, Long instructorId) {
    }

    // ----- Administrator operations --------------------------------------------------------

    public List<Course> listAllCourses(CurrentUser currentUser) {
        return database.read(connection -> {
            authorization.requireAdministrator(connection, currentUser);
            return courseDao.findAll(connection);
        });
    }

    public Course createCourse(CurrentUser currentUser, CourseDetails details) {
        CourseDetails cleaned = validate(details);
        try {
            Course course = database.inTransaction(connection -> {
                authorization.requireAdministrator(connection, currentUser);
                if (courseDao.codeInUse(connection, cleaned.code(), null)) {
                    throw duplicateCode(cleaned.code());
                }
                requireAssignableInstructor(connection, cleaned.instructorId());
                long courseId = courseDao.insert(connection, cleaned.code(), cleaned.title(), cleaned.description(),
                        cleaned.term(), cleaned.instructorId());
                return courseDao.findById(connection, courseId).orElseThrow();
            });
            LOGGER.info("Administrator " + currentUser.id() + " created course " + course.id());
            return course;
        } catch (DuplicateRecordException e) {
            throw duplicateCode(cleaned.code());
        }
    }

    public Course updateCourse(CurrentUser currentUser, long courseId, CourseDetails details, int expectedVersion) {
        CourseDetails cleaned = validate(details);
        try {
            return database.inTransaction(connection -> {
                authorization.requireAdministrator(connection, currentUser);
                Course existing = findCourse(connection, courseId);
                if (courseDao.codeInUse(connection, cleaned.code(), courseId)) {
                    throw duplicateCode(cleaned.code());
                }
                // Re-checking only when the instructor changes lets an archived course keep a
                // since-deactivated instructor on record.
                if (cleaned.instructorId() != null && !cleaned.instructorId().equals(existing.instructorId())) {
                    requireAssignableInstructor(connection, cleaned.instructorId());
                }
                if (!courseDao.update(connection, courseId, cleaned.code(), cleaned.title(), cleaned.description(),
                        cleaned.term(), cleaned.instructorId(), expectedVersion)) {
                    throw new ConcurrentUpdateException("course");
                }
                return courseDao.findById(connection, courseId).orElseThrow();
            });
        } catch (DuplicateRecordException e) {
            throw duplicateCode(cleaned.code());
        }
    }

    /** Makes a course read-only while keeping all of its records. */
    public Course archiveCourse(CurrentUser currentUser, long courseId, int expectedVersion) {
        return changeStatus(currentUser, courseId, CourseStatus.ARCHIVED, expectedVersion);
    }

    public Course restoreCourse(CurrentUser currentUser, long courseId, int expectedVersion) {
        return changeStatus(currentUser, courseId, CourseStatus.ACTIVE, expectedVersion);
    }

    /** Deletes a course that has no enrollments, assignments, materials or announcements. */
    public void deleteCourse(CurrentUser currentUser, long courseId) {
        database.inTransaction(connection -> {
            authorization.requireAdministrator(connection, currentUser);
            Course course = findCourse(connection, courseId);
            if (courseDao.countDependentRecords(connection, courseId) > 0) {
                throw new ConflictException(course.code() + " already has enrollments or course content, so it "
                        + "can't be deleted. Archive it instead to keep its records.");
            }
            courseDao.delete(connection, courseId);
            LOGGER.info("Administrator " + currentUser.id() + " deleted course " + courseId);
            return null;
        });
    }

    // ----- Instructor and student look-ups -------------------------------------------------

    /** Courses assigned to the signed-in instructor. */
    public List<Course> listTeachingCourses(CurrentUser currentUser) {
        return database.read(connection -> {
            User user = authorization.requireActiveUser(connection, currentUser);
            if (!user.hasRole(Role.INSTRUCTOR)) {
                return List.<Course>of();
            }
            return courseDao.findByInstructor(connection, user.id());
        });
    }

    /** Courses the signed-in student is actively enrolled in. */
    public List<Course> listEnrolledCourses(CurrentUser currentUser) {
        return database.read(connection -> {
            User user = authorization.requireActiveUser(connection, currentUser);
            if (!user.hasRole(Role.STUDENT)) {
                return List.<Course>of();
            }
            return courseDao.findForStudent(connection, user.id());
        });
    }

    /** Loads a course for its instructor or an enrolled student. */
    public Course getCourse(CurrentUser currentUser, long courseId) {
        return database.read(connection -> authorization.requireCourseMember(connection, currentUser, courseId).course());
    }

    public Course updateDescription(CurrentUser currentUser, long courseId, String description, int expectedVersion) {
        String cleanedDescription = InputValidator.optionalText(description, "Course description",
                InputValidator.MAX_LONG_TEXT_LENGTH);
        return database.inTransaction(connection -> {
            Course course = authorization.requireInstructorOfCourse(connection, currentUser, courseId);
            AuthorizationService.requireEditable(course);
            if (!courseDao.updateDescription(connection, courseId, cleanedDescription, expectedVersion)) {
                throw new ConcurrentUpdateException("course");
            }
            return courseDao.findById(connection, courseId).orElseThrow();
        });
    }

    /** The students currently enrolled in one of the instructor's courses. */
    public List<Enrollment> getRoster(CurrentUser currentUser, long courseId) {
        return database.read(connection -> {
            authorization.requireInstructorOfCourse(connection, currentUser, courseId);
            return enrollmentDao.findActiveByCourse(connection, courseId);
        });
    }

    // ----- Helpers -------------------------------------------------------------------------

    private Course changeStatus(CurrentUser currentUser, long courseId, CourseStatus status, int expectedVersion) {
        return database.inTransaction(connection -> {
            authorization.requireAdministrator(connection, currentUser);
            findCourse(connection, courseId);
            Instant archivedAt = status == CourseStatus.ARCHIVED ? clock.instant() : null;
            if (!courseDao.updateStatus(connection, courseId, status, archivedAt, expectedVersion)) {
                throw new ConcurrentUpdateException("course");
            }
            LOGGER.info("Administrator " + currentUser.id() + " set course " + courseId + " to " + status);
            return courseDao.findById(connection, courseId).orElseThrow();
        });
    }

    private void requireAssignableInstructor(Connection connection, Long instructorId) throws SQLException {
        if (instructorId == null) {
            return;
        }
        User instructor = userDao.findById(connection, instructorId)
                .orElseThrow(() -> new ValidationException("The selected instructor no longer exists."));
        if (!instructor.hasRole(Role.INSTRUCTOR) || !instructor.isActive()) {
            throw new ValidationException(instructor.fullName() + " isn't an active instructor, so they can't be "
                    + "assigned to a course.");
        }
    }

    private Course findCourse(Connection connection, long courseId) throws SQLException {
        return courseDao.findById(connection, courseId)
                .orElseThrow(() -> new NotFoundException("This course no longer exists. Refresh the course list."));
    }

    private static CourseDetails validate(CourseDetails details) {
        return new CourseDetails(
                InputValidator.normalizeCourseCode(details.code()),
                InputValidator.requireText(details.title(), "Course title", InputValidator.MAX_TITLE_LENGTH),
                InputValidator.optionalText(details.description(), "Description", InputValidator.MAX_LONG_TEXT_LENGTH),
                InputValidator.requireText(details.term(), "Term", InputValidator.MAX_TERM_LENGTH),
                details.instructorId());
    }

    private static ConflictException duplicateCode(String code) {
        return new ConflictException("A course with the code " + code + " already exists. Use a different code, "
                + "for example by adding the section number.");
    }
}
