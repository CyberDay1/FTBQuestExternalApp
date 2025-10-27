package dev.ftbq.editor.services.undo;

import java.util.ArrayDeque;

/** Simple in-memory undo/redo manager. */
public final class UndoManager {
    private final ArrayDeque<UndoableCommand> undo = new ArrayDeque<>();
    private final ArrayDeque<UndoableCommand> redo = new ArrayDeque<>();

    public void run(UndoableCommand c){
        c.execute();
        undo.push(c);
        redo.clear();
    }

    public boolean canUndo(){ return !undo.isEmpty(); }
    public boolean canRedo(){ return !redo.isEmpty(); }

    public void undo(){
        if(!undo.isEmpty()){
            var c = undo.pop();
            c.undo();
            redo.push(c);
        }
    }

    public void redo(){
        if(!redo.isEmpty()){
            var c = redo.pop();
            c.execute();
            undo.push(c);
        }
    }
}


