package com.inlearn.ui.support;

import com.inlearn.model.AccountStatus;
import com.inlearn.model.AssignmentProgress;
import com.inlearn.model.CourseStatus;
import com.inlearn.model.EnrollmentStatus;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.Region;
import javafx.util.Callback;

import java.util.function.Function;

/**
 * Small coloured status labels. The label text always states the status, so meaning never
 * depends on colour alone; the colour is a secondary cue.
 */
public final class StatusBadges {

    /** Colour families defined in the stylesheets as {@code badge-<tone>}. */
    public enum Tone {
        SUCCESS("badge-success"),
        WARNING("badge-warning"),
        DANGER("badge-danger"),
        INFO("badge-info"),
        NEUTRAL("badge-neutral");

        private final String styleClass;

        Tone(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    /** Text and tone for one badge. */
    public record Badge(String text, Tone tone) {
    }

    private StatusBadges() {
    }

    public static Label create(Badge badge) {
        Label label = new Label(badge.text());
        apply(label, badge);
        return label;
    }

    public static void apply(Label label, Badge badge) {
        label.setText(badge.text());
        label.getStyleClass().removeIf(styleClass -> styleClass.startsWith("badge"));
        label.getStyleClass().addAll("badge", badge.tone().styleClass);
        label.setAccessibleText("Status: " + badge.text());
        // Badges keep their full text; the surrounding layout gives way instead.
        label.setMinWidth(Region.USE_PREF_SIZE);
    }

    public static Badge forProgress(AssignmentProgress progress) {
        Tone tone = switch (progress) {
            case NOT_SUBMITTED -> Tone.NEUTRAL;
            case OVERDUE -> Tone.DANGER;
            case SUBMITTED -> Tone.INFO;
            case SUBMITTED_LATE -> Tone.WARNING;
            case GRADED -> Tone.SUCCESS;
        };
        return new Badge(progress.label(), tone);
    }

    public static Badge forAccountStatus(AccountStatus status) {
        return new Badge(status.displayName(), status == AccountStatus.ACTIVE ? Tone.SUCCESS : Tone.NEUTRAL);
    }

    public static Badge forCourseStatus(CourseStatus status) {
        return new Badge(status.displayName(), status == CourseStatus.ACTIVE ? Tone.SUCCESS : Tone.NEUTRAL);
    }

    public static Badge forEnrollmentStatus(EnrollmentStatus status) {
        return new Badge(status.displayName(), status == EnrollmentStatus.ACTIVE ? Tone.SUCCESS : Tone.NEUTRAL);
    }

    /**
     * A table cell factory that renders a badge for each row.
     *
     * @param badgeForRow maps a row's value to the badge to show, or {@code null} for none
     */
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> cellFactory(Function<T, Badge> badgeForRow) {
        return column -> new TableCell<>() {
            private final Label badgeLabel = new Label();

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                Badge badge = empty || item == null ? null : badgeForRow.apply(item);
                if (badge == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    apply(badgeLabel, badge);
                    setGraphic(badgeLabel);
                    setText(null);
                }
            }
        };
    }
}
