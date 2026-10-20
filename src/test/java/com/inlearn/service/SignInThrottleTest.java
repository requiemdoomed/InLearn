package com.inlearn.service;

import com.inlearn.exception.AuthenticationException;
import com.inlearn.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignInThrottleTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-20T12:00:00Z"));
    private final SignInThrottle throttle = new SignInThrottle(clock);

    private void failRepeatedly(String email, int times) {
        for (int attempt = 0; attempt < times; attempt++) {
            throttle.recordFailure(email);
        }
    }

    @Test
    void locksAfterTheMaximumNumberOfFailures() {
        failRepeatedly("a@test.edu", SignInThrottle.MAX_FAILED_ATTEMPTS - 1);
        assertDoesNotThrow(() -> throttle.requireNotLocked("a@test.edu"));
        throttle.recordFailure("a@test.edu");
        AuthenticationException error = assertThrows(AuthenticationException.class,
                () -> throttle.requireNotLocked("a@test.edu"));
        assertTrue(error.getMessage().contains("5 minutes"));
    }

    @Test
    void lockoutEndsAfterTheWaitingPeriod() {
        failRepeatedly("a@test.edu", SignInThrottle.MAX_FAILED_ATTEMPTS);
        clock.advance(SignInThrottle.LOCKOUT_DURATION.plus(Duration.ofSeconds(1)));
        assertDoesNotThrow(() -> throttle.requireNotLocked("a@test.edu"));
        // After the lock expires the count starts again rather than relocking immediately.
        throttle.recordFailure("a@test.edu");
        assertDoesNotThrow(() -> throttle.requireNotLocked("a@test.edu"));
    }

    @Test
    void aSuccessfulSignInResetsTheCountAndOtherEmailsAreUnaffected() {
        failRepeatedly("a@test.edu", SignInThrottle.MAX_FAILED_ATTEMPTS - 1);
        throttle.recordSuccess("a@test.edu");
        throttle.recordFailure("a@test.edu");
        assertDoesNotThrow(() -> throttle.requireNotLocked("a@test.edu"));
        failRepeatedly("b@test.edu", SignInThrottle.MAX_FAILED_ATTEMPTS);
        assertDoesNotThrow(() -> throttle.requireNotLocked("a@test.edu"));
    }
}
