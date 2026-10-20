package com.inlearn.ui.admin;

import com.inlearn.model.AccountStatus;
import com.inlearn.model.Role;
import com.inlearn.model.User;
import com.inlearn.ui.support.EmptyStates;
import com.inlearn.ui.support.FormDialog;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.StatusBadges;
import com.inlearn.ui.support.TableSupport;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.ViewLoader.LoadedView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Administrator screen for creating, editing, searching and deactivating accounts.
 */
public class UserManagementController implements Page {

    private static final String ALL_ROLES = "All roles";
    private static final String ALL_STATUSES = "All statuses";

    private final UiContext ui;
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final FilteredList<User> filteredUsers = new FilteredList<>(users);
    private boolean openNewUserFormWhenShown;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> roleFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> nameColumn;
    @FXML private TableColumn<User, String> emailColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, AccountStatus> statusColumn;
    @FXML private TableColumn<User, Instant> lastSignInColumn;
    @FXML private Label countLabel;
    @FXML private Button editButton;
    @FXML private Button statusButton;
    @FXML private Button deleteButton;

    public UserManagementController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        roleFilter.getItems().add(ALL_ROLES);
        for (Role role : Role.values()) {
            roleFilter.getItems().add(role.displayName());
        }
        roleFilter.setValue(ALL_ROLES);
        statusFilter.getItems().addAll(ALL_STATUSES, AccountStatus.ACTIVE.displayName(), AccountStatus.INACTIVE.displayName());
        statusFilter.setValue(ALL_STATUSES);

        TableSupport.textColumn(nameColumn, User::fullName);
        TableSupport.textColumn(emailColumn, User::email);
        TableSupport.textColumn(roleColumn, user -> user.role().displayName());
        TableSupport.valueColumn(statusColumn, User::status);
        statusColumn.setCellFactory(StatusBadges.cellFactory(StatusBadges::forAccountStatus));
        TableSupport.valueColumn(lastSignInColumn, User::lastLoginAt);
        lastSignInColumn.setCellFactory(TableSupport.formattedCells(
                instant -> instant == null ? "Never" : ui.formats().shortDateTime(instant)));

        SortedList<User> sortedUsers = new SortedList<>(filteredUsers);
        sortedUsers.comparatorProperty().bind(userTable.comparatorProperty());
        userTable.setItems(sortedUsers);
        userTable.setPlaceholder(EmptyStates.create("No users match", "Change the search or filters, or select New user."));
        TableSupport.onDoubleClickOrEnter(userTable, user -> onEdit());

        searchField.textProperty().addListener((observable, oldText, newText) -> applyFilters());
        roleFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        userTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldUser, newUser) -> updateActionButtons(newUser));
        updateActionButtons(null);
    }

    /** Used by the dashboard's "Add a user" shortcut. */
    public void openNewUserFormWhenShown() {
        openNewUserFormWhenShown = true;
    }

    @Override
    public void onShown() {
        refresh();
        if (openNewUserFormWhenShown) {
            Platform.runLater(this::onNewUser);
        }
    }

    @Override
    public void refresh() {
        ui.tasks().task(() -> ui.app().userService().listUsers(ui.currentUser()))
                .onSuccess(this::showUsers)
                .start();
    }

    @Override
    public void focusSearch() {
        searchField.requestFocus();
    }

    @Override
    public void createNew() {
        onNewUser();
    }

    @FXML
    private void onNewUser() {
        LoadedView<UserFormController> form = ui.viewLoader().load("admin/user-form.fxml");
        form.controller().prepareForCreate();
        if (FormDialog.show(ui, "New user", "Create account", form.root(), form.controller())) {
            refresh();
        }
    }

    @FXML
    private void onEdit() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        LoadedView<UserFormController> form = ui.viewLoader().load("admin/user-form.fxml");
        form.controller().prepareForEdit(selected);
        if (FormDialog.show(ui, "Edit " + selected.fullName(), "Save changes", form.root(), form.controller())) {
            refresh();
        }
    }

    @FXML
    private void onToggleStatus() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        boolean deactivating = selected.isActive();
        if (deactivating && !ui.dialogs().confirm("Deactivate " + selected.fullName() + "?",
                selected.fullName() + " won't be able to sign in. Their courses, submissions and grades are kept, "
                        + "and you can reactivate the account at any time.", "Deactivate", true)) {
            return;
        }
        AccountStatus newStatus = deactivating ? AccountStatus.INACTIVE : AccountStatus.ACTIVE;
        ui.tasks().task(() -> ui.app().userService().setStatus(ui.currentUser(), selected.id(), newStatus,
                        selected.version()))
                .disabling(statusButton, editButton, deleteButton)
                .onSuccess(updated -> {
                    ui.notifier().success(updated.fullName() + (deactivating ? " has been deactivated." : " has been reactivated."));
                    refresh();
                })
                .start();
    }

    @FXML
    private void onDelete() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (!ui.dialogs().confirm("Delete " + selected.fullName() + "?",
                "This permanently deletes the account. It's only possible for accounts with no courses, enrollments "
                        + "or grades — otherwise deactivate the account instead.", "Delete account", true)) {
            return;
        }
        ui.tasks().task(() -> {
                    ui.app().userService().deleteUser(ui.currentUser(), selected.id());
                    return selected;
                })
                .disabling(statusButton, editButton, deleteButton)
                .onSuccess(deleted -> {
                    ui.notifier().success(deleted.fullName() + "'s account has been deleted.");
                    refresh();
                })
                .start();
    }

    private void showUsers(List<User> loadedUsers) {
        Long selectedId = userTable.getSelectionModel().getSelectedItem() == null
                ? null : userTable.getSelectionModel().getSelectedItem().id();
        users.setAll(loadedUsers);
        applyFilters();
        if (selectedId != null) {
            userTable.getItems().stream().filter(user -> user.id() == selectedId).findFirst()
                    .ifPresent(user -> userTable.getSelectionModel().select(user));
        }
    }

    private void applyFilters() {
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String role = roleFilter.getValue();
        String status = statusFilter.getValue();
        filteredUsers.setPredicate(user ->
                (search.isEmpty()
                        || user.fullName().toLowerCase(Locale.ROOT).contains(search)
                        || user.email().contains(search))
                        && (ALL_ROLES.equals(role) || user.role().displayName().equals(role))
                        && (ALL_STATUSES.equals(status) || user.status().displayName().equals(status)));
        countLabel.setText(filteredUsers.size() + " of " + users.size() + " accounts shown");
    }

    private void updateActionButtons(User selected) {
        boolean hasSelection = selected != null;
        editButton.setDisable(!hasSelection);
        statusButton.setDisable(!hasSelection);
        deleteButton.setDisable(!hasSelection);
        statusButton.setText(hasSelection && !selected.isActive() ? "Reactivate" : "Deactivate");
    }
}
