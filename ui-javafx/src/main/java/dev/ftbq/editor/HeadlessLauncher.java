package dev.ftbq.editor;

import javafx.application.Application;

/**
 * Entry point that configures the JavaFX runtime to behave predictably in headless environments.
 */
public final class HeadlessLauncher {
    private HeadlessLauncher() {
    }

    public static void main(String[] args) {
        boolean headless = detectHeadlessEnvironment();
        if (headless) {
            applyHeadlessProperties();
            if (!Boolean.getBoolean("ftbq.editor.forceLaunch")) {
                System.out.println("Headless environment detected; skipping JavaFX launch.");
                return;
            }
        }

        try {
            Application.launch(MainApp.class, args);
        } catch (RuntimeException ex) {
            if (headless && isDisplayInitializationFailure(ex)) {
                System.err.println("Headless JavaFX startup failed: " + ex.getMessage());
                ex.printStackTrace(System.err);
                return;
            }
            throw ex;
        }
    }

    private static boolean detectHeadlessEnvironment() {
    // Use the Java AWT headless flag for cross-platform detection
    return java.awt.GraphicsEnvironment.isHeadless();
}

    private static void applyHeadlessProperties() {
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("javafx.platform", "Monocle");
        System.setProperty("javafx.headless", "true");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
    }

    private static boolean isDisplayInitializationFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("Unable to open DISPLAY")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

