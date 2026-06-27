package decok.dfcdvadstf.catframe.ui.components;

import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.*;

/**
 * <p>
 * 选择列表抽象基类 —— 带条目选择的可滚动列表。<br>
 * 对标高版本 Minecraft 的 {@code AbstractSelectionList}。
 * </p>
 * <p>
 * Abstract selection list — a scrollable list with entry selection support.<br>
 * Counterpart of the high-version Minecraft {@code AbstractSelectionList}.
 * </p>
 *
 * @param <E> the entry type / 条目类型
 */
public abstract class AbstractSelectionList<E extends AbstractSelectionList.Entry<E>> extends AbstractContainerWidget {

    /** Content padding inside each entry / 每个条目的内边距 */
    public static final int CONTENT_PADDING = 2;

    protected final Minecraft minecraft;
    protected final int defaultEntryHeight;

    private final List<E> children = new TrackedList();
    protected boolean centerListVertically = true;

    @Nullable
    private E selected;
    @Nullable
    private E hovered;

    public AbstractSelectionList(int width, int height, int y, int defaultEntryHeight) {
        super(0, y, width, height, ScrollbarSettings.defaultSettings(defaultEntryHeight / 2));
        this.minecraft = Minecraft.getMinecraft();
        this.defaultEntryHeight = defaultEntryHeight;
    }

    // ──── Selection ────

    @Nullable
    public E getSelected() {
        return selected;
    }

    public void setSelected(@Nullable E selected) {
        this.selected = selected;
        if (selected != null) {
            scrollToEntry(selected);
        }
    }

    @Nullable
    public E getHovered() {
        return hovered;
    }

    // ──── Children access ────

    @Override
    public final List<E> children() {
        return Collections.unmodifiableList(children);
    }

    // ──── Entry management ────

    protected void sort(Comparator<E> comparator) {
        children.sort(comparator);
        repositionEntries();
    }

    protected void clearEntries() {
        children.clear();
        selected = null;
    }

    public void replaceEntries(Collection<E> newChildren) {
        clearEntries();
        for (E child : newChildren) {
            addEntry(child);
        }
    }

    protected int addEntry(E entry) {
        return addEntry(entry, defaultEntryHeight);
    }

    protected int addEntry(E entry, int height) {
        entry.setX(getRowLeft());
        entry.setWidth(getRowWidth());
        entry.setY(getNextY());
        entry.setHeight(height);
        children.add(entry);
        return children.size() - 1;
    }

    protected void addEntryToTop(E entry) {
        addEntryToTop(entry, defaultEntryHeight);
    }

    protected void addEntryToTop(E entry, int height) {
        double scrollFromBottom = maxScrollAmount() - scrollAmount();
        entry.setHeight(height);
        children.add(0, entry);
        repositionEntries();
        setScrollAmount(maxScrollAmount() - scrollFromBottom);
    }

    protected void removeEntry(E entry) {
        boolean removed = children.remove(entry);
        if (removed) {
            repositionEntries();
            if (entry == selected) {
                setSelected(null);
            }
        }
    }

    protected int getItemCount() {
        return children.size();
    }

    // ──── Positioning ────

    private int getFirstEntryY() {
        return getY() + 2;
    }

    public int getNextY() {
        int y = getFirstEntryY() - (int) scrollAmount();
        for (E child : children) {
            y += child.getHeight();
        }
        return y;
    }

    private void repositionEntries() {
        int y = getFirstEntryY() - (int) scrollAmount();
        for (E child : children) {
            child.setY(y);
            y += child.getHeight();
            child.setX(getRowLeft());
            child.setWidth(getRowWidth());
        }
    }

    // ──── Content height ────

    @Override
    protected int contentHeight() {
        int total = 0;
        for (E child : children) {
            total += child.getHeight();
        }
        return total + 4;
    }

    // ──── Scroll overrides ────

    @Override
    public void setScrollAmount(double scrollAmount) {
        super.setScrollAmount(scrollAmount);
        repositionEntries();
    }

