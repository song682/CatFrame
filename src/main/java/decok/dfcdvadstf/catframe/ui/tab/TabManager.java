package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.components.Component;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * <p>
 * 标签页管理器 —— 管理 Tab 的注册、切换和生命周期。<br>
 * 同时支持 1.7.10 原版风格（基于 int ID + buttonList）和
 * 高版本风格（基于 Consumer + ScreenRectangle）。
 * </p>
 * <p>
 * Tab manager — manages tab registration, switching, and lifecycle.<br>
 * Supports both 1.7.10 style (int ID + buttonList) and
 * higher-version style (Consumer + ScreenRectangle).
 * </p>
 */
public class TabManager {
    private static final Logger LOG = LogManager.getLogger("CatFrame/Layout");
    private final Map<Integer, Tab> tabs = new HashMap<>();
    /**
     * <p>The parent screen instance.
     * TabManager only knows it as vanilla {@code GuiScreen} — zero mixin dependency.</p>
     * <p>父界面实例。
     * TabManager 只认它是原版 {@code GuiScreen}——零 Mixin 依赖。</p>
     */
    private final GuiScreen screen;
    private int currentTabId = 100;
    @Nullable
    private Tab currentTab;

    /**
     * Optional TabBar that provides background rendering and tab container. / 可选的 TabBar，提供背景绘制和 Tab 容器。
     */
    @Nullable
    private TabBar tabBar;

    // ──── New: Consumer-based widget management (high-version style) ────

    /**
     * Consumer 用于添加/移除控件，不限于 GuiButton，也可以是 GuiTextField、Component 等。
     */
    private final Consumer<Object> addWidget;
    private final Consumer<Object> removeWidget;
    /**
     * Component-specific add/remove consumers for high-version style.
     */
    private Consumer<Component> addComponent = c -> {};
    private Consumer<Component> removeComponent = c -> {};
    private Consumer<Tab> onSelected = t -> {};
    private Consumer<Tab> onDeselected = t -> {};
    @Nullable
    private ScreenRectangle tabArea;

    // ──── Constructors (backward-compatible) ────

    /**
     * Create a TabManager with a TabBar.  The barId is taken from the TabBar and
     * only entries registered to that barId will be loaded.
     * <p>通过 TabBar 创建 TabManager。barId 取自 TabBar，仅加载注册到该 barId 的条目。</p>
     */
    public TabManager(GuiScreen screen, List<GuiButton> buttonList, int width, int height, TabBar tabBar) {
        this(screen, buttonList, width, height, tabBar.getBarId());
        this.tabBar = tabBar;
    }

    /**
     * Create a TabManager without a TabBar, identified by a bare barId.
     * <p>不带 TabBar 创建 TabManager，仅通过 barId 标识。</p>
     */
    public TabManager(GuiScreen screen, List<GuiButton> buttonList, int width, int height, String barId) {
        if (barId == null || barId.isEmpty()) {
            throw new IllegalArgumentException("barId must not be null or empty");
        }
        this.screen = screen;
        this.tabBar = null;
        this.addWidget = widget -> { if (widget instanceof GuiButton) { GuiButton btn = (GuiButton) widget; if (!buttonList.contains(btn)) buttonList.add(btn); } };
        this.removeWidget = widget -> { if (widget instanceof GuiButton) { buttonList.remove(widget); } };
        initFromRegistry(barId, width, height);
    }

    // ──── Constructors (Consumer-based, high-version style) ────

    /**
     * Create a TabManager with Consumer-based widget management and a TabBar.
     * <p>使用 Consumer 控件管理方式和 TabBar 创建 TabManager。</p>
     */
    public TabManager(Consumer<Object> addWidget, Consumer<Object> removeWidget,
                      int width, int height, TabBar tabBar) {
        this(addWidget, removeWidget, width, height, tabBar.getBarId());
        this.tabBar = tabBar;
    }

    /**
     * Create a TabManager with Consumer-based widget management, identified by barId.
     * <p>使用 Consumer 控件管理方式创建 TabManager，通过 barId 标识。</p>
     */
    public TabManager(Consumer<Object> addWidget, Consumer<Object> removeWidget,
                      int width, int height, String barId) {
        if (barId == null || barId.isEmpty()) {
            throw new IllegalArgumentException("barId must not be null or empty");
        }
        this.screen = null;
        this.tabBar = null;
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
        initFromRegistry(barId, width, height);
    }

