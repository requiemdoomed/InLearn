package com.inlearn.ui.student;

import com.inlearn.model.Course;
import com.inlearn.ui.common.CourseTab;
import com.inlearn.ui.support.StatusBadges;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Student Overview tab: the course description and basic details.
 */
public class StudentOverviewTabController implements CourseTab {

    @FXML private Label descriptionLabel;
    @FXML private Label codeValue;
    @FXML private Label termValue;
    @FXML private Label instructorValue;
    @FXML private Label statusBadge;

    @Override
    public void showCourse(Course course) {
        boolean hasDescription = course.description() != null && !course.description().isBlank();
        descriptionLabel.setText(hasDescription ? course.description()
                : "Your instructor hasn't added a description for this course yet.");
        descriptionLabel.getStyleClass().remove("muted");
        if (!hasDescription) {
            descriptionLabel.getStyleClass().add("muted");
        }
        codeValue.setText(course.code());
        termValue.setText(course.term());
        instructorValue.setText(course.hasInstructor() ? course.instructorName() : "Not assigned yet");
        StatusBadges.apply(statusBadge, StatusBadges.forCourseStatus(course.status()));
    }
}