    protected void scrollToEntry(E entry) {
        int topDelta = entry.getY() - getY() - 2;
        if (topDelta < 0) {
            setScrollAmount(scrollAmount() + topDelta);
        }
        int bottomDelta = getBottom() - entry.getY() - entry.getHeight() - 2;
        if (bottomDelta < 0) {
            setScrollAmount(scrollAmount() - bottomDelta);
        }
    }

    protected void centerScrollOn(E entry) {
        int y = 0;
        for (E child : children) {
            if (child == entry) {
                y += child.getHeight() / 2;
                break;
            }
            y += child.getHeight();
        }
        setScrollAmount(y - height / 2.0);
    }

    // ──── Scrollbar position ────

    @Override
    protected int scrollBarX() {
        return getRowRight() + scrollbarWidth() + 2;
    }

    // ──── Hit testing ────

    @Nullable
    protected final E getEntryAtPosition(int mx, int my) {
        for (E child : children) {
            if (child.isMouseOver(mx, my)) {
                return child;
            }
        }
        return null;
    }

    // ──── Row dimensions ────

    public int getRowLeft() {
        return getX() + width / 2 - getRowWidth() / 2;
    }

    public int getRowRight() {
        return getRowLeft() + getRowWidth();
    }

    public int getRowWidth() {
        return 220;
    }

    // ──── Selection control ────

    protected boolean entriesCanBeSelected() {
        return true;
    }

    // ──── Focus ────

    @Override
    public void setFocused(@Nullable Component focused) {
        super.setFocused(focused);
        int index = children.indexOf(focused);
        if (index >= 0) {
            E entry = children.get(index);
            setSelected(entry);
        }
    }

    // ──── Rendering ────

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        hovered = isMouseOver(mouseX, mouseY) ? getEntryAtPosition(mouseX, mouseY) : null;

        renderBackground(mouseX, mouseY, partialTicks);

        enableScissor();
        renderListItems(mouseX, mouseY, partialTicks);
        disableScissor();

