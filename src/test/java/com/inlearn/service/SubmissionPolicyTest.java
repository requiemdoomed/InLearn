package com.inlearn.service;

import com.inlearn.model.Assignment;
import com.inlearn.model.AssignmentProgress;
import com.inlearn.model.LatePolicy;
import com.inlearn.model.Submission;
import com.inlearn.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionPolicyTest {

    private static final Instant DUE = Instant.parse("2026-10-23T03:59:00Z");

    private final MutableClock clock = new MutableClock(DUE.minus(Duration.ofHours(1)));

    private static Assignment assignment(LatePolicy policy, Instant cutoff) {
        return new Assignment(1, 1, "Lab", "Do it", DUE, new BigDecimal("10.00"), policy, cutoff, 0, DUE);
    }

    @Test
    void beforeTheDeadlineSubmissionsAreOnTime() {
        SubmissionPolicy.Decision decision = SubmissionPolicy.evaluate(assignment(LatePolicy.NO_LATE, null), false, clock.instant());
        assertTrue(decision.allowed());
        assertFalse(decision.late());
    }

    @Test
    void exactlyAtTheDeadlineStillCountsAsOnTime() {
        clock.setInstant(DUE);
        SubmissionPolicy.Decision decision = SubmissionPolicy.evaluate(assignment(LatePolicy.NO_LATE, null), false, clock.instant());
        assertTrue(decision.allowed());
        assertFalse(decision.late());
    }

    @Test
    void afterTheDeadlineNoLatePolicyRefuses() {
        clock.advance(Duration.ofHours(2));
        SubmissionPolicy.Decision decision = SubmissionPolicy.evaluate(assignment(LatePolicy.NO_LATE, null), false, clock.instant());
        assertFalse(decision.allowed());
        assertTrue(decision.message().contains("deadline has passed"));
    }

    @Test
    void afterTheDeadlineAllowLateAcceptsAndMarksLate() {
        clock.advance(Duration.ofDays(3));
        SubmissionPolicy.Decision decision = SubmissionPolicy.evaluate(assignment(LatePolicy.ALLOW_LATE, null), false, clock.instant());
        assertTrue(decision.allowed());
        assertTrue(decision.late());
    }

    @Test
    void lateSubmissionsCloseAtTheCutoff() {
        Assignment withCutoff = assignment(LatePolicy.ALLOW_LATE, DUE.plus(Duration.ofDays(1)));
        clock.setInstant(DUE.plus(Duration.ofHours(12)));
        assertTrue(SubmissionPolicy.evaluate(withCutoff, false, clock.instant()).allowed());
        clock.setInstant(DUE.plus(Duration.ofDays(2)));
        assertFalse(SubmissionPolicy.evaluate(withCutoff, false, clock.instant()).allowed());
    }

    @Test
    void gradedWorkCannotBeReplacedEvenBeforeTheDeadline() {
        SubmissionPolicy.Decision decision = SubmissionPolicy.evaluate(assignment(LatePolicy.ALLOW_LATE, null), true, clock.instant());
        assertFalse(decision.allowed());
        assertTrue(decision.message().contains("graded"));
    }

    @Test
    void progressShowsOverdueOnlyWhenNothingWasSubmitted() {
        Assignment assignment = assignment(LatePolicy.ALLOW_LATE, null);
        assertEquals(AssignmentProgress.NOT_SUBMITTED, SubmissionPolicy.progressOf(assignment, null, null, clock.instant()));
        clock.advance(Duration.ofDays(1));
        assertEquals(AssignmentProgress.OVERDUE, SubmissionPolicy.progressOf(assignment, null, null, clock.instant()));
        Submission late = new Submission(1, 1, 1, "Sam", "a.txt", "k", 1, clock.instant(), true, 1, 0);
        assertEquals(AssignmentProgress.SUBMITTED_LATE, SubmissionPolicy.progressOf(assignment, late, null, clock.instant()));
    }
}
