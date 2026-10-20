package com.inlearn.ui.student;

import com.inlearn.model.Assignment;
import com.inlearn.model.Grade;
import com.inlearn.model.StudentAssignment;
import com.inlearn.model.Submission;
import com.inlearn.service.SubmissionService.AssignmentDetails;
import com.inlearn.service.SubmissionService.SubmissionReceipt;
import com.inlearn.ui.common.FileDownloads;
import com.inlearn.ui.support.FormMessages;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.StatusBadges;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UiFormats;
import com.inlearn.util.FileStorage;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Assignment details for a student: instructions, deadline, upload, receipt, grade and feedback.
 */
public class AssignmentDetailsController implements Page {

    private final UiContext ui;
    private long assignmentId;
    private AssignmentDetails details;
    private Path chosenFile;
    private SubmissionReceipt lastReceipt;

    @FXML private Label titleLabel;
    @FXML private Label statusBadge;
    @FXML private Label dueLabel;
    @FXML private Label pointsLabel;
    @FXML private Label latePolicyLabel;
    @FXML private Label instructionsLabel;
    @FXML private VBox receiptBox;
    @FXML private Label receiptHeading;
    @FXML private Label receiptFile;
    @FXML private Label receiptTime;
    @FXML private Label receiptStatus;
    @FXML private Label noSubmissionLabel;
    @FXML private HBox downloadRow;
    @FXML private Button downloadButton;
    @FXML private VBox submitSection;
    @FXML private Label submitRules;
    @FXML private Button chooseFileButton;
    @FXML private Label chosenFileLabel;
    @FXML private Button submitButton;
    @FXML private ProgressIndicator uploadProgress;
    @FXML private Label uploadStatusLabel;
    @FXML private HBox closedBanner;
    @FXML private Label closedMessage;
    @FXML private Label errorLabel;
    @FXML private Label gradeLabel;
    @FXML private Label gradeDetailLabel;
    @FXML private VBox feedbackBox;
    @FXML private Label feedbackLabel;

    public AssignmentDetailsController(UiContext ui) {
        this.ui = ui;
    }

    public void setAssignment(long assignmentId) {
        this.assignmentId = assignmentId;
    }

    @Override
    public void onShown() {
        refresh();
    }

    @Override
    public void refresh() {
        ui.tasks().task(() -> ui.app().submissionService().getAssignmentDetails(ui.currentUser(), assignmentId))
                .onSuccess(this::showDetails)
                .start();
    }

    /** A chosen-but-not-submitted file counts as unsaved work. */
    @Override
    public boolean hasUnsavedChanges() {
        return chosenFile != null;
    }

    @Override
    public void save() {
        if (!submitButton.isDisabled() && submitSection.isVisible()) {
            onSubmit();
        }
    }

    private void showDetails(AssignmentDetails loaded) {
        this.details = loaded;
        StudentAssignment studentAssignment = loaded.studentAssignment();
        Assignment assignment = studentAssignment.assignment();
        ui.navigator().highlightCourse(loaded.course().id());
        ui.navigator().setPageTitle(assignment.title(), loaded.course().displayLabel());

        titleLabel.setText(assignment.title());
        StatusBadges.apply(statusBadge, StatusBadges.forProgress(studentAssignment.progress()));
        dueLabel.setText(ui.formats().dateTime(assignment.dueAt()) + "  (" + ui.formats().relative(assignment.dueAt(), Instant.now())
                + ")");
        pointsLabel.setText(UiFormats.points(assignment.maxPoints()) + " points");
        latePolicyLabel.setText(ui.formats().latePolicyExplanation(assignment));
        instructionsLabel.setText(assignment.instructions());

        showSubmission(studentAssignment.submission());
        showSubmitControls(loaded);
        showGrade(assignment, studentAssignment);
    }

    private void showSubmission(Submission submission) {
        boolean hasSubmission = submission != null;
        receiptBox.setVisible(hasSubmission);
        receiptBox.setManaged(hasSubmission);
        noSubmissionLabel.setVisible(!hasSubmission);
        noSubmissionLabel.setManaged(!hasSubmission);
        downloadRow.setVisible(hasSubmission);
        downloadRow.setManaged(hasSubmission);
        if (!hasSubmission) {
            return;
        }
        boolean justSubmitted = lastReceipt != null && lastReceipt.submission().id() == submission.id();
        receiptHeading.setText(justSubmitted
                ? (lastReceipt.replacedPrevious() ? "Your submission has been replaced." : "Your assignment has been submitted.")
                : "Submission receipt");
        receiptFile.setText("File: " + submission.originalFilename() + " (" + FileStorage.formatSize(submission.fileSizeBytes()) + ")");
        receiptTime.setText("Submitted: " + ui.formats().dateTime(submission.submittedAt())
                + (submission.attemptNumber() > 1 ? " · attempt " + submission.attemptNumber() : ""));
        receiptStatus.setText(submission.late() ? "Status: Submitted late (after the due date)" : "Status: On time");
    }

