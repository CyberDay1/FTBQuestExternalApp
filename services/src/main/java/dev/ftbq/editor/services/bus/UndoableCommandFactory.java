package dev.ftbq.editor.services.bus;

import com.fasterxml.jackson.databind.node.ObjectNode;

@FunctionalInterface
public interface UndoableCommandFactory {
    UndoableCommand create(ObjectNode payload);
}
