package com.inlearn.ui.instructor;

import com.inlearn.model.Assignment;
import com.inlearn.model.Course;
import com.inlearn.model.Gradebook;
import com.inlearn.model.GradebookRow;
import com.inlearn.model.GradebookRow.AssignmentResult;
import com.inlearn.ui.common.CourseTab;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.TableSupport;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UiFormats;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;

/**
 * Instructor Gradebook tab: one row per enrolled student, one column per assignment, plus the
 * current grade calculated from graded work only.
 */
public class GradebookTabController implements CourseTab {

    private static final double STUDENT_COLUMN_WIDTH = 200;
    private static final double ASSIGNMENT_COLUMN_WIDTH = 130;
    private static final double GRADE_COLUMN_WIDTH = 170;
    private static final String NOT_SUBMITTED = "—";
    private static final String NOT_GRADED = "Not graded";

    private final UiContext ui;
    private final ObservableList<GradebookRow> rows = FXCollections.observableArrayList();
    private final FilteredList<GradebookRow> filteredRows = new FilteredList<>(rows);

    @FXML private TextField searchField;
    @FXML private TableView<GradebookRow> gradebookTable;
    @FXML private Button exportButton;
    private Course course;

    public GradebookTabController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        SortedList<GradebookRow> sorted = new SortedList<>(filteredRows);
        sorted.comparatorProperty().bind(gradebookTable.comparatorProperty());
        gradebookTable.setItems(sorted);
        gradebookTable.setPlaceholder(EmptyStates.create("No students to show",
                "Grades appear here once students are enrolled and their work is graded."));
        searchField.textProperty().addListener((observable, oldText, newText) -> applyFilter());
    }

    @Override
    public void showCourse(Course loadedCourse) {
        this.course = loadedCourse;
        ui.tasks().task(() -> ui.app().gradingService().getCourseGradebook(ui.currentUser(), loadedCourse.id()))
                .onSuccess(this::showGradebook)
                .start();
    }

    /** Saves the gradebook as a CSV file for Excel, Numbers or Google Sheets. */
    @FXML
    private void onExport() {
        if (course == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export gradebook");
        chooser.setInitialFileName(course.code().replace(' ', '-') + "-gradebook.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV spreadsheet (*.csv)", "*.csv"));
        File destination = chooser.showSaveDialog(ui.stage());
        if (destination == null) {
            return;
        }
        long courseId = course.id();
        ui.tasks().task(() -> ui.app().gradingService().exportGradebookCsv(ui.currentUser(), courseId, destination.toPath()))
                .disabling(exportButton)
                .onSuccess(rowCount -> ui.notifier().success("Exported grades for " + rowCount
                        + (rowCount == 1 ? " student" : " students") + " to " + destination.getName() + "."))
                .start();
    }

    private void showGradebook(Gradebook gradebook) {
        gradebookTable.getColumns().clear();

        TableColumn<GradebookRow, String> studentColumn = new TableColumn<>("Student");
        TableSupport.textColumn(studentColumn, GradebookRow::studentName);
        studentColumn.setPrefWidth(STUDENT_COLUMN_WIDTH);
        gradebookTable.getColumns().add(studentColumn);

        for (Assignment assignment : gradebook.assignments()) {
            TableColumn<GradebookRow, String> column = new TableColumn<>();
            // A label header lets long assignment titles show in full in a tooltip.
            Label header = new Label(assignment.title());
            header.setTooltip(new Tooltip(assignment.title() + " · out of " + UiFormats.points(assignment.maxPoints())));
            column.setGraphic(header);
            column.setPrefWidth(ASSIGNMENT_COLUMN_WIDTH);
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(resultText(cell.getValue(), assignment)));
            gradebookTable.getColumns().add(column);
        }

        TableColumn<GradebookRow, BigDecimal> gradeColumn = new TableColumn<>("Current grade");
        gradeColumn.setPrefWidth(GRADE_COLUMN_WIDTH);
        TableSupport.valueColumn(gradeColumn, row -> row.courseGrade().percentage().orElse(null));
        gradeColumn.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));
        gradeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal percentage, boolean empty) {
                super.updateItem(percentage, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    return;
                }
                GradebookRow row = getTableRow().getItem();
                setText(row.courseGrade().hasGradedWork()
                        ? row.courseGrade().percentageLabel() + "  ("
                        + UiFormats.pointsOutOf(row.courseGrade().earnedPoints(), row.courseGrade().possiblePoints()) + ")"
                        : "N/A");
            }
        });
        gradebookTable.getColumns().add(gradeColumn);

        rows.setAll(gradebook.rows());
        applyFilter();
    }

    private static String resultText(GradebookRow row, Assignment assignment) {
        AssignmentResult result = row.results().get(assignment.id());
        if (result == null || result.submission() == null) {
            return NOT_SUBMITTED;
        }
        if (result.grade() == null) {
            return NOT_GRADED;
        }
        return UiFormats.pointsOutOf(result.grade().points(), assignment.maxPoints())
                + (result.submission().late() ? " (late)" : "");
    }

    private void applyFilter() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        filteredRows.setPredicate(row -> search.isEmpty()
                || row.studentName().toLowerCase(Locale.ROOT).contains(search)
                || row.studentEmail().contains(search));
    }
}
