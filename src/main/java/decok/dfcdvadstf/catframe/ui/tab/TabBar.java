package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.ContentPanelRenderer;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Tab 容器条 —— 抽象基类。<br>
 * 子类必须提供 {@code barId}，并可自定义背景（纯色填充 + 可选贴图平铺，
 * 默认纯黑填充）。<br>
 * 外部模组在 {@code preInit} 中通过 {@link TabRegistry#registerTab} 注册
 * Tab 后，在 GUI 初始化时将注册的 Tab 装载到此 Bar 中。
 * </p>
 * <p>
 * Tab container bar — abstract base class.<br>
 * Subclasses must supply a {@code barId} and may customise the background
 * (solid colour fill + optional tiled texture; defaults to solid black).<br>
 * External mods register their tabs via {@link TabRegistry#registerTab} during
 * {@code preInit}, then the tabs are loaded into this Bar at GUI initialisation.
 * </p>
 *
 * <p>
 * 此类还提供导航栏功能（按钮布局、绘制、输入处理），可直接在 GUI 屏幕中使用。<br>
 * This class also provides navigation bar features (button layout, rendering, input handling)
 * that can be used directly in GUI screens.
 * </p>
 */
public abstract class TabBar {

    // ──── Constants ────

    /**
     * Default tab button texture path. / 默认 Tab 按钮纹理路径。
     * @deprecated Use {@link Tab#DEFAULT_TAB_TEXTURE} instead.
     */
    @Deprecated
    public static final ResourceLocation DEFAULT_TAB_TEXTURE = Tab.DEFAULT_TAB_TEXTURE;

    /**
     * Default tile size for background texture. / 背景贴图默认平铺块大小。
     */
    protected static final int DEFAULT_TILE_SIZE = 16;

    /** Navigation bar height / 导航栏高度 */
    public static final int NAV_HEIGHT = 24;

    /** Maximum total width of all tab buttons / Tab 按钮最大总宽度 */
    public static final int MAX_TABS_WIDTH = 400;

    /** Margin on each side of the tab button row / Tab 按钮行两侧边距 */
    public static final int MARGIN = 14;

    private static final int NO_TAB = -1;
    private static final int TEXTURE_Y_NORMAL = 0;
    private static final int TEXTURE_Y_SELECTED = 48;

    // ──── Tab entry/instance management ────

    /**
     * Entries registered to this bar (from TabRegistry), keyed by tab ID. / 注册到此 Bar 的 TabEntry（来自 TabRegistry），以 tab ID 为键。
     */
    protected final Map<Integer, TabRegistry.TabEntry> entries = new LinkedHashMap<>();
    /**
     * Tabs instantiated from entries, keyed by tab ID. / 从 entries 实例化出的 Tab，以 tab ID 为键。
     */
    protected final Map<Integer, Tab> tabs = new LinkedHashMap<>();
    private final String barId;

    // ──── Background configuration ────

    /**
     * Solid background colour (ARGB).  Default: opaque black. / 纯色背景（ARGB）。默认：不透明黑色。
     */
    protected int backgroundColor = 0xFF000000;
    /**
     * Optional tiled background texture.  {@code null} = no texture. / 可选平铺背景贴图。{@code null} = 无贴图。
     */
    protected ResourceLocation backgroundTexture;
    /**
     * Texture used for tab buttons in this bar. / 此 Bar 的 Tab 按钮使用的纹理。
     */
    protected ResourceLocation tabTexture = Tab.DEFAULT_TAB_TEXTURE;

    // ──── Navigation layout cache ────

    private int navWidth;
    private int navX;
    private int[] buttonX = new int[0];
    private int buttonWidth;

    // ==================== Constructor ====================

    /**
     * @param barId Unique identifier for this bar. / 此 Bar 的唯一标识符。
     */
    public TabBar(String barId) {
        if (barId == null || barId.isEmpty()) {
            throw new IllegalArgumentException("barId must not be null or empty");
        }
        this.barId = barId;
    }

    // ==================== ID ====================

    /**
     * @return The unique bar identifier. / 此 Bar 的唯一标识符。
     */
    public String getBarId() {
        return barId;
    }

    // ==================== Background configuration ====================

    /**
     * @return Current solid background colour. / 当前纯色背景色。
     */
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * @param color Solid background colour in ARGB format. / ARGB 格式的纯色背景。
     */
    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
    }

    /**
     * @return The background texture, or {@code null} if none. / 背景贴图，无则为 {@code null}。
     */
    public ResourceLocation getBackgroundTexture() {
        return backgroundTexture;
    }

    /**
     * Set an optional tiled background texture.  Pass {@code null} to remove it.
     * <p>设置可选的平铺背景贴图。传入 {@code null} 移除贴图。</p>
     */
    public void setBackgroundTexture(ResourceLocation texture) {
        this.backgroundTexture = texture;
    }

    // ==================== Tab texture ====================

    /**
     * @return Texture used when rendering tab buttons.
     */
    public ResourceLocation getTabTexture() {
        return tabTexture;
    }

    /**
     * @param texture Texture used when rendering tab buttons for this bar.
     *                Pass {@code null} to reset to default.
     *                <p>传入 {@code null} 恢复默认纹理。</p>
     */
    public void setTabTexture(ResourceLocation texture) {
        this.tabTexture = texture != null ? texture : DEFAULT_TAB_TEXTURE;
    }

    // ==================== Tab entry management (from TabRegistry) ====================

    /**
     * Register a {@link TabRegistry.TabEntry} into this bar.
     * The Tab will be instantiated lazily via {@link #getOrCreateTab(int)}.
     * <p>向此 Bar 注册一个 {@link TabRegistry.TabEntry}。Tab 将通过 {@link #getOrCreateTab(int)} 延迟实例化。</p>
     */
    public void registerEntry(TabRegistry.TabEntry entry) {
        if (entry == null) return;
        entries.put(entry.tabId, entry);
    }

    /**
     * @return All registered entries in this bar. / 此 Bar 中所有已注册的条目。
     */
    public Collection<TabRegistry.TabEntry> getAllEntries() {
        return entries.values();
    }

    /**
     * @return The entry for the given tab ID, or {@code null}.
     */
    public TabRegistry.TabEntry getEntry(int tabId) {
        return entries.get(tabId);
    }

    // ==================== Tab instance management ====================

    /**
     * Register a tab instance directly into this bar.
     * <p>直接向此 Bar 注册一个 Tab 实例。</p>
     */
    public void registerTab(Tab tab) {
        if (tab == null) return;
        tabs.put(tab.getTabId(), tab);
    }

    /**
     * Get an existing tab instance, or create one from its registered entry.
     * <p>获取已有 Tab 实例，若不存在则从注册的 entry 创建。</p>
     *
     * @return The tab instance, or {@code null} if no entry is registered for this ID.
     */
    public Tab getOrCreateTab(int tabId) {
        Tab tab = tabs.get(tabId);
        if (tab == null) {
            TabRegistry.TabEntry entry = entries.get(tabId);
            if (entry != null) {
                tab = entry.factory.get();
                tabs.put(tabId, tab);
            }
        }
        return tab;
    }

    /**
     * @return A tab by its ID, or {@code null} if not found / not yet created.
     */
    public Tab getTab(int tabId) {
        return tabs.get(tabId);
    }

    /**
     * @return All tab instances in this bar (ordered by insertion).
     */
    public Collection<Tab> getAllTabs() {
        return tabs.values();
    }

    /**
     * @return Number of tab instances in this bar.
     */
    public int getTabCount() {
        return tabs.size();
    }

    /**
     * @return Whether this bar contains a tab with the given ID.
     */
    public boolean containsTab(int tabId) {
        return tabs.containsKey(tabId);
    }

    // ==================== Background rendering ====================

    /**
     * Draw the bar's background — solid colour fill first, then optional tiled texture on top.
     * <p>绘制 Bar 的背景：先绘制纯色填充，再在其上绘制可选平铺贴图。</p>
     *
     * @param x      Left edge X / 左边缘 X
     * @param y      Top edge Y / 上边缘 Y
     * @param width  Width in GUI pixels / 宽度（GUI 像素）
     * @param height Height in GUI pixels / 高度（GUI 像素）
     */
    public void drawBackground(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;

        // 1. Solid colour fill / 纯色填充
        Gui.drawRect(x, y, x + width, y + height, backgroundColor);

        // 2. Optional tiled texture / 可选平铺贴图
        if (backgroundTexture != null) {
            drawTiledBackground(x, y, width, height);
        }
    }

    /**
     * Subclasses may override this to customise how the tiled texture is rendered.
     * <p>子类可重写此方法以自定义平铺贴图的渲染方式。</p>
     * <p>Delegates to {@link TextureStretching#drawTiled}.</p>
     */
    protected void drawTiledBackground(int x, int y, int width, int height) {
        TextureStretching.drawTiled(backgroundTexture, x, y, width, height,
                DEFAULT_TILE_SIZE, DEFAULT_TILE_SIZE);
    }

    // ==================== Navigation — Layout ====================

    /**
     * <p>
     * 更新导航栏宽度并重新排列元素。<br>
     * Update the nav bar width and re-arrange elements.
     * </p>
     */
    public void setNavWidth(int width) {
        this.navWidth = width;
        arrangeNavElements();
    }

    /**
     * <p>
     * 排列所有 Tab 按钮的位置和大小。<br>
     * Arrange all tab button positions and sizes.
     * </p>
     */
    public void arrangeNavElements() {
        List<Tab> tabList = getOrderedTabList();
        int tabCount = Math.max(1, tabList.size());
        int tabsWidth = Math.min(MAX_TABS_WIDTH, this.navWidth) - 2 * MARGIN;
        int tabWidth = (tabsWidth / tabCount + 1) & ~1; // Round up to nearest even
        this.buttonWidth = Math.max(2, tabWidth);

        int barX = (this.navWidth - tabsWidth) / 2;
        this.navX = (barX + 1) & ~1; // Round to nearest even

        this.buttonX = new int[tabList.size()];
        int currentX = this.navX;
        for (int i = 0; i < tabList.size(); i++) {
            this.buttonX[i] = currentX;
            currentX += this.buttonWidth;
        }
    }

    /**
     * <p>
     * 从 {@link #tabs} map 获取有序的 Tab 列表（基于 LinkedHashMap 的插入顺序）。<br>
     * Get an ordered Tab list from {@link #tabs} map (based on LinkedHashMap insertion order).
     * </p>
     */
    private List<Tab> getOrderedTabList() {
        return new ArrayList<>(tabs.values());
    }

    // ==================== Navigation — Rendering ====================

    /**
     * <p>
     * 绘制导航栏 —— 分隔线和所有 Tab 按钮。<br>
     * Draw the navigation bar — separators and all tab buttons.
     * </p>
     *
     * @param mouseX       鼠标 X / mouse X
     * @param mouseY       鼠标 Y / mouse Y
     * @param partialTicks 部分 tick / partial tick
     * @param tabManager   标签页管理器（用于判断当前选中 Tab）/ tab manager (to determine the selected tab)
     */
    public void drawNavButtons(int mouseX, int mouseY, float partialTicks, TabManager tabManager) {
        List<Tab> tabList = getOrderedTabList();
        if (tabList.isEmpty()) return;

        int barBottom = NAV_HEIGHT - 2;
        int firstX = this.buttonX.length > 0 ? this.buttonX[0] : 0;
        int lastX = this.buttonX.length > 0
                ? this.buttonX[tabList.size() - 1] + this.buttonWidth
                : 0;

        // Draw header separator before the first tab
        // 在第一个 Tab 之前绘制分隔线
        ContentPanelRenderer.drawHeaderSeparator(0, barBottom, firstX);

        // Draw header separator after the last tab
        // 在最后一个 Tab 之后绘制分隔线
        ContentPanelRenderer.drawHeaderSeparator(lastX, barBottom, this.navWidth - lastX);

        // Draw each tab button
        // 绘制每个 Tab 按钮
        for (int i = 0; i < tabList.size(); i++) {
            drawSingleTabButton(i, tabList.get(i), mouseX, mouseY, tabManager);
        }
    }

    /**
     * <p>
     * 绘制单个 Tab 按钮。<br>
     * Draw a single tab button.
     * </p>
     */
    private void drawSingleTabButton(int index, Tab tab, int mouseX, int mouseY, TabManager tabManager) {
        if (index < 0 || index >= this.buttonX.length) return;
        int btnX = this.buttonX[index];
        int btnY = 0;
        boolean selected = tabManager != null && tabManager.getCurrentTab() == tab;
        boolean hovered = mouseX >= btnX && mouseX < btnX + this.buttonWidth
                && mouseY >= btnY && mouseY < btnY + NAV_HEIGHT;

        int textureV = selected ? TEXTURE_Y_SELECTED : TEXTURE_Y_NORMAL;

        // Bind tab texture
        // 绑定 Tab 纹理
        Minecraft.getMinecraft().getTextureManager().bindTexture(this.tabTexture);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw button background using Tessellator
        // 使用 Tessellator 绘制按钮背景
        // Note: uses atlas-based UV coordinates; for simpler cases use TextureStretching.drawFixedEndRepeat
        int edgeWidth = 4;
        int centerWidth = this.buttonWidth - 2 * edgeWidth;
        int texWidth = 256;
        int texHeight = 256;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        // Left edge
        drawTexturedRectHelper(tessellator, btnX, btnY, 0, textureV, edgeWidth, NAV_HEIGHT, texWidth, texHeight);
        // Center (tiled)
        if (centerWidth > 0) {
            drawTexturedRectHelper(tessellator, btnX + edgeWidth, btnY, edgeWidth, textureV, centerWidth, NAV_HEIGHT, texWidth, texHeight);
        }
        // Right edge
        drawTexturedRectHelper(tessellator, btnX + edgeWidth + centerWidth, btnY, texWidth - edgeWidth, textureV, edgeWidth, NAV_HEIGHT, texWidth, texHeight);

        tessellator.draw();

        // Draw tab title text
        // 绘制 Tab 标题文本
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        Text title = tab.getTabTitle();
        String titleStr = title != null ? title.getString() : tab.getTabName();
        int textColor = selected ? 0xFFFFFF : (hovered ? 0xFFFF55 : 0xA0A0A0);
        int textWidth = font.getStringWidth(titleStr);
        int textX = btnX + (this.buttonWidth - textWidth) / 2;
        int textY = btnY + (NAV_HEIGHT - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(titleStr, textX, textY, textColor);
    }

    /**
     * Draw a textured rectangle using Tessellator. / 使用 Tessellator 绘制纹理矩形。
     */
    private static void drawTexturedRectHelper(Tessellator t, int x, int y, float u, float v, int w, int h, int tw, int th) {
        float u1 = u / tw;
        float u2 = (u + w) / tw;
        float v1 = v / th;
        float v2 = (v + h) / th;
        t.addVertexWithUV(x, y + h, 0.0D, u1, v2);
        t.addVertexWithUV(x + w, y + h, 0.0D, u2, v2);
        t.addVertexWithUV(x + w, y, 0.0D, u2, v1);
        t.addVertexWithUV(x, y, 0.0D, u1, v1);
    }

    // ==================== Navigation — Input handling ====================

    /**
     * <p>
     * 处理鼠标点击事件。<br>
     * Handle mouse click events.
     * </p>
     *
     * @return true 如果点击到了某个 Tab 按钮 / true if a tab button was clicked
     */
    public boolean mouseClickedNav(int mouseX, int mouseY, int mouseButton, TabManager tabManager) {
        if (mouseButton != 0) return false;
        if (mouseY < 0 || mouseY >= NAV_HEIGHT) return false;
        if (tabManager == null) return false;

        List<Tab> tabList = getOrderedTabList();
        for (int i = 0; i < tabList.size(); i++) {
            if (i >= this.buttonX.length) break;
            if (mouseX >= this.buttonX[i]
                    && mouseX < this.buttonX[i] + this.buttonWidth) {
                tabManager.setCurrentTab(tabList.get(i), true);
                return true;
            }
        }

        return false;
    }

    /**
     * <p>
     * 处理键盘事件 —— Ctrl+Tab/Ctrl+Shift+Tab 循环切换，Ctrl+数字直接跳转。<br>
     * Handle keyboard events — Ctrl+Tab/Ctrl+Shift+Tab cycling, Ctrl+digit direct jump.
     * </p>
     *
     * @return true 如果按键被处理 / true if the key was handled
     */
    public boolean keyPressedNav(int keyCode, boolean ctrlDown, boolean shiftDown, TabManager tabManager) {
        if (!ctrlDown || tabManager == null) return false;

        List<Tab> tabList = getOrderedTabList();
        if (tabList.isEmpty()) return false;

        Tab current = tabManager.getCurrentTab();
        int currentIndex = current != null ? tabList.indexOf(current) : NO_TAB;

        if (keyCode == 15) { // Tab key
            int step = shiftDown ? -1 : 1;
            int nextIndex = currentIndex != NO_TAB
                    ? Math.floorMod(currentIndex + step, tabList.size())
                    : 0;
            tabManager.setCurrentTab(tabList.get(nextIndex), true);
            return true;
        }

        // Ctrl+1 through Ctrl+9, Ctrl+0
        if (keyCode >= 2 && keyCode <= 11) {
            int digitIndex = (keyCode == 2) ? 0 : keyCode - 3;
            if (digitIndex >= 0 && digitIndex < tabList.size()) {
                tabManager.setCurrentTab(tabList.get(digitIndex), true);
                return true;
            }
        }

        return false;
    }

    // ==================== Navigation — Getters ====================

    /**
     * @return The Y position of the nav bar bottom. / 导航栏底部 Y 坐标。
     */
    public int getNavBottom() {
        return NAV_HEIGHT;
    }

    /**
     * @return The button width currently in use. / 当前使用的按钮宽度。
     */
    public int getNavButtonWidth() {
        return buttonWidth;
    }

    // ==================== Builder ====================

    /**
     * <p>
     * 使用 TabManager 和屏幕宽度创建 Builder，用于快速配置导航栏。<br>
     * Create a Builder with the given TabManager and screen width for quick nav setup.
     * </p>
     *
     * <p>Usage / 用法:</p>
     * <pre>{@code
     * TabBar.builder(tabManager, width)
     *     .addAllFromManager()
     *     .build(tabBar);
     *
     * // Then in drawScreen:
     * tabBar.drawNavButtons(mouseX, mouseY, partialTicks, tabManager);
     *
     * // In mouseClicked:
     * tabBar.mouseClickedNav(mouseX, mouseY, mouseButton, tabManager);
     * }</pre>
     */
    public static Builder builder(TabManager tabManager, int width) {
        return new Builder(tabManager, width);
    }

    /**
     * <p>
     * TabBar 构建器 —— 用于选择要显示的 Tab 并初始化导航布局。<br>
     * TabBar Builder — selects which tabs to show and initialises the navigation layout.
     * </p>
     */
    public static final class Builder {
        private final TabManager tabManager;
        private final int width;
        private final List<Tab> tabList = new ArrayList<>();

        private Builder(TabManager tabManager, int width) {
            this.tabManager = tabManager;
            this.width = width;
        }

        /**
         * Add one or more tabs to the navigation bar.
         * <p>向导航栏添加一个或多个 Tab。</p>
         */
        public Builder addTabs(Tab... tabs) {
            Collections.addAll(this.tabList, tabs);
            return this;
        }

        /**
         * Add all tabs from the TabManager to the navigation bar.
         * <p>从 TabManager 添加所有 Tab 到导航栏。</p>
         */
        public Builder addAllFromManager() {
            this.tabList.addAll(this.tabManager.getAllTabs().values());
            return this;
        }

        /**
         * Apply the builder configuration to the given TabBar.
         * <p>将构建器配置应用到指定的 TabBar。</p>
         */
        public void build(TabBar tabBar) {
            for (Tab tab : tabList) {
                tabBar.registerTab(tab);
            }
            tabBar.navWidth = width;
            tabBar.arrangeNavElements();
        }
    }
}
