package com.inlearn.model;

/**
 * A course offering for one term.
 *
 * @param instructorId       the assigned instructor, or {@code null} when none is assigned yet
 * @param instructorName     display name of the assigned instructor, or {@code null}
 * @param activeStudentCount number of students currently enrolled (withdrawn students excluded)
 */
public record Course(
        long id,
        String code,
        String title,
        String description,
        String term,
        Long instructorId,
        String instructorName,
        CourseStatus status,
        int activeStudentCount,
        int version) {

    public boolean isActive() {
        return status == CourseStatus.ACTIVE;
    }

    public boolean hasInstructor() {
        return instructorId != null;
    }

    /** A short label such as "CS 101 · Introduction to Programming". */
    public String displayLabel() {
        return code + " · " + title;
    }

    @Override
    public String toString() {
        return displayLabel();
    }
}
