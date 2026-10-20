package com.inlearn.ui.support;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.util.Callback;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Helpers that keep table set-up code short and type-safe. Columns are filled from record
 * accessors with lambdas, which also keeps them sortable.
 */
public final class TableSupport {

    private TableSupport() {
    }

    /** Fills a text column from a property of the row. */
    public static <S> void textColumn(TableColumn<S, String> column, Function<S, String> valueForRow) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(valueForRow.apply(cell.getValue())));
    }

    /** Fills a column with any value type; combine with a cell factory to control how it's shown. */
    public static <S, T> void valueColumn(TableColumn<S, T> column, Function<S, T> valueForRow) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(valueForRow.apply(cell.getValue())));
    }

    /** Cells that display a value as formatted text while the column still sorts by the raw value. */
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> formattedCells(Function<T, String> formatter) {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : formatter.apply(item));
            }
        };
    }

    /** Opens the selected row on double-click or when Enter is pressed. */
    public static <S> void onDoubleClickOrEnter(TableView<S> table, Consumer<S> action) {
        table.setRowFactory(view -> {
            TableRow<S> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    action.accept(row.getItem());
                }
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            S selected = table.getSelectionModel().getSelectedItem();
            if (event.getCode() == KeyCode.ENTER && selected != null) {
                action.accept(selected);
                event.consume();
            }
        });
    }
}
