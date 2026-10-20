package com.inlearn.ui.common;

import com.inlearn.model.Announcement;
import com.inlearn.model.Course;
import com.inlearn.model.Role;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.FormDialog;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.ViewLoader.LoadedView;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Announcements tab, shared by instructors (who post, edit and delete) and students (who read).
 */
public class AnnouncementsTabController implements CourseTab {

    private final UiContext ui;
    private final List<Announcement> announcements = new ArrayList<>();
    private Course course;
    private boolean canManage;

    @FXML private TextField searchField;
    @FXML private Label countLabel;
    @FXML private Button postButton;
    @FXML private VBox announcementList;

    public AnnouncementsTabController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        searchField.textProperty().addListener((observable, oldText, newText) -> render());
    }

    @Override
    public void showCourse(Course loadedCourse) {
        this.course = loadedCourse;
        this.canManage = ui.session().user().role() == Role.INSTRUCTOR && loadedCourse.isActive();
        postButton.setVisible(canManage);
        postButton.setManaged(canManage);
        reload();
    }

    @FXML
    private void onPost() {
        if (course == null) {
            return;
        }
        LoadedView<AnnouncementFormController> form = ui.viewLoader().load("common/announcement-form.fxml");
        form.controller().prepareForNew(course.id());
        if (FormDialog.show(ui, "Post an announcement", "Post", form.root(), form.controller())) {
            reload();
        }
    }

    private void edit(Announcement announcement) {
        LoadedView<AnnouncementFormController> form = ui.viewLoader().load("common/announcement-form.fxml");
        form.controller().prepareForEdit(announcement);
        if (FormDialog.show(ui, "Edit announcement", "Save changes", form.root(), form.controller())) {
            reload();
        }
    }

    private void delete(Announcement announcement) {
        if (!ui.dialogs().confirm("Delete \"" + announcement.title() + "\"?",
                "Students will no longer see this announcement.", "Delete announcement", true)) {
            return;
        }
        ui.tasks().task(() -> {
                    ui.app().announcementService().deleteAnnouncement(ui.currentUser(), announcement.id());
                    return announcement;
                })
                .onSuccess(deleted -> {
                    ui.notifier().success("The announcement has been deleted.");
                    reload();
                })
                .start();
    }

    private void reload() {
        if (course == null) {
            return;
        }
        long courseId = course.id();
        ui.tasks().task(() -> ui.app().announcementService().listAnnouncements(ui.currentUser(), courseId))
                .onSuccess(loaded -> {
                    announcements.clear();
                    announcements.addAll(loaded);
                    render();
                })
                .start();
    }

    private void render() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<Announcement> visible = announcements.stream()
                .filter(announcement -> search.isEmpty()
                        || announcement.title().toLowerCase(Locale.ROOT).contains(search)
                        || announcement.body().toLowerCase(Locale.ROOT).contains(search))
                .toList();
        countLabel.setText(announcements.size() == 1 ? "1 announcement" : announcements.size() + " announcements");
        announcementList.getChildren().clear();
        if (visible.isEmpty()) {
            announcementList.getChildren().add(announcements.isEmpty()
                    ? EmptyStates.create("No announcements yet", canManage
                    ? "Select Post announcement to share news with your students."
                    : "Your instructor's announcements will appear here.")
                    : EmptyStates.create("No announcements match", "Try a different search."));
            return;
        }
        for (Announcement announcement : visible) {
            announcementList.getChildren().add(createCard(announcement));
        }
    }

    private VBox createCard(Announcement announcement) {
        Label title = new Label(announcement.title());
        title.getStyleClass().add("announcement-title");
        title.setWrapText(true);
        String meta = "Posted by " + announcement.authorName() + " · " + ui.formats().dateTime(announcement.createdAt())
                + (announcement.wasEdited() ? " · Edited " + ui.formats().shortDateTime(announcement.updatedAt()) : "");
        Label metaLabel = new Label(meta);
        metaLabel.getStyleClass().add("list-item-meta");
        metaLabel.setWrapText(true);
        Label body = new Label(announcement.body());
        body.getStyleClass().add("body-text");
        body.setWrapText(true);

        VBox card = new VBox(title, metaLabel, body);
        card.getStyleClass().add("announcement-card");
        if (canManage) {
            Button editButton = new Button("Edit");
            editButton.setAccessibleText("Edit announcement " + announcement.title());
            editButton.setOnAction(event -> edit(announcement));
            Button deleteButton = new Button("Delete");
            deleteButton.getStyleClass().add("danger-text");
            deleteButton.setAccessibleText("Delete announcement " + announcement.title());
            deleteButton.setOnAction(event -> delete(announcement));
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox actions = new HBox(8, spacer, editButton, deleteButton);
            card.getChildren().add(actions);
        }
        return card;
    }
}
