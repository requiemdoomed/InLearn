package com.inlearn.ui.instructor;

import com.inlearn.model.Assignment;
import com.inlearn.model.SubmissionReviewRow;
import com.inlearn.service.GradingService.AssignmentReview;
import com.inlearn.ui.common.FileDownloads;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.FormMessages;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.StatusBadges;
import com.inlearn.ui.support.StatusBadges.Badge;
import com.inlearn.ui.support.StatusBadges.Tone;
import com.inlearn.ui.support.TableSupport;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UiFormats;
import com.inlearn.ui.support.UnsavedChangesTracker;
import com.inlearn.util.FileStorage;
import com.inlearn.util.InputValidator;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Review and grade the submissions for one assignment.
 */
public class SubmissionReviewController implements Page {

    private static final String ALL = "All students";
    private static final String NEEDS_GRADING = "Needs grading";
    private static final String GRADED = "Graded";
    private static final String NOT_SUBMITTED = "Not submitted";

    private final UiContext ui;
    private final ObservableList<SubmissionReviewRow> rows = FXCollections.observableArrayList();
    private final FilteredList<SubmissionReviewRow> filteredRows = new FilteredList<>(rows);
    private final UnsavedChangesTracker gradeChanges = new UnsavedChangesTracker();
    private long courseId;
    private long assignmentId;
    private Assignment assignment;
    private SubmissionReviewRow shownRow;
    private boolean courseEditable;
    private boolean restoringSelection;
    /** After a "save and next", the student to open once the list reloads. */
    private Long studentToOpenAfterReload;

    @FXML private Label assignmentSummary;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterChoice;
    @FXML private TableView<SubmissionReviewRow> submissionTable;
    @FXML private TableColumn<SubmissionReviewRow, String> studentColumn;
    @FXML private TableColumn<SubmissionReviewRow, SubmissionReviewRow> statusColumn;
    @FXML private TableColumn<SubmissionReviewRow, Instant> submittedColumn;
    @FXML private TableColumn<SubmissionReviewRow, BigDecimal> pointsColumn;
    @FXML private Label progressLabel;
    @FXML private VBox detailPane;
    @FXML private Label detailStudentName;
    @FXML private Label detailHint;
    @FXML private VBox submissionDetails;
    @FXML private Label detailStatusBadge;
    @FXML private Label detailFileName;
    @FXML private Label detailSubmittedAt;
    @FXML private Button downloadButton;
    @FXML private Label pointsLabel;
    @FXML private TextField pointsField;
    @FXML private TextArea feedbackArea;
    @FXML private Label gradedByLabel;
    @FXML private Label formMessage;
    @FXML private Button saveGradeButton;
    @FXML private Button saveAndNextButton;

    public SubmissionReviewController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        filterChoice.getItems().addAll(ALL, NEEDS_GRADING, GRADED, NOT_SUBMITTED);
        filterChoice.setValue(ALL);
        TableSupport.textColumn(studentColumn, SubmissionReviewRow::studentName);
        TableSupport.valueColumn(statusColumn, row -> row);
        statusColumn.setCellFactory(StatusBadges.cellFactory(SubmissionReviewController::badgeFor));
        statusColumn.setSortable(false);
        TableSupport.valueColumn(submittedColumn, row -> row.hasSubmission() ? row.submission().submittedAt() : null);
        submittedColumn.setCellFactory(TableSupport.formattedCells(
                instant -> instant == null ? "—" : ui.formats().shortDateTime(instant)));
        TableSupport.valueColumn(pointsColumn, row -> row.isGraded() ? row.grade().points() : null);
        pointsColumn.setCellFactory(TableSupport.formattedCells(points -> points == null ? "—" : UiFormats.points(points)));

        SortedList<SubmissionReviewRow> sorted = new SortedList<>(filteredRows);
        sorted.comparatorProperty().bind(submissionTable.comparatorProperty());
        submissionTable.setItems(sorted);
        submissionTable.setPlaceholder(EmptyStates.create("No students to show",
                "Students appear here once they're enrolled. Try clearing the search or filter."));

