package com.inlearn.util;

import com.inlearn.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputValidatorTest {

    @Test
    void emailsAreNormalisedToLowerCase() {
        assertEquals("swayam.shakya@example.edu", InputValidator.normalizeEmail("  Swayam.Shakya@Example.EDU "));
    }

    @Test
    void invalidEmailIsRejected() {
        assertThrows(ValidationException.class, () -> InputValidator.normalizeEmail("not-an-email"));
    }

    @Test
    void courseCodesAreUpperCasedAndSpacesCollapsed() {
        assertEquals("CS 101", InputValidator.normalizeCourseCode("cs   101"));
        assertThrows(ValidationException.class, () -> InputValidator.normalizeCourseCode("CS/101"));
    }

    @Test
    void passwordRulesAreEnforced() {
        assertThrows(ValidationException.class, () -> InputValidator.validateNewPassword("short1".toCharArray()));
        assertThrows(ValidationException.class, () -> InputValidator.validateNewPassword("onlyletters".toCharArray()));
        assertThrows(ValidationException.class, () -> InputValidator.validateNewPassword("1234567890".toCharArray()));
        InputValidator.validateNewPassword("Learn#2026".toCharArray());
    }

    @Test
    void pointsAcceptTwoDecimalPlacesOnly() {
        assertEquals(new BigDecimal("8.50"), InputValidator.parsePoints("8.5", "Points"));
        assertThrows(ValidationException.class, () -> InputValidator.parsePoints("8.555", "Points"));
        assertThrows(ValidationException.class, () -> InputValidator.parsePoints("-1", "Points"));
        assertThrows(ValidationException.class, () -> InputValidator.parsePoints("eight", "Points"));
    }

    @Test
    void onlyHttpLinksAreAccepted() {
        assertEquals("https://example.edu/notes", InputValidator.validateWebUrl("https://example.edu/notes"));
        assertThrows(ValidationException.class, () -> InputValidator.validateWebUrl("javascript:alert(1)"));
        assertThrows(ValidationException.class, () -> InputValidator.validateWebUrl("file:///etc/passwd"));
    }
}
