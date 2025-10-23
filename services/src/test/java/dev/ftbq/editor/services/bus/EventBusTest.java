package dev.ftbq.editor.services.bus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class EventBusTest {

    @Test
    void publishesEventToSubscriber() {
        EventBus eventBus = new EventBus();
        AtomicReference<TestEvent> received = new AtomicReference<>();
        eventBus.subscribe(TestEvent.class, received::set);

        TestEvent event = new TestEvent("payload");
        eventBus.publish(event);

        assertNotNull(received.get());
        assertEquals(event, received.get());
    }

    private record TestEvent(String value) implements Event {
    }
}
