package com.inlearn.service;

import com.inlearn.exception.ValidationException;
import com.inlearn.model.Assignment;
import com.inlearn.model.CourseGrade;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The points-based gradebook formula:
 *
 * <pre>current grade = earned points on graded assignments / possible points on graded assignments * 100</pre>
 *
 * Ungraded assignments are left out entirely (they count neither as zero nor as possible points).
 * Calculations keep full precision; rounding happens only when a percentage is displayed
 * (see {@link CourseGrade#percentageLabel()}).
 */
public final class GradeCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private GradeCalculator() {
    }

    /**
     * @param assignments              every assignment in the course
     * @param earnedPointsByAssignment points earned, keyed by assignment id, for graded assignments only
     */
    public static CourseGrade calculate(List<Assignment> assignments, Map<Long, BigDecimal> earnedPointsByAssignment) {
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal possible = BigDecimal.ZERO;
        int gradedCount = 0;
        for (Assignment assignment : assignments) {
            BigDecimal points = earnedPointsByAssignment.get(assignment.id());
            if (points != null) {
                earned = earned.add(points);
                possible = possible.add(assignment.maxPoints());
                gradedCount++;
            }
        }
        Optional<BigDecimal> percentage = possible.signum() == 0
                ? Optional.empty()
                : Optional.of(earned.multiply(ONE_HUNDRED).divide(possible, MathContext.DECIMAL128));
        return new CourseGrade(earned, possible, percentage, gradedCount, assignments.size());
    }

    /**
     * Confirms a grade is between zero and the assignment's maximum points, inclusive.
     */
    public static void validatePoints(BigDecimal points, BigDecimal maxPoints) {
        if (points == null) {
            throw new ValidationException("Enter the points earned.");
        }
        if (points.signum() < 0) {
            throw new ValidationException("Points can't be negative.");
        }
        if (points.compareTo(maxPoints) > 0) {
            throw new ValidationException("Points can't be more than the assignment's maximum of "
                    + maxPoints.stripTrailingZeros().toPlainString() + ".");
        }
    }
}
