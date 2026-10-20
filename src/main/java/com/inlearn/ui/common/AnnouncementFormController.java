package com.inlearn.ui.common;

import com.inlearn.model.Announcement;
import com.inlearn.ui.support.FormDialog;
import com.inlearn.ui.support.FormMessages;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UnsavedChangesTracker;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/**
 * Form for posting a new announcement or editing an existing one.
 */
public class AnnouncementFormController implements FormDialog.Form {

    private final UiContext ui;
    private final UnsavedChangesTracker changes = new UnsavedChangesTracker();
    private long courseId;
    private Announcement editedAnnouncement;

    @FXML private TextField titleField;
    @FXML private TextArea bodyArea;
    @FXML private Label errorLabel;

    public AnnouncementFormController(UiContext ui) {
        this.ui = ui;
    }

    public void prepareForNew(long courseId) {
        this.courseId = courseId;
        this.editedAnnouncement = null;
        changes.track(titleField.textProperty(), bodyArea.textProperty());
    }

    public void prepareForEdit(Announcement announcement) {
        this.courseId = announcement.courseId();
        this.editedAnnouncement = announcement;
        titleField.setText(announcement.title());
        bodyArea.setText(announcement.body());
        changes.track(titleField.textProperty(), bodyArea.textProperty());
    }

    @Override
    public boolean hasUnsavedChanges() {
        return changes.hasUnsavedChanges();
    }

    @Override
    public void save(Button saveButton, Runnable onSaved) {
        FormMessages.clear(errorLabel);
        String title = titleField.getText();
        String body = bodyArea.getText();
        ui.tasks().task(() -> editedAnnouncement == null
                        ? ui.app().announcementService().postAnnouncement(ui.currentUser(), courseId, title, body)
                        : ui.app().announcementService().updateAnnouncement(ui.currentUser(), editedAnnouncement.id(),
                        title, body, editedAnnouncement.version()))
                .disabling(saveButton)
                .onSuccess(saved -> {
                    ui.notifier().success(editedAnnouncement == null
                            ? "Your announcement has been posted."
                            : "Your announcement has been updated.");
                    onSaved.run();
                })
                .onFailure(error -> FormMessages.showError(errorLabel, error))
                .start();
    }
}
