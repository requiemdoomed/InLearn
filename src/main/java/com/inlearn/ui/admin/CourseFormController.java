package com.inlearn.ui.admin;

import com.inlearn.model.Course;
import com.inlearn.model.Role;
import com.inlearn.model.User;
import com.inlearn.service.CourseService.CourseDetails;
import com.inlearn.ui.support.FormDialog;
import com.inlearn.ui.support.FormMessages;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UnsavedChangesTracker;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Objects;

/**
 * The create/edit course form shown in a dialog from Course Management.
 */
public class CourseFormController implements FormDialog.Form {

    /** An entry in the instructor drop-down; {@code instructorId} is null for "no instructor". */
    private record InstructorOption(Long instructorId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final InstructorOption NO_INSTRUCTOR = new InstructorOption(null, "No instructor yet");

    private final UiContext ui;
    private final UnsavedChangesTracker changes = new UnsavedChangesTracker();
    private Course editedCourse;

    @FXML private TextField codeField;
    @FXML private TextField titleField;
    @FXML private TextField termField;
    @FXML private ComboBox<InstructorOption> instructorChoice;
    @FXML private TextArea descriptionArea;
    @FXML private Label errorLabel;

    public CourseFormController(UiContext ui) {
        this.ui = ui;
    }

    public void prepareForCreate() {
        editedCourse = null;
        loadInstructors();
    }

    public void prepareForEdit(Course course) {
        editedCourse = course;
        codeField.setText(course.code());
        titleField.setText(course.title());
        termField.setText(course.term());
        descriptionArea.setText(course.description() == null ? "" : course.description());
        loadInstructors();
    }

    @Override
    public boolean hasUnsavedChanges() {
        return changes.hasUnsavedChanges();
    }

    @Override
    public void save(Button saveButton, Runnable onSaved) {
        FormMessages.clear(errorLabel);
        InstructorOption instructor = instructorChoice.getValue();
        if (instructor == null) {
            // Saving before the list loads would silently remove the current instructor.
            FormMessages.showError(errorLabel, "The instructor list hasn't loaded yet. Wait a moment and try again.");
            return;
        }
        CourseDetails details = new CourseDetails(codeField.getText(), titleField.getText(), descriptionArea.getText(),
                termField.getText(), instructor.instructorId());
        ui.tasks().task(() -> editedCourse == null
                        ? ui.app().courseService().createCourse(ui.currentUser(), details)
                        : ui.app().courseService().updateCourse(ui.currentUser(), editedCourse.id(), details,
                        editedCourse.version()))
                .disabling(saveButton)
                .onSuccess(saved -> {
                    ui.notifier().success(editedCourse == null
                            ? saved.code() + " has been created."
                            : "Changes to " + saved.code() + " have been saved.");
                    onSaved.run();
                })
                .onFailure(error -> FormMessages.showError(errorLabel, error))
                .start();
    }

    private void loadInstructors() {
        instructorChoice.setDisable(true);
        instructorChoice.setPromptText("Loading instructors…");
        ui.tasks().task(() -> ui.app().userService().listActiveUsers(ui.currentUser(), Role.INSTRUCTOR))
                .onSuccess(this::showInstructors)
                .whenDone(() -> instructorChoice.setDisable(false))
                .start();
    }

    private void showInstructors(List<User> instructors) {
        instructorChoice.getItems().setAll(NO_INSTRUCTOR);
        InstructorOption selected = NO_INSTRUCTOR;
        for (User instructor : instructors) {
            InstructorOption option = new InstructorOption(instructor.id(), instructor.fullName() + " (" + instructor.email() + ")");
            instructorChoice.getItems().add(option);
            if (editedCourse != null && Objects.equals(editedCourse.instructorId(), instructor.id())) {
                selected = option;
            }
        }
        // Keep a currently assigned instructor visible even if their account is no longer active.
        if (editedCourse != null && editedCourse.hasInstructor() && selected == NO_INSTRUCTOR) {
            selected = new InstructorOption(editedCourse.instructorId(), editedCourse.instructorName() + " (inactive)");
            instructorChoice.getItems().add(selected);
        }
        instructorChoice.setValue(selected);
        changes.track(codeField.textProperty(), titleField.textProperty(), termField.textProperty(),
                descriptionArea.textProperty(), instructorChoice.valueProperty());
    }
}
