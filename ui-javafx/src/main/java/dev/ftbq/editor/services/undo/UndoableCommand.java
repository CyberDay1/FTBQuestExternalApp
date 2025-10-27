package dev.ftbq.editor.services.undo;

import dev.ftbq.editor.services.bus.Command;

/** A command that can be executed and reversed. */
public interface UndoableCommand extends Command {
    void execute();
    void undo();
}


