package dev.ftbq.editor.services.bus;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Represents a command whose effects can be reversed and reapplied.
 */
public interface UndoableCommand extends Command {

    /**
     * Unique type identifier used for serialization and factory lookup.
     *
     * @return the command type identifier
     */
    String type();

    /**
     * Serializes the command into a JSON payload used for persistence.
     *
     * @return payload representing the command state
     */
    ObjectNode toPayload();

    /**
     * Produces the inverse command required to undo the side effects of this command.
     *
     * @return an undoable command representing the inverse operation
     */
    UndoableCommand inverse();
}
