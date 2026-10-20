package com.inlearn.ui.support;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Friendly placeholders for empty tables and lists that explain what to do next.
 */
public final class EmptyStates {

    private EmptyStates() {
    }

    /**
     * @param title       short statement, such as "No assignments yet"
     * @param nextStep    what the user can do, such as "Select New assignment to create one."
     */
    public static VBox create(String title, String nextStep) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");
        titleLabel.setWrapText(true);
        Label messageLabel = new Label(nextStep);
        messageLabel.getStyleClass().add("empty-state-message");
        messageLabel.setWrapText(true);
        VBox box = new VBox(titleLabel, messageLabel);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        box.setFillWidth(false);
        return box;
    }
}
