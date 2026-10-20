package com.inlearn.ui.support;

import java.util.function.Consumer;

/**
 * Moves between screens in the main content area. Implemented by the application shell.
 */
public interface Navigator {

    /** Shows a screen with no extra set-up. */
    default void navigate(View view) {
        navigate(view, controller -> {
        });
    }

    /**
     * Shows a screen, first asking about unsaved changes on the current one.
     *
     * @param setUp receives the new screen's controller before it is shown, for example to pass a course id
     * @param <C>   the controller type of the screen
     */
    <C> void navigate(View view, Consumer<C> setUp);

    /** Returns to the previous screen, if there is one. */
    void back();

    /**
     * Leaves the current screen for {@code view}. If the previous screen in the history is the
     * same view, it is replaced rather than duplicated, so Back doesn't revisit the screen being left.
     */
    <C> void returnTo(View view, Consumer<C> setUp);

    /** Updates the heading in the top bar, for example to the name of the open course. */
    void setPageTitle(String title, String subtitle);

    /** Reloads the course shortcuts in the sidebar after courses change. */
    void refreshSidebar();

    /** Highlights the sidebar shortcut of the course currently being viewed. */
    void highlightCourse(long courseId);
}
