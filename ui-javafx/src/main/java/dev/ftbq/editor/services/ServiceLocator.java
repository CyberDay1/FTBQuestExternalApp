package dev.ftbq.editor.services;

import dev.ftbq.editor.services.bus.CommandBus;
import dev.ftbq.editor.services.bus.EventBus;

/** Simple global locator for editor services. */
public final class ServiceLocator {
    private static final CommandBus COMMAND_BUS = new CommandBus();
    private static final EventBus EVENT_BUS = new EventBus();

    private ServiceLocator(){}

    public static CommandBus commandBus(){ return COMMAND_BUS; }
    public static EventBus eventBus(){ return EVENT_BUS; }
}


