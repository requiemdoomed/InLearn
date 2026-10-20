package com.inlearn.ui.instructor;

import com.inlearn.model.Course;
import com.inlearn.model.InstructorDashboard;
import com.inlearn.model.InstructorDashboard.PendingSubmission;
import com.inlearn.model.InstructorDashboard.UpcomingAssignment;
import com.inlearn.ui.common.DashboardWidgets;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.StatusBadges;
import com.inlearn.ui.support.StatusBadges.Badge;
import com.inlearn.ui.support.StatusBadges.Tone;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.View;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.Instant;

/**
 * Instructor dashboard: assigned courses, student count, upcoming work and the grading queue.
 */
public class InstructorDashboardController implements Page {

    private final UiContext ui;

    @FXML private Label welcomeLabel;
    @FXML private Label courseCountValue;
    @FXML private Label studentCountValue;
    @FXML private Label upcomingCountValue;
    @FXML private Label awaitingCountValue;
    @FXML private FlowPane courseTiles;
    @FXML private VBox upcomingList;
    @FXML private VBox awaitingList;

    public InstructorDashboardController(UiContext ui) {
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
        ui.tasks().task(() -> ui.app().dashboardService().getInstructorDashboard(ui.currentUser()))
                .onSuccess(this::showDashboard)
                .start();
    }

    private void showDashboard(InstructorDashboard dashboard) {
        courseCountValue.setText(String.valueOf(dashboard.courses().size()));
        studentCountValue.setText(String.valueOf(dashboard.activeStudentCount()));
        upcomingCountValue.setText(String.valueOf(dashboard.upcomingAssignments().size()));
        awaitingCountValue.setText(String.valueOf(dashboard.awaitingGradingTotal()));
        showCourses(dashboard);
        showUpcoming(dashboard);
        showAwaitingGrading(dashboard);
    }

    private void showCourses(InstructorDashboard dashboard) {
        courseTiles.getChildren().clear();
        if (dashboard.courses().isEmpty()) {
            courseTiles.getChildren().add(EmptyStates.create("No courses assigned yet",
                    "An administrator assigns instructors to courses. Once they do, your courses appear here."));
            return;
        }
        for (Course course : dashboard.courses()) {
            String detail = course.term() + " · " + course.activeStudentCount()
                    + (course.activeStudentCount() == 1 ? " student" : " students");
            courseTiles.getChildren().add(DashboardWidgets.courseTile(course, detail, () -> openCourse(course.id(), null)));
        }
    }

    private void showUpcoming(InstructorDashboard dashboard) {
        upcomingList.getChildren().clear();
        if (dashboard.upcomingAssignments().isEmpty()) {
            upcomingList.getChildren().add(EmptyStates.create("Nothing due soon",
                    "Open a course and select New assignment on its Assignments tab."));
            return;
        }
        Instant now = Instant.now();
        for (UpcomingAssignment assignment : dashboard.upcomingAssignments()) {
            String detail = assignment.courseCode() + " · Due " + ui.formats().dateTime(assignment.dueAt())
                    + " (" + ui.formats().relative(assignment.dueAt(), now) + ")";
            Badge submitted = new Badge(assignment.submissionCount() + " of " + assignment.enrolledCount() + " submitted",
                    Tone.NEUTRAL);
            upcomingList.getChildren().add(DashboardWidgets.listItem(assignment.title(), detail,
                    StatusBadges.create(submitted), () -> openReview(assignment.courseId(), assignment.assignmentId())));
        }
    }

    private void showAwaitingGrading(InstructorDashboard dashboard) {
        awaitingList.getChildren().clear();
        if (dashboard.awaitingGrading().isEmpty()) {
            awaitingList.getChildren().add(EmptyStates.create("You're all caught up",
                    "New submissions will appear here until they're graded."));
            return;
        }
        for (PendingSubmission pending : dashboard.awaitingGrading()) {
            String detail = pending.courseCode() + " · " + pending.assignmentTitle() + " · Submitted "
                    + ui.formats().shortDateTime(pending.submittedAt());
            Badge badge = pending.late() ? new Badge("Late · Needs grading", Tone.WARNING)
                    : new Badge("Needs grading", Tone.INFO);
            awaitingList.getChildren().add(DashboardWidgets.listItem(pending.studentName(), detail,
                    StatusBadges.create(badge), () -> openReview(pending.courseId(), pending.assignmentId())));
        }
        if (dashboard.awaitingGradingTotal() > dashboard.awaitingGrading().size()) {
            Label more = new Label("Showing the oldest " + dashboard.awaitingGrading().size() + " of "
                    + dashboard.awaitingGradingTotal() + ". Open a course to see the rest.");
            more.getStyleClass().add("field-hint");
            more.setWrapText(true);
            awaitingList.getChildren().add(more);
        }
    }

    private void openCourse(long courseId, InstructorCourseController.WorkspaceTab tab) {
        ui.navigator().navigate(View.INSTRUCTOR_COURSE,
                (InstructorCourseController controller) -> controller.setCourse(courseId, tab));
    }

    private void openReview(long courseId, long assignmentId) {
        ui.navigator().navigate(View.SUBMISSION_REVIEW,
                (SubmissionReviewController controller) -> controller.setAssignment(courseId, assignmentId));
    }
}
