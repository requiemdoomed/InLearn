package com.inlearn.service;

import com.inlearn.exception.AuthenticationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Slows down password guessing: after {@value #MAX_FAILED_ATTEMPTS} wrong passwords for the same
 * email, sign-in for that email is paused for {@link #LOCKOUT_DURATION}.
 *
 * <p>Unknown emails are counted exactly like real ones, so the lockout message never reveals
 * whether an account exists. The counters live in this running copy of InLearn; see the README
 * for why a desktop client cannot enforce limits across every computer.
 */
public class SignInThrottle {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(5);

    private static final Logger LOGGER = Logger.getLogger(SignInThrottle.class.getName());

    private final Clock clock;
    private final Map<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();

    /** Failed attempts since the last success, and when the current lockout (if any) ends. */
    private record Attempts(int failures, Instant lockedUntil) {
    }

    public SignInThrottle(Clock clock) {
        this.clock = clock;
    }

    /** Refuses sign-in while the email is locked out. */
    public void requireNotLocked(String email) {
        Attempts attempts = attemptsByEmail.get(email);
        Instant now = clock.instant();
        if (attempts != null && attempts.lockedUntil() != null && now.isBefore(attempts.lockedUntil())) {
            long secondsLeft = Duration.between(now, attempts.lockedUntil()).toSeconds();
            long minutesLeft = Math.max(1, (secondsLeft + 59) / 60); // round up to whole minutes
            throw new AuthenticationException("Too many unsuccessful sign-in attempts. Wait " + minutesLeft
                    + (minutesLeft == 1 ? " minute" : " minutes") + " and try again, or ask your administrator "
                    + "to reset your password.");
        }
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        Attempts updated = attemptsByEmail.compute(email, (key, previous) -> {
            boolean lockExpired = previous != null && previous.lockedUntil() != null && !now.isBefore(previous.lockedUntil());
            int failures = (previous == null || lockExpired ? 0 : previous.failures()) + 1;
            Instant lockedUntil = failures >= MAX_FAILED_ATTEMPTS ? now.plus(LOCKOUT_DURATION) : null;
            return new Attempts(failures, lockedUntil);
        });
        if (updated.lockedUntil() != null) {
            LOGGER.warning("Sign-in temporarily locked after " + updated.failures() + " failed attempts");
        }
    }

    public void recordSuccess(String email) {
        attemptsByEmail.remove(email);
    }
}
