package com.inlearn.ui.support;

import javafx.beans.value.ObservableValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Remembers the values of a form's fields at the last save (or load) and reports whether
 * any field has changed since. Used to warn before leaving a form with unsaved changes.
 */
public class UnsavedChangesTracker {

    private final List<ObservableValue<?>> trackedValues = new ArrayList<>();
    private final List<Object> savedValues = new ArrayList<>();

    /** Registers the values to watch, for example {@code titleField.textProperty()}. */
    public UnsavedChangesTracker track(ObservableValue<?>... values) {
        trackedValues.addAll(List.of(values));
        markSaved();
        return this;
    }

    /** Records the current values as the saved state. Call after loading or saving. */
    public void markSaved() {
        savedValues.clear();
        for (ObservableValue<?> value : trackedValues) {
            savedValues.add(normalise(value.getValue()));
        }
    }

    public boolean hasUnsavedChanges() {
        for (int index = 0; index < trackedValues.size(); index++) {
            if (!Objects.equals(savedValues.get(index), normalise(trackedValues.get(index).getValue()))) {
                return true;
            }
        }
        return false;
    }

    /** Treats an empty text field the same as a missing value. */
    private static Object normalise(Object value) {
        if (value instanceof String text && text.isEmpty()) {
            return null;
        }
        return value;
    }
}
