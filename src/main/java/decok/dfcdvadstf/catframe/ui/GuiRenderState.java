package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenArea;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * GUI 渲染状态收集器 — 完整对标 26.1.2 {@code GuiRenderState}。
 *
 * <h3>核心架构</h3>
 * <p>采用 <b>Strata + Node 树</b> 的分层结构管理 GUI 元素的渲染顺序：</p>
 * <ul>
 *   <li><b>Stratum（层）</b>：帧级别的渲染分层，按添加顺序遍历。
 *       通过 {@link #nextStratum()} 创建新层，用于区分背景、内容、前景等。</li>
 *   <li><b>Node（节点）</b>：每个 stratum 内部是一棵 Node 树，
 *       通过 {@code parent → up} 指针形成层级。子节点渲染在父节点之上。</li>
 *   <li><b>自动分层</b>：{@link #findAppropriateNode} 根据元素 bounds 的
 *       相交/包含关系，自动决定新元素应归属到 Node 树的哪一层。
 *       被包含的元素自动进入子节点（渲染在更上层）。</li>
 * </ul>
 *
 * <h3>与 26.1.2 的对应关系</h3>
 * <table>
 *   <tr><th>26.1.2</th><th>CatFrame 1.7.10</th></tr>
 *   <tr><td>{@code GuiRenderState.strata}</td><td>{@code strata} (List&lt;Node&gt;)</td></tr>
 *   <tr><td>{@code GuiRenderState.current}</td><td>{@code current} (Node)</td></tr>
 *   <tr><td>{@code GuiItemRenderState}</td><td>{@link ItemRenderState}</td></tr>
 *   <tr><td>{@code GuiElementRenderState}</td><td>{@link ElementRenderState}</td></tr>
 *   <tr><td>{@code GuiTextRenderState}</td><td>{@link TextRenderState}</td></tr>
 *   <tr><td>{@code forEachItem/Element/Text}</td><td>{@link #forEachItem}/{@link #forEachElement}/{@link #forEachText}</td></tr>
 *   <tr><td>{@code nextStratum()}</td><td>{@link #nextStratum()}</td></tr>
 *   <tr><td>{@code blurBeforeThisStratum()}</td><td>{@link #blurBeforeThisStratum()}</td></tr>
 * </table>
 *
 * <h3>1.7.10 适配</h3>
 * <p>26.1.2 的 GuiRenderState 用于延迟渲染（收集状态 → 统一提交到 GPU）。
 * 1.7.10 使用 GL 即时模式，GuiRenderState 的价值在于：</p>
 * <ul>
 *   <li><b>z-order 管理</b>：确保 GUI 元素按正确层级渲染</li>
 *   <li><b>状态追踪</b>：记录所有渲染过的物品位置，供 tooltip/点击判定使用</li>
 *   <li><b>帧级生命周期</b>：{@link #reset()} 在帧开始时清理</li>
 * </ul>
 */
public class GuiRenderState {

    /** 所有 stratum 的根节点列表（按添加顺序） */
    private final List<Node> strata = new ArrayList<>();

    /** 模糊效果的分界线：此 stratum 之前的内容需要在模糊前渲染 */
    private int firstStratumAfterBlur = Integer.MAX_VALUE;

    /** 当前写入节点 — 新元素默认添加到此节点 */
    private Node current;

    /** 上一元素的边界 — 用于自动分层判定 */
    @Nullable
    private ScreenRectangle lastElementBounds;

    /** 本帧渲染过的物品模型标识集合（去重用） */
    private final Set<Object> itemModelIdentities = new HashSet<>();

    public GuiRenderState() {
        nextStratum();
    }

    // ==================== Stratum 管理 ====================

    /**
     * 创建新的渲染层。
     * <p>
     * 对标 26.1.2 {@code GuiRenderState.nextStratum()}。
     * 新层在所有已有层之后渲染（更靠前/更上层）。
     */
    public void nextStratum() {
        current = new Node(null);
        strata.add(current);
    }

    /**
     * 标记在当前层之前插入模糊效果分界线。
     * <p>
     * 对标 26.1.2 {@code GuiRenderState.blurBeforeThisStratum()}。
     * 用于容器界面中背景模糊（毛玻璃效果）的渲染分界：
     * 分界线之前的内容先渲染，然后执行模糊，再渲染分界线之后的内容。
     * <p>每帧只能调用一次。</p>
     *
     * @throws IllegalStateException 如果本帧已调用过
     */
    public void blurBeforeThisStratum() {
        if (firstStratumAfterBlur != Integer.MAX_VALUE) {
            throw new IllegalStateException("Can only blur once per frame");
        }
        firstStratumAfterBlur = strata.size() - 1;
    }

    // ==================== Node 导航 ====================

    /**
     * 向上移动到当前节点的父级。
     * <p>
     * 对标 26.1.2 {@code GuiRenderState.up()}。
     * 如果当前节点没有父级，自动创建一个。
     * 效果：后续添加的元素将渲染在更上层。
     */
    public void up() {
        if (current.up == null) {
            current.up = new Node(current);
        }
        current = current.up;
    }

    // ==================== 元素添加 ====================

    /**
     * 添加物品渲染状态。
     * <p>
     * 自动分层：根据 bounds 的相交/包含关系决定归属节点。
     * 对标 26.1.2 {@code GuiRenderState.addItem(GuiItemRenderState)}。
     *
     * @return true 如果成功添加（bounds 非 null）
     */
    public boolean addItem(ItemRenderState itemState) {
        if (!findAppropriateNode(itemState)) return false;
        itemModelIdentities.add(itemState.getIdentity());
        current.addItem(itemState);
        return true;
    }

    /**
     * 添加 GUI 元素渲染状态（纹理绘制、矩形填充等）。
     * <p>对标 26.1.2 {@code GuiRenderState.addGuiElement(GuiElementRenderState)}。</p>
     *
     * @return true 如果成功添加
     */
    public boolean addElement(ElementRenderState elementState) {
        if (!findAppropriateNode(elementState)) return false;
        current.addElement(elementState);
        return true;
    }

    /**
     * 添加文本渲染状态。
     * <p>对标 26.1.2 {@code GuiRenderState.addText(GuiTextRenderState)}。</p>
     *
     * @return true 如果成功添加
     */
    public boolean addText(TextRenderState textState) {
        if (!findAppropriateNode(textState)) return false;
        current.addText(textState);
        return true;
    }

    /**
     * 直接添加元素到当前节点（跳过自动分层）。
     * <p>对标 26.1.2 {@code GuiRenderState.addBlitToCurrentLayer()}。</p>
     */
    public void addElementToCurrentLayer(ElementRenderState elementState) {
        current.addElement(elementState);
    }

    // ==================== 自动分层 ====================

    /**
     * 根据元素的 bounds 找到合适的 Node。
     * <p>
     * 对标 26.1.2 {@code GuiRenderState.findAppropriateNode(ScreenArea)}。
     * 分层逻辑：
     * <ol>
     *   <li>如果上一元素的 bounds 完全包含新元素的 bounds → {@link #up()}（进入子层）</li>
     *   <li>否则从 stratum 根节点向下搜索，找到与新元素 bounds 相交的最高节点，
     *       然后 {@link #up()} 到其父级（在相交元素之上）</li>
     * </ol>
     *
     * @return true 如果元素有有效 bounds 并找到了节点
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
     * 从当前 stratum 的最深层节点开始，向上搜索与新 bounds 相交的节点。
     * <p>
     * 对标 26.1.2 {@code navigateToAboveHighestElementWithIntersectingBounds()}。
     * 找到相交节点后，{@link #up()} 到其父级（确保新元素渲染在相交元素之上）。
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
     * 检查 bounds 列表中是否有任何元素与给定 bounds 相交。
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

    // ==================== 遍历 ====================

    /**
     * 按 Node 树顺序遍历所有物品渲染状态。
     * <p>对标 26.1.2 {@code GuiRenderState.forEachItem()}。</p>
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
     * 按 Node 树顺序遍历所有元素渲染状态。
     * <p>对标 26.1.2 {@code GuiRenderState.forEachElement()}。</p>
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
     * 按 Node 树顺序遍历所有文本渲染状态。
     * <p>对标 26.1.2 {@code GuiRenderState.forEachText()}。</p>
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
     * 对元素列表排序。
     * <p>对标 26.1.2 {@code GuiRenderState.sortElements()}。</p>
     */
    public void sortElements(Comparator<ElementRenderState> comparator) {
        traverse(node -> {
            if (node.elementStates != null) {
                node.elementStates.sort(comparator);
            }
        }, TraverseRange.ALL);
    }

    /**
     * 按 stratum 顺序遍历 Node 树。
     * 每个 stratum 从根节点开始，递归访问子节点。
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

    /** 递归遍历 Node 及其子节点。 */
    private void traverseNode(Node node, Consumer<Node> consumer) {
        consumer.accept(node);
        if (node.up != null) {
            traverseNode(node.up, consumer);
        }
    }

    // ==================== 查询 ====================

    /** 获取本帧渲染过的所有物品模型标识。 */
    public Set<Object> getItemModelIdentities() {
        return itemModelIdentities;
    }

    /** 获取当前写入节点。 */
    public Node getCurrentNode() {
        return current;
    }

    // ==================== 帧生命周期 ====================

    /**
     * 帧开始时重置所有状态。
     * <p>对标 26.1.2 {@code GuiRenderState.reset()}。</p>
     */
    public void reset() {
        itemModelIdentities.clear();
        strata.clear();
        firstStratumAfterBlur = Integer.MAX_VALUE;
        lastElementBounds = null;
        nextStratum();
    }

    // ==================== 遍历范围 ====================

    /**
     * 遍历范围 — 用于区分模糊效果前后的渲染。
     * <p>对标 26.1.2 {@code GuiRenderState.TraverseRange}。</p>
     */
    public enum TraverseRange {
        /** 遍历所有 stratum */
        ALL,
        /** 只遍历模糊分界线之前的 stratum */
        BEFORE_BLUR,
        /** 只遍历模糊分界线之后的 stratum */
        AFTER_BLUR
    }

    // ==================== Node 树节点 ====================

    /**
     * Node 树节点 — 每个节点持有多种类型的渲染状态列表。
     * <p>
     * 对标 26.1.2 {@code GuiRenderState.Node}。
     * <ul>
     *   <li>{@code parent}：父节点（向下指向更底层）</li>
     *   <li>{@code up}：子节点（向上指向更上层）</li>
     *   <li>遍历顺序：parent → up（先底层后上层）</li>
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

    // ==================== 渲染状态数据类 ====================

    /**
     * 物品渲染状态 — 对标 26.1.2 {@code GuiItemRenderState}。
     * <p>
     * 记录一次 GUI 物品渲染的完整信息：物品栈、位置、边界。
     * 实现 {@link ScreenArea} 以参与自动分层判定。
     */
    public static class ItemRenderState implements ScreenArea {
        private final ItemStack stack;
        private final int x;
        private final int y;
        private final ScreenRectangle bounds;
        /**
         * 收集时的 modelview 矩阵快照 — 对标 26.1.2 {@code GuiItemRenderState} 携带的 pose matrix。
         * <p>帧末延迟渲染据此 {@code glLoadMatrix} 恢复调用点的 GL 变换；可为 null（无快照则按当前 GL 状态绘制）。</p>
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

        /** 物品模型标识 — 用于去重和追踪 */
        public Object getIdentity() {
            return stack.getItem();
        }

        @Override
        public ScreenRectangle bounds() {
            return bounds;
        }
    }

    /**
     * GUI 元素渲染状态 — 对标 26.1.2 {@code GuiElementRenderState}。
     * <p>
     * 描述一个 2D GUI 绘制操作（纹理 blit、矩形填充等）。
     * 在 1.7.10 GL 即时模式下，存储绘制参数供回放使用。
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
     * 文本渲染状态 — 对标 26.1.2 {@code GuiTextRenderState}。
     * <p>
     * 描述一次文本绘制操作。
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
            // 简化边界计算：每个字符约 6px 宽，8px 高
            // 精确计算需要 Font 对象，此处用近似值
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
