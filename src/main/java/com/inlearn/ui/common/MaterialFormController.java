package com.inlearn.ui.common;

import com.inlearn.model.MaterialType;
import com.inlearn.ui.support.FormDialog;
import com.inlearn.ui.support.FormMessages;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UnsavedChangesTracker;
import com.inlearn.util.FileStorage;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;

/**
 * Form for adding a course material, either an uploaded file or a web link.
 */
public class MaterialFormController implements FormDialog.Form {

    private final UiContext ui;
    private final UnsavedChangesTracker changes = new UnsavedChangesTracker();
    private final ObjectProperty<Path> chosenFile = new SimpleObjectProperty<>();
    private MaterialType type;
    private long courseId;

    @FXML private TextField titleField;
    @FXML private VBox fileSection;
    @FXML private Button chooseFileButton;
    @FXML private Label chosenFileLabel;
    @FXML private Label fileRulesLabel;
    @FXML private VBox linkSection;
    @FXML private TextField urlField;
    @FXML private TextArea descriptionArea;
    @FXML private Label errorLabel;

    public MaterialFormController(UiContext ui) {
        this.ui = ui;
    }

    public void prepare(long courseId, MaterialType type) {
        this.courseId = courseId;
        this.type = type;
        boolean isFile = type == MaterialType.FILE;
        fileSection.setVisible(isFile);
        fileSection.setManaged(isFile);
        linkSection.setVisible(!isFile);
        linkSection.setManaged(!isFile);
        FileStorage storage = ui.app().fileStorage();
        fileRulesLabel.setText("Up to " + storage.maxUploadSizeLabel() + ". Allowed types: "
                + String.join(", ", storage.allowedExtensions().stream().sorted().toList()) + ".");
        changes.track(titleField.textProperty(), urlField.textProperty(), descriptionArea.textProperty(), chosenFile);
    }

    @Override
    public boolean hasUnsavedChanges() {
        return changes.hasUnsavedChanges();
    }

    @FXML
    private void onChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a file to share");
        File selected = chooser.showOpenDialog(ui.stage());
        if (selected != null) {
            chosenFile.set(selected.toPath());
            chosenFileLabel.setText(selected.getName() + " (" + FileStorage.formatSize(selected.length()) + ")");
            if (titleField.getText() == null || titleField.getText().isBlank()) {
                titleField.setText(stripExtension(selected.getName()));
            }
        }
    }

    @Override
    public void save(Button saveButton, Runnable onSaved) {
        FormMessages.clear(errorLabel);
        String title = titleField.getText();
        String description = descriptionArea.getText();
        String url = urlField.getText();
        Path file = chosenFile.get();
        if (type == MaterialType.FILE && file == null) {
            FormMessages.showError(errorLabel, "Choose a file to upload.");
            return;
        }
        ui.tasks().task(() -> type == MaterialType.FILE
                        ? ui.app().materialService().addFile(ui.currentUser(), courseId, title, description, file)
                        : ui.app().materialService().addLink(ui.currentUser(), courseId, title, description, url))
                .disabling(saveButton, chooseFileButton)
                .onSuccess(material -> {
                    ui.notifier().success("\"" + material.title() + "\" has been shared with the course.");
                    onSaved.run();
                })
                .onFailure(error -> FormMessages.showError(errorLabel, error))
                .start();
    }

    private static String stripExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }
}
