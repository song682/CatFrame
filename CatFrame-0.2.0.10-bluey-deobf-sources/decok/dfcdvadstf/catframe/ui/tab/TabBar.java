package decok.dfcdvadstf.catframe.ui.tab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.LinkedHashMap;
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
 */
public abstract class TabBar {

    /**
     * Default tab button texture path. / 默认 Tab 按钮纹理路径。
     */
    public static final ResourceLocation DEFAULT_TAB_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/tabs.png");

    /**
     * Default tile size for background texture. / 背景贴图默认平铺块大小。
     */
    protected static final int DEFAULT_TILE_SIZE = 16;
    /**
     * Entries registered to this bar (from TabRegistry), keyed by tab ID. / 注册到此 Bar 的 TabEntry（来自 TabRegistry），以 tab ID 为键。
     */
    protected final Map<Integer, TabRegistry.TabEntry> entries = new LinkedHashMap<>();
    /**
     * Tabs instantiated from entries, keyed by tab ID. / 从 entries 实例化出的 Tab，以 tab ID 为键。
     */
    protected final Map<Integer, Tab> tabs = new LinkedHashMap<>();
    private final String barId;
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
    protected ResourceLocation tabTexture = DEFAULT_TAB_TEXTURE;

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
     */
    public void setTabTexture(ResourceLocation texture) {
        this.tabTexture = texture;
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
     */
    protected void drawTiledBackground(int x, int y, int width, int height) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(backgroundTexture);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        for (int tileX = 0; tileX < width; tileX += DEFAULT_TILE_SIZE) {
            for (int tileY = 0; tileY < height; tileY += DEFAULT_TILE_SIZE) {
                int tw = Math.min(DEFAULT_TILE_SIZE, width - tileX);
                int th = Math.min(DEFAULT_TILE_SIZE, height - tileY);

                double u1 = 0.0;
                double u2 = (double) tw / (double) DEFAULT_TILE_SIZE;
                double v1 = 0.0;
                double v2 = (double) th / (double) DEFAULT_TILE_SIZE;

                tessellator.addVertexWithUV(x + tileX, y + tileY + th, 0.0D, u1, v2);
                tessellator.addVertexWithUV(x + tileX + tw, y + tileY + th, 0.0D, u2, v2);
                tessellator.addVertexWithUV(x + tileX + tw, y + tileY, 0.0D, u2, v1);
                tessellator.addVertexWithUV(x + tileX, y + tileY, 0.0D, u1, v1);
            }
        }

        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);
    }
}
