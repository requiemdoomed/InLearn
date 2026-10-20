package com.inlearn.model;

import java.time.Instant;

/**
 * A file or web link that an instructor shared with a course.
 *
 * @param linkUrl          set for {@link MaterialType#LINK} materials
 * @param originalFilename set for {@link MaterialType#FILE} materials; shown to users
 * @param storageKey       set for {@link MaterialType#FILE} materials; locates the managed copy
 */
public record CourseMaterial(
        long id,
        long courseId,
        String title,
        String description,
        MaterialType type,
        String linkUrl,
        String originalFilename,
        String storageKey,
        Long fileSizeBytes,
        String uploadedByName,
        Instant createdAt) {

    public boolean isFile() {
        return type == MaterialType.FILE;
    }
}
