package dev.ftbq.editor.services.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class CommandBusTest {

    @Test
    void dispatchesCommandToRegisteredHandler() {
        CommandBus bus = new CommandBus();
        List<String> commands = new CopyOnWriteArrayList<>();
        bus.subscribe(TestCommand.class, command -> commands.add(command.payload()));

        bus.dispatch(new TestCommand("alpha"));
        bus.dispatch(new TestCommand("beta"));

        assertEquals(List.of("alpha", "beta"), commands);
    }

    @Test
    void dispatchesCommandToGlobalListeners() {
        CommandBus bus = new CommandBus();
        List<Command> received = new CopyOnWriteArrayList<>();
        bus.subscribeAll(received::add);

        TestCommand command = new TestCommand("gamma");
        bus.dispatch(command);

        assertEquals(List.of(command), received);
    }

    private record TestCommand(String payload) implements Command {}
}
