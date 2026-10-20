package com.inlearn.model;

/**
 * Headline numbers for the administrator dashboard. "Active" excludes deactivated accounts,
 * archived courses and withdrawn enrollments.
 */
public record AdminDashboardStats(int activeStudents, int activeInstructors, int activeCourses, int activeEnrollments) {
}
