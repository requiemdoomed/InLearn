package com.inlearn.service;

import com.inlearn.dao.Database;
import com.inlearn.dao.MaterialDao;
import com.inlearn.exception.NotFoundException;
import com.inlearn.exception.ValidationException;
import com.inlearn.model.Course;
import com.inlearn.model.CourseMaterial;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.User;
import com.inlearn.util.FileStorage;
import com.inlearn.util.FileStorage.StoredFile;
import com.inlearn.util.InputValidator;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Course materials: files and web links shared by the course's instructor.
 */
public class MaterialService {

    private static final Logger LOGGER = Logger.getLogger(MaterialService.class.getName());

    private final Database database;
    private final MaterialDao materialDao;
    private final AuthorizationService authorization;
    private final FileStorage fileStorage;

    public MaterialService(Database database, MaterialDao materialDao, AuthorizationService authorization,
                           FileStorage fileStorage) {
        this.database = database;
        this.materialDao = materialDao;
        this.authorization = authorization;
        this.fileStorage = fileStorage;
    }

    public List<CourseMaterial> listMaterials(CurrentUser currentUser, long courseId) {
        return database.read(connection -> {
            authorization.requireCourseMember(connection, currentUser, courseId);
            return materialDao.findByCourse(connection, courseId);
        });
    }

    /**
     * Copies the chosen file into managed storage and records it. If saving the record fails,
     * the copied file is removed again so storage never fills with orphans.
     */
    public CourseMaterial addFile(CurrentUser currentUser, long courseId, String title, String description,
                                  Path sourceFile) {
        String cleanedTitle = InputValidator.requireText(title, "Title", InputValidator.MAX_TITLE_LENGTH);
        String cleanedDescription = InputValidator.optionalText(description, "Description",
                InputValidator.MAX_SHORT_TEXT_LENGTH);
        // Check permission before copying anything, so unauthorised users cannot fill the disk.
        database.read(connection -> requireEditableCourse(connection, currentUser, courseId));

        StoredFile storedFile = fileStorage.store(FileStorage.Area.MATERIALS, sourceFile);
        try {
            return database.inTransaction(connection -> {
                User instructor = authorization.requireActiveUser(connection, currentUser);
                requireEditableCourse(connection, currentUser, courseId);
                long materialId = materialDao.insertFile(connection, courseId, cleanedTitle, cleanedDescription,
                        storedFile.originalFilename(), storedFile.storageKey(), storedFile.sizeBytes(), instructor.id());
                return materialDao.findById(connection, materialId).orElseThrow();
            });
        } catch (RuntimeException e) {
            fileStorage.deleteIfExists(storedFile.storageKey());
            throw e;
        }
    }

    public CourseMaterial addLink(CurrentUser currentUser, long courseId, String title, String description,
                                  String url) {
        String cleanedTitle = InputValidator.requireText(title, "Title", InputValidator.MAX_TITLE_LENGTH);
        String cleanedDescription = InputValidator.optionalText(description, "Description",
                InputValidator.MAX_SHORT_TEXT_LENGTH);
        String cleanedUrl = InputValidator.validateWebUrl(url);
        return database.inTransaction(connection -> {
            User instructor = authorization.requireActiveUser(connection, currentUser);
            requireEditableCourse(connection, currentUser, courseId);
            long materialId = materialDao.insertLink(connection, courseId, cleanedTitle, cleanedDescription,
                    cleanedUrl, instructor.id());
            return materialDao.findById(connection, materialId).orElseThrow();
        });
    }

    /** Removes a material. The database row goes first; the stored file is then cleaned up. */
    public void deleteMaterial(CurrentUser currentUser, long materialId) {
        CourseMaterial deleted = database.inTransaction(connection -> {
            CourseMaterial material = findMaterial(connection, materialId);
            requireEditableCourse(connection, currentUser, material.courseId());
            materialDao.delete(connection, materialId);
            return material;
        });
        if (deleted.isFile()) {
            fileStorage.deleteIfExists(deleted.storageKey());
        }
        LOGGER.info("User " + currentUser.id() + " deleted material " + materialId);
    }

    /** Copies a material's file to a location the user chose. */
    public void downloadMaterial(CurrentUser currentUser, long materialId, Path destination) {
        CourseMaterial material = database.read(connection -> {
            CourseMaterial found = findMaterial(connection, materialId);
            authorization.requireCourseMember(connection, currentUser, found.courseId());
            return found;
        });
        if (!material.isFile()) {
            throw new ValidationException("This material is a web link, not a file.");
        }
        fileStorage.copyTo(material.storageKey(), destination);
    }

    /** Returns a link material's address after checking the user may see it. */
    public String getLinkUrl(CurrentUser currentUser, long materialId) {
        CourseMaterial material = database.read(connection -> {
            CourseMaterial found = findMaterial(connection, materialId);
            authorization.requireCourseMember(connection, currentUser, found.courseId());
            return found;
        });
        if (material.isFile()) {
            throw new ValidationException("This material is a file. Use Download instead.");
        }
        return material.linkUrl();
    }

    private Course requireEditableCourse(Connection connection, CurrentUser currentUser, long courseId)
            throws SQLException {
        Course course = authorization.requireInstructorOfCourse(connection, currentUser, courseId);
        AuthorizationService.requireEditable(course);
        return course;
    }

    private CourseMaterial findMaterial(Connection connection, long materialId) throws SQLException {
        return materialDao.findById(connection, materialId)
                .orElseThrow(() -> new NotFoundException("This material has been removed. Refresh the list."));
    }
}
