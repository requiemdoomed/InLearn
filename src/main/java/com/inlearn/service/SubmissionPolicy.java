package com.inlearn.service;

import com.inlearn.model.Assignment;
import com.inlearn.model.AssignmentProgress;
import com.inlearn.model.Grade;
import com.inlearn.model.LatePolicy;
import com.inlearn.model.Submission;

import java.time.Instant;

/**
 * The deadline and late-submission rules, kept free of database and UI code so they are easy
 * to read and to test with a fixed clock.
 *
 * <ol>
 *   <li>Once a submission is graded it can no longer be replaced.</li>
 *   <li>Up to and including the due time, students may submit and replace freely.</li>
 *   <li>After the due time, {@link LatePolicy#NO_LATE} refuses submissions.</li>
 *   <li>After the due time, {@link LatePolicy#ALLOW_LATE} accepts submissions marked late,
 *       until the optional cutoff.</li>
 * </ol>
 */
public final class SubmissionPolicy {

    private SubmissionPolicy() {
    }

    /**
     * Whether a student may submit now.
     *
     * @param late    if allowed, whether the submission will be recorded as late
     * @param message why the submission is refused, or {@code null} when it is allowed
     */
    public record Decision(boolean allowed, boolean late, String message) {

        static Decision onTime() {
            return new Decision(true, false, null);
        }

        static Decision lateAccepted() {
            return new Decision(true, true, null);
        }

        static Decision refused(String message) {
            return new Decision(false, false, message);
        }
    }

    /**
     * Decides whether a submission (new or replacement) is allowed at {@code now}.
     *
     * @param alreadyGraded whether the student's current submission has been graded
     */
    public static Decision evaluate(Assignment assignment, boolean alreadyGraded, Instant now) {
        if (alreadyGraded) {
            return Decision.refused("This assignment has already been graded, so your submission can't be replaced.");
        }
        if (!now.isAfter(assignment.dueAt())) {
            return Decision.onTime();
        }
        if (assignment.latePolicy() == LatePolicy.NO_LATE) {
            return Decision.refused("The deadline has passed and this assignment doesn't accept late submissions.");
        }
        if (assignment.lateCutoffAt() != null && now.isAfter(assignment.lateCutoffAt())) {
            return Decision.refused("The late submission period for this assignment has ended.");
        }
        return Decision.lateAccepted();
    }

    /** Whether a submission made at {@code submittedAt} counts as late for this assignment. */
    public static boolean isLate(Assignment assignment, Instant submittedAt) {
        return submittedAt.isAfter(assignment.dueAt());
    }

    /**
     * The student's progress on an assignment.
     *
     * @param submission the current submission, or {@code null}
     * @param grade      the grade for that submission, or {@code null}
     */
    public static AssignmentProgress progressOf(Assignment assignment, Submission submission, Grade grade,
                                                Instant now) {
        if (submission == null) {
            return assignment.isPastDue(now) ? AssignmentProgress.OVERDUE : AssignmentProgress.NOT_SUBMITTED;
        }
        if (grade != null) {
            return AssignmentProgress.GRADED;
        }
        return submission.late() ? AssignmentProgress.SUBMITTED_LATE : AssignmentProgress.SUBMITTED;
    }
}
