package com.inlearn.ui.student;

import com.inlearn.model.AssignmentProgress;
import com.inlearn.model.Course;
import com.inlearn.model.CourseGrade;
import com.inlearn.model.StudentAssignment;
import com.inlearn.model.StudentCourseGrades;
import com.inlearn.ui.common.CourseTab;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.StatusBadges;
import com.inlearn.ui.support.TableSupport;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UiFormats;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * A student's grades for one course: the current grade summary and a row per assignment.
 * Used as the Grades tab of the course workspace and on the Grades screen.
 */
public class CourseGradesController implements CourseTab {

    private static final String NOT_GRADED = "Not graded";
    private static final int FEEDBACK_PREVIEW_LENGTH = 80;

    private final UiContext ui;

    @FXML private Label percentageLabel;
    @FXML private Label pointsSummaryLabel;
    @FXML private TableView<StudentAssignment> gradeTable;
    @FXML private TableColumn<StudentAssignment, String> titleColumn;
    @FXML private TableColumn<StudentAssignment, AssignmentProgress> statusColumn;
    @FXML private TableColumn<StudentAssignment, String> earnedColumn;
    @FXML private TableColumn<StudentAssignment, String> possibleColumn;
    @FXML private TableColumn<StudentAssignment, String> percentColumn;
    @FXML private TableColumn<StudentAssignment, String> feedbackColumn;
    @FXML private Label feedbackHeading;
    @FXML private Label feedbackText;

    public CourseGradesController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        TableSupport.textColumn(titleColumn, item -> item.assignment().title());
        TableSupport.valueColumn(statusColumn, StudentAssignment::progress);
        statusColumn.setCellFactory(StatusBadges.cellFactory(StatusBadges::forProgress));
        TableSupport.textColumn(earnedColumn, item -> item.isGraded() ? UiFormats.points(item.grade().points()) : NOT_GRADED);
        TableSupport.textColumn(possibleColumn, item -> UiFormats.points(item.assignment().maxPoints()));
        TableSupport.textColumn(percentColumn, item -> item.isGraded()
                ? UiFormats.percentOf(item.grade().points(), item.assignment().maxPoints()) : "—");
        TableSupport.textColumn(feedbackColumn, CourseGradesController::feedbackPreview);
        gradeTable.setPlaceholder(EmptyStates.create("No assignments yet",
                "Grades appear here once your instructor posts and grades assignments."));
        gradeTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldItem, newItem) -> showFeedback(newItem));
    }

    @Override
    public void showCourse(Course course) {
        ui.tasks().task(() -> ui.app().gradingService().getStudentGrades(ui.currentUser(), course.id()))
                .onSuccess(this::showGrades)
                .start();
    }

    public void showGrades(StudentCourseGrades grades) {
        CourseGrade courseGrade = grades.courseGrade();
        percentageLabel.setText(courseGrade.percentageLabel());
        percentageLabel.setAccessibleText("Current grade " + courseGrade.percentageLabel());
        pointsSummaryLabel.setText(courseGrade.hasGradedWork()
                ? UiFormats.pointsOutOf(courseGrade.earnedPoints(), courseGrade.possiblePoints()) + " points from "
                + courseGrade.gradedCount() + " of " + courseGrade.assignmentCount() + " assignments graded so far."
                : "N/A — none of your " + courseGrade.assignmentCount() + " assignment(s) in this course have been graded yet.");
        gradeTable.getItems().setAll(grades.assignments());
        showFeedback(null);
    }

    private void showFeedback(StudentAssignment item) {
        if (item == null) {
            feedbackHeading.setText("Feedback");
            feedbackText.setText("Select an assignment to read your instructor's full feedback.");
            return;
        }
        feedbackHeading.setText("Feedback · " + item.assignment().title());
        if (!item.isGraded()) {
            feedbackText.setText(item.hasSubmission()
                    ? "Awaiting feedback. Your submission hasn't been graded yet."
                    : "You haven't submitted this assignment.");
        } else if (item.grade().feedback() == null || item.grade().feedback().isBlank()) {
            feedbackText.setText("Your instructor didn't leave written feedback.");
        } else {
            feedbackText.setText(item.grade().feedback());
        }
    }

    private static String feedbackPreview(StudentAssignment item) {
        if (!item.isGraded()) {
            return item.hasSubmission() ? "Awaiting feedback" : "—";
        }
        String feedback = item.grade().feedback();
        if (feedback == null || feedback.isBlank()) {
            return "No written feedback";
        }
        String singleLine = feedback.replaceAll("\\s+", " ").strip();
        return singleLine.length() > FEEDBACK_PREVIEW_LENGTH
                ? singleLine.substring(0, FEEDBACK_PREVIEW_LENGTH) + "…" : singleLine;
    }
}
