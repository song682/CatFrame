package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.layouts.Layout;
import decok.dfcdvadstf.catframe.ui.layouts.ILayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * <p>
 * 可滚动布局包装器 —— 将一个现有 {@link Layout} 包装为可滚动容器。<br>
 * 对标高版本 Minecraft 的 {@code ScrollableLayout}。
 * </p>
 * <p>
 * Scrollable layout wrapper — wraps an existing {@link Layout} into a scrollable
 * container. Counterpart of the high-version Minecraft {@code ScrollableLayout}.
 * </p>
 */
public class ScrollableLayout implements Layout {

    private static final int DEFAULT_SCROLLBAR_SPACING = 4;

    private final Layout content;
    private final ScrollContainer container;
    private final int scrollbarSpacing;
    private int minWidth;
    private int minHeight;
    private int maxHeight;

    /**
     * Create a scrollable layout wrapping the given content with a maximum height.
     * <p>创建一个可滚动布局，包装给定的内容并限制最大高度。</p>
     *
     * @param content   the content layout to make scrollable / 要使其可滚动的内容布局
     * @param maxHeight maximum visible height / 最大可见高度
     */
    public ScrollableLayout(Layout content, int maxHeight) {
        this.content = content;
        this.maxHeight = maxHeight;
        this.scrollbarSpacing = DEFAULT_SCROLLBAR_SPACING;
        this.container = new ScrollContainer(0, maxHeight, ScrollbarSettings.defaultSettings(10));
    }

    // ──── Size configuration ────

    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
        container.setSize(Math.max(content.getWidth(), minWidth), container.getHeight());
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        container.setSize(container.getWidth(), Math.max(content.getHeight(), minHeight));
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        container.setSize(container.getWidth(), Math.min(content.getHeight(), maxHeight));
        container.refreshScrollAmount();
    }

    // ──── Layout implementation ────

    @Override
    public void recalculate() {
        content.arrangeElements();
        int contentWidth = content.getWidth();
        int scrollbarReserve = 2 * scrollbarReserve();
        container.setSize(Math.max(contentWidth, minWidth) + scrollbarReserve,
                Math.min(Math.max(container.getHeight(), minHeight), maxHeight));
        container.refreshScrollAmount();
    }

    @Override
    public void arrangeElements() {
        content.arrangeElements();
        recalculate();
    }

    @Override
    public Layout add(ILayout child) {
        content.add(child);
        return this;
    }

    @Override
    public Layout remove(ILayout child) {
        content.remove(child);
        return this;
    }

    @Override
    public void clear() {
        content.clear();
    }

    @Override
    public List<ILayout> getChildren() {
        return content.getChildren();
    }

    @Override
    public void visitChildren(Consumer<ILayout> visitor) {
        visitor.accept(container);
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        container.render(mouseX, mouseY, partialTicks);
    }

    // ──── ILayout: position/size delegates ────

    @Override
    public int getX() { return container.getX(); }

    @Override
    public void setX(int x) {
        container.setX(x);
        content.setX(x + scrollbarReserve());
    }

    @Override
    public int getY() { return container.getY(); }

    @Override
    public void setY(int y) {
        container.setY(y);
        content.setY(y - (int) container.scrollAmount());
    }

    @Override
    public int getWidth() { return container.getWidth(); }

    @Override
    public int getHeight() { return container.getHeight(); }

    // ──── Access ────

    public Layout getContent() {
        return content;
    }

    public ScrollContainer getContainer() {
        return container;
    }

    private int scrollbarReserve() {
        return scrollbarSpacing + container.scrollbarWidth();
    }

    // ──── Inner class: ScrollContainer ────

    /**
     * <p>
     * 内部滚动容器 —— 继承 {@link AbstractContainerWidget}，实际执行裁剪渲染。
     * </p>
     * <p>
     * Inner scroll container — extends {@link AbstractContainerWidget}, performs
     * the actual clipped rendering.
     * </p>
     */
    public class ScrollContainer extends AbstractContainerWidget {

        private final List<Component> childComponents = new ArrayList<>();

        public ScrollContainer(int width, int height, ScrollbarSettings scrollbarSettings) {
            super(0, 0, width, height, scrollbarSettings);
            collectChildren();
        }

        private void collectChildren() {
            childComponents.clear();
            content.visitWidgets(obj -> {
                if (obj instanceof Component) {
                    childComponents.add((Component) obj);
                }
            });
        }

        @Override
        protected int contentHeight() {
            return content.getHeight();
        }

        @Override
        public List<? extends Component> children() {
            return childComponents;
        }

        @Override
        public void render(int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;

            // Re-collect children in case layout changed
            collectChildren();

            enableScissor();
            for (Component child : childComponents) {
                child.render(mouseX, mouseY, partialTicks);
            }
            disableScissor();

            renderScrollbar(mouseX, mouseY);
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            content.setX(x + scrollbarReserve());
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            content.setY(y - (int) scrollAmount());
        }

        @Override
        public void setScrollAmount(double scrollAmount) {
            super.setScrollAmount(scrollAmount);
            content.setY(getY() + getHeight() - (int) this.scrollAmount());
        }
    }
}
