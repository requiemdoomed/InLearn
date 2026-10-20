package com.inlearn.ui.support;

import com.inlearn.exception.ValidationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Converts the date picker + time field pairs used for deadlines into instants, interpreting
 * them in the configured display time zone.
 */
public final class DateTimeInputs {

    private static final DateTimeFormatter TWENTY_FOUR_HOUR = DateTimeFormatter.ofPattern("H:mm", Locale.US);
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US);
    private static final List<DateTimeFormatter> ACCEPTED_TIME_FORMATS = List.of(
            TWENTY_FOUR_HOUR,
            caseInsensitive("h:mm a"),
            caseInsensitive("h:mma"),
            caseInsensitive("h a"),
            caseInsensitive("ha"));

    private DateTimeInputs() {
    }

    /** Parses times such as "23:59", "9:30", "11:59 PM" or "5pm". */
    public static LocalTime parseTime(String text, String fieldName) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("Enter the " + fieldName + ", for example 23:59.");
        }
        for (DateTimeFormatter format : ACCEPTED_TIME_FORMATS) {
            try {
                return LocalTime.parse(trimmed, format);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted format; the error below covers the case where none match.
            }
        }
        throw new ValidationException("The " + fieldName + " \"" + trimmed
                + "\" isn't a valid time. Use 24-hour format such as 09:00 or 23:59.");
    }

    /** Combines a date and a time in the display zone into an instant. */
    public static Instant toInstant(LocalDate date, String timeText, ZoneId zone, String fieldName) {
        if (date == null) {
            throw new ValidationException("Choose the " + fieldName + " date.");
        }
        return date.atTime(parseTime(timeText, fieldName + " time")).atZone(zone).toInstant();
    }

    /** Formats a time for the time field, for example "09:05". */
    public static String formatTime(LocalTime time) {
        return DISPLAY_TIME.format(time);
    }

    private static DateTimeFormatter caseInsensitive(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.US);
    }
}
