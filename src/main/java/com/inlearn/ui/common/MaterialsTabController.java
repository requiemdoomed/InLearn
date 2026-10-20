package com.inlearn.ui.common;

import com.inlearn.model.Course;
import com.inlearn.model.CourseMaterial;
import com.inlearn.model.MaterialType;
import com.inlearn.model.Role;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.FormDialog;
import com.inlearn.ui.support.TableSupport;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.ViewLoader.LoadedView;
import com.inlearn.util.FileStorage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.Instant;
import java.util.Locale;

/**
 * The Materials tab, shared by instructors (who can add and delete) and students (read-only).
 */
public class MaterialsTabController implements CourseTab {

    private final UiContext ui;
    private final ObservableList<CourseMaterial> materials = FXCollections.observableArrayList();
    private final FilteredList<CourseMaterial> filteredMaterials = new FilteredList<>(materials);
    private Course course;
    private boolean canManage;

    @FXML private TextField searchField;
    @FXML private Button addFileButton;
    @FXML private Button addLinkButton;
    @FXML private TableView<CourseMaterial> materialTable;
    @FXML private TableColumn<CourseMaterial, String> titleColumn;
    @FXML private TableColumn<CourseMaterial, String> typeColumn;
    @FXML private TableColumn<CourseMaterial, Instant> addedColumn;
    @FXML private TableColumn<CourseMaterial, String> addedByColumn;
    @FXML private Label descriptionLabel;
    @FXML private Label hintLabel;
    @FXML private Button openButton;
    @FXML private Button deleteButton;

    public MaterialsTabController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        TableSupport.textColumn(titleColumn, CourseMaterial::title);
        TableSupport.textColumn(typeColumn, MaterialsTabController::typeDescription);
        TableSupport.valueColumn(addedColumn, CourseMaterial::createdAt);
        addedColumn.setCellFactory(TableSupport.formattedCells(ui.formats()::date));
        TableSupport.textColumn(addedByColumn, CourseMaterial::uploadedByName);
        SortedList<CourseMaterial> sorted = new SortedList<>(filteredMaterials);
        sorted.comparatorProperty().bind(materialTable.comparatorProperty());
        materialTable.setItems(sorted);
        TableSupport.onDoubleClickOrEnter(materialTable, material -> onOpen());

        searchField.textProperty().addListener((observable, oldText, newText) -> applyFilter());
        materialTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldMaterial, newMaterial) -> showSelection(newMaterial));
        showSelection(null);
    }

    @Override
    public void showCourse(Course loadedCourse) {
        this.course = loadedCourse;
        // Only the assigned instructor sees management controls; the service enforces this regardless.
        this.canManage = ui.session().user().role() == Role.INSTRUCTOR && loadedCourse.isActive();
        addFileButton.setVisible(canManage);
        addFileButton.setManaged(canManage);
        addLinkButton.setVisible(canManage);
        addLinkButton.setManaged(canManage);
        deleteButton.setVisible(canManage);
        deleteButton.setManaged(canManage);
        materialTable.setPlaceholder(canManage
                ? EmptyStates.create("No materials yet", "Select Add file… or Add web link… to share something with students.")
                : EmptyStates.create("No materials yet", "Your instructor hasn't shared any materials for this course."));
        reload();
    }

    @FXML
    private void onAddFile() {
        openForm(MaterialType.FILE, "Add a file");
    }

    @FXML
    private void onAddLink() {
        openForm(MaterialType.LINK, "Add a web link");
    }

    @FXML
    private void onOpen() {
        CourseMaterial selected = materialTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (selected.isFile()) {
            FileDownloads.saveAs(ui, selected.originalFilename(),
                    destination -> ui.app().materialService().downloadMaterial(ui.currentUser(), selected.id(), destination),
                    openButton);
        } else {
            ui.tasks().task(() -> ui.app().materialService().getLinkUrl(ui.currentUser(), selected.id()))
                    .disabling(openButton)
                    .onSuccess(url -> {
                        ui.hostServices().showDocument(url);
                        ui.notifier().info("Opening " + url + " in your web browser.");
                    })
                    .start();
        }
    }

    @FXML
    private void onDelete() {
        CourseMaterial selected = materialTable.getSelectionModel().getSelectedItem();
        if (selected == null || !ui.dialogs().confirm("Delete \"" + selected.title() + "\"?",
                "Students will no longer see this material." + (selected.isFile() ? " The stored file is removed too." : ""),
                "Delete material", true)) {
            return;
        }
        ui.tasks().task(() -> {
                    ui.app().materialService().deleteMaterial(ui.currentUser(), selected.id());
                    return selected;
                })
                .disabling(deleteButton, openButton)
                .onSuccess(deleted -> {
                    ui.notifier().success("\"" + deleted.title() + "\" has been removed.");
                    reload();
                })
                .start();
    }

    private void openForm(MaterialType type, String title) {
        if (course == null) {
            return;
        }
        LoadedView<MaterialFormController> form = ui.viewLoader().load("common/material-form.fxml");
        form.controller().prepare(course.id(), type);
        if (FormDialog.show(ui, title, type == MaterialType.FILE ? "Upload" : "Add link", form.root(), form.controller())) {
            reload();
        }
    }

    private void reload() {
        if (course == null) {
            return;
        }
        long courseId = course.id();
        ui.tasks().task(() -> ui.app().materialService().listMaterials(ui.currentUser(), courseId))
                .onSuccess(loaded -> {
                    materials.setAll(loaded);
                    applyFilter();
                })
                .start();
    }

    private void applyFilter() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        filteredMaterials.setPredicate(material -> search.isEmpty()
                || material.title().toLowerCase(Locale.ROOT).contains(search)
                || (material.description() != null && material.description().toLowerCase(Locale.ROOT).contains(search)));
    }

    private void showSelection(CourseMaterial selected) {
        openButton.setDisable(selected == null);
        deleteButton.setDisable(selected == null);
        openButton.setText(selected != null && !selected.isFile() ? "_Open link" : "_Download");
        descriptionLabel.setText(selected == null || selected.description() == null ? "" : selected.description());
        hintLabel.setText(selected == null ? "Select a material to download it or open its link." : "");
    }

    private static String typeDescription(CourseMaterial material) {
        if (!material.isFile()) {
            return "Web link · " + material.linkUrl();
        }
        String extension = FileStorage.extensionOf(material.originalFilename()).toUpperCase(Locale.ROOT);
        String size = material.fileSizeBytes() == null ? "" : " · " + FileStorage.formatSize(material.fileSizeBytes());
        return (extension.isEmpty() ? "File" : extension + " file") + size + " · " + material.originalFilename();
    }
}
