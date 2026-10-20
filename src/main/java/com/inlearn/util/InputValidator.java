package com.inlearn.util;

import com.inlearn.exception.ValidationException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validation and normalisation of user input. Each method either returns a cleaned value or
 * throws a {@link ValidationException} whose message names the field and explains the fix.
 */
public final class InputValidator {

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 64;
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_NAME_LENGTH = 120;
    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_COURSE_CODE_LENGTH = 20;
    public static final int MAX_TERM_LENGTH = 40;
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_SHORT_TEXT_LENGTH = 1000;
    public static final int MAX_LONG_TEXT_LENGTH = 20000;
    public static final int POINTS_DECIMAL_PLACES = 2;

    /** bcrypt only reads the first 72 bytes of a password, so longer input is rejected rather than truncated. */
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9 -]*[A-Z0-9]$");

    private InputValidator() {
    }

    /** Trims the value and rejects it when empty or too long. */
    public static String requireText(String value, String fieldName, int maxLength) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            throw new ValidationException(fieldName + " is required.");
        }
        return checkLength(trimmed, fieldName, maxLength);
    }

    /** Trims the value, returning {@code null} for blank input. */
    public static String optionalText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value.strip(), fieldName, maxLength);
    }

    /** Lower-cases and validates an email address so uniqueness checks are case-insensitive. */
    public static String normalizeEmail(String email) {
        String normalized = requireText(email, "Email", MAX_EMAIL_LENGTH).toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Enter a valid email address, such as name@example.edu.");
        }
        return normalized;
    }

    /** Course codes are stored in upper case, for example "CS 101". */
    public static String normalizeCourseCode(String code) {
        String normalized = requireText(code, "Course code", MAX_COURSE_CODE_LENGTH)
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        if (normalized.length() < 2 || !COURSE_CODE_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(
                    "Course code can contain letters, numbers, spaces and hyphens only, for example CS 101.");
        }
        return normalized;
    }

    /** Checks the strength rules for a new password. */
    public static void validateNewPassword(char[] password) {
        if (password == null || password.length < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
        }
        if (password.length > MAX_PASSWORD_LENGTH
                || new String(password).getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new ValidationException("Password must be at most " + MAX_PASSWORD_LENGTH + " characters long.");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char character : password) {
            hasLetter |= Character.isLetter(character);
            hasDigit |= Character.isDigit(character);
        }
        if (!hasLetter || !hasDigit) {
            throw new ValidationException("Password must contain at least one letter and one number.");
        }
    }

    /**
     * Parses a points value such as "8.5".
     *
     * @throws ValidationException for non-numbers, negatives or more than two decimal places
     */
    public static BigDecimal parsePoints(String text, String fieldName) {
        String trimmed = requireText(text, fieldName, MAX_TITLE_LENGTH);
        BigDecimal points;
        try {
            points = new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName + " must be a number, such as 8 or 8.5.");
        }
        if (points.signum() < 0) {
            throw new ValidationException(fieldName + " can't be negative.");
        }
        if (points.stripTrailingZeros().scale() > POINTS_DECIMAL_PLACES) {
            throw new ValidationException(fieldName + " can have at most " + POINTS_DECIMAL_PLACES + " decimal places.");
        }
        return points.setScale(POINTS_DECIMAL_PLACES);
    }

    /** Accepts only absolute http and https links, which are safe to open in a browser. */
    public static String validateWebUrl(String url) {
        String trimmed = requireText(url, "Link", MAX_URL_LENGTH);
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                throw new ValidationException("Enter a full web address starting with https:// or http://.");
            }
            return trimmed;
        } catch (URISyntaxException e) {
            throw new ValidationException("That link isn't a valid web address. Check it for spaces or typos.");
        }
    }

    private static String checkLength(String value, String fieldName, int maxLength) {
        if (value.length() > maxLength) {
            throw new ValidationException(fieldName + " must be " + maxLength + " characters or fewer.");
        }
        return value;
    }
}
