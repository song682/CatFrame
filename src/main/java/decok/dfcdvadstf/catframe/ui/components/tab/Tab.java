package decok.dfcdvadstf.catframe.ui.components.tab;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import java.util.function.Consumer;

/**
 * <p>
 * Tab 页面契约 —— 描述一个标签页的标题、旁白文本、子组件枚举与布局。<br>
 * 对标 26.1.2 {@code net.minecraft.client.gui.components.tabs.Tab}，仅保留四个方法。
 * </p>
 * <p>
 * Tab page contract — describes a tab's title, narration text, children
 * enumeration and layout. Counterpart of the high-version {@code Tab},
 * reduced to its four methods.
 * </p>
 */
public interface Tab {

    /**
     * @return the title shown on the tab button / 页签按钮上显示的标题
     */
    Text getTabTitle();

    /**
     * @return extra narration text for accessibility / 供无障碍朗读的额外旁白文本
     */
    Text getTabExtraNarration();

    /**
     * Visit all child components of this tab — used by {@link TabManager} to
     * add / remove them when the tab becomes active / inactive.
     * <p>遍历此标签页的全部子组件 —— 供 {@link TabManager} 在激活 / 失活时
     * 装载或卸载。</p>
     *
     * @param childrenConsumer consumer applied to every child / 应用于每个子组件的消费者
     */
    void visitChildren(Consumer<GuiEventListener> childrenConsumer);

    /**
     * Lay out this tab within the given screen rectangle.
     * <p>在给定的屏幕矩形区域内执行此标签页的布局。</p>
     *
     * @param screenRectangle the tab content area / 标签页内容区域
     */
    void doLayout(ScreenRectangle screenRectangle);
}
