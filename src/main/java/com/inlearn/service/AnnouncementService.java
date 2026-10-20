package com.inlearn.service;

import com.inlearn.dao.AnnouncementDao;
import com.inlearn.dao.Database;
import com.inlearn.exception.ConcurrentUpdateException;
import com.inlearn.exception.NotFoundException;
import com.inlearn.model.Announcement;
import com.inlearn.model.Course;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.User;
import com.inlearn.util.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Course announcements: posted and edited by the course's instructor, read by enrolled students.
 */
public class AnnouncementService {

    private final Database database;
    private final AnnouncementDao announcementDao;
    private final AuthorizationService authorization;

    public AnnouncementService(Database database, AnnouncementDao announcementDao, AuthorizationService authorization) {
        this.database = database;
        this.announcementDao = announcementDao;
        this.authorization = authorization;
    }

    public List<Announcement> listAnnouncements(CurrentUser currentUser, long courseId) {
        return database.read(connection -> {
            authorization.requireCourseMember(connection, currentUser, courseId);
            return announcementDao.findByCourse(connection, courseId);
        });
    }

    public Announcement postAnnouncement(CurrentUser currentUser, long courseId, String title, String body) {
        String cleanedTitle = InputValidator.requireText(title, "Title", InputValidator.MAX_TITLE_LENGTH);
        String cleanedBody = InputValidator.requireText(body, "Message", InputValidator.MAX_LONG_TEXT_LENGTH);
        return database.inTransaction(connection -> {
            User author = authorization.requireActiveUser(connection, currentUser);
            requireEditableCourse(connection, currentUser, courseId);
            long announcementId = announcementDao.insert(connection, courseId, author.id(), cleanedTitle, cleanedBody);
            return announcementDao.findById(connection, announcementId).orElseThrow();
        });
    }

    public Announcement updateAnnouncement(CurrentUser currentUser, long announcementId, String title, String body,
                                           int expectedVersion) {
        String cleanedTitle = InputValidator.requireText(title, "Title", InputValidator.MAX_TITLE_LENGTH);
        String cleanedBody = InputValidator.requireText(body, "Message", InputValidator.MAX_LONG_TEXT_LENGTH);
        return database.inTransaction(connection -> {
            Announcement existing = findAnnouncement(connection, announcementId);
            requireEditableCourse(connection, currentUser, existing.courseId());
            if (!announcementDao.update(connection, announcementId, cleanedTitle, cleanedBody, expectedVersion)) {
                throw new ConcurrentUpdateException("announcement");
            }
            return announcementDao.findById(connection, announcementId).orElseThrow();
        });
    }

    public void deleteAnnouncement(CurrentUser currentUser, long announcementId) {
        database.inTransaction(connection -> {
            Announcement existing = findAnnouncement(connection, announcementId);
            requireEditableCourse(connection, currentUser, existing.courseId());
            announcementDao.delete(connection, announcementId);
            return null;
        });
    }

    private void requireEditableCourse(Connection connection, CurrentUser currentUser, long courseId)
            throws SQLException {
        Course course = authorization.requireInstructorOfCourse(connection, currentUser, courseId);
        AuthorizationService.requireEditable(course);
    }

    private Announcement findAnnouncement(Connection connection, long announcementId) throws SQLException {
        return announcementDao.findById(connection, announcementId)
                .orElseThrow(() -> new NotFoundException("This announcement has been deleted. Refresh the list."));
    }
}
