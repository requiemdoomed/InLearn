package com.inlearn.ui.support;

/**
 * Every screen shown in the main content area, with its FXML file and default title.
 * The {@link NavSection} decides which sidebar entry is highlighted.
 */
public enum View {
    ADMIN_DASHBOARD("admin/admin-dashboard.fxml", "Dashboard", NavSection.DASHBOARD),
    USER_MANAGEMENT("admin/user-management.fxml", "Users", NavSection.USERS),
    COURSE_MANAGEMENT("admin/course-management.fxml", "Courses", NavSection.COURSES),
    ENROLLMENT_MANAGEMENT("admin/enrollment-management.fxml", "Enrollments", NavSection.ENROLLMENTS),

    INSTRUCTOR_DASHBOARD("instructor/instructor-dashboard.fxml", "Dashboard", NavSection.DASHBOARD),
    INSTRUCTOR_COURSE("instructor/instructor-course.fxml", "Course", NavSection.COURSE),
    ASSIGNMENT_EDITOR("instructor/assignment-editor.fxml", "Assignment", NavSection.COURSE),
    SUBMISSION_REVIEW("instructor/submission-review.fxml", "Submissions", NavSection.COURSE),

    STUDENT_DASHBOARD("student/student-dashboard.fxml", "Dashboard", NavSection.DASHBOARD),
    STUDENT_COURSE("student/student-course.fxml", "Course", NavSection.COURSE),
    ASSIGNMENT_DETAILS("student/assignment-details.fxml", "Assignment", NavSection.COURSE),
    STUDENT_GRADEBOOK("student/student-gradebook.fxml", "Grades", NavSection.GRADES),

    PROFILE("common/profile.fxml", "Profile & settings", NavSection.PROFILE);

    /** Sidebar groups. {@code COURSE} highlights the entry for the course being viewed. */
    public enum NavSection { DASHBOARD, USERS, COURSES, ENROLLMENTS, COURSE, GRADES, PROFILE }

    private final String fxmlPath;
    private final String defaultTitle;
    private final NavSection navSection;

    View(String fxmlPath, String defaultTitle, NavSection navSection) {
        this.fxmlPath = fxmlPath;
        this.defaultTitle = defaultTitle;
        this.navSection = navSection;
    }

    public String fxmlPath() {
        return fxmlPath;
    }

    public String defaultTitle() {
        return defaultTitle;
    }

    public NavSection navSection() {
        return navSection;
    }
}
