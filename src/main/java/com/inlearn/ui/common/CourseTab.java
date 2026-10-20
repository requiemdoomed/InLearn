package com.inlearn.ui.common;

import com.inlearn.model.Course;

/**
 * A tab inside a course workspace. The workspace loads the course and hands it to every tab;
 * each tab then loads its own data.
 */
public interface CourseTab {

    /** Shows (or reloads) the tab's content for the course. */
    void showCourse(Course course);

    /** True when the tab holds unsaved edits. */
    default boolean hasUnsavedChanges() {
        return false;
    }
}
