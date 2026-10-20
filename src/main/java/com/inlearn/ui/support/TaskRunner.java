package com.inlearn.ui.support;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs database and file work on background threads so the window never freezes.
 * Results are delivered back on the JavaFX Application Thread.
 *
 * <p>Typical use from a controller:
 * <pre>{@code
 * taskRunner.task(() -> courseService.listAllCourses(currentUser))
 *         .disabling(refreshButton)
 *         .onSuccess(courses -> courseTable.getItems().setAll(courses))
 *         .start();
 * }</pre>
 */
public class TaskRunner {

    private static final int WORKER_THREADS = 4;

    private final ExecutorService executor;
    private final ErrorPresenter errorPresenter;
    private final ReadOnlyIntegerWrapper runningTaskCount = new ReadOnlyIntegerWrapper(0);

    public TaskRunner(ErrorPresenter errorPresenter) {
        this.errorPresenter = errorPresenter;
        this.executor = Executors.newFixedThreadPool(WORKER_THREADS, new WorkerThreadFactory());
    }

    /** Starts describing a background task; call {@link UiTask#start()} to run it. */
    public <T> UiTask<T> task(Callable<T> work) {
        return new UiTask<>(this, work);
    }

    /** Number of tasks currently running; the top bar shows a progress indicator while it is above zero. */
    public ReadOnlyIntegerProperty runningTaskCountProperty() {
        return runningTaskCount.getReadOnlyProperty();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    ErrorPresenter errorPresenter() {
        return errorPresenter;
    }

    /** Called on the JavaFX thread by {@link UiTask}. */
    void submit(Task<?> task) {
        runningTaskCount.set(runningTaskCount.get() + 1);
        task.runningProperty().addListener((observable, wasRunning, isRunning) -> {
            if (!isRunning && task.isDone()) {
                runningTaskCount.set(Math.max(0, runningTaskCount.get() - 1));
            }
        });
        executor.execute(task);
    }

    /** Daemon threads so a stuck query can never keep the application alive after the window closes. */
    private static final class WorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "inlearn-worker-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
