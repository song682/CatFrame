package decok.dfcdvadstf.catframe.ui.render;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenArea;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * GUI Render State Collector — fully mirrors 26.1.2 {@code GuiRenderState}.
 *
 * <h3>Core Architecture</h3>
 * <p>Uses a <b>Strata + Node tree</b> layered structure to manage GUI element render order:</p>
 * <ul>
 *   <li><b>Stratum (layer)</b>: Frame-level render layers, iterated in addition order.
 *       Create new layer via {@link #nextStratum()} to separate background, content, foreground, etc.</li>
 *   <li><b>Node</b>: Each stratum contains a Node tree with {@code parent → up} pointers
 *       forming hierarchy. Child nodes render on top of parent nodes.</li>
 *   <li><b>Auto-layering</b>: {@link #findAppropriateNode} automatically decides which
 *       level of the Node tree a new element belongs to based on bounds intersection/
 *       containment. Contained elements automatically enter child nodes (render on top).</li>
 * </ul>
 *
 * <h3>Correspondence with 26.1.2</h3>
 * <table>
 *   <tr><th>26.1.2</th><th>CatFrame 1.7.10</th></tr>
 *   <tr><td>{@code GuiRenderState.strata}</td><td>{@code strata} (List<Node>)</td></tr>
 *   <tr><td>{@code GuiRenderState.current}</td><td>{@code current} (Node)</td></tr>
 *   <tr><td>{@code GuiItemRenderState}</td><td>{@link ItemRenderState}</td></tr>
 *   <tr><td>{@code GuiElementRenderState}</td><td>{@link ElementRenderState}</td></tr>
 *   <tr><td>{@code GuiTextRenderState}</td><td>{@link TextRenderState}</td></tr>
 *   <tr><td>{@code forEachItem/Element/Text}</td><td>{@link #forEachItem}/{@link #forEachElement}/{@link #forEachText}</td></tr>
 *   <tr><td>{@code nextStratum()}</td><td>{@link #nextStratum()}</td></tr>
 *   <tr><td>{@code blurBeforeThisStratum()}</td><td>{@link #blurBeforeThisStratum()}</td></tr>
 * </table>
 *
 * <h3>1.7.10 Adaptation</h3>
 * <p>26.1.2's GuiRenderState is for deferred rendering (collect state → unified GPU submit).
 * 1.7.10 uses GL immediate mode; GuiRenderState provides value through:</p>
 * <ul>
 *   <li><b>z-order management</b>: ensures GUI elements render in correct layer order</li>
 *   <li><b>State tracking</b>: records all rendered item positions for tooltip/click detection</li>
 *   <li><b>Frame lifecycle</b>: {@link #reset()} clears at frame start</li>
 * </ul>
 */
public class GuiRenderState {

    /** All stratum root nodes (in addition order) */
    private final List<Node> strata = new ArrayList<>();

    /** Blur effect divider: content before this stratum renders before blur */
    private int firstStratumAfterBlur = Integer.MAX_VALUE;

    /** Current write node — new elements added here by default */
    private Node current;

    /** Previous element bounds — used for auto-layering decision */
    @Nullable
    private ScreenRectangle lastElementBounds;

    /** Item model identities rendered this frame (for deduplication) */
    private final Set<Object> itemModelIdentities = new HashSet<>();

    public GuiRenderState() {
        nextStratum();
    }

    // ==================== Stratum management ====================

/**
     * Create a new render layer.
     * <p>
     * Mirrors 26.1.2 {@code GuiRenderState.nextStratum()}.
     * New layer renders after all existing layers (more front/top).
     */
    public void nextStratum() {
        current = new Node(null);
        strata.add(current);
    }

    /**
     * Mark blur effect divider before current layer.
     * <p>
     * Mirrors 26.1.2 {@code GuiRenderState.blurBeforeThisStratum()}.
     * Render divider for container background blur (frosted glass):
     * content before divider renders first, then blur, then content after.
     * <p>Can only be called once per frame.</p>
     *
     * @throws IllegalStateException if already called this frame
     */
    public void blurBeforeThisStratum() {
        if (firstStratumAfterBlur != Integer.MAX_VALUE) {
            throw new IllegalStateException("Can only blur once per frame");
        }
        firstStratumAfterBlur = strata.size() - 1;
    }

    // ==================== Node navigation ====================

    /**
     * Move up to current node's parent.
     * <p>
     * Mirrors 26.1.2 {@code GuiRenderState.up()}.
     * If current node has no parent, one is created automatically.
     * Effect: subsequent elements render on a higher layer.
     */
    public void up() {
        if (current.up == null) {
            current.up = new Node(current);
        }
        current = current.up;
    }

    // ==================== Element adding ====================

    /**
     * Add an item render state.
     * <p>
     * Auto-layering: decides target node based on bounds intersection/containment.
     * Mirrors 26.1.2 {@code GuiRenderState.addItem(GuiItemRenderState)}.
     *
     * @return true if successfully added (bounds non-null)
     */
    public boolean addItem(ItemRenderState itemState) {
        if (!findAppropriateNode(itemState)) return false;
        itemModelIdentities.add(itemState.getIdentity());
        current.addItem(itemState);
        return true;
    }

    /**
     * Add a GUI element render state (texture blit, rectangle fill, etc.).
     * <p>Mirrors 26.1.2 {@code GuiRenderState.addGuiElement(GuiElementRenderState)}.</p>
     *
     * @return true if successfully added
     */
    public boolean addElement(ElementRenderState elementState) {
        if (!findAppropriateNode(elementState)) return false;
        current.addElement(elementState);
        return true;
    }

    /**
     * Add a text render state.
     * <p>Mirrors 26.1.2 {@code GuiRenderState.addText(GuiTextRenderState)}.</p>
     *
     * @return true if successfully added
     */
    public boolean addText(TextRenderState textState) {
        if (!findAppropriateNode(textState)) return false;
        current.addText(textState);
        return true;
    }

    /**
     * Add element directly to current node (skip auto-layering).
     * <p>Mirrors 26.1.2 {@code GuiRenderState.addBlitToCurrentLayer()}.</p>
     */
    public void addElementToCurrentLayer(ElementRenderState elementState) {
        current.addElement(elementState);
    }

    // ==================== Auto-layering ====================

    /**
     * Find appropriate Node based on element bounds.
     * <p>
     * Mirrors 26.1.2 {@code GuiRenderState.findAppropriateNode(ScreenArea)}.
     * Layering logic:
     * <ol>
     *   <li>If previous element bounds fully contains new element bounds → {@link #up()} (enter child layer)</li>
     *   <li>Otherwise search down from stratum root for highest node intersecting new bounds,
     *       then {@link #up()} to its parent (above intersecting elements)</li>
     * </ol>
     *
     * @return true if element has valid bounds and node found
     */
    private boolean findAppropriateNode(ScreenArea area) {
        ScreenRectangle bounds = area.bounds();
        if (bounds == null) return false;

        if (lastElementBounds != null && lastElementBounds.encompasses(bounds)) {
            // 新元素被上一元素完全包含 → 进入子层
            up();
        } else {
            // 从 stratum 根节点搜索与新元素 bounds 相交的最高节点
            navigateToAboveHighestIntersecting(bounds);
        }

        lastElementBounds = bounds;
        return true;
    }

    /**
     * Starting from current stratum's deepest node, search up for node intersecting new bounds.
     * <p>
     * Mirrors 26.1.2 {@code navigateToAboveHighestElementWithIntersectingBounds()}.
     * On finding intersecting node, {@link #up()} to its parent (ensures new element
     * renders above intersecting elements).
     */
    private void navigateToAboveHighestIntersecting(ScreenRectangle bounds) {
        // 从 stratum 的最深层开始
        Node node = strata.get(strata.size() - 1);
        while (node.up != null) {
            node = node.up;
        }

        boolean found = false;
        while (!found) {
            found = hasIntersection(bounds, node.elementStates)
                    || hasIntersection(bounds, node.itemStates)
                    || hasIntersection(bounds, node.textStates);
            if (node.parent == null) break;
            if (!found) {
                node = node.parent;
            }
        }

        current = node;
        if (found) {
            up();
        }
    }

    /**
     * Check if any element in bounds list intersects given bounds.
     */
    private boolean hasIntersection(ScreenRectangle bounds,
                                    @Nullable List<? extends ScreenArea> states) {
        if (states != null) {
            for (ScreenArea area : states) {
                ScreenRectangle existing = area.bounds();
                if (existing != null && existing.intersects(bounds)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== Traversal ====================

    /**
     * Iterate all item render states in Node tree order.
     * <p>Mirrors 26.1.2 {@code GuiRenderState.forEachItem()}.</p>
     */
    public void forEachItem(Consumer<ItemRenderState> consumer) {
        Node backup = current;
        traverse(node -> {
            if (node.itemStates != null) {
                current = node;
                for (ItemRenderState state : node.itemStates) {
                    consumer.accept(state);
                }
            }
        }, TraverseRange.ALL);
        current = backup;
    }

    /**
     * Iterate all element render states in Node tree order.
     * <p>Mirrors 26.1.2 {@code GuiRenderState.forEachElement()}.</p>
     */
    public void forEachElement(Consumer<ElementRenderState> consumer, TraverseRange range) {
        traverse(node -> {
            if (node.elementStates != null) {
                for (ElementRenderState state : node.elementStates) {
                    consumer.accept(state);
                }
            }
        }, range);
    }

    /**
     * Iterate all text render states in Node tree order.
     * <p>Mirrors 26.1.2 {@code GuiRenderState.forEachText()}.</p>
     */
    public void forEachText(Consumer<TextRenderState> consumer) {
        Node backup = current;
        traverse(node -> {
            if (node.textStates != null) {
                current = node;
                for (TextRenderState state : node.textStates) {
                    consumer.accept(state);
                }
            }
        }, TraverseRange.ALL);
        current = backup;
    }

    /**
     * Sort element list.
     * <p>Mirrors 26.1.2 {@code GuiRenderState.sortElements()}.</p>
     */
    public void sortElements(Comparator<ElementRenderState> comparator) {
        traverse(node -> {
            if (node.elementStates != null) {
                node.elementStates.sort(comparator);
            }
        }, TraverseRange.ALL);
    }

    /**
     * Traverse Node tree in stratum order.
     * Each stratum starts at root, recursively visits child nodes.
     */
    private void traverse(Consumer<Node> consumer, TraverseRange range) {
        int start = 0;
        int end = strata.size();
        if (range == TraverseRange.BEFORE_BLUR) {
            end = Math.min(firstStratumAfterBlur, strata.size());
        } else if (range == TraverseRange.AFTER_BLUR) {
            start = firstStratumAfterBlur;
        }
        for (int i = start; i < end; i++) {
            traverseNode(strata.get(i), consumer);
        }
    }

    /** Recursively traverse Node and its children. */
    private void traverseNode(Node node, Consumer<Node> consumer) {
        consumer.accept(node);
        if (node.up != null) {
            traverseNode(node.up, consumer);
        }
    }

    // ==================== Queries ====================

    /** Gets all item model identities rendered this frame. */
    public Set<Object> getItemModelIdentities() {
        return itemModelIdentities;
    }

    /** Gets current write node. */
    public Node getCurrentNode() {
        return current;
    }

    // ==================== Frame lifecycle ====================

    /**
     * Reset all state at frame start.
     * <p>Mirrors 26.1.2 {@code GuiRenderState.reset()}.</p>
     */
    public void reset() {
        itemModelIdentities.clear();
        strata.clear();
        firstStratumAfterBlur = Integer.MAX_VALUE;
        lastElementBounds = null;
        nextStratum();
    }

    // ==================== Traversal range ====================

    /**
     * Traversal range — distinguishes rendering before/after blur effect.
     * <p>Mirrors 26.1.2 {@code GuiRenderState.TraverseRange}.</p>
     */
    public enum TraverseRange {
        /** Traverse all strata */
        ALL,
        /** Traverse only strata before blur divider */
        BEFORE_BLUR,
        /** Traverse only strata after blur divider */
        AFTER_BLUR
    }

    // ==================== Node tree node ====================

    /**
     * Node tree node — each node holds multiple render state lists.
     * <p>
     * Mirrors 26.1.2 {@code GuiRenderState.Node}.
     * <ul>
     *   <li>{@code parent}: parent node (points down to lower layer)</li>
     *   <li>{@code up}: child node (points up to higher layer)</li>
     *   <li>Traversal order: parent → up (bottom layer first, then top)</li>
     * </ul>
     */
    public static class Node {
        @Nullable
        public final Node parent;
        @Nullable
        public Node up;

        @Nullable
        public List<ElementRenderState> elementStates;
        @Nullable
        public List<ItemRenderState> itemStates;
        @Nullable
        public List<TextRenderState> textStates;

        public Node(@Nullable Node parent) {
            this.parent = parent;
        }

        public void addItem(ItemRenderState state) {
            if (itemStates == null) itemStates = new ArrayList<>();
            itemStates.add(state);
        }

        public void addElement(ElementRenderState state) {
            if (elementStates == null) elementStates = new ArrayList<>();
            elementStates.add(state);
        }

        public void addText(TextRenderState state) {
            if (textStates == null) textStates = new ArrayList<>();
            textStates.add(state);
        }
    }

    // ==================== Render state data classes ====================

/**
     * Item render state — mirrors 26.1.2 {@code GuiItemRenderState}.
     * <p>
     * Records complete info for one GUI item render: item stack, position, bounds.
     * Implements {@link ScreenArea} for auto-layering decisions.
     */
    public static class ItemRenderState implements ScreenArea {
        private final ItemStack stack;
        private final int x;
        private final int y;
        private final ScreenRectangle bounds;
        /**
         * Modelview matrix snapshot at collection time — mirrors 26.1.2 {@code GuiItemRenderState} pose matrix.
         * <p>End-of-frame deferred rendering uses this to {@code glLoadMatrix} restoring call-site GL transform;
         * may be null (no snapshot → draw with current GL state).</p>
         */
        @Nullable
        private final float[] poseMatrix;

        public ItemRenderState(ItemStack stack, int x, int y) {
            this(stack, x, y, null);
        }

        public ItemRenderState(ItemStack stack, int x, int y, @Nullable float[] poseMatrix) {
            this.stack = stack;
            this.x = x;
            this.y = y;
            this.poseMatrix = poseMatrix;
            // 标准 GUI 物品占 16×16 像素
            this.bounds = new ScreenRectangle(x, y, 16, 16);
        }

        public ItemStack getStack() { return stack; }
        public int getX() { return x; }
        public int getY() { return y; }

        /** 收集时的 modelview 矩阵快照，可为 null。 */
        @Nullable
        public float[] getPoseMatrix() { return poseMatrix; }

        /** Item model identity — for deduplication and tracking */
        public Object getIdentity() {
            return stack.getItem();
        }

        @Override
        public ScreenRectangle bounds() {
            return bounds;
        }
    }

/**
     * GUI element render state — mirrors 26.1.2 {@code GuiElementRenderState}.
     * <p>
     * Describes a 2D GUI draw operation (texture blit, rectangle fill, etc.).
     * In 1.7.10 GL immediate mode, stores draw params for replay.
     */
    public static class ElementRenderState implements ScreenArea {
        private final ScreenRectangle bounds;
        @Nullable
        private final ScreenRectangle scissorArea;

        public ElementRenderState(ScreenRectangle bounds) {
            this(bounds, null);
        }

        public ElementRenderState(ScreenRectangle bounds,
                                  @Nullable ScreenRectangle scissorArea) {
            this.bounds = bounds;
            this.scissorArea = scissorArea;
        }

        @Nullable
        public ScreenRectangle scissorArea() {
            return scissorArea;
        }

        @Override
        public ScreenRectangle bounds() {
            return bounds;
        }
    }

/**
     * Text render state — mirrors 26.1.2 {@code GuiTextRenderState}.
     * <p>
     * Describes a single text draw operation.
     */
    public static class TextRenderState implements ScreenArea {
        private final String text;
        private final int x;
        private final int y;
        private final int color;
        private final ScreenRectangle bounds;

        public TextRenderState(String text, int x, int y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            // Simplified bounds: ~6px per char width, 8px height
            // Exact calc needs Font object, approximation used here
            int estimatedWidth = text.length() * 6;
            this.bounds = new ScreenRectangle(x, y, estimatedWidth, 8);
        }

        public String getText() { return text; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getColor() { return color; }

        @Override
        public ScreenRectangle bounds() {
            return bounds;
        }
    }
}