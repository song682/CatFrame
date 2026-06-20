package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.layouts.FrameLayout;
import decok.dfcdvadstf.catframe.ui.layouts.GridLayout;
import decok.dfcdvadstf.catframe.ui.layouts.ILayout;
import net.minecraft.client.gui.GuiButton;

/**
 * A Tab backed by a {@link GridLayout}.
 * <p>
 * Children are arranged in a grid and the whole layout is centered within
 * the screen (horizontal centre, vertical ~1/6 from top).
 * </p>
 * <p>
 * 基于 {@link GridLayout} 的标签页。
 * 子元素按网格排列，整个布局在屏幕内居中（水平居中，垂直约 1/6 处）。
 * </p>
 *
 * <p>Usage / 用法:</p>
 * <pre>{@code
 * public class MyTab extends GridLayoutTab {
 *     public MyTab() {
 *         super(42, "mymod.tab.mytab");
 *     }
 *
 *     &#064;Override
 *     public void initGui(TabManager tabManager, int width, int height) {
 *         layout.addChild(new SomeWidget(), 0, 0);
 *         layout.addChild(new SomeWidget(), 0, 1);
 *         super.initGui(tabManager, width, height);
 *     }
 * }
 * }</pre>
 */
public class GridLayoutTab extends AbstractScreenTab {

    protected final GridLayout layout = new GridLayout();

    public GridLayoutTab(int tabId, String tabNameKey) {
        super(tabId, tabNameKey);
    }

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        super.initGui(tabManager, width, height);

        // Arrange elements and centre in screen
        // 排列元素并在屏幕内居中
        layout.arrangeElements();
        FrameLayout.alignInRectangle(layout, 0, 0, width, height, 0.5F, 0.16666667F);

        // Register any GuiButton children with the TabManager
        // 将 GuiButton 子元素注册到 TabManager
        for (ILayout child : layout.getChildren()) {
            if (child instanceof GuiButton) {
                addButton((GuiButton) child);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        layout.draw(mouseX, mouseY, partialTicks);
    }

    @Override
    public void actionPerformed(GuiButton button) {
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }

    public GridLayout getLayout() {
        return layout;
    }
}
