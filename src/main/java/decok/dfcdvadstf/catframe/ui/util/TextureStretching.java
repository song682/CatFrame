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
 * 纹理拉伸工具系统 —— 提供可扩展的纹理绘制策略。<br>
 * Texture stretching utility system — provides extensible texture drawing
 * strategies.
 * </p>
 *
 * <h3>内建策略 / Built-in strategies</h3>
 * <ul>
 * <li>{@link #drawNinePatch} — 九宫格拉伸（4 角固定、4 边平铺、中心平铺）</li>
 * <li>{@link #drawFixedEndRepeat} — 两端固定中间重复（水平方向）</li>
 * <li>{@link #drawTiled} — 通用平铺</li>
 * </ul>
 *
 * <h3>UI 图集分流（渲染三域架构：阶段 C）/ UI-atlas routing</h3>
 * <p>
 * 传入的 {@link ResourceLocation} 若属于 {@code catframe:gui} 图集（经
 * {@link UiTextureAtlasManager#resolve} 查到 sprite），则绑定图集 + 按 sprite
 * UV 绘制（纹理不再经 TextureManager 逐张上传）；非图集纹理保持原路径绑定。
 * 多次图集绘制可用 {@link #beginBatch()}/{@link #endBatch()} 合并为一次
 * bind + 一次 draw（批量提交）。
 * </p>
 */
public final class TextureStretching {

    private TextureStretching() {
    }

    // ──── 批次上下文（批量提交：同图集多段绘制合并一次 bind + draw） ────

    /** 批次收集的 quad（屏幕坐标 + 最终 UV + alpha），endBatch 统一提交。 */
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

    /** 当前批次收集队列（beginBatch 时清空）。 */
    private static final List<BatchQuad> BATCH = new ArrayList<>();
    /** 批次进行中标记。 */
    private static boolean inBatch = false;

    /**
     * 绘制绑定解析结果：实际绑定的纹理 + 图集 sprite（非 null 时 UV 走 sprite）。
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
     * 纹理 → 绘制绑定：属于 {@code catframe:gui} 图集的纹理解析为图集绑定 + sprite
     * （UV 由图集查表得出），其余保持原纹理绑定（sprite 为 null）。
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
     * 开始批量提交：绑定 UI 图集一次，后续图集绘制只收集 quad，
     * {@link #endBatch()} 统一一次 draw。<br>
     * Begins a batched draw scope: binds the UI atlas once, subsequent atlas
     * draws only collect quads, {@code endBatch()} submits them in one draw.
     */
    public static void beginBatch() {
        BATCH.clear();
        inBatch = true;
        bindAndPrepare(UiTextureAtlasManager.getAtlasLocation());
    }

    /**
     * 结束批量提交：一次 draw 提交全部收集 quad，并关闭批次。
     * 未在批次中调用时为空操作。<br>
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
     * 批次中遇到非图集绘制：先提交已收集的图集 quad（一次 draw），
     * 随后该绘制按原路径独立进行。
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
     * 图集 sprite 的 UV 区间映射：像素归一化坐标 [0,1] → sprite UV。
     */
    private static float mapU(CatSprite sprite, float u) {
        return sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * u;
    }

    private static float mapV(CatSprite sprite, float v) {
        return sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * v;
    }

    /**
     * 批次模式：收集一个 quad；非批次模式：立即 addVertex 到当前 Tessellator。
     * UV 输入为像素归一化 [0,1]，sprite 非 null 时映射到图集 UV。
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
     * <p>
     * 拉伸策略接口 —— 实现此接口以定义自定义拉伸行为。<br>
     * Stretch strategy interface — implement to define custom stretching behaviour.
     * </p>
     */
    public interface StretchStrategy {
        /**
         * Draw the texture using this strategy.
         * <p>
         * 使用此策略绘制纹理。
         * </p>
         *
         * @param texture 纹理资源 / texture resource
         * @param x       屏幕 X / screen X
         * @param y       屏幕 Y / screen Y
         * @param w       目标宽度 / target width
         * @param h       目标高度 / target height
         */
        void draw(ResourceLocation texture, int x, int y, int w, int h);
    }

    /**
     * 拉伸类型枚举，与 mcmeta 中的 {@code type} 字段对应。<br>
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
     * 九宫格拉伸 —— 将纹理分为 9 个区域：4 角固定、4 边平铺、中心平铺。<br>
     * Nine-patch stretch — splits the texture into 9 regions: 4 fixed corners,
     * 4 tiled edges, and a tiled centre.
     * </p>
     *
     * @param texture 纹理资源 / texture resource
     * @param x       屏幕 X / screen X
     * @param y       屏幕 Y / screen Y
     * @param w       目标宽度 / target width
     * @param h       目标高度 / target height
     * @param edgeL   左边缘宽度(纹理像素) / left edge width (texture pixels)
     * @param edgeT   上边缘高度(纹理像素) / top edge height (texture pixels)
     * @param edgeR   右边缘宽度(纹理像素) / right edge width (texture pixels)
     * @param edgeB   下边缘高度(纹理像素) / bottom edge height (texture pixels)
     * @param texW    纹理总宽度 / total texture width
     * @param texH    纹理总高度 / total texture height
     */
    public static void drawNinePatch(ResourceLocation texture, int x, int y, int w, int h,
            int edgeL, int edgeT, int edgeR, int edgeB,
            int texW, int texH) {
        if (w <= 0 || h <= 0)
            return;

        int innerW = w - edgeL - edgeR;
        int innerH = h - edgeT - edgeB;
        int texInnerW = texW - edgeL - edgeR;
        int texInnerH = texH - edgeT - edgeB;

        if (innerW < 0 || innerH < 0) {
            // Target size smaller than edges — clamp right/bottom edges to fit
            // 目标尺寸小于边缘——缩减右/下边缘以适应实际尺寸
            if (w < edgeL + edgeR) {
                edgeR = Math.max(0, w - edgeL);
                innerW = 0;
            }
            if (h < edgeT + edgeB) {
                edgeB = Math.max(0, h - edgeT);
                innerH = 0;
            }
        }

        // [渲染三域架构] 图集分流：catframe:gui 纹理解析为图集绑定 + sprite UV；
        // 批次中且非图集纹理时先 flush 已收集 quad，再按原路径独立绘制。
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
        // Centre (tiled both directions)
        if (innerW > 0 && innerH > 0) {
            addTiledQuad(t, x + edgeL, y + edgeT, innerW, innerH, edgeL, edgeT, texInnerW, texInnerH, texW, texH, b.sprite);
        }

        if (!batched) {
            t.draw();
            cleanup();
        }
    }

    // ──── Two-end-fixed, middle-repeat (three-patch, horizontal) ────

    /**
     * <p>
     * 两端固定中间重复 —— 水平方向：左端 edgeL 像素固定，右端 edgeR 像素固定，中间 tileW 像素重复平铺。<br>
     * Two-end-fixed middle-repeat — horizontally: left edgeL pixels fixed, right
     * edgeR pixels fixed,
     * middle tileW pixels tiled.
     * </p>
     *
     * @param texture 纹理资源 / texture resource
     * @param x       屏幕 X / screen X
     * @param y       屏幕 Y / screen Y
     * @param w       目标宽度 / target width
     * @param h       目标高度 / target height
     * @param edgeL   左端固定宽度(纹理像素) / left fixed width (texture pixels)
     * @param edgeR   右端固定宽度(纹理像素) / right fixed width (texture pixels)
     * @param tileW   中间平铺单元宽度(纹理像素) / middle tile width (texture pixels)
     * @param texW    纹理总宽度 / total texture width
     * @param texH    纹理总高度 / total texture height
     */
    public static void drawFixedEndRepeat(ResourceLocation texture, int x, int y, int w, int h,
            int edgeL, int edgeR, int tileW,
            int texW, int texH) {
        if (w <= 0 || h <= 0)
            return;

        int middleW = w - edgeL - edgeR;
        if (middleW < 0) {
            // Target width smaller than edges — clamp right edge to fit
            // 目标宽度小于边缘——缩减右边缘以适应实际宽度
            edgeR = Math.max(0, w - edgeL);
            middleW = 0;
        }

        // [渲染三域架构] 图集分流：同 drawNinePatch（批次中非图集纹理先 flush）。
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
     * 整图静态拉伸 —— 将整张纹理（UV 0..1）按整数倍放大绘制到目标区域。<br>
     * Static stretch — draws the whole texture (UV 0..1) scaled by an integer
     * factor over the target area.
     * </p>
     * <p>
     * 目标尺寸必须是纹理原始尺寸的整数倍（如 16×16 纹理只能绘制 16×16、32×32、
     * 48×48…），保证像素完美放大；传入不合适的尺寸将抛出
     * {@link IllegalArgumentException}。<br>
     * The target size must be an integer multiple of the texture's original size
     * (e.g. a 16×16 texture may only draw 16×16, 32×32, 48×48…), guaranteeing
     * pixel-perfect upscaling; invalid sizes throw
     * {@link IllegalArgumentException}.
     * </p>
     *
     * @param texture 纹理资源 / texture resource
     * @param x       屏幕 X / screen X
     * @param y       屏幕 Y / screen Y
     * @param w       目标宽度（须为 texW 整数倍）/ target width (integer multiple of texW)
     * @param h       目标高度（须为 texH 整数倍）/ target height (integer multiple of texH)
     * @param texW    纹理原始宽度 / original texture width
     * @param texH    纹理原始高度 / original texture height
     */
    public static void drawStatic(ResourceLocation texture, int x, int y, int w, int h, int texW, int texH) {
        drawStatic(texture, x, y, w, h, texW, texH, 1.0F);
    }

    /**
     * 整图静态拉伸，带透明度。<br>
     * Static stretch with alpha.
     *
     * @param alpha 透明度 0.0-1.0 / alpha 0.0-1.0
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

        // [渲染三域架构] 图集分流：同 drawNinePatch（批次中非图集纹理先 flush）。
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
     * 通用平铺 —— 将纹理按 tileW×tileH 像素为单位平铺到目标区域。<br>
     * General tiling — tiles the texture in tileW×tileH pixel units across the
     * target area.
     * </p>
     *
     * @param texture 纹理资源 / texture resource
     * @param x       屏幕 X / screen X
     * @param y       屏幕 Y / screen Y
     * @param w       目标宽度 / target width
     * @param h       目标高度 / target height
     * @param tileW   平铺单元宽度 / tile width
     * @param tileH   平铺单元高度 / tile height
     */
    public static void drawTiled(ResourceLocation texture, int x, int y, int w, int h,
            int tileW, int tileH) {
        if (w <= 0 || h <= 0 || tileW <= 0 || tileH <= 0)
            return;

        // [渲染三域架构] 图集分流：同 drawNinePatch（批次中非图集纹理先 flush）。
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
     * <p>
     * 根据 {@code .mcmeta} 文件中的参数自动绘制纹理。找不到元数据时使用回退类型和参数。
     * </p>
     *
     * @param texture      texture resource / 纹理资源
     * @param x            screen X / 屏幕 X
     * @param y            screen Y / 屏幕 Y
     * @param w            target width / 目标宽度
     * @param h            target height / 目标高度
     * @param fallbackType fallback stretch type / 回退拉伸类型
     * @param fallbackW    fallback texture width / 回退纹理宽度
     * @param fallbackH    fallback texture height / 回退纹理高度
     * @param fallbackL    fallback left edge / 回退左边缘
     * @param fallbackT    fallback top edge / 回退上边缘
     * @param fallbackR    fallback right edge / 回退右边缘
     * @param fallbackB    fallback bottom edge / 回退下边缘
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
                            meta.getDefaultWidth(), meta.getDefaultHeight());
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
     * <p>平铺 quad —— 委托 {@link #emitQuad} 统一走批次收集 / 即时绘制
     * （含图集 sprite UV 映射）。</p>
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
