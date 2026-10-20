package com.inlearn.ui.student;

import com.inlearn.model.Course;
import com.inlearn.ui.common.AnnouncementsTabController;
import com.inlearn.ui.common.MaterialsTabController;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.UiContext;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;

/**
 * The student's workspace for one enrolled course.
 */
public class StudentCourseController implements Page {

    /** Tabs that other screens can open directly. */
    public enum WorkspaceTab { OVERVIEW, ASSIGNMENTS, MATERIALS, ANNOUNCEMENTS, GRADES }

    private final UiContext ui;
    private long courseId;
    private WorkspaceTab initialTab;

    @FXML private HBox archivedBanner;
    @FXML private TabPane tabPane;
    @FXML private Tab overviewTab;
    @FXML private Tab assignmentsTab;
    @FXML private Tab materialsTab;
    @FXML private Tab announcementsTab;
    @FXML private Tab gradesTab;

    @FXML private StudentOverviewTabController overviewContentController;
    @FXML private StudentAssignmentsTabController assignmentsContentController;
    @FXML private MaterialsTabController materialsContentController;
    @FXML private AnnouncementsTabController announcementsContentController;
    @FXML private CourseGradesController gradesContentController;

    public StudentCourseController(UiContext ui) {
        this.ui = ui;
    }

    /**
     * @param tab the tab to open first, or {@code null} for the Overview
     */
    public void setCourse(long courseId, WorkspaceTab tab) {
        this.courseId = courseId;
        this.initialTab = tab;
    }

    @Override
    public void onShown() {
        if (initialTab != null) {
            tabPane.getSelectionModel().select(tabFor(initialTab));
        }
        ui.navigator().highlightCourse(courseId);
        refresh();
    }

    @Override
    public void refresh() {
        ui.tasks().task(() -> ui.app().courseService().getCourse(ui.currentUser(), courseId))
                .onSuccess(this::showCourse)
                .start();
    }

    private void showCourse(Course course) {
        ui.navigator().setPageTitle(course.displayLabel(), course.term()
                + (course.hasInstructor() ? " · Instructor: " + course.instructorName() : ""));
        archivedBanner.setVisible(!course.isActive());
        archivedBanner.setManaged(!course.isActive());
        overviewContentController.showCourse(course);
        assignmentsContentController.showCourse(course);
        materialsContentController.showCourse(course);
        announcementsContentController.showCourse(course);
        gradesContentController.showCourse(course);
    }

    private Tab tabFor(WorkspaceTab tab) {
        return switch (tab) {
            case OVERVIEW -> overviewTab;
            case ASSIGNMENTS -> assignmentsTab;
            case MATERIALS -> materialsTab;
            case ANNOUNCEMENTS -> announcementsTab;
            case GRADES -> gradesTab;
        };
    }
}