        searchField.textProperty().addListener((observable, oldText, newText) -> applyFilter());
        filterChoice.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        submissionTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldRow, newRow) -> onSelectionChanged(oldRow, newRow));
        gradeChanges.track(pointsField.textProperty(), feedbackArea.textProperty());
        showRow(null);
    }

    public void setAssignment(long courseId, long assignmentId) {
        this.courseId = courseId;
        this.assignmentId = assignmentId;
    }

    @Override
    public void onShown() {
        ui.navigator().highlightCourse(courseId);
        refresh();
    }

    @Override
    public void refresh() {
        ui.tasks().task(() -> ui.app().gradingService().getAssignmentReview(ui.currentUser(), assignmentId))
                .onSuccess(this::showReview)
                .start();
    }

    @Override
    public boolean hasUnsavedChanges() {
        return shownRow != null && gradeChanges.hasUnsavedChanges();
    }

    @Override
    public void focusSearch() {
        searchField.requestFocus();
    }

    @Override
    public void save() {
        if (!saveGradeButton.isDisabled() && shownRow != null && shownRow.hasSubmission()) {
            onSaveGrade();
        }
    }

    private void showReview(AssignmentReview review) {
        this.assignment = review.assignment();
        this.courseEditable = review.course().isActive();
        ui.navigator().setPageTitle("Submissions · " + assignment.title(), review.course().displayLabel());
        assignmentSummary.setText("Due " + ui.formats().dateTime(assignment.dueAt()) + "  ·  Out of "
                + UiFormats.points(assignment.maxPoints()) + " points  ·  " + ui.formats().latePolicyExplanation(assignment)
                + (courseEditable ? "" : "  ·  This course is archived, so grades are read-only."));
        pointsLabel.setText("Points (0 – " + UiFormats.points(assignment.maxPoints()) + ")");

        Long selectedStudentId = studentToOpenAfterReload != null ? studentToOpenAfterReload
                : shownRow == null ? null : shownRow.studentId();
        studentToOpenAfterReload = null;
        rows.setAll(review.rows());
        applyFilter();
        long submitted = review.rows().stream().filter(SubmissionReviewRow::hasSubmission).count();
        long graded = review.rows().stream().filter(SubmissionReviewRow::isGraded).count();
        progressLabel.setText(submitted + " of " + review.rows().size() + " submitted · " + graded + " graded");
        reselect(selectedStudentId);
    }

    private void onSelectionChanged(SubmissionReviewRow oldRow, SubmissionReviewRow newRow) {
        if (restoringSelection) {
            return;
        }
        if (oldRow != null && newRow != null && oldRow.studentId() == newRow.studentId()) {
            showRow(newRow);
            return;
        }
        if (hasUnsavedChanges() && !ui.dialogs().confirmDiscardChanges()) {
            // Stay on the student whose grade is being edited.
            restoringSelection = true;
            submissionTable.getSelectionModel().select(oldRow);
            restoringSelection = false;
            return;
        }
        showRow(newRow);
    }

    private void showRow(SubmissionReviewRow row) {
        shownRow = row;
        FormMessages.clear(formMessage);
        boolean hasRow = row != null;
        boolean hasSubmission = hasRow && row.hasSubmission();
        detailStudentName.setText(hasRow ? row.studentName() : "Select a student");
        detailHint.setText(!hasRow ? "Choose a student on the left to see their submission and grade it."
                : hasSubmission ? row.studentEmail()
                : row.studentName() + " hasn't submitted anything yet, so there's nothing to grade.");
        submissionDetails.setVisible(hasSubmission);
        submissionDetails.setManaged(hasSubmission);
        if (hasSubmission) {
            StatusBadges.apply(detailStatusBadge, badgeFor(row));
            detailFileName.setText(row.submission().originalFilename() + " ("
                    + FileStorage.formatSize(row.submission().fileSizeBytes()) + ")");
            detailSubmittedAt.setText(ui.formats().dateTime(row.submission().submittedAt())
                    + (row.submission().late() ? " — late" : " — on time")
                    + (row.submission().attemptNumber() > 1 ? " · attempt " + row.submission().attemptNumber() : ""));
            pointsField.setText(row.isGraded() ? UiFormats.points(row.grade().points()) : "");
            feedbackArea.setText(row.isGraded() && row.grade().feedback() != null ? row.grade().feedback() : "");
            gradedByLabel.setText(row.isGraded()
                    ? "Graded by " + row.grade().gradedByName() + " on " + ui.formats().dateTime(row.grade().gradedAt())
                    : "Not graded yet. The student sees “Awaiting feedback” until you save a grade.");
        } else {
            pointsField.clear();
            feedbackArea.clear();
        }
        pointsField.setEditable(courseEditable);
        feedbackArea.setEditable(courseEditable);
        saveGradeButton.setDisable(!courseEditable || !hasSubmission);
        saveAndNextButton.setDisable(!courseEditable || !hasSubmission);
        gradeChanges.markSaved();
    }

    @FXML
    private void onDownload() {
        SubmissionReviewRow row = shownRow;
        if (row == null || !row.hasSubmission()) {
            return;
        }
        long submissionId = row.submission().id();
        FileDownloads.saveAs(ui, row.submission().originalFilename(),
                destination -> ui.app().submissionService().downloadSubmission(ui.currentUser(), submissionId, destination),
                downloadButton);
    }

    @FXML
    private void onSaveGrade() {
        saveGrade(false);
    }

    /** Saves, then opens the next student whose submission still needs grading. */
    @FXML
    private void onSaveAndNext() {
        saveGrade(true);
    }

    private void saveGrade(boolean openNextAfterwards) {
        SubmissionReviewRow row = shownRow;
        if (row == null || !row.hasSubmission()) {
            return;
        }
        FormMessages.clear(formMessage);
        BigDecimal points;
        try {
            points = InputValidator.parsePoints(pointsField.getText(), "Points");
        } catch (RuntimeException e) {
            FormMessages.showError(formMessage, e);
            pointsField.requestFocus();
            return;
        }
        String feedback = feedbackArea.getText();
        Integer gradeVersion = row.isGraded() ? row.grade().version() : null;
        ui.tasks().task(() -> ui.app().gradingService().saveGrade(ui.currentUser(), row.submission().id(), points,
                        feedback, gradeVersion))
                .disabling(saveGradeButton, saveAndNextButton)
                .onSuccess(grade -> {
                    gradeChanges.markSaved();
                    String saved = row.studentName() + "'s grade has been saved: "
                            + UiFormats.pointsOutOf(grade.points(), assignment.maxPoints()) + ".";
                    if (openNextAfterwards) {
                        studentToOpenAfterReload = nextStudentNeedingGrading(row.studentId());
                        saved += studentToOpenAfterReload == null ? " Every submission is now graded." : "";
                    }
                    ui.notifier().success(saved);
                    refresh();
                })
                .onFailure(error -> FormMessages.showError(formMessage, error))
                .start();
    }

    /** The next student after the given one (in the table's current order) with ungraded work. */
    private Long nextStudentNeedingGrading(long currentStudentId) {
        List<SubmissionReviewRow> visibleRows = submissionTable.getItems();
        int currentIndex = -1;
        for (int index = 0; index < visibleRows.size(); index++) {
            if (visibleRows.get(index).studentId() == currentStudentId) {
                currentIndex = index;
            }
        }
        for (int offset = 1; offset < visibleRows.size(); offset++) {
            SubmissionReviewRow candidate = visibleRows.get((currentIndex + offset + visibleRows.size()) % visibleRows.size());
            if (candidate.hasSubmission() && !candidate.isGraded() && candidate.studentId() != currentStudentId) {
                return candidate.studentId();
            }
        }
        return null;
    }

    private void reselect(Long studentId) {
        restoringSelection = true;
        try {
            SubmissionReviewRow match = studentId == null ? null : submissionTable.getItems().stream()
                    .filter(row -> row.studentId() == studentId).findFirst().orElse(null);
            if (match != null) {
                submissionTable.getSelectionModel().select(match);
            } else {
                submissionTable.getSelectionModel().clearSelection();
            }
            showRow(match);
        } finally {
            restoringSelection = false;
        }
    }

    private void applyFilter() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String filter = filterChoice.getValue();
        filteredRows.setPredicate(row -> {
            boolean matchesSearch = search.isEmpty()
                    || row.studentName().toLowerCase(Locale.ROOT).contains(search)
                    || row.studentEmail().contains(search);
            boolean matchesFilter = switch (filter) {
                case NEEDS_GRADING -> row.hasSubmission() && !row.isGraded();
                case GRADED -> row.isGraded();
                case NOT_SUBMITTED -> !row.hasSubmission();
                default -> true;
            };
            return matchesSearch && matchesFilter;
        });
    }

    private static Badge badgeFor(SubmissionReviewRow row) {
        if (!row.hasSubmission()) {
            return new Badge("Not submitted", Tone.NEUTRAL);
        }
        if (row.isGraded()) {
            return new Badge(row.submission().late() ? "Graded · late" : "Graded", Tone.SUCCESS);
        }
        return row.submission().late() ? new Badge("Late · Needs grading", Tone.WARNING)
                : new Badge("Needs grading", Tone.INFO);
    }
}
