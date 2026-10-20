package com.inlearn.ui.common;

import com.inlearn.ui.support.UiContext;
import javafx.scene.Node;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Asks where to save a file, then copies it there in the background.
 * The operating system's own Save dialog is used, so it follows the system theme.
 */
public final class FileDownloads {

    private FileDownloads() {
    }

    /**
     * @param suggestedName the original file name, offered as the default
     * @param copyTo        performs the authorized copy to the chosen path (runs in the background)
     * @param busyNodes     controls to disable while copying
     */
    public static void saveAs(UiContext ui, String suggestedName, Consumer<Path> copyTo, Node... busyNodes) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save " + suggestedName);
        chooser.setInitialFileName(suggestedName);
        File destination = chooser.showSaveDialog(ui.stage());
        if (destination == null) {
            return;
        }
        Path destinationPath = destination.toPath();
        ui.tasks().task(() -> {
                    copyTo.accept(destinationPath);
                    return destinationPath;
                })
                .disabling(busyNodes)
                .onSuccess(saved -> ui.notifier().success("Saved " + saved.getFileName() + " to " + saved.getParent() + "."))
                .start();
    }
}
