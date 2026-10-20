package com.inlearn.ui.support;

/**
 * Implemented by controllers of screens shown in the main content area. All methods are
 * optional hooks that the application shell calls.
 */
public interface Page {

    /** Called after the page is shown and its dependencies are set; load data here. */
    default void onShown() {
    }

    /** Reloads data that another user may have changed (Refresh button / F5). */
    default void refresh() {
    }

    /** True when the page holds edits that would be lost by navigating away. */
    default boolean hasUnsavedChanges() {
        return false;
    }

    /** Keyboard shortcut Ctrl+F: focus the page's search field, if it has one. */
    default void focusSearch() {
    }

    /** Keyboard shortcut Ctrl+N: start creating a new item, if the page supports it. */
    default void createNew() {
    }

    /** Keyboard shortcut Ctrl+S: save the page's form, if it has one. */
    default void save() {
    }
}
