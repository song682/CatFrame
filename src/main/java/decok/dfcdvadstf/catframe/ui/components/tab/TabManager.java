package decok.dfcdvadstf.catframe.ui.components.tab;

import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * <p>
 * 标签页管理器 —— 只负责「当前 Tab 内容的换装」：切换时卸载旧页子组件、
 * 装载新页子组件、按缓存区域布局并触发选中 / 取消选中回调。<br>
 * 对标 26.1.2 {@code net.minecraft.client.gui.components.tabs.TabManager}。
 * </p>
 * <p>
 * Tab manager — responsible only for swapping the current tab's content:
 * unloading the old tab's children, loading the new tab's children, laying out
 * within the cached area and firing the (de)selection callbacks. Counterpart of
 * the high-version {@code TabManager}.
 * </p>
 * <p>
 * 跳过音效：{@code gui.button.press}，与 {@code AbstractButton#playPressSound}
 * 同源，对齐高版本播放 {@code UI_BUTTON_CLICK} 的行为。
 * </p>
 */
public class TabManager {

    private final Consumer<GuiEventListener> addWidget;
    private final Consumer<GuiEventListener> removeWidget;
    private final Consumer<Tab> onSelected;
    private final Consumer<Tab> onDeselected;

    @Nullable
    private Tab currentTab;
    @Nullable
    private ScreenRectangle tabArea;

    /**
     * Creates a manager with no-op selection callbacks.
     * <p>创建不带选中回调的管理器。</p>
     *
     * @param addWidget    consumer adding a child component to the host screen
     *                     / 将子组件添加到宿主屏幕的消费者
     * @param removeWidget consumer removing a child component from the host screen
     *                     / 从宿主屏幕移除子组件的消费者
     */
    public TabManager(Consumer<GuiEventListener> addWidget, Consumer<GuiEventListener> removeWidget) {
        this(addWidget, removeWidget, t -> {}, t -> {});
    }

    /**
     * Creates a manager with full callbacks.
     * <p>创建带完整回调的管理器。</p>
     */
    public TabManager(Consumer<GuiEventListener> addWidget, Consumer<GuiEventListener> removeWidget,
                      Consumer<Tab> onSelected, Consumer<Tab> onDeselected) {
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
        this.onSelected = onSelected;
        this.onDeselected = onDeselected;
    }

    /**
     * Cache the tab content area and re-layout the current tab, if any.
     * <p>缓存标签页内容区域，并在存在当前 Tab 时重新布局。</p>
     */
    public void setTabArea(ScreenRectangle tabArea) {
        this.tabArea = tabArea;
        Tab tab = this.getCurrentTab();
        if (tab != null) {
            tab.doLayout(tabArea);
        }
    }

    /**
     * Switch to the given tab — swaps children, lays out and fires callbacks.
     * <p>切换到给定标签页 —— 换装子组件、执行布局并触发回调。</p>
     *
     * @param tab       target tab (must not be null) / 目标标签页（不可为 null）
     * @param playSound whether to play the switch sound / 是否播放切换音效
     */
    public void setCurrentTab(Tab tab, boolean playSound) {
        if (!Objects.equals(this.currentTab, tab)) {
            if (this.currentTab != null) {
                this.currentTab.visitChildren(this.removeWidget);
            }

            Tab oldTab = this.currentTab;
            this.currentTab = tab;
            tab.visitChildren(this.addWidget);
            if (this.tabArea != null) {
                tab.doLayout(this.tabArea);
            }

            if (playSound) {
                Minecraft.getMinecraft().getSoundHandler().playSound(
                        PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
            }

            this.onDeselected.accept(oldTab);
            this.onSelected.accept(this.currentTab);
        }
    }

    /**
     * @return the current tab, or {@code null} before the first switch
     *         / 当前标签页；首次切换前为 {@code null}
     */
    @Nullable
    public Tab getCurrentTab() {
        return this.currentTab;
    }
}
