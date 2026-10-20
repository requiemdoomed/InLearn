package com.inlearn.ui.student;

import com.inlearn.model.Announcement;
import com.inlearn.model.AssignmentProgress;
import com.inlearn.model.Course;
import com.inlearn.model.StudentDashboard;
import com.inlearn.model.StudentDashboard.Deadline;
import com.inlearn.model.StudentDashboard.RecentGrade;
import com.inlearn.ui.common.DashboardWidgets;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.StatusBadges;
import com.inlearn.ui.support.StatusBadges.Badge;
import com.inlearn.ui.support.StatusBadges.Tone;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UiFormats;
import com.inlearn.ui.support.View;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.Instant;

/**
 * Student dashboard: enrolled courses, upcoming deadlines, recent announcements and grades.
 */
public class StudentDashboardController implements Page {

    private static final int ANNOUNCEMENT_PREVIEW_LENGTH = 160;

    private final UiContext ui;

    @FXML private Label welcomeLabel;
    @FXML private Label summaryLabel;
    @FXML private FlowPane courseTiles;
    @FXML private Label deadlineZoneLabel;
    @FXML private VBox deadlineList;
    @FXML private VBox gradedList;
    @FXML private VBox announcementList;

    public StudentDashboardController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        welcomeLabel.setText("Welcome back, " + ui.session().user().fullName());
        deadlineZoneLabel.setText("Times shown in " + ui.formats().zoneDescription() + ".");
    }

    @Override
    public void onShown() {
        refresh();
    }

    @Override
    public void refresh() {
        ui.tasks().task(() -> ui.app().dashboardService().getStudentDashboard(ui.currentUser()))
                .onSuccess(this::showDashboard)
                .start();
    }

    private void showDashboard(StudentDashboard dashboard) {
        long overdue = dashboard.deadlines().stream().filter(d -> d.progress() == AssignmentProgress.OVERDUE).count();
        summaryLabel.setText(dashboard.courses().size() + (dashboard.courses().size() == 1 ? " course" : " courses")
                + " · " + dashboard.deadlines().size() + " assignment(s) need attention"
                + (overdue > 0 ? " · " + overdue + " overdue" : ""));
        showCourses(dashboard);
        showDeadlines(dashboard);
        showGrades(dashboard);
        showAnnouncements(dashboard);
    }

    private void showCourses(StudentDashboard dashboard) {
        courseTiles.getChildren().clear();
        if (dashboard.courses().isEmpty()) {
            courseTiles.getChildren().add(EmptyStates.create("You're not enrolled in any courses yet",
                    "Your administrator enrolls you in courses. Once they do, your courses appear here."));
            return;
        }
        for (Course course : dashboard.courses()) {
            String detail = course.term() + (course.hasInstructor() ? " · " + course.instructorName() : "");
            courseTiles.getChildren().add(DashboardWidgets.courseTile(course, detail,
                    () -> openCourse(course.id())));
        }
    }

    private void showDeadlines(StudentDashboard dashboard) {
        deadlineList.getChildren().clear();
        if (dashboard.deadlines().isEmpty()) {
            deadlineList.getChildren().add(EmptyStates.create("No upcoming deadlines",
                    "You're up to date. New assignments will appear here."));
            return;
        }
        Instant now = Instant.now();
        for (Deadline deadline : dashboard.deadlines()) {
            String timing = deadline.progress() == AssignmentProgress.OVERDUE
                    ? "Was due " + ui.formats().dateTime(deadline.dueAt())
                    : "Due " + ui.formats().dateTime(deadline.dueAt()) + " (" + ui.formats().relative(deadline.dueAt(), now) + ")";
            deadlineList.getChildren().add(DashboardWidgets.listItem(deadline.title(),
                    deadline.courseCode() + " · " + timing,
                    StatusBadges.create(StatusBadges.forProgress(deadline.progress())),
                    () -> openAssignment(deadline.assignmentId())));
        }
    }

    private void showGrades(StudentDashboard dashboard) {
        gradedList.getChildren().clear();
        if (dashboard.recentGrades().isEmpty()) {
            gradedList.getChildren().add(EmptyStates.create("No grades yet",
                    "When your instructor grades your work, it shows up here with their feedback."));
            return;
        }
        for (RecentGrade grade : dashboard.recentGrades()) {
            Badge score = new Badge(UiFormats.pointsOutOf(grade.points(), grade.maxPoints())
                    + " · " + UiFormats.percentOf(grade.points(), grade.maxPoints()), Tone.SUCCESS);
            gradedList.getChildren().add(DashboardWidgets.listItem(grade.assignmentTitle(),
                    grade.courseCode() + " · Graded " + ui.formats().shortDateTime(grade.gradedAt()),
                    StatusBadges.create(score), () -> openAssignment(grade.assignmentId())));
        }
    }

    private void showAnnouncements(StudentDashboard dashboard) {
        announcementList.getChildren().clear();
        if (dashboard.recentAnnouncements().isEmpty()) {
            announcementList.getChildren().add(EmptyStates.create("No announcements yet",
                    "Announcements from your instructors will appear here."));
            return;
        }
        for (Announcement announcement : dashboard.recentAnnouncements()) {
            String preview = announcement.body().length() > ANNOUNCEMENT_PREVIEW_LENGTH
                    ? announcement.body().substring(0, ANNOUNCEMENT_PREVIEW_LENGTH).strip() + "…"
                    : announcement.body();
            announcementList.getChildren().add(DashboardWidgets.listItem(announcement.title(),
                    announcement.courseCode() + " · " + announcement.authorName() + " · "
                            + ui.formats().shortDateTime(announcement.createdAt()) + "\n" + preview,
                    null, () -> openCourseTab(announcement.courseId(), StudentCourseController.WorkspaceTab.ANNOUNCEMENTS)));
        }
    }

    private void openCourse(long courseId) {
        openCourseTab(courseId, null);
    }

    private void openCourseTab(long courseId, StudentCourseController.WorkspaceTab tab) {
        ui.navigator().navigate(View.STUDENT_COURSE, (StudentCourseController controller) -> controller.setCourse(courseId, tab));
    }

    private void openAssignment(long assignmentId) {
        ui.navigator().navigate(View.ASSIGNMENT_DETAILS,
                (AssignmentDetailsController controller) -> controller.setAssignment(assignmentId));
    }
}
