package com.inlearn.ui.support;

import com.inlearn.model.Assignment;
import com.inlearn.model.LatePolicy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Formats dates, deadlines and points for display. Every date shown in InLearn goes through
 * here so they all use the configured display time zone and always show that zone.
 */
public class UiFormats {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a z", Locale.US);
    private static final DateTimeFormatter SHORT_DATE_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a z", Locale.US);
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final ZoneId zone;

    public UiFormats(ZoneId zone) {
        this.zone = zone;
    }

    public ZoneId zone() {
        return zone;
    }

    /** For example "Fri, Oct 23, 2026 at 11:59 PM EDT". */
    public String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant.atZone(zone));
    }

    /** For example "Oct 23, 11:59 PM EDT", for compact table cells. */
    public String shortDateTime(Instant instant) {
        return instant == null ? "—" : SHORT_DATE_TIME.format(instant.atZone(zone));
    }

    public String date(Instant instant) {
        return instant == null ? "—" : DATE_ONLY.format(instant.atZone(zone));
    }

    /** The time zone in words, for example "Eastern Time (America/Toronto)". */
    public String zoneDescription() {
        return zone.getDisplayName(TextStyle.FULL_STANDALONE, Locale.US) + " (" + zone.getId() + ")";
    }

    /** The zone's current abbreviation, for example "EDT". */
    public String zoneAbbreviation() {
        return DateTimeFormatter.ofPattern("z", Locale.US).format(ZonedDateTime.now(zone));
    }

    /** "in 3 days", "in 5 hours", "2 days ago" — friendly relative times for dashboards. */
    public String relative(Instant instant, Instant now) {
        Duration difference = Duration.between(now, instant);
        boolean future = !difference.isNegative();
        Duration magnitude = difference.abs();
        String amount;
        if (magnitude.toDays() >= 1) {
            amount = plural(magnitude.toDays(), "day");
        } else if (magnitude.toHours() >= 1) {
            amount = plural(magnitude.toHours(), "hour");
        } else if (magnitude.toMinutes() >= 1) {
            amount = plural(magnitude.toMinutes(), "minute");
        } else {
            return future ? "in less than a minute" : "just now";
        }
        return future ? "in " + amount : amount + " ago";
    }

    /** Points without unnecessary zeros: 8.50 becomes "8.5", 10.00 becomes "10". */
    public static String points(BigDecimal points) {
        return points == null ? "—" : points.stripTrailingZeros().toPlainString();
    }

    public static String pointsOutOf(BigDecimal earned, BigDecimal possible) {
        return points(earned) + " / " + points(possible);
    }

    /** One assignment's score as a percentage, rounded to one decimal place for display only. */
    public static String percentOf(BigDecimal earned, BigDecimal possible) {
        if (earned == null || possible == null || possible.signum() == 0) {
            return "N/A";
        }
        BigDecimal percent = earned.multiply(ONE_HUNDRED).divide(possible, MathContext.DECIMAL128);
        return percent.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** A sentence explaining the late policy, shown to students next to the deadline. */
    public String latePolicyExplanation(Assignment assignment) {
        if (assignment.latePolicy() == LatePolicy.NO_LATE) {
            return "Late submissions are not accepted. Submissions close at the due time.";
        }
        if (assignment.lateCutoffAt() != null) {
            return "Late submissions are accepted until " + dateTime(assignment.lateCutoffAt())
                    + " and are marked late.";
        }
        return "Late submissions are accepted and marked late.";
    }

    private static String plural(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s");
    }
}
