package com.inlearn.ui.admin;

import com.inlearn.model.Course;
import com.inlearn.model.Enrollment;
import com.inlearn.model.EnrollmentStatus;
import com.inlearn.model.Role;
import com.inlearn.model.User;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.StatusBadges;
import com.inlearn.ui.support.TableSupport;
import com.inlearn.ui.support.UiContext;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Administrator screen for enrolling students in a course and withdrawing them.
 */
public class EnrollmentManagementController implements Page {

    private final UiContext ui;
    private final ObservableList<User> activeStudents = FXCollections.observableArrayList();
    private final FilteredList<User> availableStudents = new FilteredList<>(activeStudents);
    private final ObservableList<Enrollment> enrollments = FXCollections.observableArrayList();
    private Long preselectedCourseId;

    @FXML private ComboBox<Course> courseChoice;
    @FXML private Label courseStatusNote;
    @FXML private Label enrolledCountLabel;
    @FXML private TableView<Enrollment> enrollmentTable;
    @FXML private TableColumn<Enrollment, String> studentNameColumn;
    @FXML private TableColumn<Enrollment, String> studentEmailColumn;
    @FXML private TableColumn<Enrollment, EnrollmentStatus> enrollmentStatusColumn;
    @FXML private TableColumn<Enrollment, Instant> enrolledAtColumn;
    @FXML private Button withdrawButton;
    @FXML private TextField studentSearchField;
    @FXML private ListView<User> availableStudentsList;
    @FXML private Button enrollButton;

    public EnrollmentManagementController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        TableSupport.textColumn(studentNameColumn, Enrollment::studentName);
        TableSupport.textColumn(studentEmailColumn, Enrollment::studentEmail);
        TableSupport.valueColumn(enrollmentStatusColumn, Enrollment::status);
        enrollmentStatusColumn.setCellFactory(StatusBadges.cellFactory(StatusBadges::forEnrollmentStatus));
        TableSupport.valueColumn(enrolledAtColumn, Enrollment::enrolledAt);
        enrolledAtColumn.setCellFactory(TableSupport.formattedCells(ui.formats()::date));
        SortedList<Enrollment> sortedEnrollments = new SortedList<>(enrollments);
        sortedEnrollments.comparatorProperty().bind(enrollmentTable.comparatorProperty());
        enrollmentTable.setItems(sortedEnrollments);
        enrollmentTable.setPlaceholder(EmptyStates.create("No one is enrolled yet",
                "Choose students on the right and select Enroll selected."));