    private void showSubmitControls(AssignmentDetails loaded) {
        boolean allowed = loaded.submissionDecision().allowed();
        boolean hasSubmission = loaded.studentAssignment().hasSubmission();
        submitSection.setVisible(allowed);
        submitSection.setManaged(allowed);
        closedBanner.setVisible(!allowed);
        closedBanner.setManaged(!allowed);
        closedMessage.setText(allowed ? "" : loaded.submissionDecision().message());
        submitButton.setText(hasSubmission ? "_Replace submission" : "_Submit assignment");
        String lateNote = loaded.submissionDecision().late()
                ? "The deadline has passed, so this submission will be marked late. " : "";
        submitRules.setText(lateNote + "You can replace your submission until the deadline as long as it hasn't been "
                + "graded. Up to " + ui.app().fileStorage().maxUploadSizeLabel() + " per file.");
        chosenFile = null;
        chosenFileLabel.setText("No file chosen");
        submitButton.setDisable(true);
        uploadStatusLabel.setText("");
    }

    private void showGrade(Assignment assignment, StudentAssignment studentAssignment) {
        Grade grade = studentAssignment.grade();
        boolean graded = grade != null;
        feedbackBox.setVisible(graded);
        feedbackBox.setManaged(graded);
        if (graded) {
            gradeLabel.setText(UiFormats.pointsOutOf(grade.points(), assignment.maxPoints()) + "  ·  "
                    + UiFormats.percentOf(grade.points(), assignment.maxPoints()));
            gradeDetailLabel.setText("Graded by " + grade.gradedByName() + " on " + ui.formats().dateTime(grade.gradedAt()));
            feedbackLabel.setText(grade.feedback() == null || grade.feedback().isBlank()
                    ? "No written feedback was left." : grade.feedback());
        } else if (studentAssignment.hasSubmission()) {
            gradeLabel.setText("Not graded");
            gradeDetailLabel.setText("Awaiting feedback. You'll see your grade and your instructor's comments here.");
        } else {
            gradeLabel.setText("Not graded");
            gradeDetailLabel.setText("Submit your work to receive a grade.");
        }
    }

    @FXML
    private void onChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose your assignment file");
        File selected = chooser.showOpenDialog(ui.stage());
        if (selected == null) {
            return;
        }
        FormMessages.clear(errorLabel);
        try {
            ui.app().fileStorage().validateUpload(selected.toPath());
        } catch (RuntimeException e) {
            FormMessages.showError(errorLabel, e);
            return;
        }
        chosenFile = selected.toPath();
        chosenFileLabel.setText(selected.getName() + " (" + FileStorage.formatSize(selected.length()) + ")");
        submitButton.setDisable(false);
    }

    @FXML
    private void onSubmit() {
        Path file = chosenFile;
        if (file == null || details == null) {
            return;
        }
        if (details.studentAssignment().hasSubmission() && !ui.dialogs().confirm("Replace your submission?",
                "Your current file, " + details.studentAssignment().submission().originalFilename()
                        + ", will be replaced by " + file.getFileName() + ".", "Replace submission", false)) {
            return;
        }
        FormMessages.clear(errorLabel);
        uploadStatusLabel.setText("Uploading " + file.getFileName() + "…");
        ui.tasks().task(() -> ui.app().submissionService().submit(ui.currentUser(), assignmentId, file))
                .disabling(submitButton, chooseFileButton)
                .showing(uploadProgress)
                .onSuccess(receipt -> {
                    lastReceipt = receipt;
                    chosenFile = null;
                    ui.notifier().success(receipt.replacedPrevious()
                            ? "Your submission has been replaced." : "Your assignment has been submitted.");
                    refresh();
                })
                .onFailure(error -> {
                    // The previous submission (if any) is untouched. Keep the chosen file so the
                    // student can simply select Submit again once the problem is fixed.
                    uploadStatusLabel.setText("Not submitted.");
                    FormMessages.showError(errorLabel, error);
                })
                .start();
    }

    @FXML
    private void onDownloadOwn() {
        if (details == null || !details.studentAssignment().hasSubmission()) {
            return;
        }
        Submission submission = details.studentAssignment().submission();
        FileDownloads.saveAs(ui, submission.originalFilename(),
                destination -> ui.app().submissionService().downloadSubmission(ui.currentUser(), submission.id(), destination),
                downloadButton);
    }
}
