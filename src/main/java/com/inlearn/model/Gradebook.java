package com.inlearn.model;

import java.util.List;

/**
 * A course gradebook: the assignments (columns) and one row per enrolled student.
 */
public record Gradebook(Course course, List<Assignment> assignments, List<GradebookRow> rows) {
}
