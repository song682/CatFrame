package decok.dfcdvadstf.catframe.ui.layouts;

import decok.dfcdvadstf.catframe.ui.ContentPanelRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/**
 * HeaderFooterLayout — a three-zone layout with a header on top, a content
 * area in the middle and a footer on the bottom.
 * <p>
 * Internally uses three {@link FrameLayout} instances (header / content / footer).
 * By default the layout draws itself using {@link ContentPanelRenderer#drawContentPanel},
 * giving you the tiled background + top/bottom separators out of the box.
 * Call {@link #setDrawPanel(boolean) setDrawPanel(false)} to skip drawing.
 * </p>
 *
 * <p>
 * HeaderFooterLayout —— 三区域布局：顶部 header、中间 content、底部 footer。
 * 内部使用三个 {@link FrameLayout} 实例。默认会用
 * {@link ContentPanelRenderer#drawContentPanel} 绘制自身，
 * 内置平铺背景 + 顶底分隔线。调用 {@link #setDrawPanel(boolean) setDrawPanel(false)} 跳过绘制。
 * </p>
 */
public class HeaderFooterLayout extends AbstractLayout {

    private static final Logger LOG = LogManager.getLogger("CatFrame/Layout");
    private static final int CONTENT_MARGIN_TOP = 30;

    private final FrameLayout headerFrame = new FrameLayout();
    private final FrameLayout footerFrame = new FrameLayout();
    private final FrameLayout contentsFrame = new FrameLayout();
    private boolean drawPanel = true;
    private int headerHeight = 0;
    private int footerHeight = 0;

    /**
     * Creates a HeaderFooterLayout that draws the panel background. / 创建绘制面板背景的 HeaderFooterLayout。
     */
    public HeaderFooterLayout() {
        this(0, 0);
    }

    /**
     * Creates a HeaderFooterLayout with custom header/footer height.
     * <p>创建自定义 header/footer 高度的 HeaderFooterLayout。</p>
     */
    public HeaderFooterLayout(int headerHeight, int footerHeight) {
        this.headerHeight = headerHeight;
        this.footerHeight = footerHeight;
        this.headerFrame.defaultChildLayoutSetting().align(0.5F, 0.5F);
        this.footerFrame.defaultChildLayoutSetting().align(0.5F, 0.5F);
    }

    /**
     * Creates a HeaderFooterLayout with optional panel drawing. / 创建可选择是否绘制面板背景的 HeaderFooterLayout。
     */
    public HeaderFooterLayout(boolean drawPanel) {
        this.drawPanel = drawPanel;
        this.headerFrame.defaultChildLayoutSetting().align(0.5F, 0.5F);
        this.footerFrame.defaultChildLayoutSetting().align(0.5F, 0.5F);
    }

    // ──── Properties ────

    public boolean isDrawPanel() {
        return drawPanel;
    }

    public void setDrawPanel(boolean drawPanel) {
        this.drawPanel = drawPanel;
    }

    public int getFooterHeight() {
        return footerHeight;
    }

    public void setFooterHeight(int footerHeight) {
        this.footerHeight = footerHeight;
    }

    public int getHeaderHeight() {
        return headerHeight;
    }

    public void setHeaderHeight(int headerHeight) {
        this.headerHeight = headerHeight;
    }

    // ──── Zone setters / getters ────

    /**
     * Sets the header child. / 设置顶栏子元素。
     */
    public <T extends ILayout> T setHeader(T header) {
        this.headerFrame.clear();
        if (header != null) {
            this.headerFrame.addChild(header);
        }
        return header;
    }

    /**
     * Sets the content child. / 设置内容子元素。
     */
    public <T extends ILayout> T setContent(T content) {
        this.contentsFrame.clear();
        if (content != null) {
            this.contentsFrame.addChild(content);
        }
        return content;
    }

    /**
     * Sets the footer child. / 设置底栏子元素。
     */
    public <T extends ILayout> T setFooter(T footer) {
        this.footerFrame.clear();
        if (footer != null) {
            this.footerFrame.addChild(footer);
        }
        return footer;
    }

    /**
     * Returns the header child, or {@code null}. / 返回顶栏子元素，没有则返回 null。
     */
    public ILayout getHeader() {
        return headerFrame.getChildren().isEmpty() ? null : headerFrame.getChildren().get(0);
    }

