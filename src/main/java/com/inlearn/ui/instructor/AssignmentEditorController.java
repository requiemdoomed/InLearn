package com.inlearn.ui.instructor;

import com.inlearn.exception.ValidationException;
import com.inlearn.model.Assignment;
import com.inlearn.model.LatePolicy;
import com.inlearn.service.AssignmentService.AssignmentDraft;
import com.inlearn.service.AssignmentService.AssignmentEditContext;
import com.inlearn.service.AssignmentService.UpdateResult;
import com.inlearn.ui.support.DateTimeInputs;
import com.inlearn.ui.support.ErrorPresenter;
import com.inlearn.ui.support.FormMessages;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UiFormats;
import com.inlearn.ui.support.UnsavedChangesTracker;
import com.inlearn.ui.support.View;
import com.inlearn.util.InputValidator;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Create or edit an assignment: title, instructions, deadline, points and late policy.
 * Settings that can no longer change safely (because work has been graded) are locked and
 * the reason is shown next to them.
 */
public class AssignmentEditorController implements Page {

    private static final LocalTime DEFAULT_DUE_TIME = LocalTime.of(23, 59);
    private static final int DEFAULT_DAYS_UNTIL_DUE = 7;
    private static final int DEFAULT_CUTOFF_DAYS_AFTER_DUE = 2;
    private static final String DEFAULT_MAX_POINTS = "10";

    private final UiContext ui;
    private final UnsavedChangesTracker changes = new UnsavedChangesTracker();
    private Long courseIdForNew;
    private Long assignmentIdToEdit;
    private AssignmentEditContext context;

    @FXML private HBox existingWorkBanner;
    @FXML private Label existingWorkLabel;
    @FXML private TextField titleField;
    @FXML private TextArea instructionsArea;
    @FXML private DatePicker dueDatePicker;
    @FXML private TextField dueTimeField;
    @FXML private TextField maxPointsField;
    @FXML private Label zoneLabel;
    @FXML private Label maxPointsLockNote;
    @FXML private ComboBox<LatePolicy> latePolicyChoice;
    @FXML private CheckBox cutoffCheck;
    @FXML private HBox cutoffRow;
    @FXML private DatePicker cutoffDatePicker;
    @FXML private TextField cutoffTimeField;
    @FXML private Label latePolicyExplanation;
    @FXML private Label errorLabel;
    @FXML private Button deleteButton;
    @FXML private Button cancelButton;
    @FXML private Button saveButton;