    /**
     * Create a TabManager with Consumer-based widget management, supporting Component.
     * <p>使用 Consumer 控件管理方式（支持 Component）创建 TabManager。</p>
     */
    public TabManager(Consumer<Object> addWidget, Consumer<Object> removeWidget,
                      Consumer<Component> addComponent, Consumer<Component> removeComponent,
                      int width, int height, String barId) {
        if (barId == null || barId.isEmpty()) {
            throw new IllegalArgumentException("barId must not be null or empty");
        }
        this.screen = null;
        this.tabBar = null;
        this.addWidget = addWidget;
        this.removeWidget = removeWidget;
        this.addComponent = addComponent != null ? addComponent : c -> {};
        this.removeComponent = removeComponent != null ? removeComponent : c -> {};
        initFromRegistry(barId, width, height);
    }

    /**
     * Internal: freeze the bar, load entries from registry, initialise tabs.
     * <p>内部方法：冻结对应 bar，从注册表加载条目并初始化 Tab。</p>
     */
    private void initFromRegistry(String barId, int width, int height) {
        // Freeze this bar to prevent further registration
        // 冻结该 bar 的注册表，阻止后续注册
        if (!TabRegistry.isFrozen(barId)) {
            TabRegistry.freeze(barId);
        }

        // Create all registered tabs for this bar
        // 创建此 bar 下所有已注册的标签页
        for (TabRegistry.TabEntry entry : TabRegistry.getEntries(barId)) {
            Tab tab = entry.factory.get();
            registerTab(tab);
            // Register the entry into TabBar if present (lazy instantiation)
            // 如果有 TabBar，将 entry 注册到其中（延迟实例化）
            if (tabBar != null) {
                tabBar.registerEntry(entry);
            }
        }

        // Initialize all tabs
        // 初始化所有标签页
        for (Tab tab : tabs.values()) {
            tab.initGui(this, width, height);
        }

        // Set current tab (default to first registered tab)
        // 设置当前标签页（默认为第一个注册的）
        List<Integer> sortedIds = getSortedTabIds();
        if (!sortedIds.isEmpty()) {
            Tab firstTab = tabs.get(sortedIds.get(0));
            if (firstTab != null) {
                setCurrentTab(firstTab, false);
            }
        }
    }

    // ──── Widget management ────

    /**
     * Add a button through the configured addWidget consumer.
     * <p>通过配置的 addWidget consumer 添加按钮。</p>
     */
    public void addButton(GuiButton button) {
        addWidget.accept(button);
    }

    /**
     * Remove a button through the configured removeWidget consumer.
     * <p>通过配置的 removeWidget consumer 移除按钮。</p>
     */
    public void removeButton(GuiButton button) {
        removeWidget.accept(button);
    }

    /**
     * <p>
     * 注册一个标签页<br>
     * 内部使用，外部模组应通过 {@link TabRegistry#registerTab} 注册
     * </p>
     * <p>
     * Register a tab<br>
     * For internal use; external mods should use {@link TabRegistry#registerTab}
     * </p>
     */
    public void registerTab(Tab tab) {
        tabs.put(tab.getTabId(), tab);
    }

    /**
     * <p>
     * 检查指定ID是否为标签页按钮ID<br>
     * Check if the given ID belongs to a tab button
     * </p>
     */
    public boolean isTabButton(int id) {
        return tabs.containsKey(id);
    }

    /**
     * <p>
     * 获取按顺序排列的所有标签页ID<br>
     * Get all tab IDs in sorted order
     * </p>
     */
    public List<Integer> getSortedTabIds() {
        List<Integer> ids = new ArrayList<>(tabs.keySet());
        Collections.sort(ids);
        return ids;
    }

    // ──── Tab switching ────

    /**
     * <p>
     * 高版本风格切换方法 —— 通过 Consumer 添加/移除控件，触发 doLayout 和回调。<br>
     * High-version style switch — adds/removes widgets via Consumer, triggers doLayout and callbacks.
     * </p>
     *
     * @param tab      目标标签页 / target tab
     * @param playSound 是否播放切换音效 / whether to play switch sound
     */
    public void setCurrentTab(@Nullable Tab tab, boolean playSound) {
        if (!Objects.equals(this.currentTab, tab)) {
            // Remove old tab's widgets
            // 移除旧标签页的控件
            if (this.currentTab != null) {
                this.currentTab.visitChildren(this.removeWidget);
                this.currentTab.visitComponents(this.removeComponent);
                this.currentTab.setVisible(false);
            }

            Tab oldTab = this.currentTab;
            this.currentTab = tab;
            this.currentTabId = tab != null ? tab.getTabId() : -1;

            // Add new tab's widgets
            // 添加新标签页的控件
            if (tab != null) {
                tab.visitChildren(this.addWidget);
                tab.visitComponents(this.addComponent);
                tab.setVisible(true);
                if (this.tabArea != null) {
                    tab.doLayout(this.tabArea);
                }
            }

            // Play click sound
            // 播放点击音效
            if (playSound) {
                Minecraft.getMinecraft().thePlayer.playSound("random.click", 1.0F, 1.0F);
            }

            // Fire callbacks
            // 触发回调
            this.onDeselected.accept(oldTab);
            this.onSelected.accept(this.currentTab);
        }
    }