    /**
     * Returns the content child, or {@code null}. / 返回内容子元素，没有则返回 null。
     */
    public ILayout getContent() {
        return contentsFrame.getChildren().isEmpty() ? null : contentsFrame.getChildren().get(0);
    }

    /**
     * Returns the footer child, or {@code null}. / 返回底栏子元素，没有则返回 null。
     */
    public ILayout getFooter() {
        return footerFrame.getChildren().isEmpty() ? null : footerFrame.getChildren().get(0);
    }

    // ──── Frame access ────

    public FrameLayout getHeaderFrame() {
        return headerFrame;
    }

    public FrameLayout getContentsFrame() {
        return contentsFrame;
    }

    public FrameLayout getFooterFrame() {
        return footerFrame;
    }

    // ──── Add to zones directly ────

    public <T extends ILayout> T addToHeader(T child) {
        return this.headerFrame.addChild(child);
    }

    public <T extends ILayout> T addToHeader(T child, Consumer<LayoutSettings> configurator) {
        return this.headerFrame.addChild(child, configurator);
    }

    public <T extends ILayout> T addToContents(T child) {
        return this.contentsFrame.addChild(child);
    }

    public <T extends ILayout> T addToContents(T child, Consumer<LayoutSettings> configurator) {
        return this.contentsFrame.addChild(child, configurator);
    }

    public <T extends ILayout> T addToFooter(T child) {
        return this.footerFrame.addChild(child);
    }

    public <T extends ILayout> T addToFooter(T child, Consumer<LayoutSettings> configurator) {
        return this.footerFrame.addChild(child, configurator);
    }

    // ──── Layout interface (delegate to inner frames) ────

    @Override
    public void visitChildren(Consumer<ILayout> visitor) {
        this.headerFrame.visitChildren(visitor);
        this.contentsFrame.visitChildren(visitor);
        this.footerFrame.visitChildren(visitor);
    }

    @Override
    public void setX(int x) {
        // no-op: position is managed by recalculate
    }

    @Override
    public void setY(int y) {
        // no-op: position is managed by recalculate
    }

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public int getY() {
        return 0;
    }

    // ──── Recalculate ────

    /**
     * Recalculate using the current width and height.
     * <p>使用当前 width / height 重新计算。</p>
     */
    @Override
    public void recalculate() {
        recalculate(width, height);
    }

    /**
     * Recalculate with an explicit size — useful when the parent container
     * knows the available space.
     * <p>使用显式尺寸重新计算 —— 当父容器知道可用空间时很有用。</p>
     */
    public void recalculate(int availableWidth, int availableHeight) {
        LOG.debug("[HeaderFooterLayout] recalculate({}, {}): headerH={}, footerH={}",
                availableWidth, availableHeight, headerHeight, footerHeight);
        this.width = Math.max(0, availableWidth);
        this.height = Math.max(0, availableHeight);

        int hdrH = this.headerHeight > 0 ? this.headerHeight : getChildHeight(headerFrame);
        int ftrH = this.footerHeight > 0 ? this.footerHeight : getChildHeight(footerFrame);

        // Arrange header
        this.headerFrame.setMinDimensions(this.width, hdrH);
        this.headerFrame.setPosition(0, 0);
        this.headerFrame.recalculate();

        // Arrange footer
        this.footerFrame.setMinDimensions(this.width, ftrH);
        this.footerFrame.recalculate();
        this.footerFrame.setY(this.height - ftrH);

        // Arrange content in remaining space
        int headerBottom = hdrH;
        int footerTop = this.height - ftrH;
        int contentAreaHeight = footerTop - headerBottom;
        if (contentAreaHeight < 0) contentAreaHeight = 0;

        int preferredContentY = hdrH + CONTENT_MARGIN_TOP;
        this.contentsFrame.setMinDimensions(this.width, contentAreaHeight);
        this.contentsFrame.recalculate();
        int maxContentY = this.height - ftrH - this.contentsFrame.getHeight();
        this.contentsFrame.setPosition(0, Math.min(preferredContentY, maxContentY));
    }

    // ──── Draw ────

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!drawPanel || width <= 0 || height <= 0) return;

        int footerY = y + height - ContentPanelRenderer.SEPARATOR_HEIGHT;
        ContentPanelRenderer.drawContentPanel(x, y, width, footerY);
    }

    // ──── Internal ────

    private int getChildHeight(FrameLayout frame) {
        if (frame.getChildren().isEmpty()) return 0;
        return frame.getChildren().get(0).getHeight();
    }
}
