package decok.dfcdvadstf.catframe.ui.tab;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TabManager {
    private final Map<Integer, Tab> tabs = new HashMap<>();
    private final List<GuiButton> buttonList;
    /**
     * <p>The parent screen instance (the actual {@code GuiCreateWorld}).
     * TabManager only knows it as vanilla {@code GuiScreen} — zero mixin dependency.</p>
     * <p>父界面实例（实际是 {@code GuiCreateWorld}）。
     * TabManager 只认它是原版 {@code GuiScreen}——零 mixin 依赖。</p>
     */
    private final GuiScreen screen;
    private int currentTabId = 100;
    private Tab currentTab;

    public TabManager(GuiScreen screen, List<GuiButton> buttonList, int width, int height) {
        this.buttonList = buttonList;
        this.screen = screen;

        // Freeze registry to prevent further registration
        // 冻结注册表，阻止后续注册
        if (!TabRegistry.isFrozen()) {
            TabRegistry.freeze();
        }

        // Create all registered tabs
        // 创建所有已注册的标签页
        for (TabRegistry.TabEntry entry : TabRegistry.getEntries()) {
            Tab tab = entry.factory.get();
            registerTab(tab);
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
            currentTabId = sortedIds.get(0);
        }
        switchToTab(currentTabId);
    }

    // Add a button to the Mixin's button list
    // 添加按钮到 Mixin 的按钮列表
    public void addButton(GuiButton button) {
        if (!buttonList.contains(button)) {
            buttonList.add(button);
        }
    }

    /**
     * <p>
     *     注册一个标签页<br>
     *     内部使用，外部模组应通过 {@link TabRegistry#registerTab} 注册
     * </p>
     * <p>
     *     Register a tab<br>
     *     For internal use; external mods should use {@link TabRegistry#registerTab}
     * </p>
     */
    public void registerTab(Tab tab) {
        tabs.put(tab.getTabId(), tab);
    }

    /**
     * <p>
     *     检查指定ID是否为标签页按钮ID<br>
     *     Check if the given ID belongs to a tab button
     * </p>
     */
    public boolean isTabButton(int id) {
        return tabs.containsKey(id);
    }

    /**
     * <p>
     *     获取按顺序排列的所有标签页ID<br>
     *     Get all tab IDs in sorted order
     * </p>
     */
    public List<Integer> getSortedTabIds() {
        List<Integer> ids = new ArrayList<>(tabs.keySet());
        Collections.sort(ids);
        return ids;
    }

    public void switchToTab(int tabId) {
        // 隐藏当前标签页
        if (currentTab != null) {
            currentTab.setVisible(false);
        }

        // 显示新标签页
        currentTabId = tabId;
        currentTab = tabs.get(tabId);
        if (currentTab != null) {
            currentTab.setVisible(true);
        }
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (currentTab != null) {
            currentTab.drawScreen(mouseX, mouseY, partialTicks);
        }
    }

    public void actionPerformed(GuiButton button) {
        // 首先处理标签页切换
        if (button.id >= 100 && button.id <= 102) {
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
     * @param width 新的窗口宽度
     * @param height 新的窗口高度
     */
    public void reinitializeTabs(int width, int height) {
        // 保存当前选中的标签页ID
        int savedTabId = currentTabId;

        // 重新初始化所有标签页
        for (Tab tab : tabs.values()) {
            tab.initGui(this, width, height);
        }

        // 恢复之前选中的标签页
        switchToTab(savedTabId);
    }

    public GuiScreen getScreen() { return screen; }

    /**
     * <p>
     *     获取指定ID的标签页在其排序位置中的索引<br>
     *     Get the sorted index of the tab with the given ID
     * </p>
     */
    public int getTabIndex(int tabId) {
        List<Integer> sortedIds = getSortedTabIds();
        return sortedIds.indexOf(tabId);
    }

    public int getCurrentTabId() { return currentTabId; }
    public int getTabCount() { return tabs.size(); }
    public Map<Integer, Tab> getAllTabs() { return tabs; }
}