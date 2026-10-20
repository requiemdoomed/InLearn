package com.inlearn.model;

import java.util.List;

/**
 * Everything a student needs for their gradebook in one course.
 */
public record StudentCourseGrades(Course course, List<StudentAssignment> assignments, CourseGrade courseGrade) {
}
