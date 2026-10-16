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
 * Tab manager — responsible only for swapping the current tab's content:
 * unloading the old tab's children, loading the new tab's children, laying out
 * within the cached area and firing the (de)selection callbacks. Counterpart of
 * the high-version {@code TabManager}.
 * </p>
 * <p>
 * Skip sound: {@code gui.button.press}, shared with {@code AbstractButton#playPressSound},
 * aligning with the high-version behaviour of playing {@code UI_BUTTON_CLICK}.
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
     *
     * @param addWidget    consumer adding a child component to the host screen
     * @param removeWidget consumer removing a child component from the host screen
     */
    public TabManager(Consumer<GuiEventListener> addWidget, Consumer<GuiEventListener> removeWidget) {
        this(addWidget, removeWidget, t -> {}, t -> {});
    }

    /**
     * Creates a manager with full callbacks.
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
     *
     * @param tab       target tab (must not be null)
     * @param playSound whether to play the switch sound
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
     */
    @Nullable
    public Tab getCurrentTab() {
        return this.currentTab;
    }
}
