package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.Component;
import decok.dfcdvadstf.catframe.ui.layouts.FrameLayout;
import decok.dfcdvadstf.catframe.ui.layouts.GridLayout;
import decok.dfcdvadstf.catframe.ui.layouts.ILayout;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.client.gui.GuiButton;

import java.util.function.Consumer;

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

    /**
     * Vertical alignment within the tab content area (0.0 = top, 1.0 = bottom).
     * Subclasses can override this to adjust vertical positioning.
     * <p>在 Tab 内容区域内的垂直对齐系数。子类可覆写以调整垂直位置。</p>
     */
    protected float verticalAlignment = 0.16666667F;

    public GridLayoutTab(int tabId, String tabNameKey) {
        super(tabId, tabNameKey);
    }

    /**
     * 使用显式 {@link Text} 标题创建标签页。<br>
     * 推荐使用此构造器以获得正确的延迟翻译行为。<br>
     * Create a tab with an explicit {@link Text} title.<br>
     * This constructor is recommended for correct lazy translation behaviour.
     * </p>
     *
     * <pre>{@code
     *   // CatFrame domain:key format (lazy translation via LocalizationManager)
     *   super(100, Text.translatable("createworldui", "tab.game"));
     *
     *   // Literal fallback
     *   super(100, Text.literal("My Tab"));
     * }</pre>
     */
    public GridLayoutTab(int tabId, Text tabTitle) {
        super(tabId, tabTitle);
    }

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        super.initGui(tabManager, width, height);

        // Register all children with the Tab (GuiButton → buttonList, Component → component list, others → widget list)
        // 将所有子元素注册到 Tab（GuiButton 进 buttonList，Component 进 component list，其他进 widget list）
        for (ILayout child : layout.getChildren()) {
            if (child instanceof GuiButton) {
                addButton((GuiButton) child);
            } else if (child instanceof Component) {
                addComponent((Component) child);
            } else {
                addWidget(child);
            }
        }
    }

    @Override
    public void doLayout(ScreenRectangle rectangle) {
        // Arrange elements and align within the tab content area.
        // Called by TabManager when setTabArea() is invoked.
        // This overrides any positioning done in initGui.
        // 排列元素并在 Tab 内容区域内对齐。
        // 由 TabManager 在 setTabArea() 时调用。
        // 此调用会覆盖 initGui 中的降级定位。
        layout.arrangeElements();
        FrameLayout.alignInRectangle(layout, rectangle.x, rectangle.y,
            rectangle.width, rectangle.height, 0.5F, verticalAlignment);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        layout.draw(mouseX, mouseY, partialTicks);
        // Auto-render all Component children
        for (Component component : tabComponents) {
            component.render(mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public void visitComponents(Consumer<Component> visitor) {
        // Visit all Component children registered via addComponent
        super.visitComponents(visitor);
    }

    @Override
    public void actionPerformed(GuiButton button) {
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Forward mouse click to all registered Components (CyclingArea, Button, EditBox, etc.)
        // 将鼠标点击转发到所有已注册的 Component（CyclingArea、Button、EditBox 等）
        for (Component component : tabComponents) {
            component.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        // Forward key input to all registered Components (EditBox, etc.)
        // 将按键输入转发到所有已注册的 Component（EditBox 等）
        for (Component component : tabComponents) {
            component.keyTyped(typedChar, keyCode);
        }
    }

    public GridLayout getLayout() {
        return layout;
    }
}
