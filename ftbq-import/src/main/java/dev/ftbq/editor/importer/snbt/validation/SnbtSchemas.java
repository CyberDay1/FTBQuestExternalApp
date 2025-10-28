package dev.ftbq.editor.importer.snbt.validation;

import dev.ftbq.editor.domain.RewardTypeRegistry;
import dev.ftbq.editor.domain.TaskTypeRegistry;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class SnbtSchemas {
    private static final SnbtSchemaNode QUEST_PACK = createQuestPackSchema();

    private SnbtSchemas() {
    }

    static SnbtSchemaNode questPack() {
        return QUEST_PACK;
    }

    private static SnbtSchemaNode createQuestPackSchema() {
        var visibilityEnum = enumValues(Visibility.class);
        var taskTypes = TaskTypeRegistry.ids();
        var rewardTypes = RewardTypeRegistry.ids();

        SnbtSchemaNode descriptionNode = union(new StringSchemaNode(), new ArraySchemaNode(new StringSchemaNode(), true));

        SnbtSchemaNode dependencyNode = union(
                new ObjectSchemaNode(
                        Map.of(
                                "quest", stringOrNumber(),
                                "id", stringOrNumber(),
                                "required", new BooleanSchemaNode()
                        ),
                        Set.of(),
                        true,
                        List.of()
                ),
                new StringSchemaNode(),
                new NumberSchemaNode()
        );

        SnbtSchemaNode rewardNode = new ObjectSchemaNode(
                Map.of(
                        "type", new EnumStringSchemaNode(rewardTypes),
                        "table", new StringSchemaNode(),
                        "command", new StringSchemaNode(),
                        "run_as_server", new BooleanSchemaNode(),
                        "amount", new NumberSchemaNode(),
                        "item", new ObjectSchemaNode(Map.of(), Set.of(), true, List.of())
                ),
                Set.of("type"),
                true,
                List.of()
        );

        SnbtSchemaNode taskNode = new ObjectSchemaNode(
                Map.of(
                        "type", new EnumStringSchemaNode(taskTypes),
                        "consume", new BooleanSchemaNode(),
                        "item", new ObjectSchemaNode(Map.of(
                                "id", new StringSchemaNode(),
                                "count", new NumberSchemaNode()
                        ), Set.of("id"), true, List.of()),
                        "advancement", new StringSchemaNode(),
                        "dimension", new StringSchemaNode(),
                        "x", new NumberSchemaNode(),
                        "y", new NumberSchemaNode(),
                        "z", new NumberSchemaNode(),
                        "radius", new NumberSchemaNode()
                ),
                Set.of("type"),
                true,
                List.of()
        );

        SnbtSchemaNode questNode = new ObjectSchemaNode(
                Map.of(
                        "id", stringOrNumber(),
                        "title", new StringSchemaNode(),
                        "description", descriptionNode,
                        "icon", iconSchema(),
                        "visibility", new EnumStringSchemaNode(visibilityEnum),
                        "tasks", new ArraySchemaNode(taskNode, true),
                        "rewards", new ArraySchemaNode(rewardNode, true),
                        "dependencies", union(new ArraySchemaNode(dependencyNode, true), dependencyNode)
                ),
                Set.of("id", "title", "icon", "tasks", "rewards"),
                true,
                List.of()
        );

        SnbtSchemaNode chapterNode = new ObjectSchemaNode(
                Map.of(
                        "id", stringOrNumber(),
                        "title", new StringSchemaNode(),
                        "description", descriptionNode,
                        "group", stringOrNumber(),
                        "group_id", stringOrNumber(),
                        "icon", iconSchema(),
                        "background", new StringSchemaNode(),
                        "visibility", new EnumStringSchemaNode(visibilityEnum),
                        "quests", new ArraySchemaNode(questNode, true)
                ),
                Set.of("id", "title", "icon", "background", "quests"),
                true,
                List.of()
        );

        SnbtSchemaNode chapterGroupNode = new ObjectSchemaNode(
                Map.of(
                        "id", stringOrNumber(),
                        "title", new StringSchemaNode(),
                        "icon", iconSchema(),
                        "chapter_ids", union(new ArraySchemaNode(stringOrNumber(), true), stringOrNumber()),
                        "chapters", union(new ArraySchemaNode(stringOrNumber(), true), stringOrNumber()),
                        "visibility", new EnumStringSchemaNode(visibilityEnum)
                ),
                Set.of("id", "title", "icon"),
                true,
                List.of(new RequireAnyRule(Set.of("chapter_ids", "chapters"), "Chapter group must list chapters via 'chapter_ids' or 'chapters'."))
        );

        SnbtSchemaNode lootTableNode = new ObjectSchemaNode(
                Map.of(
                        "id", new StringSchemaNode(),
                        "pools", new ArraySchemaNode(new ObjectSchemaNode(Map.of(), Set.of(), true, List.of()), true)
                ),
                Set.of("id"),
                true,
                List.of()
        );

        return new ObjectSchemaNode(
                Map.of(
                        "id", new StringSchemaNode(),
                        "title", new StringSchemaNode(),
                        "file_version", new NumberSchemaNode(),
                        "version", new NumberSchemaNode(),
                        "chapter_groups", new ArraySchemaNode(chapterGroupNode, true),
                        "chapters", new ArraySchemaNode(chapterNode, true),
                        "loot_tables", new ArraySchemaNode(lootTableNode, true)
                ),
                Set.of("id", "title", "chapters"),
                true,
                List.of()
        );
    }

    private static Set<String> enumValues(Class<? extends Enum<?>> type) {
        Set<String> values = new LinkedHashSet<>();
        for (Enum<?> constant : type.getEnumConstants()) {
            values.add(constant.name().toLowerCase(Locale.ROOT));
        }
        return values;
    }

    private static SnbtSchemaNode iconSchema() {
        return new PatternStringSchemaNode(Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+"));
    }

    private static SnbtSchemaNode stringOrNumber() {
        return union(new StringSchemaNode(), new NumberSchemaNode());
    }

    private static SnbtSchemaNode union(SnbtSchemaNode... options) {
        return new UnionSchemaNode(List.of(options));
    }

    private interface ObjectRule {
        void validate(Map<String, Object> value, ValidationPath path, List<ValidationIssue> issues);
    }

    private static final class RequireAnyRule implements ObjectRule {
        private final Set<String> keys;
        private final String message;

        private RequireAnyRule(Set<String> keys, String message) {
            this.keys = new LinkedHashSet<>(Objects.requireNonNull(keys, "keys"));
            this.message = Objects.requireNonNull(message, "message");
        }

        @Override
        public void validate(Map<String, Object> value, ValidationPath path, List<ValidationIssue> issues) {
            for (String key : keys) {
                if (value.containsKey(key) && value.get(key) != null) {
                    return;
                }
            }
            issues.add(new ValidationIssue("ERROR", path.toString(), message));
        }
    }

    private static final class ObjectSchemaNode implements SnbtSchemaNode {
        private final Map<String, SnbtSchemaNode> properties;
        private final Set<String> required;
        private final boolean allowAdditional;
        private final List<ObjectRule> rules;

        private ObjectSchemaNode(Map<String, SnbtSchemaNode> properties,
                                 Set<String> required,
                                 boolean allowAdditional,
                                 List<ObjectRule> rules) {
            this.properties = new LinkedHashMap<>(Objects.requireNonNull(properties, "properties"));
            this.required = new LinkedHashSet<>(Objects.requireNonNull(required, "required"));
            this.allowAdditional = allowAdditional;
            this.rules = new ArrayList<>(Objects.requireNonNull(rules, "rules"));
        }

        @Override
        public void validate(Object value, ValidationPath path, List<ValidationIssue> issues) {
            if (!(value instanceof Map<?, ?> map)) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Expected object but found " + describeType(value)));
                return;
            }

            Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                typed.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            for (String req : required) {
                if (!typed.containsKey(req)) {
                    issues.add(new ValidationIssue("ERROR", path.property(req).toString(), "Missing required property '" + req + "'"));
                } else if (typed.get(req) == null) {
                    issues.add(new ValidationIssue("ERROR", path.property(req).toString(), "Property '" + req + "' cannot be null"));
                }
            }

            for (Map.Entry<String, Object> entry : typed.entrySet()) {
                SnbtSchemaNode child = properties.get(entry.getKey());
                if (child != null) {
                    child.validate(entry.getValue(), path.property(entry.getKey()), issues);
                } else if (!allowAdditional) {
                    issues.add(new ValidationIssue("WARNING", path.property(entry.getKey()).toString(), "Unexpected property '" + entry.getKey() + "'"));
                }
            }

            for (ObjectRule rule : rules) {
                rule.validate(typed, path, issues);
            }
        }
    }

    private static final class ArraySchemaNode implements SnbtSchemaNode {
        private final SnbtSchemaNode element;
        private final boolean allowEmpty;

        private ArraySchemaNode(SnbtSchemaNode element, boolean allowEmpty) {
            this.element = Objects.requireNonNull(element, "element");
            this.allowEmpty = allowEmpty;
        }

        @Override
        public void validate(Object value, ValidationPath path, List<ValidationIssue> issues) {
            if (!(value instanceof List<?> list)) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Expected list but found " + describeType(value)));
                return;
            }
            if (!allowEmpty && list.isEmpty()) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Array must contain at least one entry"));
            }
            for (int i = 0; i < list.size(); i++) {
                Object entry = list.get(i);
                element.validate(entry, path.index(i), issues);
            }
        }
    }

    private static final class StringSchemaNode implements SnbtSchemaNode {
        @Override
        public void validate(Object value, ValidationPath path, List<ValidationIssue> issues) {
            if (!(value instanceof String)) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Expected string but found " + describeType(value)));
            } else if (((String) value).isBlank()) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Value cannot be blank"));
            }
        }
    }

    private static final class PatternStringSchemaNode implements SnbtSchemaNode {
        private final Pattern pattern;

        private PatternStringSchemaNode(Pattern pattern) {
            this.pattern = Objects.requireNonNull(pattern, "pattern");
        }

        @Override
        public void validate(Object value, ValidationPath path, List<ValidationIssue> issues) {
            if (!(value instanceof String string)) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Expected string but found " + describeType(value)));
                return;
            }
            if (!pattern.matcher(string).matches()) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Value '" + string + "' does not match required pattern"));
            }
        }
    }

    private static final class EnumStringSchemaNode implements SnbtSchemaNode {
        private final Set<String> allowed;

        private EnumStringSchemaNode(Set<String> allowed) {
            this.allowed = new LinkedHashSet<>(Objects.requireNonNull(allowed, "allowed"));
        }

        @Override
        public void validate(Object value, ValidationPath path, List<ValidationIssue> issues) {
            if (!(value instanceof String string)) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Expected string but found " + describeType(value)));
                return;
            }
            String normalized = string.toLowerCase(Locale.ROOT);
            if (!allowed.contains(normalized)) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Unsupported value '" + string + "'. Expected one of " + allowed));
            }
        }
    }

    private static final class NumberSchemaNode implements SnbtSchemaNode {
        @Override
        public void validate(Object value, ValidationPath path, List<ValidationIssue> issues) {
            if (!(value instanceof Number)) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Expected number but found " + describeType(value)));
            }
        }
    }

    private static final class BooleanSchemaNode implements SnbtSchemaNode {
        @Override
        public void validate(Object value, ValidationPath path, List<ValidationIssue> issues) {
            if (!(value instanceof Boolean)) {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Expected boolean but found " + describeType(value)));
            }
        }
    }

    private static final class UnionSchemaNode implements SnbtSchemaNode {
        private final List<SnbtSchemaNode> options;

        private UnionSchemaNode(List<SnbtSchemaNode> options) {
            this.options = new ArrayList<>(Objects.requireNonNull(options, "options"));
        }

        @Override
        public void validate(Object value, ValidationPath path, List<ValidationIssue> issues) {
            List<ValidationIssue> best = null;
            for (SnbtSchemaNode option : options) {
                List<ValidationIssue> trial = new ArrayList<>();
                option.validate(value, path, trial);
                if (containsError(trial)) {
                    if (best == null || countErrors(trial) < countErrors(best)) {
                        best = trial;
                    }
                    continue;
                }
                issues.addAll(trial);
                return;
            }
            if (best != null) {
                issues.addAll(best);
            } else {
                issues.add(new ValidationIssue("ERROR", path.toString(), "Value does not match any allowed type"));
            }
        }

        private boolean containsError(List<ValidationIssue> issues) {
            for (ValidationIssue issue : issues) {
                if ("ERROR".equalsIgnoreCase(issue.severity())) {
                    return true;
                }
            }
            return false;
        }

        private int countErrors(List<ValidationIssue> issues) {
            int count = 0;
            for (ValidationIssue issue : issues) {
                if ("ERROR".equalsIgnoreCase(issue.severity())) {
                    count++;
                }
            }
            return count;
        }
    }

    private static String describeType(Object value) {
        if (value == null) {
            return "null";
        }
        return value.getClass().getSimpleName();
    }
}
