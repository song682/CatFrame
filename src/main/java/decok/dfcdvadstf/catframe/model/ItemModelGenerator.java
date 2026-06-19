package decok.dfcdvadstf.catframe.model;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import decok.dfcdvadstf.catframe.CatFrame;

/**
 * 物品模型侧面生成器 —— 对标 26.1.2 {@code ItemModelGenerator.bakeSideFaces}。
 * <p>
 * 逐像素扫描纹理，在"不透明→透明"的交界处生成 1px 宽的侧面 quad，
 * 让 2D 物品的挤出厚度侧面拥有与边缘像素一致的颜色。
 * <p>
 * 与 ModelResolver 中 builtin/generated 的 from/to (7.5→8.5) 一致：
 * 侧面 quad 的 Z 范围为 {@code MIN_Z/16 ~ MAX_Z/16}（block 空间）。
 */
public class ItemModelGenerator {

    /** Z 轴挤出范围（像素空间），与 ModelResolver 中 builtin/generated 的 from/to 一致 */
    static final float MIN_Z = 7.5F;
    static final float MAX_Z = 8.5F;

    /**
     * 保留的纹理帧数据缓存。
     * <p>
     * Minecraft 1.7.10 的 {@code TextureMap.loadTextureAtlas} 在将非动画 sprite
     * 上传 GPU 后会调用 {@code clearFramesTextureData()} 清空帧数据。
     * 而模型烘焙发生在 atlas 加载之后，此时 {@code getFrameTextureData(0)} 已返回空。
     * <p>
     * 此缓存由 {@code MixinTextureMap} 在 clear 之前保存，供侧面生成器使用。
     * 键为 sprite 名称（如 "stick"、"diamond_sword"），值为帧数据（mipmap 层级数组）。
     */
    public static final Map<String, int[][]> preservedFrames = new HashMap<>();

    /**
     * 清空保留的帧数据缓存。在所有模型烘焙完成后调用以释放内存。
     */
    public static void clearPreservedFrames() {
        preservedFrames.clear();
    }

    /**
     * 为一个图层纹理生成侧面 quad。
     *
     * @param icon      图层纹理 sprite
     * @param tintIndex 染色索引（多层模型时对应 layerN），-1 表示无染色
     * @return 侧面 BakedQuad 列表
     */
    public static List<BlockJsonModelBake.BakedQuad> bakeSideFaces(IIcon icon, int tintIndex) {
        List<BlockJsonModelBake.BakedQuad> quads = new ArrayList<>();

        if (!(icon instanceof TextureAtlasSprite)) {
            return quads;
        }
        TextureAtlasSprite sprite = (TextureAtlasSprite) icon;

        int width = sprite.getIconWidth();
        int height = sprite.getIconHeight();

        // 纹理帧数据可能已被 TextureMap.clearFramesTextureData() 清空，
        // 优先从 MixinTextureMap 保存的缓存中获取
        int[][] frameData = null;
        if (sprite.getFrameCount() > 0) {
            frameData = sprite.getFrameTextureData(0);
        }
        if (frameData == null || frameData.length == 0 || frameData[0] == null) {
            frameData = preservedFrames.get(sprite.getIconName());
        }
        if (frameData == null || frameData.length == 0) {
            CatFrame.logger.debug("[ItemModelGenerator] no frame data for sprite '{}'", sprite.getIconName());
            return quads;
        }

        // getFrameTextureData(0) 返回 int[mipmapLevels][flatPixels]
        // level 0 是扁平数组，大小为 width*height，不是二维 [y][x]
        int[] flatPixels = frameData[0];
        if (flatPixels == null || flatPixels.length < width * height) {
            CatFrame.logger.warn("[ItemModelGenerator] flat pixel data too small: "
                    + "declared={}x{}={}px, actual={}px for sprite '{}'",
                    width, height, width * height, flatPixels != null ? flatPixels.length : 0, sprite.getIconName());
            return quads;
        }

        float xScale = 16.0F / width;
        float yScale = 16.0F / height;

        // 使用 Set 去重：同一像素的同一方向只生成一次（多帧扫描时重复）
        Set<String> emitted = new HashSet<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isTransparent(flatPixels, x, y, width)) {
                    continue;
                }

                // 读取当前不透明像素的 ARGB 颜色，用于侧面纯色填充
                int pixelARGB = flatPixels[y * width + x];

