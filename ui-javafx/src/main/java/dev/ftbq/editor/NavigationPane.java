package dev.ftbq.editor;

import java.util.EnumMap;
import java.util.Map;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class NavigationPane extends BorderPane {
    private static final double SIDEBAR_SPACING = 8.0;

    private final Map<NavigationTab, ToggleButton> buttonsByTab = new EnumMap<>(NavigationTab.class);
    private final Map<NavigationTab, Node> contentByTab = new EnumMap<>(NavigationTab.class);
    private final StackPane contentPane = new StackPane();

    public NavigationPane() {
        setPrefSize(1280, 800);
        setLeft(createSidebar());
        setCenter(contentPane);
        showTab(NavigationTab.CHAPTER_GROUPS);
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(SIDEBAR_SPACING);
        sidebar.setPadding(new Insets(16, 12, 16, 12));
        sidebar.getStyleClass().add("navigation-sidebar");

        ToggleGroup toggleGroup = new ToggleGroup();
        for (NavigationTab tab : NavigationTab.values()) {
            ToggleButton button = new ToggleButton(tab.getTitle());
            button.setToggleGroup(toggleGroup);
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("navigation-button");
            button.setOnAction(event -> showTab(tab));
            sidebar.getChildren().add(button);
            VBox.setVgrow(button, Priority.NEVER);
            buttonsByTab.put(tab, button);
            if (tab == NavigationTab.CHAPTER_GROUPS) {
                button.setSelected(true);
            }
        }

        return sidebar;
    }

    private void showTab(NavigationTab tab) {
        ToggleButton button = buttonsByTab.get(tab);
        if (button != null && !button.isSelected()) {
            button.setSelected(true);
        }

        Node content = contentByTab.computeIfAbsent(tab, this::createContentFor);
        contentPane.getChildren().setAll(content);
    }

    private Node createContentFor(NavigationTab tab) {
        Label title = new Label(tab.getTitle());
        title.getStyleClass().add("navigation-content-title");
        StackPane container = new StackPane(title);
        container.setPadding(new Insets(24));
        container.getStyleClass().add("navigation-content-container");
        return container;
    }
}

enum NavigationTab {
    CHAPTER_GROUPS("Chapter Groups"),
    CHAPTER_GRAPH("Chapter Graph"),
    QUEST_EDITOR("Quest Editor"),
    ITEM_BROWSER("Item Browser"),
    LOOT_TABLE_EDITOR("Loot Table Editor"),
    SETTINGS("Settings");

    private final String title;

    NavigationTab(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}


