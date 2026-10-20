package com.inlearn.ui.student;

import com.inlearn.model.AssignmentProgress;
import com.inlearn.model.Course;
import com.inlearn.model.StudentAssignment;
import com.inlearn.ui.common.CourseTab;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.StatusBadges;
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

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Student Assignments tab: every assignment with its due date and the student's status.
 */
public class StudentAssignmentsTabController implements CourseTab {

    private static final String ALL = "All assignments";
    private static final String TO_DO = "To do";
    private static final String SUBMITTED = "Submitted";
    private static final String GRADED = "Graded";
    private static final Set<AssignmentProgress> TO_DO_STATES = Set.of(AssignmentProgress.NOT_SUBMITTED, AssignmentProgress.OVERDUE);
    private static final Set<AssignmentProgress> SUBMITTED_STATES = Set.of(AssignmentProgress.SUBMITTED, AssignmentProgress.SUBMITTED_LATE);

    private final UiContext ui;
    private final ObservableList<StudentAssignment> assignments = FXCollections.observableArrayList();
    private final FilteredList<StudentAssignment> filteredAssignments = new FilteredList<>(assignments);

    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterChoice;
    @FXML private Label zoneLabel;
    @FXML private TableView<StudentAssignment> assignmentTable;
    @FXML private TableColumn<StudentAssignment, String> titleColumn;
    @FXML private TableColumn<StudentAssignment, Instant> dueColumn;
    @FXML private TableColumn<StudentAssignment, AssignmentProgress> statusColumn;
    @FXML private TableColumn<StudentAssignment, String> scoreColumn;
    @FXML private Button openButton;

    public StudentAssignmentsTabController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        filterChoice.getItems().addAll(ALL, TO_DO, SUBMITTED, GRADED);
        filterChoice.setValue(ALL);
        zoneLabel.setText("Times in " + ui.formats().zoneAbbreviation());
        TableSupport.textColumn(titleColumn, item -> item.assignment().title());
        TableSupport.valueColumn(dueColumn, item -> item.assignment().dueAt());
        dueColumn.setCellFactory(TableSupport.formattedCells(ui.formats()::shortDateTime));
        TableSupport.valueColumn(statusColumn, StudentAssignment::progress);
        statusColumn.setCellFactory(StatusBadges.cellFactory(StatusBadges::forProgress));
        TableSupport.textColumn(scoreColumn, item -> item.isGraded()
                ? UiFormats.pointsOutOf(item.grade().points(), item.assignment().maxPoints())
                : item.hasSubmission() ? "Not graded" : "—");

        SortedList<StudentAssignment> sorted = new SortedList<>(filteredAssignments);
        sorted.comparatorProperty().bind(assignmentTable.comparatorProperty());
        assignmentTable.setItems(sorted);
        assignmentTable.setPlaceholder(EmptyStates.create("No assignments yet",
                "Your instructor hasn't posted any assignments for this course."));
        TableSupport.onDoubleClickOrEnter(assignmentTable, item -> onOpen());
        searchField.textProperty().addListener((observable, oldText, newText) -> applyFilter());
        filterChoice.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        assignmentTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> openButton.setDisable(newValue == null));
        openButton.setDisable(true);
    }

    @Override
    public void showCourse(Course course) {
        ui.tasks().task(() -> ui.app().submissionService().listForStudent(ui.currentUser(), course.id()))
                .onSuccess(loaded -> {
                    assignments.setAll(loaded);
                    applyFilter();
                })
                .start();
    }

    @FXML
    private void onOpen() {
        StudentAssignment selected = assignmentTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            long assignmentId = selected.assignment().id();
            ui.navigator().navigate(View.ASSIGNMENT_DETAILS,
                    (AssignmentDetailsController controller) -> controller.setAssignment(assignmentId));
        }
    }

    private void applyFilter() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String filter = filterChoice.getValue();
        filteredAssignments.setPredicate(item -> {
            boolean matchesSearch = search.isEmpty() || item.assignment().title().toLowerCase(Locale.ROOT).contains(search);
            boolean matchesFilter = switch (filter) {
                case TO_DO -> TO_DO_STATES.contains(item.progress());
                case SUBMITTED -> SUBMITTED_STATES.contains(item.progress());
                case GRADED -> item.progress() == AssignmentProgress.GRADED;
                default -> true;
            };
            return matchesSearch && matchesFilter;
        });
    }
}