        renderSeparators();
        renderScrollbar(mouseX, mouseY);
    }

    /**
     * Render the list background. Default is a dark fill.
     * <p>渲染列表背景。默认为深色填充。</p>
     */
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        GuiDrawing.drawRect(getX(), getY(), getRight(), getBottom(), 0xFF000000);
    }

    /**
     * Render visible list entries within the scissor clip.
     * <p>在裁剪区域内渲染可见的列表条目。</p>
     */
    protected void renderListItems(int mouseX, int mouseY, float partialTicks) {
        for (E child : children) {
            if (child.getY() + child.getHeight() >= getY() && child.getY() <= getBottom()) {
                renderItem(child, mouseX, mouseY, partialTicks);
            }
        }
    }

    /**
     * Render a single entry, including selection highlight.
     * <p>渲染单个条目，包括选中高亮。</p>
     */
    protected void renderItem(E entry, int mouseX, int mouseY, float partialTicks) {
        if (entriesCanBeSelected() && selected == entry) {
            int outlineColor = isFocused() ? 0xFFFFFFFF : 0xFF808000;
            renderSelection(entry, outlineColor);
        }
        boolean isHovered = Objects.equals(hovered, entry);
        entry.renderContent(mouseX, mouseY, isHovered, partialTicks);
    }

    /**
     * Render selection highlight around an entry.
     * <p>渲染条目周围的选中高亮边框。</p>
     */
    protected void renderSelection(E entry, int outlineColor) {
        int x0 = entry.getX();
        int y0 = entry.getY();
        int x1 = x0 + entry.getWidth();
        int y1 = y0 + entry.getHeight();
        // Outer outline
        GuiDrawing.drawRect(x0, y0, x1, y1, outlineColor);
        // Inner fill
        GuiDrawing.drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF000000);
    }

    /**
     * Render header/footer separators. Default is no-op.
     * <p>渲染头部/底部分隔线。默认为空操作。</p>
     */
    protected void renderSeparators() {
        // no-op by default; subclasses can override
    }

    // ──── Size update ────

    public void updateSize(int width, int height, int y) {
        updateSizeAndPosition(width, height, 0, y);
    }

    public void updateSizeAndPosition(int width, int height, int x, int y) {
        setSize(width, height);
        setPosition(x, y);
        repositionEntries();
        if (getSelected() != null) {
            scrollToEntry(getSelected());
        }
        refreshScrollAmount();
    }

    // ──── Inner class: Entry ────

    /**
     * <p>
     * 列表条目基类 —— 表示选择列表中的一个可渲染条目。<br>
     * Base class for list entries — a renderable item in a selection list.
     * </p>
     *
     * @param <E> the entry type / 条目类型
     */
    public abstract static class Entry<E extends Entry<E>> implements Component {

        private int x = 0;
        private int y = 0;
        private int width = 0;
        private int height;
        private boolean visible = true;
        private boolean active = true;

        // ──── Component: position/size ────

        @Override
        public int getX() { return x; }

        @Override
        public void setX(int x) { this.x = x; }

        @Override
        public int getY() { return y; }

        @Override
        public void setY(int y) { this.y = y; }

        @Override
        public int getWidth() { return width; }

        public void setWidth(int width) { this.width = width; }

        @Override
        public int getHeight() { return height; }

        public void setHeight(int height) { this.height = height; }

        @Override
        public void setSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        // ──── Component: visibility/active ────

        @Override
        public boolean isVisible() { return visible; }

        @Override
        public void setVisible(boolean visible) { this.visible = visible; }

        @Override
        public boolean isActive() { return active; }

        @Override
        public void setActive(boolean active) { this.active = active; }

        // ──── Component: events (defaults) ────

        @Override
        public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        }

        @Override
        public void keyTyped(char typedChar, int keyCode) {
        }

        // ──── Focus ────

        public void setFocused(boolean focused) {
        }

        public boolean isFocused() {
            return false;
        }

        // ──── Content area (with padding) ────

        public int getContentX() { return getX() + CONTENT_PADDING; }

        public int getContentY() { return getY() + CONTENT_PADDING; }

        public int getContentHeight() { return getHeight() - CONTENT_PADDING * 2; }

        public int getContentYMiddle() { return getContentY() + getContentHeight() / 2; }

        public int getContentBottom() { return getContentY() + getContentHeight(); }

        public int getContentWidth() { return getWidth() - CONTENT_PADDING * 2; }

        public int getContentXMiddle() { return getContentX() + getContentWidth() / 2; }

        public int getContentRight() { return getContentX() + getContentWidth(); }

        // ──── Rendering ────

        @Override
        public void render(int mouseX, int mouseY, float partialTicks) {
        }

        /**
         * Render this entry's content. Subclasses must implement this.
         * <p>渲染此条目的内容。子类必须实现。</p>
         *
         * @param mouseX       mouse X / 鼠标 X
         * @param mouseY       mouse Y / 鼠标 Y
         * @param hovered      whether this entry is hovered / 是否被悬停
         * @param partialTicks partial ticks / 部分 tick
         */
        public abstract void renderContent(int mouseX, int mouseY, boolean hovered, float partialTicks);
    }

    // ──── Inner class: TrackedList ────

    private class TrackedList extends java.util.AbstractList<E> {
        private final List<E> delegate = Lists.newArrayList();

        @Override
        public E get(int index) {
            return delegate.get(index);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public E set(int index, E element) {
            E old = delegate.set(index, element);
            bindEntryToSelf(element);
            return old;
        }

        @Override
        public void add(int index, E element) {
            delegate.add(index, element);
            bindEntryToSelf(element);
        }

        @Override
        public E remove(int index) {
            return delegate.remove(index);
        }
    }

    private void bindEntryToSelf(Entry<E> entry) {
        // Entry may need a reference back to the list; currently no-op
        // but kept for subclass extensibility
    }
}
