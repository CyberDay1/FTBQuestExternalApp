package dev.ftbq.editor.view.graph;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.Quest;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Data-only representation of a quest chapter graph.
 */
public final class QuestGraphModel {

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    private QuestGraphModel() {
    }

    public static QuestGraphModel fromChapter(Chapter chapter,
                                              Map<String, GraphCanvas.ValidationLevel> validationLevels,
                                              Map<String, Point2D> persistedPositions) {
        Objects.requireNonNull(chapter, "chapter");
        QuestGraphModel model = new QuestGraphModel();
        double nodeSize = 72;
        double spacingX = nodeSize * 2.2;
        double spacingY = nodeSize * 1.8;
        List<Quest> quests = chapter.quests();
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(quests.size())));
        int row = 0;
        int column = 0;
        Map<String, Point2D> positions = persistedPositions == null ? Map.of() : persistedPositions;

        for (Quest quest : quests) {
            GraphCanvas.ValidationLevel validation = validationLevels.getOrDefault(quest.id(), GraphCanvas.ValidationLevel.OK);
            double x = column * spacingX;
            double y = row * spacingY;
            Point2D savedPosition = positions.get(quest.id());
            Point2D initialPosition = savedPosition != null ? savedPosition : new Point2D(x, y);
            Node node = new Node(quest, initialPosition, validation);
            model.nodes.put(quest.id(), node);

            column++;
            if (column >= columns) {
                column = 0;
                row++;
            }
        }

        for (Quest quest : quests) {
            Node target = model.nodes.get(quest.id());
            if (target == null) {
                continue;
            }
            for (Dependency dependency : quest.dependencies()) {
                Node source = model.nodes.get(dependency.questId());
                if (source != null) {
                    model.edges.add(new Edge(source, target, dependency.required()));
                }
            }
        }

        return model;
    }

    public Collection<Node> getNodes() {
        return List.copyOf(nodes.values());
    }

    public List<Edge> getEdges() {
        return List.copyOf(edges);
    }

    public Optional<Node> findNode(String questId) {
        return Optional.ofNullable(nodes.get(questId));
    }

    public void moveNode(String questId, double x, double y) {
        Node node = nodes.get(questId);
        if (node != null) {
            node.setPosition(new Point2D(x, y));
        }
    }

    public void updateValidation(String questId, GraphCanvas.ValidationLevel level) {
        Node node = nodes.get(questId);
        if (node != null) {
            node.setValidation(level);
        }
    }

    public static final class Node {
        private final Quest quest;
        private Point2D position;
        private GraphCanvas.ValidationLevel validationLevel;

        private Node(Quest quest, Point2D position, GraphCanvas.ValidationLevel validationLevel) {
            this.quest = Objects.requireNonNull(quest, "quest");
            this.position = Objects.requireNonNull(position, "position");
            this.validationLevel = Objects.requireNonNull(validationLevel, "validationLevel");
        }

        public Quest getQuest() {
            return quest;
        }

        public Point2D getPosition() {
            return position;
        }

        public void setPosition(Point2D position) {
            this.position = Objects.requireNonNull(position, "position");
        }

        public void setPosition(double x, double y) {
            this.position = new Point2D(x, y);
        }

        public GraphCanvas.ValidationLevel getValidationLevel() {
            return validationLevel;
        }

        public void setValidation(GraphCanvas.ValidationLevel validationLevel) {
            this.validationLevel = Objects.requireNonNull(validationLevel, "validationLevel");
        }
    }

    public static final class Edge {
        private final Node source;
        private final Node target;
        private final boolean required;

        private Edge(Node source, Node target, boolean required) {
            this.source = Objects.requireNonNull(source, "source");
            this.target = Objects.requireNonNull(target, "target");
            this.required = required;
        }

        public Node getSource() {
            return source;
        }

        public Node getTarget() {
            return target;
        }

        public boolean isRequired() {
            return required;
        }
    }
}
