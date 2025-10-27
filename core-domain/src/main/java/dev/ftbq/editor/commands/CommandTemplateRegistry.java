package dev.ftbq.editor.commands;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry containing built-in command templates that can be reused by the UI.
 */
public final class CommandTemplateRegistry {

    private static final Map<String, String> VANILLA_COMMANDS;

    static {
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put("Give Diamond", "/give @p minecraft:diamond 1");
        templates.put("Add XP", "/xp add @p 10 levels");
        VANILLA_COMMANDS = Collections.unmodifiableMap(templates);
    }

    private CommandTemplateRegistry() {
    }

    /**
     * @return an immutable view of the built-in vanilla command templates
     */
    public static Map<String, String> vanillaCommands() {
        return VANILLA_COMMANDS;
    }

    /**
     * @return the ordered list of vanilla template names for display purposes
     */
    public static List<String> vanillaTemplateNames() {
        return List.copyOf(VANILLA_COMMANDS.keySet());
    }

    /**
     * Resolve a template name to the underlying command string.
     *
     * @param name template name
     * @return command string or {@code null} if none exists
     */
    public static String findVanillaCommand(String name) {
        return VANILLA_COMMANDS.get(name);
    }
}