                // 上边：上方像素透明 → 生成 UP 面 quad
                if (isTransparent(flatPixels, x, y - 1, width)) {
                    String key = "U" + x + "," + y;
                    if (emitted.add(key)) {
                        bakeHorizontalEdge(quads, sprite, x, y, true, xScale, yScale, tintIndex, pixelARGB);
                    }
                }
                // 下边：下方像素透明 → 生成 DOWN 面 quad
                if (isTransparent(flatPixels, x, y + 1, width)) {
                    String key = "D" + x + "," + y;
                    if (emitted.add(key)) {
                        bakeHorizontalEdge(quads, sprite, x, y, false, xScale, yScale, tintIndex, pixelARGB);
                    }
                }
                // 左边：左侧像素透明 → 生成 EAST 面 quad（26.1.2 LEFT→EAST）
                if (isTransparent(flatPixels, x - 1, y, width)) {
                    String key = "L" + x + "," + y;
                    if (emitted.add(key)) {
                        bakeVerticalEdge(quads, sprite, x, y, true, xScale, yScale, tintIndex, pixelARGB);
                    }
                }
                // 右边：右侧像素透明 → 生成 WEST 面 quad（26.1.2 RIGHT→WEST）
                if (isTransparent(flatPixels, x + 1, y, width)) {
                    String key = "R" + x + "," + y;
                    if (emitted.add(key)) {
                        bakeVerticalEdge(quads, sprite, x, y, false, xScale, yScale, tintIndex, pixelARGB);
                    }
                }
            }
        }

        CatFrame.logger.info("[ItemModelGenerator] sprite '{}' {}x{}: {} edge pixels → {} side quads",
                sprite.getIconName(), width, height, emitted.size(), quads.size());

        return quads;
    }

    /**
     * 生成水平边缘（上/下）的侧面 quad —— 对标 BlockJsonModelBake.emitFaceFromCorners 的绕序规范。
     * <p>
     * quad 是 XZ 平面上的平片：宽 = 1px，深 = 1px（Z: 7.5→8.5）。
     * UP 和 DOWN 使用不同的顶点绕序以产生正确的法线方向。
     */
    private static void bakeHorizontalEdge(
            List<BlockJsonModelBake.BakedQuad> quads,
            TextureAtlasSprite sprite,
            int x, int y, boolean isTop,
            float xScale, float yScale, int tintIndex, int pixelARGB) {

        // 世界坐标（block 空间 0-1）
        float worldX0 = (x * xScale) / 16.0F;
        float worldX1 = ((x + 1.0F) * xScale) / 16.0F;

        // Y: 逐像素定位
        //    纹理 y=0 是顶部 → 世界 Y=1；y=height 是底部 → 世界 Y=0
        float worldY;
        if (isTop) {
            // UP = 像素上边缘
            worldY = (16.0F - y * yScale) / 16.0F;
        } else {
            // DOWN = 像素下边缘
            worldY = (16.0F - (y + 1.0F) * yScale) / 16.0F;
        }

        // UV（0-16 抽象空间）—— 侧面所有顶点采样同一像素中心，确保纯色一致
        float uCenter = (x + 0.5F) * xScale;
        float vCenter = (y + 0.5F) * yScale;

        EnumFacing face = isTop ? EnumFacing.UP : EnumFacing.DOWN;

        BlockJsonModelBake.BakedQuad q = new BlockJsonModelBake.BakedQuad();
        q.icon = sprite;
        q.face = face;
        q.tintIndex = tintIndex;
        q.guiLight = "front";
        q.solidColor = pixelARGB;

        // 顶点绕序：对标 BlockJsonModelBake.emitFaceFromCorners
        //   UP:  v0(idx=010)=MIN_X,MIN_Z  v1(idx=011)=MIN_X,MAX_Z  v2(idx=111)=MAX_X,MAX_Z  v3(idx=110)=MAX_X,MIN_Z
        //   DOWN:v0(idx=001)=MIN_X,MAX_Z  v1(idx=000)=MIN_X,MIN_Z  v2(idx=100)=MAX_X,MIN_Z  v3(idx=101)=MAX_X,MAX_Z
        if (isTop) {
            q.vx[0] = worldX0; q.vy[0] = worldY; q.vz[0] = MIN_Z / 16.0F;
            q.vx[1] = worldX0; q.vy[1] = worldY; q.vz[1] = MAX_Z / 16.0F;
            q.vx[2] = worldX1; q.vy[2] = worldY; q.vz[2] = MAX_Z / 16.0F;
            q.vx[3] = worldX1; q.vy[3] = worldY; q.vz[3] = MIN_Z / 16.0F;
        } else {
            q.vx[0] = worldX0; q.vy[0] = worldY; q.vz[0] = MAX_Z / 16.0F;
            q.vx[1] = worldX0; q.vy[1] = worldY; q.vz[1] = MIN_Z / 16.0F;
            q.vx[2] = worldX1; q.vy[2] = worldY; q.vz[2] = MIN_Z / 16.0F;
            q.vx[3] = worldX1; q.vy[3] = worldY; q.vz[3] = MAX_Z / 16.0F;
        }
        // 侧面 quad 所有顶点 UV 指向像素中心，配合 solidColor 实现纯色填充
        for (int i = 0; i < 4; i++) {
            q.up[i] = uCenter;
            q.vp[i] = vCenter;
        }

        q.faceNormal = faceNormal(face);
        quads.add(q);
    }

    /**
     * 生成垂直边缘（左/右）的侧面 quad —— 对标 BlockJsonModelBake.emitFaceFromCorners 的绕序规范。
     * <p>
     * quad 是 YZ 平面上的平片：高 = 1px，深 = 1px（Z: 7.5→8.5）。
     * <p>
     * 26.1.2 方向约定：
     *   LEFT  → Direction.EAST  (法线 +X，面在像素左侧，从左边可见)
     *   RIGHT → Direction.WEST  (法线 -X，面在像素右侧，从右边可见)
     */
    private static void bakeVerticalEdge(
            List<BlockJsonModelBake.BakedQuad> quads,
            TextureAtlasSprite sprite,
            int x, int y, boolean isLeft,
            float xScale, float yScale, int tintIndex, int pixelARGB) {

        // 世界坐标（block 空间 0-1）
        float worldX = isLeft ? 0.0F : 1.0F;

        // Y: 像素 y → block 空间（Y 翻转：纹理上方 → 世界上方）
        float worldY0 = (16.0F - (y + 1.0F) * yScale) / 16.0F;
        float worldY1 = (16.0F - y * yScale) / 16.0F;

        // UV（垂直面所有顶点采样同一像素中心，配合 solidColor 实现纯色填充）
        float uCenter = (x + 0.5F) * xScale;
        float vCenter = (y + 0.5F) * yScale;

        // 26.1.2: LEFT→EAST, RIGHT→WEST
        EnumFacing face = isLeft ? EnumFacing.EAST : EnumFacing.WEST;

        BlockJsonModelBake.BakedQuad q = new BlockJsonModelBake.BakedQuad();
        q.icon = sprite;
        q.face = face;
        q.tintIndex = tintIndex;
        q.guiLight = "front";
        q.solidColor = pixelARGB;

        // 顶点绕序：对标 BlockJsonModelBake.emitFaceFromCorners
        //   EAST: v0(idx=111)=MAX_Y,MAX_Z  v1(idx=101)=MIN_Y,MAX_Z  v2(idx=100)=MIN_Y,MIN_Z  v3(idx=110)=MAX_Y,MIN_Z
        //   WEST: v0(idx=010)=MAX_Y,MIN_Z  v1(idx=000)=MIN_Y,MIN_Z  v2(idx=001)=MIN_Y,MAX_Z  v3(idx=011)=MAX_Y,MAX_Z
        if (isLeft) {
            // EAST face (LEFT edge)
            q.vx[0] = worldX; q.vy[0] = worldY1; q.vz[0] = MAX_Z / 16.0F;
            q.vx[1] = worldX; q.vy[1] = worldY0; q.vz[1] = MAX_Z / 16.0F;
            q.vx[2] = worldX; q.vy[2] = worldY0; q.vz[2] = MIN_Z / 16.0F;
            q.vx[3] = worldX; q.vy[3] = worldY1; q.vz[3] = MIN_Z / 16.0F;
        } else {
            // WEST face (RIGHT edge)
            q.vx[0] = worldX; q.vy[0] = worldY1; q.vz[0] = MIN_Z / 16.0F;
            q.vx[1] = worldX; q.vy[1] = worldY0; q.vz[1] = MIN_Z / 16.0F;
            q.vx[2] = worldX; q.vy[2] = worldY0; q.vz[2] = MAX_Z / 16.0F;
            q.vx[3] = worldX; q.vy[3] = worldY1; q.vz[3] = MAX_Z / 16.0F;
        }
        // 侧面 quad 所有顶点 UV 指向像素中心，配合 solidColor 实现纯色填充
        for (int i = 0; i < 4; i++) {
            q.up[i] = uCenter;
            q.vp[i] = vCenter;
        }

        q.faceNormal = faceNormal(face);
        quads.add(q);
    }

    /**
     * 检查扁平像素数组中 (x,y) 是否透明（超出边界视为透明）。
     * getFrameTextureData(0) 返回 mipmap 层级数组，level 0 是 width*height 扁平数组。
     */
    private static boolean isTransparent(int[] flatPixels, int x, int y, int width) {
        if (x < 0 || y < 0 || x >= width) {
            return true;
        }
        int idx = y * width + x;
        if (idx < 0 || idx >= flatPixels.length) {
            return true;
        }
        return (flatPixels[idx] >> 24 & 0xFF) == 0;
    }

    private static double[] faceNormal(EnumFacing face) {
        switch (face) {
            case DOWN:  return new double[]{ 0, -1,  0};
            case UP:    return new double[]{ 0,  1,  0};
            case NORTH: return new double[]{ 0,  0, -1};
            case SOUTH: return new double[]{ 0,  0,  1};
            case WEST:  return new double[]{-1,  0,  0};
            case EAST:  return new double[]{ 1,  0,  0};
            default:    return new double[]{ 0,  1,  0};
        }
    }
}
