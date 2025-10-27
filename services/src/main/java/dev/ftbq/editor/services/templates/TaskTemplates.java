package dev.ftbq.editor.services.templates;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.Task;

public final class TaskTemplates {

    private TaskTemplates() {
    }

    public static Task item(String nsItem, int count) {
        return new ItemTask(nsItem, count);
    }

    public static Task item(String nsItem, int count, boolean consume) {
        return new ItemTask(nsItem, count, consume);
    }

    public static Task advancement(String id) {
        return new AdvancementTask(id);
    }

    public static Task location(double x, double y, double z, String dimension) {
        return new LocationTask(x, y, z, dimension);
    }
}
