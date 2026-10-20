package com.inlearn.ui.support;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.net.URL;

/**
 * Loads FXML views and creates their controllers.
 *
 * <p>Controllers that declare a constructor taking a {@link UiContext} receive it automatically,
 * including controllers of nested {@code fx:include} files. The FXML files keep their normal
 * {@code fx:controller} attribute, so they still open in Scene Builder.
 */
public class ViewLoader {

    private static final String VIEW_ROOT = "/com/inlearn/view/";

    private final UiContext context;

    ViewLoader(UiContext context) {
        this.context = context;
    }

    /** A loaded view: its root node and its controller. */
    public record LoadedView<C>(Parent root, C controller) {
    }

    /**
     * @param relativePath path below {@code /com/inlearn/view/}, for example {@code "admin/user-management.fxml"}
     */
    public <C> LoadedView<C> load(String relativePath) {
        URL location = ViewLoader.class.getResource(VIEW_ROOT + relativePath);
        if (location == null) {
            throw new IllegalStateException("Missing view " + relativePath);
        }
        FXMLLoader loader = new FXMLLoader(location);
        loader.setControllerFactory(this::createController);
        try {
            Parent root = loader.load();
            return new LoadedView<>(root, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load view " + relativePath, e);
        }
    }

    private Object createController(Class<?> controllerType) {
        try {
            for (Constructor<?> constructor : controllerType.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 1 && parameters[0] == UiContext.class) {
                    return constructor.newInstance(context);
                }
            }
            return controllerType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not create controller " + controllerType.getName(), e);
        }
    }
}
