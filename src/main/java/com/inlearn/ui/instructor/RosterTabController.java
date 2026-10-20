package com.inlearn.ui.instructor;

import com.inlearn.model.Course;
import com.inlearn.model.Enrollment;
import com.inlearn.ui.common.CourseTab;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.TableSupport;
import com.inlearn.ui.support.UiContext;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.Instant;
import java.util.Locale;

/**
 * Instructor Roster tab: the students currently enrolled in the course.
 */
public class RosterTabController implements CourseTab {

    private final UiContext ui;
    private final ObservableList<Enrollment> roster = FXCollections.observableArrayList();
    private final FilteredList<Enrollment> filteredRoster = new FilteredList<>(roster);

    @FXML private TextField searchField;
    @FXML private Label countLabel;
    @FXML private TableView<Enrollment> rosterTable;
    @FXML private TableColumn<Enrollment, String> nameColumn;
    @FXML private TableColumn<Enrollment, String> emailColumn;
    @FXML private TableColumn<Enrollment, Instant> enrolledColumn;

    public RosterTabController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        TableSupport.textColumn(nameColumn, Enrollment::studentName);
        TableSupport.textColumn(emailColumn, Enrollment::studentEmail);
        TableSupport.valueColumn(enrolledColumn, Enrollment::enrolledAt);
        enrolledColumn.setCellFactory(TableSupport.formattedCells(ui.formats()::date));
        SortedList<Enrollment> sorted = new SortedList<>(filteredRoster);
        sorted.comparatorProperty().bind(rosterTable.comparatorProperty());
        rosterTable.setItems(sorted);
        rosterTable.setPlaceholder(EmptyStates.create("No students enrolled yet",
                "Students appear here once an administrator enrolls them in this course."));
        searchField.textProperty().addListener((observable, oldText, newText) -> applyFilter());
    }

    @Override
    public void showCourse(Course course) {
        ui.tasks().task(() -> ui.app().courseService().getRoster(ui.currentUser(), course.id()))
                .onSuccess(enrollments -> {
                    roster.setAll(enrollments);
                    applyFilter();
                })
                .start();
    }

    private void applyFilter() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        filteredRoster.setPredicate(enrollment -> search.isEmpty()
                || enrollment.studentName().toLowerCase(Locale.ROOT).contains(search)
                || enrollment.studentEmail().contains(search));
        countLabel.setText(roster.size() + (roster.size() == 1 ? " student enrolled" : " students enrolled"));
    }
}
