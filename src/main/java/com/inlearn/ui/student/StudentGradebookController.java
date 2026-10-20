package com.inlearn.ui.student;

import com.inlearn.model.StudentCourseGrades;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.UiContext;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.util.List;

/**
 * The student gradebook: current grade for each enrolled course, with per-assignment detail.
 */
public class StudentGradebookController implements Page {

    private final UiContext ui;

    @FXML private ListView<StudentCourseGrades> courseList;
    @FXML private Label selectedCourseLabel;
    @FXML private CourseGradesController gradesController;

    public StudentGradebookController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        courseList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(StudentCourseGrades grades, boolean empty) {
                super.updateItem(grades, empty);
                if (empty || grades == null) {
                    setText(null);
                    setAccessibleText(null);
                } else {
                    String text = grades.course().code() + "  ·  " + grades.courseGrade().percentageLabel();
                    setText(text);
                    setAccessibleText(grades.course().displayLabel() + ", current grade "
                            + grades.courseGrade().percentageLabel());
                }
            }
        });
        courseList.setPlaceholder(EmptyStates.create("No courses yet",
                "Once you're enrolled in a course, your grades appear here."));
        courseList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                selectedCourseLabel.setText(selected.course().displayLabel());
                gradesController.showGrades(selected);
            }
        });
    }

    @Override
    public void onShown() {
        refresh();
    }

    @Override
    public void refresh() {
        ui.tasks().task(() -> ui.app().gradingService().getStudentGradeOverview(ui.currentUser()))
                .onSuccess(this::showOverview)
                .start();
    }

    private void showOverview(List<StudentCourseGrades> overview) {
        Long selectedCourseId = courseList.getSelectionModel().getSelectedItem() == null
                ? null : courseList.getSelectionModel().getSelectedItem().course().id();
        courseList.getItems().setAll(overview);
        if (overview.isEmpty()) {
            selectedCourseLabel.setText("");
            return;
        }
        StudentCourseGrades toSelect = overview.stream()
                .filter(grades -> selectedCourseId != null && grades.course().id() == selectedCourseId)
                .findFirst()
                .orElse(overview.getFirst());
        courseList.getSelectionModel().select(toSelect);
    }
}
