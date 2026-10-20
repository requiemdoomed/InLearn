package com.inlearn.ui.common;

import com.inlearn.model.Course;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Reusable building blocks for the dashboards: clickable course tiles and list rows.
 * Both are real buttons, so they work with the keyboard (Tab to focus, Enter or Space to open).
 */
public final class DashboardWidgets {

    private DashboardWidgets() {
    }

    /** A course card showing code, title and a short detail line. */
    public static Button courseTile(Course course, String detail, Runnable onOpen) {
        Label code = new Label(course.code() + (course.isActive() ? "" : " · Archived"));
        code.getStyleClass().add("course-tile-code");
        // Single-line labels keep every tile the same height; the full title is in the tooltip.
        Label title = new Label(course.title());
        title.getStyleClass().add("course-tile-title");
        Label meta = new Label(detail);
        meta.getStyleClass().add("list-item-meta");
        VBox content = new VBox(4, code, title, meta);
        content.setAlignment(Pos.TOP_LEFT);

        Button tile = new Button();
        tile.setGraphic(content);
        tile.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        tile.setAlignment(Pos.TOP_LEFT);
        tile.getStyleClass().setAll("course-tile");
        tile.setAccessibleText("Open " + course.displayLabel());
        tile.setTooltip(new Tooltip(course.displayLabel() + "\n" + detail));
        tile.setOnAction(event -> onOpen.run());
        return tile;
    }

    /**
     * A row with a title, a muted detail line and an optional trailing node (such as a status
     * badge). When {@code onOpen} is given, the title becomes a link-style button.
     */
    public static HBox listItem(String title, String detail, Node trailing, Runnable onOpen) {
        Node titleNode;
        if (onOpen != null) {
            Button titleButton = new Button(title);
            titleButton.getStyleClass().setAll("button", "ghost", "list-item-title");
            titleButton.setOnAction(event -> onOpen.run());
            titleNode = titleButton;
        } else {
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("list-item-title");
            titleLabel.setWrapText(true);
            titleNode = titleLabel;
        }
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("list-item-meta");
        detailLabel.setWrapText(true);
        VBox text = new VBox(2, titleNode, detailLabel);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(text);
        row.getStyleClass().add("list-item");
        if (trailing != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.SOMETIMES);
            row.getChildren().addAll(spacer, trailing);
        }
        return row;
    }
}