    public AssignmentEditorController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        latePolicyChoice.getItems().setAll(LatePolicy.values());
        latePolicyChoice.valueProperty().addListener((observable, oldPolicy, newPolicy) -> updateLatePolicyControls());
        cutoffCheck.selectedProperty().addListener((observable, wasSelected, isSelected) -> updateLatePolicyControls());
        zoneLabel.setText("Deadlines use " + ui.formats().zoneDescription()
                + ". Students see the deadline with its time zone.");
        setFormDisabled(true);
    }

    /** Opens the editor to create a new assignment in a course. */
    public void createIn(long courseId) {
        this.courseIdForNew = courseId;
    }

    /** Opens the editor for an existing assignment. */
    public void edit(long assignmentId) {
        this.assignmentIdToEdit = assignmentId;
    }

    @Override
    public void onShown() {
        ui.tasks().task(() -> assignmentIdToEdit != null
                        ? ui.app().assignmentService().getEditContext(ui.currentUser(), assignmentIdToEdit)
                        : ui.app().assignmentService().getNewAssignmentContext(ui.currentUser(), courseIdForNew))
                .onSuccess(this::showContext)
                .onFailure(error -> {
                    FormMessages.showError(errorLabel, error);
                    ui.errorPresenter().present(error, this::onShown);
                })
                .start();
    }

    @Override
    public boolean hasUnsavedChanges() {
        return context != null && changes.hasUnsavedChanges();
    }

    @Override
    public void save() {
        if (!saveButton.isDisabled()) {
            onSave();
        }
    }

    private void showContext(AssignmentEditContext loaded) {
        this.context = loaded;
        ui.navigator().highlightCourse(loaded.course().id());
        Assignment assignment = loaded.assignment();
        ZoneId zone = ui.formats().zone();
        if (assignment == null) {
            ui.navigator().setPageTitle("New assignment", loaded.course().displayLabel());
            dueDatePicker.setValue(LocalDate.now(zone).plusDays(DEFAULT_DAYS_UNTIL_DUE));
            dueTimeField.setText(DateTimeInputs.formatTime(DEFAULT_DUE_TIME));
            maxPointsField.setText(DEFAULT_MAX_POINTS);
            latePolicyChoice.setValue(LatePolicy.NO_LATE);
            deleteButton.setVisible(false);
            deleteButton.setManaged(false);
        } else {
            ui.navigator().setPageTitle("Edit assignment", loaded.course().displayLabel() + " · " + assignment.title());
            titleField.setText(assignment.title());
            instructionsArea.setText(assignment.instructions());
            ZonedDateTime due = assignment.dueAt().atZone(zone);
            dueDatePicker.setValue(due.toLocalDate());
            dueTimeField.setText(DateTimeInputs.formatTime(due.toLocalTime()));
            maxPointsField.setText(UiFormats.points(assignment.maxPoints()));
            latePolicyChoice.setValue(assignment.latePolicy());
            if (assignment.lateCutoffAt() != null) {
                ZonedDateTime cutoff = assignment.lateCutoffAt().atZone(zone);
                cutoffCheck.setSelected(true);
                cutoffDatePicker.setValue(cutoff.toLocalDate());
                cutoffTimeField.setText(DateTimeInputs.formatTime(cutoff.toLocalTime()));
            }
            showExistingWorkNotes(loaded);
        }
        setFormDisabled(!loaded.course().isActive());
        maxPointsField.setDisable(!loaded.course().isActive() || loaded.maxPointsLocked());
        updateLatePolicyControls();
        changes.track(titleField.textProperty(), instructionsArea.textProperty(), dueDatePicker.valueProperty(),
                dueTimeField.textProperty(), maxPointsField.textProperty(), latePolicyChoice.valueProperty(),
                cutoffCheck.selectedProperty(), cutoffDatePicker.valueProperty(), cutoffTimeField.textProperty());
        titleField.requestFocus();
    }

    private void showExistingWorkNotes(AssignmentEditContext loaded) {
        if (loaded.submissionCount() == 0) {
            return;
        }
        existingWorkLabel.setText(loaded.submissionCount() + (loaded.submissionCount() == 1 ? " student has" : " students have")
                + " already submitted, and " + loaded.gradedCount() + " of those submissions "
                + (loaded.gradedCount() == 1 ? "is" : "are") + " graded. Changing the due date recalculates whether "
                + "each submission was late. Changes that would reject work already accepted are blocked.");
        existingWorkBanner.setVisible(true);
        existingWorkBanner.setManaged(true);
        deleteButton.setDisable(true);
        deleteButton.setText("Can't delete (has submissions)");
        if (loaded.maxPointsLocked()) {
            maxPointsLockNote.setText("Maximum points are locked because graded work was marked out of "
                    + UiFormats.points(loaded.assignment().maxPoints()) + ".");
            maxPointsLockNote.setVisible(true);
            maxPointsLockNote.setManaged(true);
        }
    }

    @FXML
    private void onSave() {
        if (context == null) {
            return;
        }
        FormMessages.clear(errorLabel);
        AssignmentDraft draft;
        try {
            draft = readDraft();
        } catch (ValidationException e) {
            FormMessages.showError(errorLabel, e.getMessage());
            return;
        }
        Assignment existing = context.assignment();
        if (existing != null && context.submissionCount() > 0 && !existing.dueAt().equals(draft.dueAt())
                && !ui.dialogs().confirm("Change the due date?",
                "This assignment already has " + context.submissionCount() + " submission(s). Each one's late status "
                        + "will be recalculated against the new due date of " + ui.formats().dateTime(draft.dueAt()) + ".",
                "Change due date", false)) {
            return;
        }
        ui.tasks().task(() -> existing == null
                        ? new UpdateResult(ui.app().assignmentService().createAssignment(ui.currentUser(),
                        context.course().id(), draft), 0)
                        : ui.app().assignmentService().updateAssignment(ui.currentUser(), existing.id(), draft,
                        existing.version()))
                .disabling(saveButton, cancelButton, deleteButton)
                .onSuccess(outcome -> {
                    changes.markSaved();
                    String message = existing == null
                            ? "\"" + outcome.assignment().title() + "\" has been created. Students can see it now."
                            : "Your changes to \"" + outcome.assignment().title() + "\" have been saved.";
                    if (outcome.lateStatusChanges() > 0) {
                        message += " Late status was updated for " + outcome.lateStatusChanges() + " submission(s).";
                    }
                    ui.notifier().success(message);
                    returnToCourse();
                })
                .onFailure(error -> FormMessages.showError(errorLabel, ErrorPresenter.userMessage(error)))
                .start();
    }

    @FXML
    private void onCancel() {
        returnToCourse();
    }

    @FXML
    private void onDelete() {
        Assignment existing = context == null ? null : context.assignment();
        if (existing == null || !ui.dialogs().confirm("Delete \"" + existing.title() + "\"?",
                "The assignment will be removed for everyone.", "Delete assignment", true)) {
            return;
        }
        ui.tasks().task(() -> {
                    ui.app().assignmentService().deleteAssignment(ui.currentUser(), existing.id());
                    return existing;
                })
                .disabling(saveButton, cancelButton, deleteButton)
                .onSuccess(deleted -> {
                    changes.markSaved();
                    ui.notifier().success("\"" + deleted.title() + "\" has been deleted.");
                    returnToCourse();
                })
                .start();
    }

    /** Reads and checks the form. Business rules are re-checked by the service when saving. */
    private AssignmentDraft readDraft() {
        ZoneId zone = ui.formats().zone();
        Instant dueAt = DateTimeInputs.toInstant(dueDatePicker.getValue(), dueTimeField.getText(), zone, "due");
        BigDecimal maxPoints = InputValidator.parsePoints(maxPointsField.getText(), "Maximum points");
        LatePolicy policy = latePolicyChoice.getValue();
        Instant cutoff = null;
        if (policy == LatePolicy.ALLOW_LATE && cutoffCheck.isSelected()) {
            cutoff = DateTimeInputs.toInstant(cutoffDatePicker.getValue(), cutoffTimeField.getText(), zone, "cutoff");
        }
        return new AssignmentDraft(titleField.getText(), instructionsArea.getText(), dueAt, maxPoints, policy, cutoff);
    }

    private void updateLatePolicyControls() {
        boolean allowLate = latePolicyChoice.getValue() == LatePolicy.ALLOW_LATE;
        boolean editable = context != null && context.course().isActive();
        cutoffCheck.setDisable(!allowLate || !editable);
        boolean showCutoff = allowLate && cutoffCheck.isSelected();
        cutoffRow.setDisable(!showCutoff || !editable);
        if (showCutoff && cutoffDatePicker.getValue() == null && dueDatePicker.getValue() != null) {
            cutoffDatePicker.setValue(dueDatePicker.getValue().plusDays(DEFAULT_CUTOFF_DAYS_AFTER_DUE));
            cutoffTimeField.setText(DateTimeInputs.formatTime(DEFAULT_DUE_TIME));
        }
        latePolicyExplanation.setText(allowLate
                ? "Students can still submit after the deadline" + (showCutoff ? " until the cutoff" : "")
                + "; those submissions are labelled late so you can decide how to treat them."
                : "The submit button closes at the due time. Students who haven't submitted are shown as overdue.");
    }

    private void setFormDisabled(boolean disabled) {
        titleField.setDisable(disabled);
        instructionsArea.setDisable(disabled);
        dueDatePicker.setDisable(disabled);
        dueTimeField.setDisable(disabled);
        maxPointsField.setDisable(disabled);
        latePolicyChoice.setDisable(disabled);
        saveButton.setDisable(disabled);
    }

    private void returnToCourse() {
        if (context == null) {
            ui.navigator().back();
            return;
        }
        // Return to the course's Assignments tab rather than to whichever tab was open before.
        long courseId = context.course().id();
        ui.navigator().returnTo(View.INSTRUCTOR_COURSE, (InstructorCourseController controller) ->
                controller.setCourse(courseId, InstructorCourseController.WorkspaceTab.ASSIGNMENTS));
    }
}