    /**
     * <p>
     * 旧版风格切换方法 —— 通过 setVisible 控制标签页显示。<br>
     * 向后兼容，内部委托给 {@link #setCurrentTab(Tab, boolean)}。<br>
     * Legacy style switch — controls tab visibility via setVisible.<br>
     * Backward-compatible, delegates to {@link #setCurrentTab(Tab, boolean)}.
     * </p>
     */
    public void switchToTab(int tabId) {
        Tab tab = tabs.get(tabId);
        if (tab != null) {
            setCurrentTab(tab, false);
        }
    }

    // ──── Tab area management ────

    /**
     * <p>
     * 设置标签页内容区域，并在切换到现有标签页时触发 doLayout。<br>
     * 对标高版本 {@code TabManager.setTabArea(ScreenRectangle)}。<br>
     * Set the tab content area, triggering doLayout on the current tab.
     * </p>
     */
    public void setTabArea(@Nullable ScreenRectangle tabArea) {
        LOG.debug("[TabManager] setTabArea: area={}", tabArea != null
                ? String.format("(%d,%d %dx%d)", tabArea.left(), tabArea.top(), tabArea.width, tabArea.height)
                : "null");
        this.tabArea = tabArea;
        if (this.currentTab != null && tabArea != null) {
            this.currentTab.doLayout(tabArea);
        }
    }

    /**
     * <p>
     * 设置标签页选中/取消选中回调。<br>
     * Set the on-selected / on-deselected callbacks.
     * </p>
     */
    public void setTabCallbacks(Consumer<Tab> onSelected, Consumer<Tab> onDeselected) {
        this.onSelected = onSelected != null ? onSelected : t -> {};
        this.onDeselected = onDeselected != null ? onDeselected : t -> {};
    }

    /**
     * @return The TabBar associated with this manager, or {@code null}. / 与此管理器关联的 TabBar，或 {@code null}。
     */
    @Nullable
    public TabBar getTabBar() {
        return tabBar;
    }

    // ──── Event forwarding ────

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (currentTab != null) {
            currentTab.drawScreen(mouseX, mouseY, partialTicks);
        }
    }

    public void actionPerformed(GuiButton button) {
        // 首先处理标签页切换（动态判断，不限内置 ID）
        if (isTabButton(button.id)) {
            switchToTab(button.id);
            return;
        }

        // 然后传递给当前标签页处理
        if (currentTab != null) {
            currentTab.actionPerformed(button);
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (currentTab != null) {
            currentTab.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (currentTab != null) {
            currentTab.keyTyped(typedChar, keyCode);
        }
    }

    /**
     * 重新初始化标签页，在窗口大小改变时调用以保持当前选中的标签页
     *
     * @param width  新的窗口宽度
     * @param height 新的窗口高度
     */
    public void reinitializeTabs(int width, int height) {
        // 保存当前选中的标签页
        Tab savedTab = currentTab;

        // 重新初始化所有标签页
        for (Tab tab : tabs.values()) {
            tab.initGui(this, width, height);
        }

        // 恢复之前选中的标签页
        if (savedTab != null) {
            setCurrentTab(savedTab, false);
        } else {
            List<Integer> sortedIds = getSortedTabIds();
            if (!sortedIds.isEmpty()) {
                Tab firstTab = tabs.get(sortedIds.get(0));
                if (firstTab != null) {
                    setCurrentTab(firstTab, false);
                }
            }
        }
    }

    // ──── Getters ────

    public GuiScreen getScreen() {
        return screen;
    }

    /**
     * <p>
     * 获取指定ID的标签页在其排序位置中的索引<br>
     * Get the sorted index of the tab with the given ID
     * </p>
     */
    public int getTabIndex(int tabId) {
        List<Integer> sortedIds = getSortedTabIds();
        return sortedIds.indexOf(tabId);
    }

    public int getCurrentTabId() {
        return currentTabId;
    }

    /**
     * <p>
     * 高版本风格：获取当前 Tab 引用。<br>
     * High-version style: get the current Tab reference.
     * </p>
     */
    @Nullable
    public Tab getCurrentTab() {
        return currentTab;
    }

    public int getTabCount() {
        return tabs.size();
    }

    public Map<Integer, Tab> getAllTabs() {
        return tabs;
    }
}