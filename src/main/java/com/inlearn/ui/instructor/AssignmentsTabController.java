package com.inlearn.ui.instructor;

import com.inlearn.model.Assignment;
import com.inlearn.model.Course;
import com.inlearn.model.LatePolicy;
import com.inlearn.service.AssignmentService.AssignmentOverview;
import com.inlearn.ui.common.CourseTab;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.StatusBadges;
import com.inlearn.ui.support.StatusBadges.Badge;
import com.inlearn.ui.support.StatusBadges.Tone;
import com.inlearn.ui.support.TableSupport;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UiFormats;
import com.inlearn.ui.support.View;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * Instructor Assignments tab: lists assignments with submission and grading counts.
 */
public class AssignmentsTabController implements CourseTab {

    private static final String ALL = "All assignments";
    private static final String UPCOMING = "Upcoming";
    private static final String PAST_DUE = "Past due";
    private static final String NEEDS_GRADING = "Needs grading";

    private final UiContext ui;
    private final ObservableList<AssignmentOverview> assignments = FXCollections.observableArrayList();
    private final FilteredList<AssignmentOverview> filteredAssignments = new FilteredList<>(assignments);
    private Course course;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterChoice;
    @FXML private Button newButton;
    @FXML private TableView<AssignmentOverview> assignmentTable;
    @FXML private TableColumn<AssignmentOverview, String> titleColumn;
    @FXML private TableColumn<AssignmentOverview, Instant> dueColumn;
    @FXML private TableColumn<AssignmentOverview, BigDecimal> pointsColumn;
    @FXML private TableColumn<AssignmentOverview, String> latePolicyColumn;
    @FXML private TableColumn<AssignmentOverview, String> submittedColumn;
    @FXML private TableColumn<AssignmentOverview, AssignmentOverview> awaitingColumn;
    @FXML private Label zoneLabel;
    @FXML private Button reviewButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;

    public AssignmentsTabController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        filterChoice.getItems().addAll(ALL, UPCOMING, PAST_DUE, NEEDS_GRADING);
        filterChoice.setValue(ALL);
        TableSupport.textColumn(titleColumn, overview -> overview.assignment().title());
        TableSupport.valueColumn(dueColumn, overview -> overview.assignment().dueAt());
        dueColumn.setCellFactory(TableSupport.formattedCells(ui.formats()::shortDateTime));
        TableSupport.valueColumn(pointsColumn, overview -> overview.assignment().maxPoints());
        pointsColumn.setCellFactory(TableSupport.formattedCells(UiFormats::points));
        TableSupport.textColumn(latePolicyColumn, overview -> latePolicySummary(overview.assignment()));
        TableSupport.textColumn(submittedColumn, overview -> overview.submissionCount() + " of " + overview.enrolledCount());
        TableSupport.valueColumn(awaitingColumn, overview -> overview);
        awaitingColumn.setSortable(false);
        awaitingColumn.setCellFactory(StatusBadges.cellFactory(AssignmentsTabController::gradingBadge));

        SortedList<AssignmentOverview> sorted = new SortedList<>(filteredAssignments);
        sorted.comparatorProperty().bind(assignmentTable.comparatorProperty());
        assignmentTable.setItems(sorted);
        assignmentTable.setPlaceholder(EmptyStates.create("No assignments yet",
                "Select New assignment to create the first one for this course."));
        TableSupport.onDoubleClickOrEnter(assignmentTable, overview -> onReview());
        zoneLabel.setText("Times shown in " + ui.formats().zoneDescription());

        searchField.textProperty().addListener((observable, oldText, newText) -> applyFilter());
        filterChoice.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        assignmentTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> updateButtons());
        updateButtons();
    }

    @Override
    public void showCourse(Course loadedCourse) {
        this.course = loadedCourse;
        updateButtons();
        ui.tasks().task(() -> ui.app().assignmentService().listForInstructor(ui.currentUser(), loadedCourse.id()))
                .onSuccess(loaded -> {
                    assignments.setAll(loaded);
                    applyFilter();
                })
                .start();
    }

    @FXML
    private void onNewAssignment() {
        if (course != null) {
            long courseId = course.id();
            ui.navigator().navigate(View.ASSIGNMENT_EDITOR,
                    (AssignmentEditorController controller) -> controller.createIn(courseId));
        }
    }

    @FXML
    private void onEdit() {
        AssignmentOverview selected = assignmentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            long assignmentId = selected.assignment().id();
            ui.navigator().navigate(View.ASSIGNMENT_EDITOR,
                    (AssignmentEditorController controller) -> controller.edit(assignmentId));
        }
    }

    @FXML
    private void onReview() {
        AssignmentOverview selected = assignmentTable.getSelectionModel().getSelectedItem();
        if (selected != null && course != null) {
            long courseId = course.id();
            long assignmentId = selected.assignment().id();
            ui.navigator().navigate(View.SUBMISSION_REVIEW,
                    (SubmissionReviewController controller) -> controller.setAssignment(courseId, assignmentId));
        }
    }

    @FXML
    private void onDelete() {
        AssignmentOverview selected = assignmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!ui.dialogs().confirm("Delete \"" + selected.assignment().title() + "\"?",
                "The assignment will be removed for everyone. This is only possible before anyone has submitted.",
                "Delete assignment", true)) {
            return;
        }
        ui.tasks().task(() -> {
                    ui.app().assignmentService().deleteAssignment(ui.currentUser(), selected.assignment().id());
                    return selected;
                })
                .disabling(deleteButton, editButton, reviewButton)
                .onSuccess(deleted -> {
                    ui.notifier().success("\"" + deleted.assignment().title() + "\" has been deleted.");
                    showCourse(course);
                })
                .start();
    }

    private void applyFilter() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String filter = filterChoice.getValue();
        Instant now = Instant.now();
        filteredAssignments.setPredicate(overview -> {
            boolean matchesSearch = search.isEmpty()
                    || overview.assignment().title().toLowerCase(Locale.ROOT).contains(search);
            boolean matchesFilter = switch (filter) {
                case UPCOMING -> !overview.assignment().isPastDue(now);
                case PAST_DUE -> overview.assignment().isPastDue(now);
                case NEEDS_GRADING -> overview.awaitingGradingCount() > 0;
                default -> true;
            };
            return matchesSearch && matchesFilter;
        });
    }

    private void updateButtons() {
        boolean hasSelection = assignmentTable.getSelectionModel().getSelectedItem() != null;
        boolean editable = course != null && course.isActive();
        newButton.setDisable(!editable);
        reviewButton.setDisable(!hasSelection);
        editButton.setDisable(!hasSelection || !editable);
        deleteButton.setDisable(!hasSelection || !editable);
    }

    private static Badge gradingBadge(AssignmentOverview overview) {
        if (overview.submissionCount() == 0) {
            return new Badge("No submissions yet", Tone.NEUTRAL);
        }
        return overview.awaitingGradingCount() == 0
                ? new Badge("All graded", Tone.SUCCESS)
                : new Badge(overview.awaitingGradingCount() + " to grade", Tone.INFO);
    }

    private String latePolicySummary(Assignment assignment) {
        if (assignment.latePolicy() == LatePolicy.NO_LATE) {
            return "Not accepted";
        }
        return assignment.lateCutoffAt() == null
                ? "Accepted (marked late)"
                : "Until " + ui.formats().shortDateTime(assignment.lateCutoffAt());
    }
}
