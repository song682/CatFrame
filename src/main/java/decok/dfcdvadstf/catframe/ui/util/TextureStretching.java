package decok.dfcdvadstf.catframe.ui.util;

import decok.dfcdvadstf.catframe.resources.atlas.CatSprite;
import decok.dfcdvadstf.catframe.ui.UiTextureAtlasManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Texture stretching utility system — provides extensible texture drawing
 * strategies.
 * </p>
 *
 * <h3>Built-in strategies</h3>
 * <ul>
 * <li>{@link #drawNinePatch} — Nine-patch stretch (4 fixed corners, 4 tiled edges, tiled center)</li>
 * <li>{@link #drawFixedEndRepeat} — Two-end fixed middle repeat (horizontal)</li>
 * <li>{@link #drawTiled} — General tiling</li>
 * </ul>
 *
 * <h3>UI atlas routing (render three-domain architecture: stage C)</h3>
 * <p>
 * If the incoming {@link ResourceLocation} belongs to the {@code catframe:gui} atlas (found
 * via {@link UiTextureAtlasManager#resolve}), it binds the atlas and draws using sprite
 * UVs (textures no longer uploaded individually via TextureManager); non-atlas textures
 * remain bound via their original path. Multiple atlas draws can be merged into a single
 * bind + draw using {@link #beginBatch()}/{@link #endBatch()} (batched submission).
 * </p>
 */
public final class TextureStretching {

    private TextureStretching() {
    }

    // Batch context (batched submission: multiple atlas draws merged into one bind + draw)

    /** Batch-collected quads (screen coords + final UV + alpha), submitted together by endBatch. */
    private static final class BatchQuad {
        final float x, y, w, h;
        final float u1, v1, u2, v2;
        final float alpha;

        BatchQuad(float x, float y, float w, float h,
                  float u1, float v1, float u2, float v2, float alpha) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.u1 = u1;
            this.v1 = v1;
            this.u2 = u2;
            this.v2 = v2;
            this.alpha = alpha;
        }
    }

    /** Current batch collection queue (cleared at beginBatch). */
    private static final List<BatchQuad> BATCH = new ArrayList<>();
    /** Batch in progress flag. */
    private static boolean inBatch = false;

    /**
     * Draw binding resolution result: actual bound texture + atlas sprite (non-null means UV uses sprite).
     */
    private static final class DrawBinding {
        final ResourceLocation bind;
        @Nullable
        final CatSprite sprite;

        DrawBinding(ResourceLocation bind, @Nullable CatSprite sprite) {
            this.bind = bind;
            this.sprite = sprite;
        }
    }

    /**
     * Texture → draw binding: textures belonging to the {@code catframe:gui} atlas are resolved
     * to an atlas binding + sprite (UV from atlas lookup), others remain as original texture binding
     * (sprite is null).
     */
    private static DrawBinding resolve(ResourceLocation texture) {
        if (texture != null) {
            CatSprite sprite = UiTextureAtlasManager.resolve(texture);
            if (sprite != null) {
                return new DrawBinding(UiTextureAtlasManager.getAtlasLocation(), sprite);
            }
        }
        return new DrawBinding(texture, null);
    }

    /**
     * Begins a batched draw scope: binds the UI atlas once, subsequent atlas
     * draws only collect quads, {@code endBatch()} submits them in one draw.
     */
    public static void beginBatch() {
        BATCH.clear();
        inBatch = true;
        bindAndPrepare(UiTextureAtlasManager.getAtlasLocation());
    }

    /**
     * Ends the batched draw scope and submits all collected quads in one draw.
     */
    public static void endBatch() {
        if (!inBatch) {
            return;
        }
        inBatch = false;
        if (!BATCH.isEmpty()) {
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            for (BatchQuad q : BATCH) {
                GL11.glColor4f(1.0f, 1.0f, 1.0f, q.alpha);
                t.addVertexWithUV(q.x, q.y + q.h, 0.0D, q.u1, q.v2);
                t.addVertexWithUV(q.x + q.w, q.y + q.h, 0.0D, q.u2, q.v2);
                t.addVertexWithUV(q.x + q.w, q.y, 0.0D, q.u2, q.v1);
                t.addVertexWithUV(q.x, q.y, 0.0D, q.u1, q.v1);
            }
            t.draw();
            BATCH.clear();
        }
        cleanup();
    }

    /**
     * Non-atlas draw encountered during batch: flush collected atlas quads (one draw),
     * then let the draw proceed via its original path.
     */
    private static void flushBatch() {
        boolean wasInBatch = inBatch;
        inBatch = false;
        if (!BATCH.isEmpty()) {
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            for (BatchQuad q : BATCH) {
                GL11.glColor4f(1.0f, 1.0f, 1.0f, q.alpha);
                t.addVertexWithUV(q.x, q.y + q.h, 0.0D, q.u1, q.v2);
                t.addVertexWithUV(q.x + q.w, q.y + q.h, 0.0D, q.u2, q.v2);
                t.addVertexWithUV(q.x + q.w, q.y, 0.0D, q.u2, q.v1);
                t.addVertexWithUV(q.x, q.y, 0.0D, q.u1, q.v1);
            }
            t.draw();
            BATCH.clear();
        }
        cleanup();
        inBatch = wasInBatch;
    }

    /**
     * Atlas sprite UV range mapping: normalized pixel coords [0,1] → sprite UV.
     */
    private static float mapU(CatSprite sprite, float u) {
        return sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * u;
    }

    private static float mapV(CatSprite sprite, float v) {
        return sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * v;
    }

    /**
     * Batch mode: collects a quad; non-batch mode: immediately addVertex to current Tessellator.
     * UV input is normalized pixels [0,1], mapped to atlas UV when sprite is non-null.
     */
    private static void emitQuad(Tessellator t, int sx, int sy, int sw, int sh,
            float u1, float v1, float u2, float v2, float alpha,
            @Nullable CatSprite sprite) {
        float uu1 = sprite != null ? mapU(sprite, u1) : u1;
        float uu2 = sprite != null ? mapU(sprite, u2) : u2;
        float vv1 = sprite != null ? mapV(sprite, v1) : v1;
        float vv2 = sprite != null ? mapV(sprite, v2) : v2;
        if (inBatch) {
            BATCH.add(new BatchQuad(sx, sy, sw, sh, uu1, vv1, uu2, vv2, alpha));
            return;
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
        t.addVertexWithUV(sx, sy + sh, 0.0D, uu1, vv2);
        t.addVertexWithUV(sx + sw, sy + sh, 0.0D, uu2, vv2);
        t.addVertexWithUV(sx + sw, sy, 0.0D, uu2, vv1);
        t.addVertexWithUV(sx, sy, 0.0D, uu1, vv1);
    }

    // ──── Strategy interface ────

    /**
     * Stretch strategy interface — implement to define custom stretching behaviour.
     */
    public interface StretchStrategy {
        /**
         * Draw the texture using this strategy.
         *
         * @param texture texture resource
         * @param x       screen X
         * @param y       screen Y
         * @param w       target width
         * @param h       target height
         */
        void draw(ResourceLocation texture, int x, int y, int w, int h);
    }

    /**
     * Stretch type enum, corresponds to the {@code type} field in mcmeta.
     */
    public enum StretchType {
        NINE_PATCH,
        THREE_PATCH,
        TILE,
        STATIC
    }

    // ──── Nine-patch (9-slice) ────

    /**
     * <p>
     * Nine-patch stretch — splits the texture into 9 regions: 4 fixed corners,
     * 4 tiled edges, and a tiled centre.
     * </p>
     *
     * @param texture texture resource
     * @param x       screen X
     * @param y       screen Y
     * @param w       target width
     * @param h       target height
     * @param edgeL   left edge width (texture pixels)
     * @param edgeT   top edge height (texture pixels)
     * @param edgeR   right edge width (texture pixels)
     * @param edgeB   bottom edge height (texture pixels)
     * @param texW    total texture width
     * @param texH    total texture height
     */
    public static void drawNinePatch(ResourceLocation texture, int x, int y, int w, int h,
            int edgeL, int edgeT, int edgeR, int edgeB,
            int texW, int texH) {
        drawNinePatch(texture, x, y, w, h, edgeL, edgeT, edgeR, edgeB, texW, texH, false);
    }

    /**
     * Nine-patch stretch with explicit inner region control.
     * <p>
     * When {@code stretchInner} is {@code true}, the centre region is drawn as a single
     * stretched quad instead of tiled — aligns with 26.1.2 {@code NineSlice.stretchInner()}.
     * </p>
     *
     * @param stretchInner if {@code true}, stretch the centre region; if {@code false}, tile it
     */
    public static void drawNinePatch(ResourceLocation texture, int x, int y, int w, int h,
            int edgeL, int edgeT, int edgeR, int edgeB,
            int texW, int texH, boolean stretchInner) {
        if (w <= 0 || h <= 0)
            return;

        int innerW = w - edgeL - edgeR;
        int innerH = h - edgeT - edgeB;
        int texInnerW = texW - edgeL - edgeR;
        int texInnerH = texH - edgeT - edgeB;

        if (innerW < 0 || innerH < 0) {
            // Target size smaller than edges — clamp right/bottom edges to fit
            if (w < edgeL + edgeR) {
                edgeR = Math.max(0, w - edgeL);
                innerW = 0;
            }
            if (h < edgeT + edgeB) {
                edgeB = Math.max(0, h - edgeT);
                innerH = 0;
            }
        }

        // [Render three-domain architecture] Atlas routing: catframe:gui textures resolve to atlas binding + sprite UV;
        // If in batch and non-atlas texture, flush collected quads first, then draw via original path.
        DrawBinding b = resolve(texture);
        if (inBatch && b.sprite == null) {
            flushBatch();
        }
        boolean batched = inBatch && b.sprite != null;
        if (!batched) {
            bindAndPrepare(b.bind);
        }
        Tessellator t = Tessellator.instance;
        if (!batched) {
            t.startDrawingQuads();
        }

        // Top-left corner
        emitQuad(t, x, y, edgeL, edgeT, 0, 0, (float) edgeL / texW, (float) edgeT / texH, 1.0F, b.sprite);
        // Top-right corner
        emitQuad(t, x + w - edgeR, y, edgeR, edgeT, (float) (texW - edgeR) / texW, 0, 1.0F, (float) edgeT / texH, 1.0F, b.sprite);
        // Bottom-left corner
        emitQuad(t, x, y + h - edgeB, edgeL, edgeB, 0, (float) (texH - edgeB) / texH, (float) edgeL / texW, 1.0F, 1.0F, b.sprite);
        // Bottom-right corner
        emitQuad(t, x + w - edgeR, y + h - edgeB, edgeR, edgeB,
                (float) (texW - edgeR) / texW, (float) (texH - edgeB) / texH, 1.0F, 1.0F, 1.0F, b.sprite);

        // Top edge (tiled horizontally)
        if (innerW > 0) {
            addTiledQuad(t, x + edgeL, y, innerW, edgeT, edgeL, 0, texInnerW, edgeT, texW, texH, b.sprite);
        }
        // Bottom edge (tiled horizontally)
        if (innerW > 0) {
            addTiledQuad(t, x + edgeL, y + h - edgeB, innerW, edgeB, edgeL, texH - edgeB, texInnerW, edgeB, texW, texH, b.sprite);
        }
        // Left edge (tiled vertically)
        if (innerH > 0) {
            addTiledQuad(t, x, y + edgeT, edgeL, innerH, 0, edgeT, edgeL, texInnerH, texW, texH, b.sprite);
        }
        // Right edge (tiled vertically)
        if (innerH > 0) {
            addTiledQuad(t, x + w - edgeR, y + edgeT, edgeR, innerH, texW - edgeR, edgeT, edgeR, texInnerH, texW, texH, b.sprite);
        }
        // Centre: stretch or tile depending on stretchInner flag
        if (innerW > 0 && innerH > 0) {
            if (stretchInner) {
                // Single stretched quad covering the entire centre region
                emitQuad(t, x + edgeL, y + edgeT, innerW, innerH,
                        (float) edgeL / texW, (float) edgeT / texH,
                        (float) (texW - edgeR) / texW, (float) (texH - edgeB) / texH,
                        1.0F, b.sprite);
            } else {
                addTiledQuad(t, x + edgeL, y + edgeT, innerW, innerH, edgeL, edgeT, texInnerW, texInnerH, texW, texH, b.sprite);
            }
        }

        if (!batched) {
            t.draw();
            cleanup();
        }
    }

    // ──── Two-end-fixed, middle-repeat (three-patch, horizontal) ────

    /**
     * <p>
     * Two-end-fixed middle-repeat — horizontally: left edgeL pixels fixed, right
     * edgeR pixels fixed, middle tileW pixels tiled.
     * </p>
     *
     * @param texture texture resource
     * @param x       screen X
     * @param y       screen Y
     * @param w       target width
     * @param h       target height
     * @param edgeL   left fixed width (texture pixels)
     * @param edgeR   right fixed width (texture pixels)
     * @param tileW   middle tile width (texture pixels)
     * @param texW    total texture width
     * @param texH    total texture height
     */
    public static void drawFixedEndRepeat(ResourceLocation texture, int x, int y, int w, int h,
            int edgeL, int edgeR, int tileW,
            int texW, int texH) {
        if (w <= 0 || h <= 0)
            return;

        int middleW = w - edgeL - edgeR;
        if (middleW < 0) {
            // Target width smaller than edges — clamp right edge to fit
            edgeR = Math.max(0, w - edgeL);
            middleW = 0;
        }

        // [Render three-domain architecture] Atlas routing: same as drawNinePatch (flush non-atlas first in batch).
        DrawBinding b = resolve(texture);
        if (inBatch && b.sprite == null) {
            flushBatch();
        }
        boolean batched = inBatch && b.sprite != null;
        if (!batched) {
            bindAndPrepare(b.bind);
        }
        Tessellator t = Tessellator.instance;
        if (!batched) {
            t.startDrawingQuads();
        }

        // Left edge
        emitQuad(t, x, y, edgeL, h, 0, 0, (float) edgeL / texW, 1.0F, 1.0F, b.sprite);

        // Middle (tiled)
        if (middleW > 0 && tileW > 0) {
            addTiledQuad(t, x + edgeL, y, middleW, h, edgeL, 0, tileW, h, texW, texH, b.sprite);
        }

        // Right edge
        emitQuad(t, x + w - edgeR, y, edgeR, h, (float) (texW - edgeR) / texW, 0, 1.0F, 1.0F, 1.0F, b.sprite);

        if (!batched) {
            t.draw();
            cleanup();
        }
    }

    // ──── Static (integer-multiple upscale) ────

    /**
     * <p>
     * Static stretch — draws the whole texture (UV 0..1) scaled by an integer
     * factor over the target area.
     * </p>
     * <p>
     * The target size must be an integer multiple of the texture's original size
     * (e.g. a 16×16 texture may only draw 16×16, 32×32, 48×48…), guaranteeing
     * pixel-perfect upscaling; invalid sizes throw
     * {@link IllegalArgumentException}.
     * </p>
     *
     * @param texture texture resource
     * @param x       screen X
     * @param y       screen Y
     * @param w       target width (integer multiple of texW)
     * @param h       target height (integer multiple of texH)
     * @param texW    original texture width
     * @param texH    original texture height
     */
    public static void drawStatic(ResourceLocation texture, int x, int y, int w, int h, int texW, int texH) {
        drawStatic(texture, x, y, w, h, texW, texH, 1.0F);
    }

    /**
     * Static stretch with alpha.
     *
     * @param alpha 0.0-1.0
     */
    public static void drawStatic(ResourceLocation texture, int x, int y, int w, int h,
            int texW, int texH, float alpha) {
        if (w <= 0 || h <= 0)
            return;
        if (texW <= 0 || texH <= 0) {
            throw new IllegalArgumentException(
                    "Original texture size must be positive: " + texW + "x" + texH);
        }
        if (w % texW != 0 || h % texH != 0) {
            throw new IllegalArgumentException("Target size " + w + "x" + h
                    + " is not an integer multiple of texture size " + texW + "x" + texH);
        }

        // [Render three-domain architecture] Atlas routing: same as drawNinePatch (flush non-atlas first in batch).
        DrawBinding b = resolve(texture);
        if (inBatch && b.sprite == null) {
            flushBatch();
        }
        boolean batched = inBatch && b.sprite != null;
        if (!batched) {
            bindAndPrepare(b.bind, alpha);
        }
        Tessellator t = Tessellator.instance;
        if (!batched) {
            t.startDrawingQuads();
        }
        emitQuad(t, x, y, w, h, 0, 0, 1, 1, alpha, b.sprite);
        if (!batched) {
            t.draw();
            cleanup();
        }
    }

    // ──── General tiling ────

    /**
     * <p>
     * General tiling — tiles the texture in tileW×tileH pixel units across the
     * target area.
     * </p>
     *
     * @param texture texture resource
     * @param x       screen X
     * @param y       screen Y
     * @param w       target width
     * @param h       target height
     * @param tileW   tile width
     * @param tileH   tile height
     */
    public static void drawTiled(ResourceLocation texture, int x, int y, int w, int h,
            int tileW, int tileH) {
        if (w <= 0 || h <= 0 || tileW <= 0 || tileH <= 0)
            return;

        // [Render three-domain architecture] Atlas routing: same as drawNinePatch (flush non-atlas first in batch).
        DrawBinding b = resolve(texture);
        if (inBatch && b.sprite == null) {
            flushBatch();
        }
        boolean batched = inBatch && b.sprite != null;
        if (!batched) {
            bindAndPrepare(b.bind);
        }
        Tessellator t = Tessellator.instance;
        if (!batched) {
            t.startDrawingQuads();
        }

        for (int offX = 0; offX < w; offX += tileW) {
            for (int offY = 0; offY < h; offY += tileH) {
                int drawW = Math.min(tileW, w - offX);
                int drawH = Math.min(tileH, h - offY);

                float u2 = (float) drawW / (float) tileW;
                float v2 = (float) drawH / (float) tileH;

                emitQuad(t, x + offX, y + offY, drawW, drawH, 0, 0, u2, v2, 1.0F, b.sprite);
            }
        }

        if (!batched) {
            t.draw();
            cleanup();
        }
    }

    // ──── Auto-draw from mcmeta ────

    /**
     * Draw a texture using parameters loaded from its {@code .mcmeta} file.
     * <p>
     * Falls back to the given type with hardcoded defaults if no metadata is found.
     * </p>
     *
     * @param texture      texture resource
     * @param x            screen X
     * @param y            screen Y
     * @param w            target width
     * @param h            target height
     * @param fallbackType fallback stretch type
     * @param fallbackW    fallback texture width
     * @param fallbackH    fallback texture height
     * @param fallbackL    fallback left edge
     * @param fallbackT    fallback top edge
     * @param fallbackR    fallback right edge
     * @param fallbackB    fallback bottom edge
     */
    public static void drawAuto(ResourceLocation texture, int x, int y, int w, int h,
            StretchType fallbackType,
            int fallbackW, int fallbackH,
            int fallbackL, int fallbackT, int fallbackR, int fallbackB) {
        TextureStretchingMetadata meta = TextureStretchingMetadata.load(texture);

        if (meta != null) {
            switch (meta.getType()) {
                case NINE_PATCH:
                    drawNinePatch(texture, x, y, w, h,
                            meta.getEdgeLeft(), meta.getEdgeTop(),
                            meta.getEdgeRight(), meta.getEdgeBottom(),
                            meta.getDefaultWidth(), meta.getDefaultHeight(),
                            meta.isStretchInner());
                    return;
                case THREE_PATCH:
                    drawFixedEndRepeat(texture, x, y, w, h,
                            meta.getEdgeLeft(), meta.getEdgeRight(),
                            meta.getTileWidth(),
                            meta.getDefaultWidth(), meta.getDefaultHeight());
                    return;
                case TILE:
                    drawTiled(texture, x, y, w, h,
                            meta.getDefaultWidth(), meta.getDefaultHeight());
                    return;
                case STATIC:
                    drawStatic(texture, x, y, w, h,
                            meta.getDefaultWidth(), meta.getDefaultHeight());
                    return;
                default:
                    break;
            }
        }

        // Fallback with explicit type
        switch (fallbackType) {
            case THREE_PATCH:
                drawFixedEndRepeat(texture, x, y, w, h,
                        fallbackL, fallbackR,
                        fallbackW - fallbackL - fallbackR,
                        fallbackW, fallbackH);
                break;
            case TILE:
                drawTiled(texture, x, y, w, h, fallbackW, fallbackH);
                break;
            case STATIC:
                drawStatic(texture, x, y, w, h, fallbackW, fallbackH);
                break;
            case NINE_PATCH:
            default:
                drawNinePatch(texture, x, y, w, h,
                        fallbackL, fallbackT, fallbackR, fallbackB,
                        fallbackW, fallbackH);
                break;
        }
    }

    /**
     * Draw a texture using mcmeta metadata (nine-patch default fallback).
     * <p>
     * Convenience overload that assumes nine-patch with symmetric borders.
     * </p>
     */
    public static void drawAutoNinePatch(ResourceLocation texture, int x, int y, int w, int h,
            int fallbackTexW, int fallbackTexH, int fallbackBorder) {
        drawAuto(texture, x, y, w, h,
                StretchType.NINE_PATCH,
                fallbackTexW, fallbackTexH,
                fallbackBorder, fallbackBorder, fallbackBorder, fallbackBorder);
    }

    /**
     * Draw a texture using mcmeta metadata (three-patch default fallback).
     * <p>
     * Convenience overload for horizontal three-patch with symmetric edges.
     * </p>
     */
    public static void drawAutoThreePatch(ResourceLocation texture, int x, int y, int w, int h,
            int fallbackTexW, int fallbackTexH, int fallbackEdge) {
        drawAuto(texture, x, y, w, h,
                StretchType.THREE_PATCH,
                fallbackTexW, fallbackTexH,
                fallbackEdge, 0, fallbackEdge, 0);
    }

    // ──── Internal helpers ────

    /**
     * Bind texture and set up GL blend state.
     * <p>
     * If texture is null, skips binding (assumes texture already bound).
     * </p>
     */
    private static void bindAndPrepare(ResourceLocation texture) {
        bindAndPrepare(texture, 1.0F);
    }

    private static void bindAndPrepare(ResourceLocation texture, float alpha) {
        if (texture != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Restore GL state after drawing.
     */
    private static void cleanup() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * Add a tiled textured quad — repeats the UV region across the screen area.
     */
    private static void addTiledQuad(Tessellator t, int sx, int sy, int screenW, int screenH,
            float texU, float texV, float texTileW, float texTileH,
            int texW, int texH, @Nullable CatSprite sprite) {
        for (int offX = 0; offX < screenW; offX += (int) texTileW) {
            for (int offY = 0; offY < screenH; offY += (int) texTileH) {
                int drawW = Math.min((int) texTileW, screenW - offX);
                int drawH = Math.min((int) texTileH, screenH - offY);

                emitQuad(t, sx + offX, sy + offY, drawW, drawH,
                        texU / texW, texV / texH,
                        (texU + drawW) / texW, (texV + drawH) / texH,
                        1.0F, sprite);
            }
        }
    }
}
