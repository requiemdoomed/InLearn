package com.inlearn.ui.admin;

import com.inlearn.model.AdminDashboardStats;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.View;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Administrator dashboard: headline counts and shortcuts to the management screens.
 */
public class AdminDashboardController implements Page {

    private final UiContext ui;

    @FXML private Label welcomeLabel;
    @FXML private Label activeStudentsValue;
    @FXML private Label activeInstructorsValue;
    @FXML private Label activeCoursesValue;
    @FXML private Label activeEnrollmentsValue;

    public AdminDashboardController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        welcomeLabel.setText("Welcome back, " + ui.session().user().fullName());
    }

    @Override
    public void onShown() {
        refresh();
    }

    @Override
    public void refresh() {
        ui.tasks().task(() -> ui.app().dashboardService().getAdminStats(ui.currentUser()))
                .onSuccess(this::showStats)
                .start();
    }

    private void showStats(AdminDashboardStats stats) {
        activeStudentsValue.setText(String.valueOf(stats.activeStudents()));
        activeInstructorsValue.setText(String.valueOf(stats.activeInstructors()));
        activeCoursesValue.setText(String.valueOf(stats.activeCourses()));
        activeEnrollmentsValue.setText(String.valueOf(stats.activeEnrollments()));
    }

    @FXML
    private void onAddUser() {
        ui.navigator().navigate(View.USER_MANAGEMENT, (UserManagementController controller) ->
                controller.openNewUserFormWhenShown());
    }

    @FXML
    private void onCreateCourse() {
        ui.navigator().navigate(View.COURSE_MANAGEMENT, (CourseManagementController controller) ->
                controller.openNewCourseFormWhenShown());
    }

    @FXML
    private void onManageEnrollments() {
        ui.navigator().navigate(View.ENROLLMENT_MANAGEMENT);
    }
}
