package com.inlearn.service;

import com.inlearn.model.Assignment;
import com.inlearn.model.CourseGrade;
import com.inlearn.model.Gradebook;
import com.inlearn.model.GradebookRow;
import com.inlearn.model.GradebookRow.AssignmentResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a course gradebook into CSV that opens cleanly in Excel, Numbers or Google Sheets.
 *
 * <p>One row per student; one column per assignment, then earned points, possible points and
 * the current grade. Cells use the same words as the interface: "Not graded" for submitted but
 * ungraded work and "Not submitted" when there is no submission, never a misleading zero.
 */
public final class GradebookCsvExporter {

    /** Lets Excel recognise the file as UTF-8 so accented names display correctly. */
    public static final String UTF8_BYTE_ORDER_MARK = "﻿";

    private static final String LINE_END = "\r\n";
    private static final int PERCENT_DECIMALS = 1;

    private GradebookCsvExporter() {
    }

    public static String toCsv(Gradebook gradebook) {
        StringBuilder csv = new StringBuilder(UTF8_BYTE_ORDER_MARK);
        List<String> header = new ArrayList<>(List.of("Student", "Email"));
        for (Assignment assignment : gradebook.assignments()) {
            header.add(assignment.title() + " (out of " + plain(assignment.maxPoints()) + ")");
        }
        header.addAll(List.of("Earned points (graded work)", "Possible points (graded work)", "Current grade (%)"));
        appendRow(csv, header);

        for (GradebookRow row : gradebook.rows()) {
            List<String> cells = new ArrayList<>(List.of(row.studentName(), row.studentEmail()));
            for (Assignment assignment : gradebook.assignments()) {
                cells.add(resultCell(row.results().get(assignment.id())));
            }
            CourseGrade grade = row.courseGrade();
            cells.add(plain(grade.earnedPoints()));
            cells.add(plain(grade.possiblePoints()));
            cells.add(grade.percentage()
                    .map(percent -> percent.setScale(PERCENT_DECIMALS, RoundingMode.HALF_UP).toPlainString())
                    .orElse("N/A"));
            appendRow(csv, cells);
        }
        return csv.toString();
    }

    private static String resultCell(AssignmentResult result) {
        if (result == null || result.submission() == null) {
            return "Not submitted";
        }
        if (result.grade() == null) {
            return "Not graded";
        }
        return plain(result.grade().points());
    }

    private static void appendRow(StringBuilder csv, List<String> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escape(cells.get(index)));
        }
        csv.append(LINE_END);
    }

    /**
     * Quotes a cell when needed (commas, quotes, line breaks). Cells starting with a formula
     * character get a leading apostrophe so spreadsheets don't execute them ("CSV injection").
     */
    static String escape(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !looksNumeric(text)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static boolean looksNumeric(String text) {
        try {
            new BigDecimal(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
