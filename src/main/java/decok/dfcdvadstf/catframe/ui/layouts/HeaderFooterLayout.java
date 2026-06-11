package decok.dfcdvadstf.catframe.ui.layouts;

import decok.dfcdvadstf.catframe.ui.ContentPanelRenderer;

/**
 * HeaderFooterLayout — a three-zone layout with a header on top, a content
 * area in the middle and a footer on the bottom.
 * <p>
 * By default the layout draws itself using {@link ContentPanelRenderer#drawContentPanel},
 * giving you the tiled background + top/bottom separators out of the box.
 * Call {@link #setDrawPanel(boolean) setDrawPanel(false)} to skip drawing.
 * </p>
 *
 * <p>
 * HeaderFooterLayout —— 三区域布局：顶部 header、中间 content、底部 footer。
 * 默认会用 {@link ContentPanelRenderer#drawContentPanel} 绘制自身，
 * 内置平铺背景 + 顶底分隔线。调用 {@link #setDrawPanel(boolean) setDrawPanel(false)} 跳过绘制。
 * </p>
 *
 * <h3>Usage / 用法</h3>
 * <pre>{@code
 * HeaderFooterLayout layout = new HeaderFooterLayout();
 * layout.setHeader(someHeaderWidget);
 * layout.setContent(someContentWidget);
 * layout.setFooter(someFooterWidget);
 * layout.setPosition(10, 10);
 * layout.recalculate(panelWidth, panelHeight);
 * layout.draw(mouseX, mouseY, partialTicks);
 * }</pre>
 */
public class HeaderFooterLayout extends AbstractLayout {

    private boolean drawPanel = true;

    /**
     * Creates a HeaderFooterLayout that draws the panel background. / 创建绘制面板背景的 HeaderFooterLayout。
     */
    public HeaderFooterLayout() {
    }

    /**
     * Creates a HeaderFooterLayout with optional panel drawing. / 创建可选择是否绘制面板背景的 HeaderFooterLayout。
     */
    public HeaderFooterLayout(boolean drawPanel) {
        this.drawPanel = drawPanel;
    }

    // ──── Properties ────

    public boolean isDrawPanel() {
        return drawPanel;
    }

    public void setDrawPanel(boolean drawPanel) {
        this.drawPanel = drawPanel;
    }

    // ──── Zone setters / getters ────

    /**
     * Sets the header child (first slot, index 0).
     * <p>设置顶栏子元素（第一个槽位，索引 0）。</p>
     */
    public Layout setHeader(ILayout header) {
        setSlot(0, header);
        return this;
    }

    /**
     * Sets the content child (second slot, index 1).
     * <p>设置内容子元素（第二个槽位，索引 1）。</p>
     */
    public Layout setContent(ILayout content) {
        setSlot(1, content);
        return this;
    }

    /**
     * Sets the footer child (third slot, index 2).
     * <p>设置底栏子元素（第三个槽位，索引 2）。</p>
     */
    public Layout setFooter(ILayout footer) {
        setSlot(2, footer);
        return this;
    }

    /**
     * Returns the header child, or {@code null}. / 返回顶栏子元素，没有则返回 null。
     */
    public ILayout getHeader() {
        return getSlot(0);
    }

    /**
     * Returns the content child, or {@code null}. / 返回内容子元素，没有则返回 null。
     */
    public ILayout getContent() {
        return getSlot(1);
    }

    /**
     * Returns the footer child, or {@code null}. / 返回底栏子元素，没有则返回 null。
     */
    public ILayout getFooter() {
        return getSlot(2);
    }

    // ──── Recalculate ────

    /**
     * Recalculate using the current width and height.
     * <p>
     * If either dimension is ≤ 0 the layout will fall back to
     * auto-sizing based on children.
     * </p>
     * <p>使用当前 width / height 重新计算。若任一维度 ≤ 0 则按子元素自适应。</p>
     */
    @Override
    public void recalculate() {
        recalculate(width, height);
    }

    /**
     * Recalculate with an explicit size — useful when the parent container
     * knows the available space.
     * <p>使用显式尺寸重新计算 —— 当父容器知道可用空间时很有用。</p>
     *
     * @param availableWidth  available width / 可用宽度
     * @param availableHeight available height / 可用高度
     */
    public void recalculate(int availableWidth, int availableHeight) {
        this.width = Math.max(0, availableWidth);
        this.height = Math.max(0, availableHeight);

        int contentW = width - padding * 2;
        if (contentW < 0) contentW = 0;

        // ── Measure children ──
        int headerH = 0;
        int footerH = 0;
        int contentH = 0;

        ILayout header = getSlot(0);
        ILayout content = getSlot(1);
        ILayout footer = getSlot(2);

        if (header != null) headerH = header.getHeight();
        if (footer != null) footerH = footer.getHeight();
        if (content != null) contentH = content.getHeight();

        // ── Auto-size if external size wasn't provided ──
        boolean autoW = width <= 0;
        boolean autoH = height <= 0;

        if (autoW || autoH) {
            int maxW = 0;
            if (header != null) maxW = Math.max(maxW, header.getWidth());
            if (content != null) maxW = Math.max(maxW, content.getWidth());
            if (footer != null) maxW = Math.max(maxW, footer.getWidth());

            if (autoW) width = maxW + padding * 2;
            if (autoH) height = headerH + spacing + contentH + spacing + footerH + padding * 2;

            contentW = width - padding * 2;
        }

        // ── Position children ──
        int zoneY = y + padding;

        if (header != null) {
            int hdrX = x + padding + (contentW - header.getWidth()) / 2;
            header.setPosition(hdrX, zoneY);
            zoneY += header.getHeight() + spacing;
        }

        if (content != null) {
            // Content stretches to fill remaining vertical space
            int remainingH = height - padding - zoneY - (footer != null ? footer.getHeight() + spacing : 0) - padding;
            remainingH = Math.max(remainingH, content.getHeight());
            // Position the content; if it wants to fill, the caller controls its width
            int ctxX = x + padding;
            content.setPosition(ctxX, zoneY);
            zoneY += remainingH + spacing;
        }

        if (footer != null) {
            int ftrY = y + height - padding - footer.getHeight();
            int ftrX = x + padding + (contentW - footer.getWidth()) / 2;
            footer.setPosition(ftrX, ftrY);
        }
    }

    // ──── Draw ────

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!drawPanel || width <= 0 || height <= 0) return;

        int footerY = y + height - ContentPanelRenderer.SEPARATOR_HEIGHT;
        ContentPanelRenderer.drawContentPanel(x, y, width, footerY);
    }

    // ──── Internal slot helpers ────

    private void setSlot(int index, ILayout child) {
        if (child == null) return;
        while (children.size() <= index) {
            children.add(null);
        }
        children.set(index, child);
        recalculate();
    }

    private ILayout getSlot(int index) {
        if (index < 0 || index >= children.size()) return null;
        return children.get(index);
    }
}
