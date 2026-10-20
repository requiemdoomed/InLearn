package com.inlearn.ui.admin;

import com.inlearn.model.Course;
import com.inlearn.model.CourseStatus;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.FormDialog;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.StatusBadges;
import com.inlearn.ui.support.TableSupport;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.View;
import com.inlearn.ui.support.ViewLoader.LoadedView;
import javafx.application.Platform;
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

import java.util.List;
import java.util.Locale;

/**
 * Administrator screen for creating, editing, archiving and assigning instructors to courses.
 */
public class CourseManagementController implements Page {

    private static final String ALL_STATUSES = "All courses";

    private final UiContext ui;
    private final ObservableList<Course> courses = FXCollections.observableArrayList();
    private final FilteredList<Course> filteredCourses = new FilteredList<>(courses);
    private boolean openNewCourseFormWhenShown;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<Course> courseTable;
    @FXML private TableColumn<Course, String> codeColumn;
    @FXML private TableColumn<Course, String> titleColumn;
    @FXML private TableColumn<Course, String> termColumn;
    @FXML private TableColumn<Course, String> instructorColumn;
    @FXML private TableColumn<Course, Integer> studentsColumn;
    @FXML private TableColumn<Course, CourseStatus> statusColumn;
    @FXML private Label countLabel;
    @FXML private Button enrollmentsButton;
    @FXML private Button editButton;
    @FXML private Button archiveButton;
    @FXML private Button deleteButton;

    public CourseManagementController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        statusFilter.getItems().addAll(ALL_STATUSES, CourseStatus.ACTIVE.displayName(), CourseStatus.ARCHIVED.displayName());
        statusFilter.setValue(ALL_STATUSES);

        TableSupport.textColumn(codeColumn, Course::code);
        TableSupport.textColumn(titleColumn, Course::title);
        TableSupport.textColumn(termColumn, Course::term);
        TableSupport.textColumn(instructorColumn, course -> course.hasInstructor() ? course.instructorName() : "Not assigned");
        TableSupport.valueColumn(studentsColumn, Course::activeStudentCount);
        TableSupport.valueColumn(statusColumn, Course::status);
        statusColumn.setCellFactory(StatusBadges.cellFactory(StatusBadges::forCourseStatus));

        SortedList<Course> sortedCourses = new SortedList<>(filteredCourses);
        sortedCourses.comparatorProperty().bind(courseTable.comparatorProperty());
        courseTable.setItems(sortedCourses);
        courseTable.setPlaceholder(EmptyStates.create("No courses to show",
                "Select New course to create one, or change the search and filter."));
        TableSupport.onDoubleClickOrEnter(courseTable, course -> onEdit());

        searchField.textProperty().addListener((observable, oldText, newText) -> applyFilters());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        courseTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldCourse, newCourse) -> updateActionButtons(newCourse));
        updateActionButtons(null);
    }

    /** Used by the dashboard's "Create a course" shortcut. */
    public void openNewCourseFormWhenShown() {
        openNewCourseFormWhenShown = true;
    }

    @Override
    public void onShown() {
        refresh();
        if (openNewCourseFormWhenShown) {
            Platform.runLater(this::onNewCourse);
        }
    }

    @Override
    public void refresh() {
        ui.tasks().task(() -> ui.app().courseService().listAllCourses(ui.currentUser()))
                .onSuccess(this::showCourses)
                .start();
    }

    @Override
    public void focusSearch() {
        searchField.requestFocus();
    }

    @Override
    public void createNew() {
        onNewCourse();
    }

    @FXML
    private void onNewCourse() {
        LoadedView<CourseFormController> form = ui.viewLoader().load("admin/course-form.fxml");
        form.controller().prepareForCreate();
        if (FormDialog.show(ui, "New course", "Create course", form.root(), form.controller())) {
            refresh();
        }
    }

    @FXML
    private void onEdit() {
        Course selected = courseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        LoadedView<CourseFormController> form = ui.viewLoader().load("admin/course-form.fxml");
        form.controller().prepareForEdit(selected);
        if (FormDialog.show(ui, "Edit " + selected.code(), "Save changes", form.root(), form.controller())) {
            refresh();
        }
    }

    @FXML
    private void onManageEnrollments() {
        Course selected = courseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            ui.navigator().navigate(View.ENROLLMENT_MANAGEMENT,
                    (EnrollmentManagementController controller) -> controller.preselectCourse(selected.id()));
        }
    }

    @FXML
    private void onToggleArchive() {
        Course selected = courseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        boolean archiving = selected.isActive();
        if (archiving && !ui.dialogs().confirm("Archive " + selected.code() + "?",
                "The course becomes read-only: its materials, submissions and grades stay visible, but no one can "
                        + "add or change anything. You can restore it later.", "Archive course", true)) {
            return;
        }
        ui.tasks().task(() -> archiving
                        ? ui.app().courseService().archiveCourse(ui.currentUser(), selected.id(), selected.version())
                        : ui.app().courseService().restoreCourse(ui.currentUser(), selected.id(), selected.version()))
                .disabling(archiveButton, editButton, deleteButton, enrollmentsButton)
                .onSuccess(updated -> {
                    ui.notifier().success(updated.code() + (archiving ? " has been archived." : " has been restored."));
                    refresh();
                })
                .start();
    }

    @FXML
    private void onDelete() {
        Course selected = courseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!ui.dialogs().confirm("Delete " + selected.code() + "?",
                "This permanently deletes the course. It's only possible for courses with no enrollments or content — "
                        + "otherwise archive the course instead.", "Delete course", true)) {
            return;
        }
        ui.tasks().task(() -> {
                    ui.app().courseService().deleteCourse(ui.currentUser(), selected.id());
                    return selected;
                })
                .disabling(archiveButton, editButton, deleteButton, enrollmentsButton)
                .onSuccess(deleted -> {
                    ui.notifier().success(deleted.code() + " has been deleted.");
                    refresh();
                })
                .start();
    }

    private void showCourses(List<Course> loadedCourses) {
        Long selectedId = courseTable.getSelectionModel().getSelectedItem() == null
                ? null : courseTable.getSelectionModel().getSelectedItem().id();
        courses.setAll(loadedCourses);
        applyFilters();
        if (selectedId != null) {
            courseTable.getItems().stream().filter(course -> course.id() == selectedId).findFirst()
                    .ifPresent(course -> courseTable.getSelectionModel().select(course));
        }
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String status = statusFilter.getValue();
        filteredCourses.setPredicate(course ->
                (search.isEmpty() || matches(course, search))
                        && (ALL_STATUSES.equals(status) || course.status().displayName().equals(status)));
        countLabel.setText(filteredCourses.size() + " of " + courses.size() + " courses shown");
    }

    private static boolean matches(Course course, String search) {
        return course.code().toLowerCase(Locale.ROOT).contains(search)
                || course.title().toLowerCase(Locale.ROOT).contains(search)
                || course.term().toLowerCase(Locale.ROOT).contains(search)
                || (course.instructorName() != null && course.instructorName().toLowerCase(Locale.ROOT).contains(search));
    }

    private void updateActionButtons(Course selected) {
        boolean hasSelection = selected != null;
        enrollmentsButton.setDisable(!hasSelection);
        editButton.setDisable(!hasSelection);
        archiveButton.setDisable(!hasSelection);
        deleteButton.setDisable(!hasSelection);
        archiveButton.setText(hasSelection && !selected.isActive() ? "Restore" : "Archive");
    }
}
