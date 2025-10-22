package dev.ftbq.editor.domain;

/**
 * A requirement that must be satisfied to complete a quest.
 */
public sealed interface Task permits ItemTask, AdvancementTask, LocationTask {

    String type();
}
