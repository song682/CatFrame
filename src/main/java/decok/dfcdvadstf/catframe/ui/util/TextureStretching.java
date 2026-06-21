package decok.dfcdvadstf.catframe.ui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * 纹理拉伸工具系统 —— 提供可扩展的纹理绘制策略。<br>
 * Texture stretching utility system — provides extensible texture drawing strategies.
 * </p>
 *
 * <h3>内建策略 / Built-in strategies</h3>
 * <ul>
 *   <li>{@link #drawNinePatch} — 九宫格拉伸（4 角固定、4 边平铺、中心平铺）</li>
 *   <li>{@link #drawFixedEndRepeat} — 两端固定中间重复（水平方向）</li>
 *   <li>{@link #drawTiled} — 通用平铺</li>
 * </ul>
 *
 * <h3>扩展方式 / Extension</h3>
 * <p>实现 {@link StretchStrategy} 接口即可定义自定义策略。</p>
 */
public final class TextureStretching {

    private TextureStretching() {}

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
         * <p>使用此策略绘制纹理。</p>
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
        if (w <= 0 || h <= 0) return;

        int innerW = w - edgeL - edgeR;
        int innerH = h - edgeT - edgeB;
        int texInnerW = texW - edgeL - edgeR;
        int texInnerH = texH - edgeT - edgeB;

        if (innerW < 0 || innerH < 0) {
            throw new TextureStretchLimitException(
                    String.format("Nine-patch target size (%dx%d) smaller than edges (%d+%d, %d+%d)",
                            w, h, edgeL, edgeR, edgeT, edgeB));
        }

        bindAndPrepare(texture);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();

        // Top-left corner
        addQuad(t, x, y, edgeL, edgeT, 0, 0, edgeL, edgeT, texW, texH);
        // Top-right corner
        addQuad(t, x + w - edgeR, y, edgeR, edgeT, texW - edgeR, 0, edgeR, edgeT, texW, texH);
        // Bottom-left corner
        addQuad(t, x, y + h - edgeB, edgeL, edgeB, 0, texH - edgeB, edgeL, edgeB, texW, texH);
        // Bottom-right corner
        addQuad(t, x + w - edgeR, y + h - edgeB, edgeR, edgeB, texW - edgeR, texH - edgeB, edgeR, edgeB, texW, texH);

        // Top edge (tiled horizontally)
        if (innerW > 0) {
            addTiledQuad(t, x + edgeL, y, innerW, edgeT, edgeL, 0, texInnerW, edgeT, texW, texH);
        }
        // Bottom edge (tiled horizontally)
        if (innerW > 0) {
            addTiledQuad(t, x + edgeL, y + h - edgeB, innerW, edgeB, edgeL, texH - edgeB, texInnerW, edgeB, texW, texH);
        }
        // Left edge (tiled vertically)
        if (innerH > 0) {
            addTiledQuad(t, x, y + edgeT, edgeL, innerH, 0, edgeT, edgeL, texInnerH, texW, texH);
        }
        // Right edge (tiled vertically)
        if (innerH > 0) {
            addTiledQuad(t, x + w - edgeR, y + edgeT, edgeR, innerH, texW - edgeR, edgeT, edgeR, texInnerH, texW, texH);
        }
        // Centre (tiled both directions)
        if (innerW > 0 && innerH > 0) {
            addTiledQuad(t, x + edgeL, y + edgeT, innerW, innerH, edgeL, edgeT, texInnerW, texInnerH, texW, texH);
        }

        t.draw();
        cleanup();
    }

    // ──── Two-end-fixed, middle-repeat (three-patch, horizontal) ────

    /**
     * <p>
     * 两端固定中间重复 —— 水平方向：左端 edgeL 像素固定，右端 edgeR 像素固定，中间 tileW 像素重复平铺。<br>
     * Two-end-fixed middle-repeat — horizontally: left edgeL pixels fixed, right edgeR pixels fixed,
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
        if (w <= 0 || h <= 0) return;

        int middleW = w - edgeL - edgeR;
        if (middleW < 0) {
            throw new TextureStretchLimitException(
                    String.format("Three-patch target width (%d) smaller than edges (%d+%d)",
                            w, edgeL, edgeR));
        }

        bindAndPrepare(texture);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();

        // Left edge
        addQuad(t, x, y, edgeL, h, 0, 0, edgeL, h, texW, texH);

        // Middle (tiled)
        if (middleW > 0 && tileW > 0) {
            addTiledQuad(t, x + edgeL, y, middleW, h, edgeL, 0, tileW, h, texW, texH);
        }

        // Right edge
        addQuad(t, x + w - edgeR, y, edgeR, h, texW - edgeR, 0, edgeR, h, texW, texH);

        t.draw();
        cleanup();
    }

    // ──── General tiling ────

    /**
     * <p>
     * 通用平铺 —— 将纹理按 tileW×tileH 像素为单位平铺到目标区域。<br>
     * General tiling — tiles the texture in tileW×tileH pixel units across the target area.
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
        if (w <= 0 || h <= 0 || tileW <= 0 || tileH <= 0) return;

        bindAndPrepare(texture);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();

        for (int offX = 0; offX < w; offX += tileW) {
            for (int offY = 0; offY < h; offY += tileH) {
                int drawW = Math.min(tileW, w - offX);
                int drawH = Math.min(tileH, h - offY);

                double u2 = (double) drawW / (double) tileW;
                double v2 = (double) drawH / (double) tileH;

                t.addVertexWithUV(x + offX, y + offY + drawH, 0.0D, 0.0, v2);
                t.addVertexWithUV(x + offX + drawW, y + offY + drawH, 0.0D, u2, v2);
                t.addVertexWithUV(x + offX + drawW, y + offY, 0.0D, u2, 0.0);
                t.addVertexWithUV(x + offX, y + offY, 0.0D, 0.0, 0.0);
            }
        }

        t.draw();
        cleanup();
    }

    // ──── Auto-draw from mcmeta ────

    /**
     * Draw a texture using parameters loaded from its {@code .mcmeta} file.
     * <p>Falls back to the given type with hardcoded defaults if no metadata is found.</p>
     * <p>根据 {@code .mcmeta} 文件中的参数自动绘制纹理。找不到元数据时使用回退类型和参数。</p>
     *
     * @param texture       texture resource / 纹理资源
     * @param x             screen X / 屏幕 X
     * @param y             screen Y / 屏幕 Y
     * @param w             target width / 目标宽度
     * @param h             target height / 目标高度
     * @param fallbackType  fallback stretch type / 回退拉伸类型
     * @param fallbackW     fallback texture width / 回退纹理宽度
     * @param fallbackH     fallback texture height / 回退纹理高度
     * @param fallbackL     fallback left edge / 回退左边缘
     * @param fallbackT     fallback top edge / 回退上边缘
     * @param fallbackR     fallback right edge / 回退右边缘
     * @param fallbackB     fallback bottom edge / 回退下边缘
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
                            meta.getBorderLeft(), meta.getBorderTop(),
                            meta.getBorderRight(), meta.getBorderBottom(),
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
     * <p>Convenience overload that assumes nine-patch with symmetric borders.</p>
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
     * <p>Convenience overload for horizontal three-patch with symmetric edges.</p>
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
     * <p>If texture is null, skips binding (assumes texture already bound).</p>
     */
    private static void bindAndPrepare(ResourceLocation texture) {
        if (texture != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        }
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
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
     * Add a single textured quad (fixed UV, no tiling).
     */
    private static void addQuad(Tessellator t, int sx, int sy, int sw, int sh,
                                float texU, float texV, float texSW, float texSH,
                                int texW, int texH) {
        float u1 = texU / texW;
        float u2 = (texU + texSW) / texW;
        float v1 = texV / texH;
        float v2 = (texV + texSH) / texH;

        t.addVertexWithUV(sx, sy + sh, 0.0D, u1, v2);
        t.addVertexWithUV(sx + sw, sy + sh, 0.0D, u2, v2);
        t.addVertexWithUV(sx + sw, sy, 0.0D, u2, v1);
        t.addVertexWithUV(sx, sy, 0.0D, u1, v1);
    }

    /**
     * Add a tiled textured quad — repeats the UV region across the screen area.
     */
    private static void addTiledQuad(Tessellator t, int sx, int sy, int screenW, int screenH,
                                     float texU, float texV, float texTileW, float texTileH,
                                     int texW, int texH) {
        for (int offX = 0; offX < screenW; offX += (int) texTileW) {
            for (int offY = 0; offY < screenH; offY += (int) texTileH) {
                int drawW = Math.min((int) texTileW, screenW - offX);
                int drawH = Math.min((int) texTileH, screenH - offY);

                float u1 = texU / texW;
                float u2 = (texU + drawW) / texW;
                float v1 = texV / texH;
                float v2 = (texV + drawH) / texH;

                t.addVertexWithUV(sx + offX, sy + offY + drawH, 0.0D, u1, v2);
                t.addVertexWithUV(sx + offX + drawW, sy + offY + drawH, 0.0D, u2, v2);
                t.addVertexWithUV(sx + offX + drawW, sy + offY, 0.0D, u2, v1);
                t.addVertexWithUV(sx + offX, sy + offY, 0.0D, u1, v1);
            }
        }
    }
}
