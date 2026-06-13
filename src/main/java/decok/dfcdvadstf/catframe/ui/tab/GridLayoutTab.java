package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.layouts.GridLayout;
import decok.dfcdvadstf.catframe.ui.layouts.ILayout;
import net.minecraft.client.gui.GuiButton;

/**
 * <p>
 * A Tab backed by a {@link GridLayout}.<br>
 * Children are arranged in a fixed-column grid — left-to-right, then top-to-bottom —
 * and the whole grid is centred within the screen area.
 * </p>
 * <p>
 * 基于 {@link GridLayout} 的标签页。<br>
 * 子元素按固定列数从左到右、从上到下排布，整个网格在屏幕区域内居中。
 * </p>
 *
 * <p>Usage / 用法:</p>
 * <pre>{@code
 * public class MyTab extends GridLayoutTab {
 *     public MyTab() {
 *         super(42, "mymod.tab.mytab", 2);   // 2 columns
 *     }
 *
 *     @Override
 *     protected void buildLayout(GridLayout layout) {
 *         layout.add(new SomeWidget(...));
 *         layout.add(new SomeWidget(...));
 *     }
 * }
 * }</pre>
 */
public abstract class GridLayoutTab extends AbstractScreenTab {

    protected final GridLayout layout;

    // ──── Constructors / 构造器 ────

    /**
     * Create a GridLayoutTab with 2 columns (default). / 创建默认 2 列的 GridLayoutTab。
     */
    public GridLayoutTab(int tabId, String tabNameKey) {
        this(tabId, tabNameKey, 2);
    }

    /**
     * Create a GridLayoutTab with the given column count. / 创建指定列数的 GridLayoutTab。
     */
    public GridLayoutTab(int tabId, String tabNameKey, int columns) {
        super(tabId, tabNameKey);
        this.layout = new GridLayout(columns);
    }

    /**
     * Create a GridLayoutTab with fixed cell size. / 创建固定格子尺寸的 GridLayoutTab。
     */
    public GridLayoutTab(int tabId, String tabNameKey, int columns, int cellWidth, int cellHeight) {
        super(tabId, tabNameKey);
        this.layout = new GridLayout(columns, cellWidth, cellHeight);
    }

    // ──── Layout building / 布局构建 ────

    /**
     * <p>
     * Override this to populate the layout with children.<br>
     * Called once during {@link #initGui}, <em>before</em> the layout is arranged.
     * </p>
     * <p>
     * 覆写此方法向布局添加子元素。<br>
     * 在 {@link #initGui} 期间、布局排列<em>之前</em>调用一次。
     * </p>
     */
    protected abstract void buildLayout(GridLayout layout);

    // ──── Tab lifecycle / 标签页生命周期 ────

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        super.initGui(tabManager, width, height);
        layout.clear();

        // Let subclasses fill the layout / 让子类填充布局
        buildLayout(layout);

        // Centre the grid within the screen — mirroring 26.1's alignInRectangle(layout, rect, 0.5F, 0.1666F)
        // 在屏幕内居中网格 —— 对应 26.1 的 alignInRectangle(layout, rect, 0.5F, 0.1666F)
        arrangeAndCenter(width, height);

        // Walk the arranged children and register any GuiButtons with the TabManager
        // 遍历排列好的子元素，将 GuiButton 注册到 TabManager
        for (ILayout child : layout.getChildren()) {
            if (child instanceof GuiButton) {
                addButton((GuiButton) child);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Draw layout background (if any) / 绘制布局背景（如果有）
        layout.draw(mouseX, mouseY, partialTicks);
    }

    @Override
    public void actionPerformed(GuiButton button) {
        // Subclasses handle their own button logic / 子类自行处理按钮逻辑
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // no-op by default / 默认无操作
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        // no-op by default / 默认无操作
    }

    // ──── Helpers / 辅助方法 ────

    /**
     * <p>
     * Arrange the layout and centre it within the given screen dimensions.<br>
     * Horizontal alignment: centred (0.5).<br>
     * Vertical alignment: roughly one-sixth from the top (0.1666), matching 26.1.
     * </p>
     * <p>
     * 排列布局并在给定屏幕尺寸内居中。<br>
     * 水平对齐：居中 (0.5)。<br>
     * 垂直对齐：约六分之一处 (0.1666)，与 26.1 一致。
     * </p>
     */
    protected void arrangeAndCenter(int screenWidth, int screenHeight) {
        layout.recalculate();

        // Horizontal: centre  /  水平居中
        int layoutX = (screenWidth - layout.getWidth()) / 2;
        // Vertical: ~16.66 % from top — same as 26.1's 0.16666667F
        int layoutY = (int) ((screenHeight - layout.getHeight()) * 0.16666667F);

        layout.setPosition(layoutX, layoutY);
    }

    /**
     * Direct access to the underlying layout for advanced use. / 直接访问底层布局，供高级用法使用。
     */
    public GridLayout getLayout() {
        return layout;
    }
}