        availableStudentsList.setItems(availableStudents);
        availableStudentsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        availableStudentsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(User student, boolean empty) {
                super.updateItem(student, empty);
                setText(empty || student == null ? null : student.fullName() + "\n" + student.email());
            }
        });
        availableStudentsList.setPlaceholder(EmptyStates.create("No students available",
                "Every active student is already enrolled, or none match your search."));

        courseChoice.valueProperty().addListener((observable, oldCourse, newCourse) -> loadEnrollments());
        studentSearchField.textProperty().addListener((observable, oldText, newText) -> applyStudentFilter());
        enrollmentTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                updateButtons());
        availableStudentsList.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<User>) change -> updateButtons());
        updateButtons();
    }

    /** Opens the screen with a course already chosen (from Course Management). */
    public void preselectCourse(long courseId) {
        this.preselectedCourseId = courseId;
    }

    @Override
    public void onShown() {
        refresh();
    }

    @Override
    public void refresh() {
        Long selectedCourseId = courseChoice.getValue() != null ? Long.valueOf(courseChoice.getValue().id()) : preselectedCourseId;
        ui.tasks().task(() -> new CoursesAndStudents(
                        ui.app().courseService().listAllCourses(ui.currentUser()),
                        ui.app().userService().listActiveUsers(ui.currentUser(), Role.STUDENT)))
                .onSuccess(loaded -> {
                    activeStudents.setAll(loaded.students());
                    courseChoice.getItems().setAll(loaded.courses());
                    Course reselected = loaded.courses().stream()
                            .filter(course -> selectedCourseId != null && course.id() == selectedCourseId)
                            .findFirst()
                            .orElse(null);
                    if (Objects.equals(courseChoice.getValue(), reselected)) {
                        loadEnrollments();
                    } else {
                        courseChoice.setValue(reselected); // the value listener reloads enrollments
                    }
                })
                .start();
    }

    @Override
    public void focusSearch() {
        studentSearchField.requestFocus();
    }

    @FXML
    private void onEnroll() {
        Course course = courseChoice.getValue();
        List<User> selectedStudents = List.copyOf(availableStudentsList.getSelectionModel().getSelectedItems());
        if (course == null || selectedStudents.isEmpty()) {
            return;
        }
        List<Long> studentIds = selectedStudents.stream().map(User::id).toList();
        ui.tasks().task(() -> ui.app().enrollmentService().enrollStudents(ui.currentUser(), course.id(), studentIds))
                .disabling(enrollButton, withdrawButton, courseChoice)
                .onSuccess(count -> {
                    ui.notifier().success(count == 1
                            ? selectedStudents.getFirst().fullName() + " has been enrolled in " + course.code() + "."
                            : count + " students have been enrolled in " + course.code() + ".");
                    loadEnrollments();
                })
                .start();
    }

    @FXML
    private void onWithdraw() {
        Course course = courseChoice.getValue();
        Enrollment selected = enrollmentTable.getSelectionModel().getSelectedItem();
        if (course == null || selected == null || !selected.isActive()) {
            return;
        }
        if (!ui.dialogs().confirm("Withdraw " + selected.studentName() + "?",
                selected.studentName() + " will lose access to " + course.code() + ". Their submissions and grades are "
                        + "kept, and you can enroll them again later.", "Withdraw student", true)) {
            return;
        }
        ui.tasks().task(() -> ui.app().enrollmentService().withdrawStudent(ui.currentUser(), course.id(),
                        selected.studentId(), selected.version()))
                .disabling(enrollButton, withdrawButton, courseChoice)
                .onSuccess(withdrawn -> {
                    ui.notifier().success(withdrawn.studentName() + " has been withdrawn from " + course.code() + ".");
                    loadEnrollments();
                })
                .start();
    }

    private void loadEnrollments() {
        Course course = courseChoice.getValue();
        enrollments.clear();
        courseStatusNote.setText(course != null && !course.isActive()
                ? "This course is archived, so enrollments can't change." : "");
        if (course == null) {
            applyStudentFilter();
            updateButtons();
            return;
        }
        ui.tasks().task(() -> ui.app().enrollmentService().listEnrollments(ui.currentUser(), course.id()))
                .onSuccess(loaded -> {
                    enrollments.setAll(loaded);
                    long activeCount = loaded.stream().filter(Enrollment::isActive).count();
                    enrolledCountLabel.setText(activeCount + " enrolled"
                            + (loaded.size() > activeCount ? " · " + (loaded.size() - activeCount) + " withdrawn" : ""));
                    applyStudentFilter();
                    updateButtons();
                })
                .start();
    }

    /** Shows students who are not currently enrolled and match the search. */
    private void applyStudentFilter() {
        Set<Long> enrolledIds = enrollments.stream()
                .filter(Enrollment::isActive)
                .map(Enrollment::studentId)
                .collect(Collectors.toSet());
        String search = studentSearchField.getText() == null ? ""
                : studentSearchField.getText().trim().toLowerCase(Locale.ROOT);
        availableStudents.setPredicate(student -> !enrolledIds.contains(student.id())
                && (search.isEmpty()
                || student.fullName().toLowerCase(Locale.ROOT).contains(search)
                || student.email().contains(search)));
    }

    private void updateButtons() {
        Course course = courseChoice.getValue();
        boolean editable = course != null && course.isActive();
        Enrollment selectedEnrollment = enrollmentTable.getSelectionModel().getSelectedItem();
        withdrawButton.setDisable(!editable || selectedEnrollment == null || !selectedEnrollment.isActive());
        enrollButton.setDisable(!editable || availableStudentsList.getSelectionModel().getSelectedItems().isEmpty());
    }

    /** The two lists this screen loads together. */
    private record CoursesAndStudents(List<Course> courses, List<User> students) {
    }
}
