package com.inlearn.service;

import com.inlearn.model.Assignment;
import com.inlearn.model.Course;
import com.inlearn.model.CourseStatus;
import com.inlearn.model.Grade;
import com.inlearn.model.Gradebook;
import com.inlearn.model.GradebookRow;
import com.inlearn.model.GradebookRow.AssignmentResult;
import com.inlearn.model.LatePolicy;
import com.inlearn.model.Submission;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradebookCsvExporterTest {

    private static Assignment assignment(long id, String title, String maxPoints) {
        return new Assignment(id, 1, title, "x", Instant.EPOCH, new BigDecimal(maxPoints), LatePolicy.NO_LATE, null, 0,
                Instant.EPOCH);
    }

    private static Submission submission(long id, long assignmentId) {
        return new Submission(id, assignmentId, 7, "Student", "f.txt", "k" + id, 1, Instant.EPOCH, false, 1, 0);
    }

    @Test
    void writesHeaderGradesAndPlainWordsForMissingWork() {
        Assignment lab = assignment(1, "Lab 1", "10");
        Assignment essay = assignment(2, "Essay, draft", "40");
        Assignment quiz = assignment(3, "Quiz", "5");
        Map<Long, AssignmentResult> results = Map.of(
                1L, new AssignmentResult(submission(11, 1), new Grade(1, 11, new BigDecimal("8.50"), "", 2, "T", Instant.EPOCH, 0)),
                2L, new AssignmentResult(submission(12, 2), null));
        GradebookRow row = new GradebookRow(7, "Lovelace, Ada", "ada@test.edu", results,
                GradeCalculator.calculate(List.of(lab, essay, quiz), Map.of(1L, new BigDecimal("8.50"))));
        Course course = new Course(1, "CS 1", "Intro", null, "Fall", 2L, "T", CourseStatus.ACTIVE, 1, 0);

        String csv = GradebookCsvExporter.toCsv(new Gradebook(course, List.of(lab, essay, quiz), List.of(row)));
        String[] lines = csv.substring(GradebookCsvExporter.UTF8_BYTE_ORDER_MARK.length()).split("\r\n");

        assertEquals("Student,Email,Lab 1 (out of 10),\"Essay, draft (out of 40)\",Quiz (out of 5),"
                + "Earned points (graded work),Possible points (graded work),Current grade (%)", lines[0]);
        assertEquals("\"Lovelace, Ada\",ada@test.edu,8.5,Not graded,Not submitted,8.5,10,85.0", lines[1]);
    }

    @Test
    void escapesQuotesAndNeutralisesFormulas() {
        assertEquals("\"Say \"\"hi\"\"\"", GradebookCsvExporter.escape("Say \"hi\""));
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", GradebookCsvExporter.escape("=HYPERLINK(\"x\")"));
        assertEquals("-5", GradebookCsvExporter.escape("-5"));
        assertTrue(GradebookCsvExporter.escape("@cmd").startsWith("'"));
    }
}
