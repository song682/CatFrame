package decok.dfcdvadstf.catframe.ui.util;

import net.minecraft.util.ResourceLocation;

/**
 * High-version {@code GuiGraphics.blitSprite()} compatibility layer.
 * <p>
 * Provides an API style similar to 26.1.2's sprite drawing methods, internally
 * delegating to CatFrame's {@link TextureStretching} system. This allows code
 * written in a modern sprite-drawing style to work with CatFrame's immediate-mode
 * rendering without directly calling {@code TextureStretching} methods.
 * </p>
 *
 * <h3>Mapping to 26.1.2</h3>
 * <ul>
 *   <li>{@link #blitSprite} → high-version {@code blitSprite(RenderPipeline, Identifier, x, y, w, h)}
 *       — auto-dispatches based on mcmeta metadata</li>
 *   <li>{@link #blitNineSliced} → high-version {@code blitNineSlicedSprite(...)}
 *       — explicit nine-slice with border and stretch-inner control</li>
 *   <li>{@link #blitTiled} → high-version {@code blitTiledSprite(...)}
 *       — explicit tiling</li>
 *   <li>{@link #blitStretch} → high-version {@code blitSprite(...)} with stretch scaling
 *       — simple whole-texture stretch</li>
 * </ul>
 *
 * <h3>Differences from 26.1.2</h3>
 * <ul>
 *   <li>Uses {@link ResourceLocation} instead of {@code Identifier} / {@code TextureAtlasSprite}</li>
 *   <li>No {@code RenderPipeline} parameter — CatFrame uses immediate-mode GL</li>
 *   <li>Color parameter is simplified to alpha extraction (full ARGB tinting not supported)</li>
 *   <li>Auto-dispatch reads CatFrame's {@link TextureStretchingMetadata} (mcmeta {@code "stretching"} section),
 *       not the high-version's {@code GuiSpriteScaling}</li>
 * </ul>
 */
public final class SpriteBlitter {

    private SpriteBlitter() {
    }

    // ──── Auto-dispatch (reads mcmeta metadata) ────

    /**
     * Draw a sprite using the stretching behaviour defined in its {@code .mcmeta} file.
     * <p>
     * Corresponds to 26.1.2 {@code blitSprite(RenderPipeline, Identifier, x, y, w, h)}
     * which reads {@code GuiSpriteScaling} from resource metadata and dispatches to
     * stretch / tile / nine-slice automatically.
     * </p>
     * <p>
     * Falls back to nine-patch with hardcoded defaults if no metadata is found.
     * </p>
     *
     * @param texture texture ResourceLocation
     * @param x       screen X
     * @param y       screen Y
     * @param w       target width
     * @param h       target height
     */
    public static void blitSprite(ResourceLocation texture, int x, int y, int w, int h) {
        TextureStretching.drawAutoNinePatch(texture, x, y, w, h, 32, 32, 4);
    }

    /**
     * Draw a sprite with alpha, using the stretching behaviour defined in its {@code .mcmeta} file.
     * <p>
     * The alpha is extracted from the ARGB-packed color parameter (matching 26.1.2's
     * {@code ARGB.white(alpha)} convention). Full color tinting is not supported —
     * only the alpha channel is applied.
     * </p>
     *
     * @param texture texture ResourceLocation
     * @param x       screen X
     * @param y       screen Y
     * @param w       target width
     * @param h       target height
     * @param color   ARGB-packed color (only alpha channel is used)
     */
    public static void blitSprite(ResourceLocation texture, int x, int y, int w, int h, int color) {
        // Extract alpha from ARGB — full tinting not supported in immediate mode
        float alpha = (float) (color >>> 24 & 0xFF) / 255.0F;
        if (alpha >= 1.0F) {
            blitSprite(texture, x, y, w, h);
            return;
        }
        // For non-opaque, we need to set GL color before drawing.
        // Delegate to drawAuto with explicit fallback (same as blitSprite but with alpha).
        org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
        TextureStretching.drawAutoNinePatch(texture, x, y, w, h, 32, 32, 4);
    }

    // ──── Explicit nine-slice ────

    /**
     * Draw a sprite using explicit nine-slice parameters.
     * <p>
     * Corresponds to 26.1.2 {@code blitNineSlicedSprite(RenderPipeline, Identifier, x, y, w, h,
     * borderL, borderT, borderR, borderB, spriteW, spriteH)}.
     * The additional {@code stretchInner} parameter aligns with 26.1.2's
     * {@code NineSlice.stretchInner()}.
     * </p>
     *
     * @param texture      texture ResourceLocation
     * @param x            screen X
     * @param y            screen Y
     * @param w            target width
     * @param h            target height
     * @param borderL      left border width (texture pixels)
     * @param borderT      top border height (texture pixels)
     * @param borderR      right border width (texture pixels)
     * @param borderB      bottom border height (texture pixels)
     * @param spriteW      total sprite width (texture pixels)
     * @param spriteH      total sprite height (texture pixels)
     * @param stretchInner if {@code true}, stretch the centre region; if {@code false}, tile it
     */
    public static void blitNineSliced(ResourceLocation texture, int x, int y, int w, int h,
            int borderL, int borderT, int borderR, int borderB,
            int spriteW, int spriteH, boolean stretchInner) {
        TextureStretching.drawNinePatch(texture, x, y, w, h,
                borderL, borderT, borderR, borderB, spriteW, spriteH, stretchInner);
    }

    /**
     * Draw a sprite using nine-slice with symmetric borders and tiled centre (default).
     * <p>
     * Convenience overload matching the most common 26.1.2 usage pattern.
     * </p>
     *
     * @param texture  texture ResourceLocation
     * @param x        screen X
     * @param y        screen Y
     * @param w        target width
     * @param h        target height
     * @param border   uniform border width (all four sides, texture pixels)
     * @param spriteW  total sprite width (texture pixels)
     * @param spriteH  total sprite height (texture pixels)
     */
    public static void blitNineSliced(ResourceLocation texture, int x, int y, int w, int h,
            int border, int spriteW, int spriteH) {
        blitNineSliced(texture, x, y, w, h, border, border, border, border, spriteW, spriteH, false);
    }

    // ──── Explicit tiling ────

    /**
     * Draw a sprite by tiling a sub-region across the target area.
     * <p>
     * Corresponds to 26.1.2 {@code blitTiledSprite(RenderPipeline, Identifier, ...)}.
     * </p>
     *
     * @param texture texture ResourceLocation
     * @param x       screen X
     * @param y       screen Y
     * @param w       target width
     * @param h       target height
     * @param tileW   tile width (texture pixels)
     * @param tileH   tile height (texture pixels)
     */
    public static void blitTiled(ResourceLocation texture, int x, int y, int w, int h,
            int tileW, int tileH) {
        TextureStretching.drawTiled(texture, x, y, w, h, tileW, tileH);
    }

    // ──── Simple stretch ────

    /**
     * Draw a sprite by stretching the entire texture across the target area.
     * <p>
     * Corresponds to 26.1.2 {@code blitSprite} with {@code GuiSpriteScaling.Stretch}.
     * </p>
     *
     * @param texture texture ResourceLocation
     * @param x       screen X
     * @param y       screen Y
     * @param w       target width
     * @param h       target height
     * @param texW    texture width (pixels)
     * @param texH    texture height (pixels)
     */
    public static void blitStretch(ResourceLocation texture, int x, int y, int w, int h,
            int texW, int texH) {
        TextureStretching.drawStatic(texture, x, y, w, h, texW, texH);
    }
}
