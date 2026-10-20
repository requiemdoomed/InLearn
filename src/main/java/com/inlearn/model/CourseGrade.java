package com.inlearn.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * A student's current grade in one course, calculated from graded work only.
 *
 * @param earnedPoints     sum of points earned on graded assignments
 * @param possiblePoints   sum of maximum points of graded assignments
 * @param percentage       full-precision percentage, empty when nothing has been graded
 * @param gradedCount      number of graded assignments included
 * @param assignmentCount  number of assignments in the course
 */
public record CourseGrade(
        BigDecimal earnedPoints,
        BigDecimal possiblePoints,
        Optional<BigDecimal> percentage,
        int gradedCount,
        int assignmentCount) {

    /** Digits shown after the decimal point. Rounding happens only here, at display time. */
    private static final int DISPLAY_DECIMALS = 1;

    /** Display text such as "87.5%", or "N/A" when no assignments are graded yet. */
    public String percentageLabel() {
        return percentage
                .map(value -> value.setScale(DISPLAY_DECIMALS, RoundingMode.HALF_UP).toPlainString() + "%")
                .orElse("N/A");
    }

    public boolean hasGradedWork() {
        return percentage.isPresent();
    }
}
