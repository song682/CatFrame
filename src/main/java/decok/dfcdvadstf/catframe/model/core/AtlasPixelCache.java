package decok.dfcdvadstf.catframe.model.core;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.ItemModelGenerator;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * GPU 侧纹理图集回读缓存 —— 替代 {@code MixinTextureMap} 的 CPU 帧数据保存机制。
 * <p>
 * 在 {@link net.minecraftforge.client.event.TextureStitchEvent.Post} 阶段（主线程、GL
 * 上下文活着、
 * atlas 已上传完毕）通过 {@code glGetTexImage} 一次性回读整张图集 level-0，
 * 再按各 sprite 的 UV 坐标切出子矩形并转为 ARGB int 数组。
 * <p>
 * 异步烘焙线程只读此纯 CPU 缓存（线程安全，与原 {@code preservedFrames} 读法一致），
 * 不触碰 GL 上下文。
 * <p>
 * <b>通道转换</b>：{@code glGetTexImage(GL_RGBA, GL_UNSIGNED_BYTE)} 的字节序为 R,G,B,A；
 * 而代码中（{@link ItemModelGenerator#bakeSideFaces}）按 Java int ARGB 格式
 * （bits 24-31=A, 16-23=R, 8-15=G, 0-7=B）处理。本类在回读时完成 RGBA→ARGB 重排。
 */
public final class AtlasPixelCache {

    private AtlasPixelCache() {
    }

    /**
     * 每个 sprite 的 level-0 内容像素切片（flat ARGB int[]，大小 = width × height）。
     * 键为 {@link TextureAtlasSprite#getIconName()}。
     * <p>
     * 切片尺寸按 sprite 的 UV 区域（minU~maxU）换算，而非 getIconWidth()/getIconHeight()：
     * 开启各向异性过滤时 1.7.10 原版会把物理存储扩为 (内容+16)x(内容+16)、并把 UV 缩进
     * 指向居中的内容区域（见 TextureAtlasSprite.loadSprite / initSprite /
     * prepareAnisotropicData），
     * 此时 getIconWidth() 返回物理存储尺寸，若直接用作切片宽会混入相邻 sprite 的像素。
     */
    private static final Map<String, CachedSprite> spriteCache = new HashMap<>();

    /**
     * 按 UV 区域裁剪出的 sprite 像素切片（内容区域，不含各向异性过滤的填充边框）。
     */
    public static final class CachedSprite {
        /** flat ARGB 像素数组（row-major，width × height） */
        public final int[] pixels;
        /** 内容区域宽度（像素） */
        public final int width;
        /** 内容区域高度（像素） */
        public final int height;

        CachedSprite(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * 从 GPU 回读指定图集的 level-0 像素，并为给定的 sprite 集合切出子矩形。
     * <p>
     * <b>必须在主线程（GL 上下文活着）调用。</b>
     *
     * @param atlas 纹理图集（block atlas 或 item atlas）
     * @param icons 需要缓存像素的 sprite 集合（通常来自
     *              {@link VanillaTextureTracker#textureIcons}）
     */
    public static void readAtlas(TextureMap atlas, Collection<IIcon> icons) {
        if (atlas == null || icons == null || icons.isEmpty())
            return;

        int glTexId = atlas.getGlTextureId();
        if (glTexId <= 0) {
            CatFrame.logger.warn("[AtlasPixelCache] atlas GL texture id invalid: {}", glTexId);
            return;
        }

        // 绑定并查询 atlas 尺寸
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexId);
        int atlasW = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int atlasH = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (atlasW <= 0 || atlasH <= 0) {
            CatFrame.logger.warn("[AtlasPixelCache] atlas dimensions invalid: {}x{}", atlasW, atlasH);
            return;
        }

        // 一次性回读整张 atlas level-0（RGBA 字节序）
        ByteBuffer buffer = BufferUtils.createByteBuffer(atlasW * atlasH * 4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

        // 转为 ARGB int 数组（全图）
        int[] atlasARGB = new int[atlasW * atlasH];
        for (int i = 0; i < atlasARGB.length; i++) {
            int r = buffer.get() & 0xFF;
            int g = buffer.get() & 0xFF;
            int b = buffer.get() & 0xFF;
            int a = buffer.get() & 0xFF;
            atlasARGB[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        // 逐 sprite 切片
        int cached = 0;
        for (IIcon icon : icons) {
            if (!(icon instanceof TextureAtlasSprite))
                continue;
            TextureAtlasSprite sprite = (TextureAtlasSprite) icon;

            // 切片尺寸按 UV 区域换算（内容区域）：各向异性过滤时 1.7.10 原版把物理存储
            // 扩为 (内容+16)x(内容+16) 并把 UV 缩进指向居中的内容区域，getIconWidth()
            // 返回物理存储尺寸，直接作为切片宽会混入相邻 sprite 的像素（见类注释）。
            int w = Math.max(1, Math.round((sprite.getMaxU() - sprite.getMinU()) * atlasW));
            int h = Math.max(1, Math.round((sprite.getMaxV() - sprite.getMinV()) * atlasH));
            if (w <= 0 || h <= 0)
                continue;

            // 从 UV 反算像素原点（atlas 坐标系）
            int originX = Math.round(sprite.getMinU() * atlasW);
            int originY = Math.round(sprite.getMinV() * atlasH);

            // 安全边界检查
            if (originX < 0 || originY < 0
                    || originX + w > atlasW || originY + h > atlasH) {
                CatFrame.logger.debug(
                        "[AtlasPixelCache] sprite '{}' out of bounds: origin=({},{}) size={}x{} atlas={}x{}",
                        sprite.getIconName(), originX, originY, w, h, atlasW, atlasH);
                continue;
            }

            // 切出子矩形（flat array, row-major）
            int[] pixels = new int[w * h];
            for (int sy = 0; sy < h; sy++) {
                int srcOffset = (originY + sy) * atlasW + originX;
                System.arraycopy(atlasARGB, srcOffset, pixels, sy * w, w);
            }

            spriteCache.put(sprite.getIconName(), new CachedSprite(pixels, w, h));
            cached++;
        }

        CatFrame.logger.info("[AtlasPixelCache] readAtlas: {}x{} | sprites cached: {} / {}",
                atlasW, atlasH, cached, icons.size());
    }

    /**
     * 获取指定 sprite 的 level-0 内容像素切片（flat ARGB int[]）。
     *
     * @param iconName sprite 名称（如 "stick"、"diamond_sword"）
     * @return 像素数组，或 null（未缓存时）
     */
    public static int[] getPixels(String iconName) {
        CachedSprite cached = spriteCache.get(iconName);
        return cached != null ? cached.pixels : null;
    }

    /**
     * 获取指定 sprite 的内容像素切片（含内容宽高）。
     *
     * @param iconName sprite 名称（如 "stick"、"diamond_sword"）
     * @return 切片对象，或 null（未缓存时）
     */
    public static CachedSprite getSprite(String iconName) {
        return spriteCache.get(iconName);
    }

    /**
     * 清空缓存（资源重载时调用）。
     */
    public static void clear() {
        spriteCache.clear();
    }
}
