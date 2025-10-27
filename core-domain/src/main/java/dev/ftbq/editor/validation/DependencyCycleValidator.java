package dev.ftbq.editor.validation;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.validation.Validator.ItemResolver;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator that detects circular dependencies among quests inside a chapter.
 */
public final class DependencyCycleValidator implements Validator {

    @Override
    public List<ValidationIssue> validate(QuestFile qf, ItemResolver resolver) {
        if (qf == null || qf.chapters() == null) {
            return List.of();
        }

        List<ValidationIssue> issues = new ArrayList<>();
        for (Chapter chapter : qf.chapters()) {
            if (chapter == null || chapter.quests() == null) {
                continue;
            }
            detectCycles(chapter, issues);
        }
        return issues;
    }

    private void detectCycles(Chapter chapter, Collection<ValidationIssue> issues) {
        Map<String, Quest> questsById = chapter.quests().stream()
                .filter(Objects::nonNull)
                .filter(quest -> quest.id() != null && !quest.id().isBlank())
                .collect(Collectors.toMap(Quest::id, quest -> quest, (a, b) -> a, HashMap::new));

        Map<String, List<String>> adjacency = new HashMap<>();
        questsById.values().forEach(quest -> {
            List<String> targets = quest.dependencies().stream()
                    .map(Dependency::questId)
                    .filter(Objects::nonNull)
                    .filter(questsById::containsKey)
                    .toList();
            adjacency.put(quest.id(), targets);
        });

        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> stackSet = new HashSet<>();
        Set<List<String>> reportedCycles = new HashSet<>();

        for (String questId : adjacency.keySet()) {
            dfs(questId, chapter.id(), adjacency, visited, stack, stackSet, reportedCycles, issues);
        }
    }

    private void dfs(String questId,
                     String chapterId,
                     Map<String, List<String>> adjacency,
                     Set<String> visited,
                     Deque<String> stack,
                     Set<String> stackSet,
                     Set<List<String>> reportedCycles,
                     Collection<ValidationIssue> issues) {
        if (!visited.add(questId)) {
            return;
        }
        stack.push(questId);
        stackSet.add(questId);

        for (String target : adjacency.getOrDefault(questId, List.of())) {
            if (stackSet.contains(target)) {
                List<String> cycle = extractCycle(stack, target);
                if (reportedCycles.add(cycle)) {
                    issues.add(buildIssue(chapterId, cycle));
                }
                continue;
            }
            dfs(target, chapterId, adjacency, visited, stack, stackSet, reportedCycles, issues);
        }

        stackSet.remove(questId);
        stack.pop();
    }

    private List<String> extractCycle(Deque<String> stack, String target) {
        List<String> nodes = new ArrayList<>(stack);
        int index = nodes.indexOf(target);
        if (index < 0) {
            return List.of(target, target);
        }
        List<String> cycle = new ArrayList<>(nodes.subList(0, index + 1));
        Collections.reverse(cycle);
        cycle.add(target);
        return cycle;
    }

    private ValidationIssue buildIssue(String chapterId, List<String> cycle) {
        String message = "Dependency cycle detected: " + String.join(" → ", cycle);
        String path = "chapter/" + Optional.ofNullable(chapterId).orElse("<unknown>")
                + "/quests/" + cycle.get(0);
        return new ValidationIssue("ERROR", path, message);
    }
}
