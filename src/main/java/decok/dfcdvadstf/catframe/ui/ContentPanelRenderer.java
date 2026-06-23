package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>Shared renderer for the content panel used by createworld-style screens.</p>
 * <p>Gives you the header separator, a tiled panel background,
 * and the footer separator. Use the pieces you want, or let {@link #drawContentPanel(int, int, int, int)}
 * do the whole thing in one shot.</p>
 */
public final class ContentPanelRenderer {

    /**
     * Header separator (top line), 32x2 tileable.
     */
    public static final ResourceLocation HEADER_SEPARATOR =
            new ResourceLocation("catframe", "textures/gui/seperator/header_separator.png");

    /**
     * Footer separator (bottom line), 32x2 tileable.
     */
    public static final ResourceLocation FOOTER_SEPARATOR =
            new ResourceLocation("catframe", "textures/gui/seperator/footer_separator.png");

    /**
     * Panel background, 16x16 tileable.
     */
    public static final ResourceLocation PANEL_BACKGROUND =
            new ResourceLocation("catframe", "textures/gui/seperator/panel_background.png");

    /**
     * Separator height in GUI pixels.
     */
    public static final int SEPARATOR_HEIGHT = 2;

    private static final int SEPARATOR_TILE_W = 32;
    private static final int SEPARATOR_TILE_H = 2;
    private static final int PANEL_TILE = 16;

    private ContentPanelRenderer() {
    }

    /**
     * Draw the full content panel in one call — header line on top, tiled background in the middle,
     * footer line on bottom.
     *
     * @param x      left edge
     * @param top    Y coordinate of the header separator (its top pixel)
     * @param width  panel width
     * @param bottom Y coordinate of the footer separator (its top pixel)
     */
    public static void drawContentPanel(int x, int top, int width, int bottom) {
        if (width <= 0) return;

        // Background sits between the two separators — right below the header, right above the footer
        int bgTop = top + SEPARATOR_HEIGHT;
        if (bottom > bgTop) {
            drawPanelBackground(x, bgTop, width, bottom - bgTop);
        }

        drawHeaderSeparator(x, top, width);
        drawFooterSeparator(x, bottom, width);
    }

    /**
     * Draw only the header separator line (2px tall, tiled horizontally).
     * <p>仅绘制顶部分隔线（2px 高，横向平铺）。</p>
     */
    public static void drawHeaderSeparator(int x, int y, int width) {
        drawSeparator(x, y, width, HEADER_SEPARATOR);
    }

    /**
     * Draw only the footer separator line (2px tall, tiled horizontally).
     * <p>仅绘制底部分隔线（2px 高，横向平铺）。</p>
     */
    public static void drawFooterSeparator(int x, int y, int width) {
        drawSeparator(x, y, width, FOOTER_SEPARATOR);
    }

    /**
     * Draw a separator line with a custom 32x2 texture — handy if you want a styled variant.
     * <p>用自定义 32x2 纹理绘制分隔线 —— 想换个花样？传进来就行。</p>
     */
    public static void drawSeparator(int x, int y, int width, ResourceLocation texture) {
        if (width <= 0 || texture == null) return;
        drawTiledTexture(texture, x, y, width, SEPARATOR_TILE_H, SEPARATOR_TILE_W, SEPARATOR_TILE_H);
    }

    /**
     * Draw only the panel background — tiles the 16x16 panel texture over the given region.
     * <p>仅绘制面板背景 —— 把 16x16 的面板纹理平铺到指定区域。</p>
     */
    public static void drawPanelBackground(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        drawTiledTexture(PANEL_BACKGROUND, x, y, width, height, PANEL_TILE, PANEL_TILE);
    }

    /**
     * <p>Tile the given texture across the specified region.<br>Delegates to {@link TextureStretching#drawTiled}.</p>
     * <p>在指定区域内平铺指定纹理。委托给 {@link TextureStretching#drawTiled}。</p>
     */
    private static void drawTiledTexture(ResourceLocation texture, int x, int y, int width, int height, int tileWidth, int tileHeight) {
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        TextureStretching.drawTiled(texture, x, y, width, height, tileWidth, tileHeight);
    }
}
