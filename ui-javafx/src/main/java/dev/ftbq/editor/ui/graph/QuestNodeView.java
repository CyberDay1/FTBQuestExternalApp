package dev.ftbq.editor.ui.graph;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestNodeView extends Pane {
    private static final Color DEFAULT_FILL_COLOR = Color.web("#2f4f4f");
    private static final Color DEFAULT_STROKE_COLOR = Color.web("#b0b0b0");
    private static final Color DEFAULT_LABEL_COLOR = Color.web("#e6e6e6");
    private static final CssMetaData<QuestNodeView, Paint> FILL_PAINT_META =
            new CssMetaData<>("-quest-node-fill", PaintConverter.getInstance(), DEFAULT_FILL_COLOR) {
                @Override
                public boolean isSettable(QuestNodeView node) {
                    return node.fillPaint == null || !node.fillPaint.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(QuestNodeView node) {
                    return node.fillPaint;
                }
            };
    private static final CssMetaData<QuestNodeView, Paint> STROKE_PAINT_META =
            new CssMetaData<>("-quest-node-stroke", PaintConverter.getInstance(), DEFAULT_STROKE_COLOR) {
                @Override
                public boolean isSettable(QuestNodeView node) {
                    return node.strokePaint == null || !node.strokePaint.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(QuestNodeView node) {
                    return node.strokePaint;
                }
            };
    private static final CssMetaData<QuestNodeView, Paint> LABEL_PAINT_META =
            new CssMetaData<>("-quest-node-label", PaintConverter.getInstance(), DEFAULT_LABEL_COLOR) {
                @Override
                public boolean isSettable(QuestNodeView node) {
                    return node.labelPaint == null || !node.labelPaint.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(QuestNodeView node) {
                    return node.labelPaint;
                }
            };
    private static final List<CssMetaData<? extends Styleable, ?>> CSS_META_DATA;

    static {
        List<CssMetaData<? extends Styleable, ?>> list = new ArrayList<>(Pane.getClassCssMetaData());
        list.add(FILL_PAINT_META);
        list.add(STROKE_PAINT_META);
        list.add(LABEL_PAINT_META);
        CSS_META_DATA = Collections.unmodifiableList(list);
    }

    public final String questId;
    private double worldX;
    private double worldY;
    private double dragDX, dragDY;
    private double pressScreenX;
    private double pressScreenY;
    private boolean dragging;
    private static final double DRAG_THRESHOLD = 4.0;
    private final Circle body = new Circle(18);
    private final Text label = new Text();
    private boolean selected;

    private final StyleableObjectProperty<Paint> fillPaint = new StyleableObjectProperty<>(DEFAULT_FILL_COLOR) {
        @Override
        public Object getBean() {
            return QuestNodeView.this;
        }

        @Override
        public String getName() {
            return "questNodeFill";
        }

        @Override
        public CssMetaData<QuestNodeView, Paint> getCssMetaData() {
            return FILL_PAINT_META;
        }
    };
    private final StyleableObjectProperty<Paint> strokePaint = new StyleableObjectProperty<>(DEFAULT_STROKE_COLOR) {
        @Override
        public Object getBean() {
            return QuestNodeView.this;
        }

        @Override
        public String getName() {
            return "questNodeStroke";
        }

        @Override
        public CssMetaData<QuestNodeView, Paint> getCssMetaData() {
            return STROKE_PAINT_META;
        }
    };
    private final StyleableObjectProperty<Paint> labelPaint = new StyleableObjectProperty<>(DEFAULT_LABEL_COLOR) {
        @Override
        public Object getBean() {
            return QuestNodeView.this;
        }

        @Override
        public String getName() {
            return "questNodeLabel";
        }

        @Override
        public CssMetaData<QuestNodeView, Paint> getCssMetaData() {
            return LABEL_PAINT_META;
        }
    };

    public interface MoveListener {
        void moved(String questId, double screenX, double screenY);
    }

    private MoveListener onMove;
    private EditListener onEdit;

    public interface EditListener {
        void edit(String questId);
    }

    public QuestNodeView(String questId, String title, double worldX, double worldY) {
        this.questId = questId;
        this.worldX = worldX;
        this.worldY = worldY;
        getStyleClass().add("quest-node");
        label.getStyleClass().add("quest-node-label");
        body.setFill(DEFAULT_FILL_COLOR);
        body.setStroke(DEFAULT_STROKE_COLOR);
        label.setText(title);
        label.setFill(DEFAULT_LABEL_COLOR);
        label.setMouseTransparent(true);
        label.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            label.setLayoutX(-newBounds.getWidth() / 2);
            label.setLayoutY(newBounds.getHeight() / 4);
        });
        bindStyleProperties();
        getChildren().addAll(body, label);
        setPickOnBounds(false);
        updateScreenPosition(worldX, worldY);
        enableDrag();
        enableEdit();
    }

    public void setOnMove(MoveListener listener) {
        this.onMove = listener;
    }

    public void setOnEdit(EditListener listener) {
        this.onEdit = listener;
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS_META_DATA;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    public double getWorldX() {
        return worldX;
    }

    public double getWorldY() {
        return worldY;
    }

    public void setWorldPosition(double x, double y) {
        this.worldX = x;
        this.worldY = y;
    }

    public void updateScreenPosition(double screenX, double screenY) {
        relocate(screenX - body.getRadius(), screenY - body.getRadius());
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        if (selected) {
            body.setStroke(Color.web("#ffd37f"));
            body.setStrokeWidth(2.4);
        } else {
            body.setStroke(toColor(strokePaint.get(), DEFAULT_STROKE_COLOR));
            body.setStrokeWidth(1.0);
        }
    }

    private void enableDrag() {
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            dragDX = e.getX();
            dragDY = e.getY();
            pressScreenX = e.getScreenX();
            pressScreenY = e.getScreenY();
            dragging = false;
        });
        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!dragging) {
                double dx = e.getScreenX() - pressScreenX;
                double dy = e.getScreenY() - pressScreenY;
                if (Math.hypot(dx, dy) >= DRAG_THRESHOLD) {
                    dragging = true;
                }
            }
            double nx = getLayoutX() + (e.getX() - dragDX);
            double ny = getLayoutY() + (e.getY() - dragDY);
            relocate(nx, ny);
        });
        addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            double nx = getLayoutX() + body.getRadius();
            double ny = getLayoutY() + body.getRadius();
            if (onMove != null) {
                onMove.moved(questId, nx, ny);
            }
            dragging = false;
        });
    }

    private void enableEdit() {
        addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.PRIMARY
                    && e.getClickCount() >= 2
                    && !dragging
                    && e.isStillSincePress()
                    && onEdit != null) {
                onEdit.edit(questId);
            }
        });
    }

    private void bindStyleProperties() {
        fillPaint.addListener((obs, oldPaint, newPaint) -> body.setFill(toColor(newPaint, DEFAULT_FILL_COLOR)));
        strokePaint.addListener((obs, oldPaint, newPaint) -> {
            if (!selected) {
                body.setStroke(toColor(newPaint, DEFAULT_STROKE_COLOR));
            }
        });
        labelPaint.addListener((obs, oldPaint, newPaint) -> label.setFill(toColor(newPaint, DEFAULT_LABEL_COLOR)));
        body.setFill(toColor(fillPaint.get(), DEFAULT_FILL_COLOR));
        if (!selected) {
            body.setStroke(toColor(strokePaint.get(), DEFAULT_STROKE_COLOR));
            body.setStrokeWidth(1.0);
        }
        label.setFill(toColor(labelPaint.get(), DEFAULT_LABEL_COLOR));
    }

    private Color toColor(Paint paint, Color fallback) {
        if (paint instanceof Color color) {
            return color;
        }
        return fallback;
    }
}


