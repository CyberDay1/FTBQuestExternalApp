package dev.ftbq.editor.services.bus;

/**
 * Provides global access to shared service instances used by the application.
 */
public final class ServiceLocator {

    private static final CommandBus COMMAND_BUS = new CommandBus();
    private static final EventBus EVENT_BUS = new EventBus();

    private ServiceLocator() {
        // utility class
    }

    public static CommandBus commandBus() {
        return COMMAND_BUS;
    }

    public static EventBus eventBus() {
        return EVENT_BUS;
    }
}
