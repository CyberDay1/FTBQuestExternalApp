package dev.ftbq.editor.viewmodel.commands;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ftbq.editor.services.bus.UndoableCommand;

import java.util.Objects;

public final class QuestFieldChangeCommand implements UndoableCommand {

    public static final String TYPE = "quest.field.change";

    public enum Field {
        TITLE,
        DESCRIPTION
    }

    private final String questId;
    private final Field field;
    private final String value;
    private final String previousValue;

    public QuestFieldChangeCommand(String questId, Field field, String value, String previousValue) {
        this.questId = Objects.requireNonNull(questId, "questId");
        this.field = Objects.requireNonNull(field, "field");
        this.value = value;
        this.previousValue = previousValue;
    }

    public String questId() {
        return questId;
    }

    public Field field() {
        return field;
    }

    public String value() {
        return value;
    }

    public String previousValue() {
        return previousValue;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ObjectNode toPayload() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("questId", questId);
        node.put("field", field.name());
        if (value != null) {
            node.put("value", value);
        } else {
            node.putNull("value");
        }
        if (previousValue != null) {
            node.put("previousValue", previousValue);
        } else {
            node.putNull("previousValue");
        }
        return node;
    }

    @Override
    public UndoableCommand inverse() {
        return new QuestFieldChangeCommand(questId, field, previousValue, value);
    }

    public static QuestFieldChangeCommand fromPayload(ObjectNode node) {
        String questId = node.path("questId").asText("");
        String fieldName = node.path("field").asText(Field.TITLE.name());
        Field field = Field.valueOf(fieldName);
        String value = node.has("value") && !node.get("value").isNull() ? node.get("value").asText() : null;
        String previousValue = node.has("previousValue") && !node.get("previousValue").isNull()
                ? node.get("previousValue").asText()
                : null;
        return new QuestFieldChangeCommand(questId, field, value, previousValue);
    }
}


