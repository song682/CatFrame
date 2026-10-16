package decok.dfcdvadstf.catframe.ui.components.tab;

import decok.dfcdvadstf.catframe.ui.ContentPanelRenderer;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.components.TabButton;
import decok.dfcdvadstf.catframe.ui.components.Tooltip;
import decok.dfcdvadstf.catframe.ui.components.events.AbstractContainerEventHandler;
import decok.dfcdvadstf.catframe.ui.components.events.ComponentPath;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import decok.dfcdvadstf.catframe.ui.components.events.KeyTypedEvent;
import decok.dfcdvadstf.catframe.ui.layouts.LinearLayout;
import decok.dfcdvadstf.catframe.ui.navigation.FocusNavigationEvent;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * Tab navigation bar — a row of {@link TabButton}s plus two header-separator
 * segments; owns button layout, click switching, keyboard shortcuts and focus
 * management. Counterpart of the high-version {@code TabNavigationBar}.
 * </p>
 * <p>
 * Focus means selection: the click dispatch chain through containers lands on
 * {@link #setFocused(GuiEventListener)}, and once focus reaches an active
 * button the current tab switches — matching high-version behaviour.<br>
 * Shortcuts (Ctrl required): {@code Ctrl+1..9} → tabs 1..9, {@code Ctrl+0} →
 * last tab, {@code Ctrl+Tab} / {@code Ctrl+Shift+Tab} cycle with wrap-around
 * and skip inactive tabs.
 * </p>
 */
public class TabNavigationBar extends AbstractContainerEventHandler {

    /** Sentinel: no tab resolved */
    private static final int NO_TAB = -1;
    /** Max width of the button strip */
    private static final int MAX_WIDTH = 400;
    /** Bar height */
    private static final int HEIGHT = 24;
    /** Horizontal margin on both sides */
    private static final int MARGIN = 14;

    private final LinearLayout layout = new LinearLayout(LinearLayout.Axis.HORIZONTAL, LinearLayout.Alignment.CENTER);
    private final TabManager tabManager;
    private final List<Tab> tabs;
    private final List<TabButton> tabButtons;

    private TabNavigationBar(int width, TabManager tabManager, Iterable<Tab> tabs) {
        super(0, 0, width, HEIGHT);
        this.tabManager = tabManager;

        List<Tab> tabsCopy = new ArrayList<Tab>();
        for (Tab tab : tabs) {
            tabsCopy.add(tab);
        }
        this.tabs = Collections.unmodifiableList(tabsCopy);

        this.layout.defaultChildLayoutSetting().alignHorizontallyCenter();

        List<TabButton> buttons = new ArrayList<TabButton>();
        for (Tab tab : tabsCopy) {
            buttons.add(this.layout.addChild(new TabButton(tabManager, tab, 0, HEIGHT)));
        }
        this.tabButtons = Collections.unmodifiableList(buttons);
    }

    /**
     * Creates a builder for the navigation bar.
     *
     * @param tabManager the content manager
     * @param width      initial screen width
     */
    public static Builder builder(TabManager tabManager, int width) {
        return new Builder(tabManager, width);
    }

    /**
     * Update the bar width (on screen resize) and re-arrange the buttons.
     */
    public void updateWidth(int width) {
        this.width = width;
        this.arrangeElements();
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= this.layout.getX()
                && mouseY >= this.layout.getY()
                && mouseX < this.layout.getX() + this.layout.getWidth()
                && mouseY < this.layout.getY() + this.layout.getHeight();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (this.getFocused() != null) {
            this.setFocused((GuiEventListener) null);
        }
    }

    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        super.setFocused(focused);
        if (focused instanceof TabButton) {
            TabButton button = (TabButton) focused;
            if (button.isActive()) {
                this.tabManager.setCurrentTab(button.tab(), true);
            }
        }
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
        if (!this.isFocused()) {
            TabButton button = this.currentTabButton();
            if (button != null) {
                return ComponentPath.path(this, ComponentPath.leaf(button));
            }
        }

        return navigationEvent instanceof FocusNavigationEvent.TabNavigation ? null : super.nextFocusPath(navigationEvent);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return this.tabButtons;
    }

    /**
     * @return the tabs managed by this bar, in button order
     */
    public List<Tab> getTabs() {
        return this.tabs;
    }

    /**
     * Renders the navigation bar — black tab-header background, two header
     * separator segments (on both sides of the button strip) and all the
     * buttons.<br>
     * Visibility and hover state are already handled by
     * {@link #extractRenderState} before the call.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        ContentPanelRenderer.drawTabHeaderBackground(0, this.layout.getY(), this.width, HEIGHT);

        int separatorY = this.layout.getY() + this.layout.getHeight() - 2;
        ContentPanelRenderer.drawHeaderSeparator(0, separatorY, this.tabButtons.get(0).getX());
        TabButton lastButton = this.tabButtons.get(this.tabButtons.size() - 1);
        int afterLastTab = lastButton.getX() + lastButton.getWidth();
        ContentPanelRenderer.drawHeaderSeparator(afterLastTab, separatorY, this.width - afterLastTab);

        for (TabButton button : this.tabButtons) {
            button.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public ScreenRectangle getRectangle() {
        return ScreenRectangle.of(this.layout);
    }

    /**
     * Arrange the buttons: strip width capped at {@link #MAX_WIDTH} with
     * {@link #MARGIN} on each side, buttons evenly divided and rounded toward
     * an even number, the strip horizontally centred.
     */
    public void arrangeElements() {
        int tabsWidth = Math.min(MAX_WIDTH, this.width) - MARGIN * 2;
        int tabWidth = roundToward(tabsWidth / this.tabs.size(), 2);

        for (TabButton button : this.tabButtons) {
            button.setSize(tabWidth, this.getHeight());
        }

        this.layout.arrangeElements();
        this.layout.setX(roundToward((this.width - tabsWidth) / 2, 2));
        this.layout.setY(0);
    }

    /**
     * Select the tab at the given index. When the bar already holds focus the
     * focus is moved to the button instead (focus is the single source of
     * truth); otherwise the tab is switched directly.
     */
    public void selectTab(int index, boolean playSound) {
        if (this.isFocused()) {
            this.setFocused(this.tabButtons.get(index));
        } else if (this.tabButtons.get(index).isActive()) {
            this.tabManager.setCurrentTab(this.tabs.get(index), playSound);
        }
    }

    /**
     * Set the active state of a tab button (bounds-checked).
     */
    public void setTabActiveState(int index, boolean active) {
        if (index >= 0 && index < this.tabButtons.size()) {
            this.tabButtons.get(index).setActive(active);
        }
    }

    /**
     * Set the tooltip of a tab button (bounds-checked).
     */
    public void setTabTooltip(int index, @Nullable Tooltip tooltip) {
        if (index >= 0 && index < this.tabButtons.size()) {
            this.tabButtons.get(index).setTooltip(tooltip);
        }
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (KeyTypedEvent.isControlKeyPressed()) {
            int tabIndex = this.getNextTabIndex(keyCode);
            if (tabIndex != NO_TAB) {
                this.selectTab(Math.max(0, Math.min(tabIndex, this.tabs.size() - 1)), true);
                return true;
            }
        }

        return super.keyPressed(keyCode);
    }

    private int getNextTabIndex(int keyCode) {
        return this.getNextTabIndex(this.currentTabIndex(), keyCode);
    }

    private int getNextTabIndex(int currentTab, int keyCode) {
        int digit = digitOf(keyCode);
        if (digit != NO_TAB) {
            return Math.floorMod(digit - 1, 10);
        } else if (keyCode == Keyboard.KEY_TAB && currentTab != NO_TAB) {
            int nextTabIndex = KeyTypedEvent.isShiftKeyPressed() ? currentTab - 1 : currentTab + 1;
            int index = Math.floorMod(nextTabIndex, this.tabs.size());
            return this.tabButtons.get(index).isActive() ? index : this.getNextTabIndex(index, keyCode);
        } else {
            return NO_TAB;
        }
    }

    private int currentTabIndex() {
        Tab currentTab = this.tabManager.getCurrentTab();
        return this.tabs.indexOf(currentTab);
    }

    @Nullable
    private TabButton currentTabButton() {
        int index = this.currentTabIndex();
        return index != NO_TAB ? this.tabButtons.get(index) : null;
    }

    /**
     * LWJGL digit-key mapping — counterpart of {@code KeyEvent.getDigit()}:
     * {@code KEY_1..KEY_9} → {@code 1..9}, {@code KEY_0} → {@code 0}, anything
     * else → {@link #NO_TAB}.
     */
    private static int digitOf(int keyCode) {
        if (keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9) {
            return keyCode - Keyboard.KEY_1 + 1;
        }
        if (keyCode == Keyboard.KEY_0) {
            return 0;
        }
        return NO_TAB;
    }

    /**
     * Round a value up to the nearest multiple — counterpart of
     * {@code Mth.roundToward}.
     */
    private static int roundToward(int value, int multiple) {
        return -Math.floorDiv(-value, multiple) * multiple;
    }

    /**
     * Builder for {@link TabNavigationBar}.
     */
    public static class Builder {

        private final TabManager tabManager;
        private final int width;
        private final List<Tab> tabs = new ArrayList<Tab>();

        private Builder(TabManager tabManager, int width) {
            this.tabManager = tabManager;
            this.width = width;
        }

        /**
         * Append tabs to the bar, in display order.
         */
        public Builder addTabs(Tab... tabs) {
            Collections.addAll(this.tabs, tabs);
            return this;
        }

        /**
         * @return the built navigation bar
         */
        public TabNavigationBar build() {
            return new TabNavigationBar(this.width, this.tabManager, this.tabs);
        }
    }
}
