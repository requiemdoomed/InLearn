package com.inlearn.ui.support;

import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * A background task described fluently: what to run, which controls to disable while it runs
 * (preventing double submissions), and what to do with the result or error.
 *
 * @param <T> the result type
 */
public final class UiTask<T> {

    private final TaskRunner runner;
    private final Callable<T> work;
    private final List<Node> nodesToDisable = new ArrayList<>();
    private final List<Node> progressIndicators = new ArrayList<>();
    private Consumer<T> successHandler = result -> {
    };
    private Consumer<Throwable> failureHandler;
    private Runnable completionHandler = () -> {
    };

    UiTask(TaskRunner runner, Callable<T> work) {
        this.runner = runner;
        this.work = work;
    }

    /** Disables these controls until the task finishes, so an action can't be started twice. */
    public UiTask<T> disabling(Node... nodes) {
        nodesToDisable.addAll(List.of(nodes));
        return this;
    }

    /** Shows these nodes (for example a spinner) only while the task runs. */
    public UiTask<T> showing(Node... indicators) {
        progressIndicators.addAll(List.of(indicators));
        return this;
    }

    public UiTask<T> onSuccess(Consumer<T> handler) {
        this.successHandler = handler;
        return this;
    }

    /**
     * Replaces the default error handling (a notification with "Try again" for connection
     * problems). Use this to show validation errors inline in a form.
     */
    public UiTask<T> onFailure(Consumer<Throwable> handler) {
        this.failureHandler = handler;
        return this;
    }

    /** Runs after success or failure, for clean-up such as resetting a status label. */
    public UiTask<T> whenDone(Runnable handler) {
        this.completionHandler = handler;
        return this;
    }

    /** Starts the task. Must be called on the JavaFX Application Thread. */
    public Task<T> start() {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        List<Node> nodesDisabledHere = new ArrayList<>();
        for (Node node : nodesToDisable) {
            if (!node.isDisabled() && !node.disableProperty().isBound()) {
                node.setDisable(true);
                nodesDisabledHere.add(node);
            }
        }
        progressIndicators.forEach(indicator -> indicator.setVisible(true));

        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, event -> {
            finish(nodesDisabledHere);
            successHandler.accept(task.getValue());
        });
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, event -> {
            finish(nodesDisabledHere);
            Throwable error = task.getException();
            if (failureHandler != null && !ErrorPresenter.isAccountUnavailable(error)) {
                failureHandler.accept(error);
            } else {
                runner.errorPresenter().present(error, this::start);
            }
        });
        runner.submit(task);
        return task;
    }

    private void finish(List<Node> nodesDisabledHere) {
        nodesDisabledHere.forEach(node -> node.setDisable(false));
        progressIndicators.forEach(indicator -> indicator.setVisible(false));
        completionHandler.run();
    }
}
