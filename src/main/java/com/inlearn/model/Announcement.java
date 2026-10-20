package com.inlearn.model;

import java.time.Instant;

/**
 * A message an instructor posts to everyone in a course.
 */
public record Announcement(
        long id,
        long courseId,
        String courseCode,
        long authorId,
        String authorName,
        String title,
        String body,
        Instant createdAt,
        Instant updatedAt,
        int version) {

    /** True when the announcement was changed after it was first posted. */
    public boolean wasEdited() {
        return updatedAt != null && createdAt != null && updatedAt.isAfter(createdAt);
    }
}
