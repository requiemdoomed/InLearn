package com.inlearn.ui.shell;

import com.inlearn.model.Course;
import com.inlearn.model.Role;
import com.inlearn.model.User;
import com.inlearn.ui.instructor.InstructorCourseController;
import com.inlearn.ui.student.StudentCourseController;
import com.inlearn.ui.support.Navigator;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.View;
import com.inlearn.ui.support.ViewLoader.LoadedView;
import com.inlearn.ui.theme.ThemeToggle;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The signed-in application frame: role-specific sidebar, top bar and page area.
 * It also implements {@link Navigator}, keeping a short history for the Back button and
 * asking before leaving a page with unsaved changes.
 */
public class ShellController implements Navigator {

    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
    private static final int MAX_HISTORY = 25;

    private static final KeyCombination REFRESH_SHORTCUT = new KeyCodeCombination(KeyCode.F5);
    private static final KeyCombination SEARCH_SHORTCUT = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination NEW_SHORTCUT = new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination SAVE_SHORTCUT = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination BACK_SHORTCUT = new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN);
    private static final KeyCombination THEME_SHORTCUT =
            new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

    private final UiContext ui;
    private final Map<View.NavSection, Button> sectionButtons = new EnumMap<>(View.NavSection.class);
    private final Map<Long, Button> courseButtons = new HashMap<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();
    private Page currentPage;
    private Long highlightedCourseId;
    private VBox courseSection;
    private ChangeListener<User> signedInUserListener;

    @FXML private VBox navigationContainer;
    @FXML private Label sidebarUserName;
    @FXML private Label sidebarUserRole;
    @FXML private Button signOutButton;
    @FXML private Button backButton;
    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;
    @FXML private ProgressIndicator busyIndicator;
    @FXML private Button refreshButton;
    @FXML private Button themeToggleButton;
    @FXML private Label avatarInitials;
    @FXML private Label userNameLabel;
    @FXML private Label userRoleLabel;
    @FXML private StackPane pageContainer;

    /** A screen that can be shown again by the Back button. */
    private record HistoryEntry(View view, Consumer<Object> setUp) {
    }

    public ShellController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        setIcon(backButton, "icon-back");
        setIcon(refreshButton, "icon-refresh");
        setIcon(signOutButton, "icon-sign-out");
        backButton.setTooltip(new Tooltip("Back (Alt+Left)"));
        refreshButton.setTooltip(new Tooltip("Refresh (F5)"));
        busyIndicator.visibleProperty().bind(ui.tasks().runningTaskCountProperty().greaterThan(0));
        busyIndicator.managedProperty().bind(busyIndicator.visibleProperty());

        ThemeToggle.attach(themeToggleButton, ui.themeManager(), false, preference ->
                ui.tasks().task(() -> {
                            ui.app().profileService().saveThemePreference(ui.currentUser(), preference);
                            return preference;
                        })
                        .start());

        // Weak, so a shell discarded at sign-out can be garbage-collected.
        signedInUserListener = (observable, oldUser, newUser) -> {
            if (newUser != null) {
                showUser(newUser);
            }
        };
        ui.session().signedInUserProperty().addListener(new WeakChangeListener<>(signedInUserListener));
        showUser(ui.session().user());
        buildNavigation(ui.session().user().role());

        pageContainer.sceneProperty().addListener((observable, oldScene, scene) -> {
            if (scene != null) {
                registerShortcuts(scene);
            }
        });
    }

    /** Opens the dashboard for the signed-in user's role. */
    public void showHome() {
        navigate(homeViewFor(ui.session().user().role()));
    }

    public boolean hasUnsavedChanges() {
        return currentPage != null && currentPage.hasUnsavedChanges();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C> void navigate(View view, Consumer<C> setUp) {
        if (hasUnsavedChanges() && !ui.dialogs().confirmDiscardChanges()) {
            return;
        }
        Consumer<Object> storedSetUp = controller -> setUp.accept((C) controller);
        show(view, storedSetUp);
        history.push(new HistoryEntry(view, storedSetUp));
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
        updateBackButton();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C> void returnTo(View view, Consumer<C> setUp) {
        if (hasUnsavedChanges() && !ui.dialogs().confirmDiscardChanges()) {
            return;
        }
        Consumer<Object> storedSetUp = controller -> setUp.accept((C) controller);
        if (!history.isEmpty()) {
            history.pop();
        }
        if (!history.isEmpty() && history.peek().view() == view) {
            history.pop();
        }
        show(view, storedSetUp);
        history.push(new HistoryEntry(view, storedSetUp));
        updateBackButton();
    }

    @Override
    public void back() {
        if (history.size() < 2) {
            return;
        }
        if (hasUnsavedChanges() && !ui.dialogs().confirmDiscardChanges()) {
            return;
        }
        history.pop();
        HistoryEntry previous = history.peek();
        show(previous.view(), previous.setUp());
        updateBackButton();
    }

    @Override
    public void setPageTitle(String title, String subtitle) {
        pageTitleLabel.setText(title);
        boolean hasSubtitle = subtitle != null && !subtitle.isBlank();
        pageSubtitleLabel.setText(hasSubtitle ? subtitle : "");
        pageSubtitleLabel.setVisible(hasSubtitle);
        pageSubtitleLabel.setManaged(hasSubtitle);
    }

    @Override
    public void highlightCourse(long courseId) {
        highlightedCourseId = courseId;
        courseButtons.forEach((id, button) -> button.pseudoClassStateChanged(CURRENT, id == courseId));
        sectionButtons.values().forEach(button -> button.pseudoClassStateChanged(CURRENT, false));
    }

    @Override
    public void refreshSidebar() {
        Role role = ui.session().user().role();
        if (role == Role.ADMIN) {
            return;
        }
        ui.tasks().task(() -> role == Role.INSTRUCTOR
                        ? ui.app().courseService().listTeachingCourses(ui.currentUser())
                        : ui.app().courseService().listEnrolledCourses(ui.currentUser()))
                .onSuccess(this::showCourseShortcuts)
                .start();
    }

    @FXML
    private void onSignOut() {
        ui.appWindow().signOut();
    }

    @FXML
    private void onBack() {
        back();
    }

    @FXML
    private void onRefresh() {
        if (currentPage != null) {
            currentPage.refresh();
        }
        refreshSidebar();
    }

    /** Loads a fresh copy of the view, lets the caller configure it, then displays it. */
    private void show(View view, Consumer<Object> setUp) {
        LoadedView<Object> loaded = ui.viewLoader().load(view.fxmlPath());
        setPageTitle(view.defaultTitle(), null);
        setUp.accept(loaded.controller());
        pageContainer.getChildren().setAll(loaded.root());
        currentPage = loaded.controller() instanceof Page page ? page : null;
        highlightSection(view.navSection());
        if (currentPage != null) {
            currentPage.onShown();
        }
        // Move keyboard focus into the new page so Tab starts from its content.
        loaded.root().requestFocus();
    }

    private void highlightSection(View.NavSection section) {
        sectionButtons.forEach((navSection, button) -> button.pseudoClassStateChanged(CURRENT, navSection == section));
        if (section != View.NavSection.COURSE) {
            highlightedCourseId = null;
            courseButtons.values().forEach(button -> button.pseudoClassStateChanged(CURRENT, false));
        }
    }

    private void updateBackButton() {
        boolean canGoBack = history.size() > 1;
        backButton.setVisible(canGoBack);
        backButton.setManaged(canGoBack);
    }

    private void buildNavigation(Role role) {
        navigationContainer.getChildren().clear();
        switch (role) {
            case ADMIN -> {
                addNavButton(View.NavSection.DASHBOARD, "Dashboard", "icon-dashboard", View.ADMIN_DASHBOARD);
                addNavButton(View.NavSection.USERS, "Users", "icon-users", View.USER_MANAGEMENT);
                addNavButton(View.NavSection.COURSES, "Courses", "icon-courses", View.COURSE_MANAGEMENT);
                addNavButton(View.NavSection.ENROLLMENTS, "Enrollments", "icon-enrollments", View.ENROLLMENT_MANAGEMENT);
            }
            case INSTRUCTOR -> {
                addNavButton(View.NavSection.DASHBOARD, "Dashboard", "icon-dashboard", View.INSTRUCTOR_DASHBOARD);
                addCourseSection("Teaching");
            }
            case STUDENT -> {
                addNavButton(View.NavSection.DASHBOARD, "Dashboard", "icon-dashboard", View.STUDENT_DASHBOARD);
                addNavButton(View.NavSection.GRADES, "Grades", "icon-grades", View.STUDENT_GRADEBOOK);
                addCourseSection("My courses");
            }
        }
        Label accountLabel = new Label("ACCOUNT");
        accountLabel.getStyleClass().add("sidebar-section-label");
        navigationContainer.getChildren().add(accountLabel);
        addNavButton(View.NavSection.PROFILE, "Profile & settings", "icon-profile", View.PROFILE);
        refreshSidebar();
    }

    private void addCourseSection(String heading) {
        Label label = new Label(heading.toUpperCase(Locale.ROOT));
        label.getStyleClass().add("sidebar-section-label");
        courseSection = new VBox(2);
        navigationContainer.getChildren().addAll(label, courseSection);
    }

    private void showCourseShortcuts(List<Course> courses) {
        if (courseSection == null) {
            return;
        }
        courseSection.getChildren().clear();
        courseButtons.clear();
        Role role = ui.session().user().role();
        View courseView = role == Role.INSTRUCTOR ? View.INSTRUCTOR_COURSE : View.STUDENT_COURSE;
        for (Course course : courses) {
            Button button = createNavButton(course.code(), "icon-course");
            button.setTextOverrun(OverrunStyle.ELLIPSIS);
            String archivedNote = course.isActive() ? "" : " (archived)";
            button.setTooltip(new Tooltip(course.displayLabel() + archivedNote));
            button.setAccessibleText("Open course " + course.displayLabel() + archivedNote);
            button.setOnAction(event -> openCourse(courseView, course.id()));
            courseButtons.put(course.id(), button);
            courseSection.getChildren().add(button);
        }
        if (courses.isEmpty()) {
            Label none = new Label(role == Role.INSTRUCTOR ? "No courses assigned yet" : "Not enrolled in any courses yet");
            none.getStyleClass().add("nav-course-code");
            none.setWrapText(true);
            none.getStyleClass().add("nav-empty");
            courseSection.getChildren().add(none);
        }
        if (highlightedCourseId != null) {
            highlightCourse(highlightedCourseId);
        }
    }

    private void openCourse(View courseView, long courseId) {
        if (courseView == View.INSTRUCTOR_COURSE) {
            navigate(courseView, (InstructorCourseController controller) -> controller.setCourse(courseId, null));
        } else {
            navigate(courseView, (StudentCourseController controller) -> controller.setCourse(courseId, null));
        }
    }

    private void addNavButton(View.NavSection section, String text, String iconClass, View view) {
        Button button = createNavButton(text, iconClass);
        button.setOnAction(event -> navigate(view));
        sectionButtons.put(section, button);
        navigationContainer.getChildren().add(button);
    }

    private Button createNavButton(String text, String iconClass) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        Region icon = new Region();
        icon.getStyleClass().addAll("nav-icon", iconClass);
        button.setGraphic(icon);
        return button;
    }

    private void showUser(User user) {
        sidebarUserName.setText(user.fullName());
        sidebarUserRole.setText(user.role().displayName() + " · " + user.email());
        userNameLabel.setText(user.fullName());
        userRoleLabel.setText(user.role().displayName());
        avatarInitials.setText(initialsOf(user.fullName()));
    }

    private void registerShortcuts(Scene scene) {
        Map<KeyCombination, Runnable> accelerators = scene.getAccelerators();
        accelerators.put(REFRESH_SHORTCUT, this::onRefresh);
        accelerators.put(SEARCH_SHORTCUT, () -> withPage(Page::focusSearch));
        accelerators.put(NEW_SHORTCUT, () -> withPage(Page::createNew));
        accelerators.put(SAVE_SHORTCUT, () -> withPage(Page::save));
        accelerators.put(BACK_SHORTCUT, this::back);
        accelerators.put(THEME_SHORTCUT, themeToggleButton::fire);
    }

    private void withPage(Consumer<Page> action) {
        if (currentPage != null) {
            action.accept(currentPage);
        }
    }

    private static View homeViewFor(Role role) {
        return switch (role) {
            case ADMIN -> View.ADMIN_DASHBOARD;
            case INSTRUCTOR -> View.INSTRUCTOR_DASHBOARD;
            case STUDENT -> View.STUDENT_DASHBOARD;
        };
    }

    private static String initialsOf(String fullName) {
        StringBuilder initials = new StringBuilder();
        for (String part : fullName.trim().split("\\s+")) {
            if (!part.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return initials.toString();
    }

    private static void setIcon(Button button, String iconClass) {
        Region icon = new Region();
        icon.getStyleClass().addAll("nav-icon", iconClass);
        button.setGraphic(icon);
    }
}
