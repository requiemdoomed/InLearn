package com.inlearn.ui.instructor;

import com.inlearn.model.Course;
import com.inlearn.ui.common.CourseTab;
import com.inlearn.ui.support.FormMessages;
import com.inlearn.ui.support.StatusBadges;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UnsavedChangesTracker;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

/**
 * Instructor Overview tab: course facts and the editable course description.
 */
public class CourseOverviewTabController implements CourseTab {

    private final UiContext ui;
    private final UnsavedChangesTracker changes = new UnsavedChangesTracker();
    private Course course;

    @FXML private Label codeValue;
    @FXML private Label termValue;
    @FXML private Label studentsValue;
    @FXML private Label statusBadge;
    @FXML private TextArea descriptionArea;
    @FXML private Label messageLabel;
    @FXML private Button discardButton;
    @FXML private Button saveButton;

    public CourseOverviewTabController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        changes.track(descriptionArea.textProperty());
        descriptionArea.textProperty().addListener((observable, oldText, newText) -> updateButtons());
        updateButtons();
    }

    @Override
    public void showCourse(Course loadedCourse) {
        // A refresh must not wipe out a description the instructor is still writing.
        boolean keepDraft = changes.hasUnsavedChanges() && course != null && course.id() == loadedCourse.id();
        this.course = loadedCourse;
        codeValue.setText(loadedCourse.code());
        termValue.setText(loadedCourse.term());
        studentsValue.setText(String.valueOf(loadedCourse.activeStudentCount()));
        StatusBadges.apply(statusBadge, StatusBadges.forCourseStatus(loadedCourse.status()));
        descriptionArea.setEditable(loadedCourse.isActive());
        if (!keepDraft) {
            descriptionArea.setText(loadedCourse.description() == null ? "" : loadedCourse.description());
            changes.markSaved();
        }
        updateButtons();
    }

    @Override
    public boolean hasUnsavedChanges() {
        return changes.hasUnsavedChanges();
    }

    /** Ctrl+S on this tab. */
    public void save() {
        if (!saveButton.isDisabled()) {
            onSave();
        }
    }

    @FXML
    private void onSave() {
        if (course == null) {
            return;
        }
        FormMessages.clear(messageLabel);
        String description = descriptionArea.getText();
        ui.tasks().task(() -> ui.app().courseService().updateDescription(ui.currentUser(), course.id(), description,
                        course.version()))
                .disabling(saveButton, discardButton)
                .onSuccess(saved -> {
                    course = saved;
                    changes.markSaved();
                    updateButtons();
                    ui.notifier().success("The course description has been saved.");
                })
                .onFailure(error -> FormMessages.showError(messageLabel, error))
                .start();
    }

    @FXML
    private void onDiscard() {
        if (course != null) {
            descriptionArea.setText(course.description() == null ? "" : course.description());
            changes.markSaved();
            FormMessages.clear(messageLabel);
            updateButtons();
        }
    }

    private void updateButtons() {
        boolean editable = course != null && course.isActive();
        boolean dirty = changes.hasUnsavedChanges();
        saveButton.setDisable(!editable || !dirty);
        discardButton.setDisable(!dirty);
    }
}
