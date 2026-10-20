package com.inlearn.ui.instructor;

import com.inlearn.model.Course;
import com.inlearn.ui.common.AnnouncementsTabController;
import com.inlearn.ui.common.CourseTab;
import com.inlearn.ui.common.MaterialsTabController;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.UiContext;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * The instructor's workspace for one course. Each tab has its own controller (loaded with
 * {@code fx:include}); this controller loads the course, checks access, and passes it to them.
 */
public class InstructorCourseController implements Page {

    /** Tabs that other screens can open directly. */
    public enum WorkspaceTab { OVERVIEW, ROSTER, MATERIALS, ASSIGNMENTS, ANNOUNCEMENTS, GRADEBOOK }

    private final UiContext ui;
    private long courseId;
    private WorkspaceTab initialTab;

    @FXML private HBox archivedBanner;
    @FXML private TabPane tabPane;
    @FXML private Tab overviewTab;
    @FXML private Tab rosterTab;
    @FXML private Tab materialsTab;
    @FXML private Tab assignmentsTab;
    @FXML private Tab announcementsTab;
    @FXML private Tab gradebookTab;

    @FXML private CourseOverviewTabController overviewContentController;
    @FXML private RosterTabController rosterContentController;
    @FXML private MaterialsTabController materialsContentController;
    @FXML private AssignmentsTabController assignmentsContentController;
    @FXML private AnnouncementsTabController announcementsContentController;
    @FXML private GradebookTabController gradebookContentController;

    public InstructorCourseController(UiContext ui) {
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

    @Override
    public boolean hasUnsavedChanges() {
        return tabs().stream().anyMatch(CourseTab::hasUnsavedChanges);
    }

    @Override
    public void save() {
        if (tabPane.getSelectionModel().getSelectedItem() == overviewTab) {
            overviewContentController.save();
        }
    }

    private void showCourse(Course course) {
        ui.navigator().setPageTitle(course.displayLabel(), course.term() + " · "
                + course.activeStudentCount() + (course.activeStudentCount() == 1 ? " student" : " students"));
        archivedBanner.setVisible(!course.isActive());
        archivedBanner.setManaged(!course.isActive());
        overviewContentController.showCourse(course);
        rosterContentController.showCourse(course);
        materialsContentController.showCourse(course);
        assignmentsContentController.showCourse(course);
        announcementsContentController.showCourse(course);
        gradebookContentController.showCourse(course);
    }

    private List<CourseTab> tabs() {
        return List.of(overviewContentController, rosterContentController, materialsContentController,
                assignmentsContentController, announcementsContentController, gradebookContentController);
    }

    private Tab tabFor(WorkspaceTab tab) {
        return switch (tab) {
            case OVERVIEW -> overviewTab;
            case ROSTER -> rosterTab;
            case MATERIALS -> materialsTab;
            case ASSIGNMENTS -> assignmentsTab;
            case ANNOUNCEMENTS -> announcementsTab;
            case GRADEBOOK -> gradebookTab;
        };
    }
}
