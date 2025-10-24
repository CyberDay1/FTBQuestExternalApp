package dev.ftbq.editor.services.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class EventBusTest {

    @Test
    void publishesEventToSubscribers() {
        EventBus bus = new EventBus();
        List<String> received = new CopyOnWriteArrayList<>();
        bus.subscribe(TestEvent.class, event -> received.add(event.message()));

        bus.publish(new TestEvent("hello"));
        bus.publish(new TestEvent("world"));

        assertEquals(List.of("hello", "world"), received);
    }

    private record TestEvent(String message) implements Event {}
}
