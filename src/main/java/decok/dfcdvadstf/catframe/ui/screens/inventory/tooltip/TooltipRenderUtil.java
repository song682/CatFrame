package decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Tooltip background rendering utility — draws the tooltip background and frame
 * using nine-patch textures.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil}.
 * Uses two texture layers:
 * <ul>
 *   <li>{@link #BACKGROUND_TEXTURE background} — background fill layer</li>
 *   <li>{@link #FRAME_TEXTURE frame} — frame layer</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class TooltipRenderUtil {

    /** Mouse offset (mirrors 26.1.2 MOUSE_OFFSET) */
    public static final int MOUSE_OFFSET = 12;

    /** Tooltip padding (text to background edge) — mirrors 26.1.2 PADDING = 3 */
    public static final int PADDING = 3;
    public static final int PADDING_LEFT = 3;
    public static final int PADDING_RIGHT = 3;
    public static final int PADDING_TOP = 3;
    public static final int PADDING_BOTTOM = 3;

    /** Outer margin (background to texture edge) — mirrors 26.1.2 MARGIN = 9 */
    private static final int MARGIN = 9;

    /** Default background fill texture (tiled in the center) */
    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(Tags.MODID, "textures/gui/sprites/tooltips/background.png");
    /** Default frame texture (stretched in the center) */
    private static final ResourceLocation FRAME_TEXTURE = new ResourceLocation(Tags.MODID, "textures/gui/sprites/tooltips/frame.png");

    /**
     * Renders the tooltip background (no custom style).
     *
     * @param x top-left X of the tooltip text area
     * @param y top-left Y of the tooltip text area
     * @param w text area width
     * @param h text area height
     */
    public static void renderTooltipBackground(int x, int y, int w, int h) {
        renderTooltipBackground(x, y, w, h, null);
    }

    /**
     * Renders the tooltip background (supports a custom style).
     * <p>Draws the background fill layer first, then overlays the frame layer.</p>
     * <p>Mirrors 26.1.2 {@code TooltipRenderUtil.extractTooltipBackground()}:
     * background area = text area expanded outward by PADDING + MARGIN.</p>
     * <p>[Render three-domain architecture: phase C] The two nine-patch layers are
     * merged by {@link TextureStretching#beginBatch()} into a single bind + single
     * draw (batched submission); when the texture belongs to the {@code catframe:gui}
     * atlas it automatically switches to atlas lookup-table UVs instead
     * (TextureStretching dispatches internally).</p>
     *
     * @param x     top-left X of the tooltip text area
     * @param y     top-left Y of the tooltip text area
     * @param w     text area width
     * @param h     text area height
     * @param style custom style ID (may be null; when null the default textures are used)
     */
    public static void renderTooltipBackground(int x, int y, int w, int h, @Nullable ResourceLocation style) {
        // Mirrors 26.1.2: x0 = x - PADDING - MARGIN, paddedWidth = w + PADDING*2 + MARGIN*2
        int bgX = x - PADDING - MARGIN;
        int bgY = y - PADDING - MARGIN;
        int bgW = w + PADDING_LEFT + PADDING_RIGHT + MARGIN * 2;
        int bgH = h + PADDING_TOP + PADDING_BOTTOM + MARGIN * 2;

        ResourceLocation bgTexture = getBackgroundTexture(style);
        ResourceLocation frameTexture = getFrameTexture(style);

        // Batched submission: the two nine-patch layers merge into a single bind + single draw (cross-layer merge when they share an atlas).
        TextureStretching.beginBatch();
        // Layer 1: background fill
        TextureStretching.drawAutoNinePatch(bgTexture, bgX, bgY, bgW, bgH, 100, 100, PADDING + MARGIN);
        // Layer 2: frame
        TextureStretching.drawAutoNinePatch(frameTexture, bgX, bgY, bgW, bgH, 100, 100, PADDING + MARGIN);
        TextureStretching.endBatch();
    }

    /**
     * Gets the background texture — supports a custom style path.
     * <p>Mirrors 26.1.2 {@code getBackgroundSprite(style)}.</p>
     */
    private static ResourceLocation getBackgroundTexture(@Nullable ResourceLocation style) {
        if (style == null) return BACKGROUND_TEXTURE;
        return new ResourceLocation(style.getResourceDomain(),
                "textures/gui/tooltips/" + style.getResourcePath() + "_background.png");
    }

    /**
     * Gets the frame texture — supports a custom style path.
     * <p>Mirrors 26.1.2 {@code getFrameSprite(style)}.</p>
     */
    private static ResourceLocation getFrameTexture(@Nullable ResourceLocation style) {
        if (style == null) return FRAME_TEXTURE;
        return new ResourceLocation(style.getResourceDomain(),
                "textures/gui/tooltips/" + style.getResourcePath() + "_frame.png");
    }
}
