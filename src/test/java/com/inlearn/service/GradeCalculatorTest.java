package com.inlearn.service;

import com.inlearn.exception.ValidationException;
import com.inlearn.model.Assignment;
import com.inlearn.model.CourseGrade;
import com.inlearn.model.LatePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GradeCalculatorTest {

    private static Assignment assignment(long id, String maxPoints) {
        return new Assignment(id, 1, "A" + id, "x", Instant.EPOCH, new BigDecimal(maxPoints), LatePolicy.NO_LATE, null, 0,
                Instant.EPOCH);
    }

    private final List<Assignment> assignments = List.of(assignment(1, "10"), assignment(2, "20"), assignment(3, "50"));

    @Test
    void ungradedAssignmentsAreLeftOutRatherThanCountedAsZero() {
        CourseGrade grade = GradeCalculator.calculate(assignments,
                Map.of(1L, new BigDecimal("9.5"), 2L, new BigDecimal("15")));

        assertEquals(0, new BigDecimal("24.5").compareTo(grade.earnedPoints()));
        assertEquals(0, new BigDecimal("30").compareTo(grade.possiblePoints()));
        assertEquals("81.7%", grade.percentageLabel());
        assertEquals(2, grade.gradedCount());
        assertEquals(3, grade.assignmentCount());
    }

    @Test
    void nothingGradedShowsNotApplicable() {
        CourseGrade grade = GradeCalculator.calculate(assignments, Map.of());
        assertFalse(grade.hasGradedWork());
        assertEquals("N/A", grade.percentageLabel());
    }

    @Test
    void fullPrecisionIsKeptUntilDisplay() {
        CourseGrade grade = GradeCalculator.calculate(List.of(assignment(1, "3")), Map.of(1L, new BigDecimal("1")));
        // 1/3 is kept to 34 significant digits and only rounded in the label.
        assertEquals("33.3%", grade.percentageLabel());
        assertEquals(0, grade.percentage().orElseThrow().compareTo(new BigDecimal("33.33333333333333333333333333333333")));
    }

    @Test
    void gradesMustBeBetweenZeroAndTheMaximum() {
        BigDecimal max = new BigDecimal("10");
        GradeCalculator.validatePoints(BigDecimal.ZERO, max);
        GradeCalculator.validatePoints(new BigDecimal("10.00"), max);
        assertThrows(ValidationException.class, () -> GradeCalculator.validatePoints(new BigDecimal("10.01"), max));
        assertThrows(ValidationException.class, () -> GradeCalculator.validatePoints(new BigDecimal("-0.5"), max));
        assertThrows(ValidationException.class, () -> GradeCalculator.validatePoints(null, max));
    }
}
