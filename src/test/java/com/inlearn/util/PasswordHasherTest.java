package com.inlearn.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    /** The lowest bcrypt cost keeps the test fast; production uses {@link PasswordHasher#DEFAULT_COST}. */
    private final PasswordHasher hasher = new PasswordHasher(4);

    @Test
    void correctPasswordVerifies() {
        String hash = hasher.hash("Correct#Horse1".toCharArray());
        assertTrue(hasher.verify("Correct#Horse1".toCharArray(), hash));
    }

    @Test
    void wrongPasswordIsRejected() {
        String hash = hasher.hash("Correct#Horse1".toCharArray());
        assertFalse(hasher.verify("correct#horse1".toCharArray(), hash));
    }

    @Test
    void samePasswordGetsDifferentSaltedHashes() {
        String first = hasher.hash("Repeat#Me1".toCharArray());
        String second = hasher.hash("Repeat#Me1".toCharArray());
        assertNotEquals(first, second);
        assertTrue(first.startsWith("$2b$"));
    }

    @Test
    void malformedOrMissingHashNeverVerifies() {
        assertFalse(hasher.verify("anything1".toCharArray(), "not-a-bcrypt-hash"));
        assertFalse(hasher.verify("anything1".toCharArray(), null));
        assertFalse(hasher.verify("anything1".toCharArray(), ""));
    }
}
