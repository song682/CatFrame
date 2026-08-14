package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.resources.atlas.layout.CatStitcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CatFrame 自研纹理图集实现 —— CPU 组装 + OpenGL 上传 + sprite 查找。
 * <p>
 * 职责（对标 26.1.2 {@code TextureAtlas}，适配 1.7.10 无 GPU blit 管线）：
 * <ul>
 *   <li>用 {@link CatStitcher} 布局 sprite（排序 → 区域分割 → 2^n 扩展）；</li>
 *   <li>主线程一次性组装 ARGB int[] 图集缓冲区（padding 区域清为透明）并转 RGBA
 *       ByteBuffer，单次 {@code glTexImage2D} 上传 level-0；</li>
 *   <li>上传成功后回调 {@link CatSprite#complete} 写入 UV 数据；</li>
 *   <li>同时实现 {@link ITextureObject}，可经 TextureManager 以
 *       {@code catframe:atlas/<id>} 注册，渲染层绑定零结构改动。</li>
 * </ul>
 * <p>
 * <b>通道转换</b>：本地像素为 Java int ARGB（bits 24-31=A, 16-23=R, 8-15=G, 0-7=B），
 * GL 期望 R,G,B,A 字节序 —— 上传时显式重排（与 {@code AtlasPixelCache} 回读的
 * RGBA→ARGB 方向相反，见其类注释）。
 * <p>
 * M1 仅上传 level-0，min filter 用 GL_LINEAR（无 mipmap）；M4 per-atlas mipmap
 * policy 落地时在此逐级上传 box-filter 生成的 mip 并切换 filter。
 *
 * <p>CPU-assembled, GL-uploaded atlas; also serves as the registered
 * {@code ITextureObject} so render layers bind it via {@code catframe:atlas/<id>}.
 */
@SideOnly(Side.CLIENT)
public class CatAtlas implements IAtlas, ITextureObject {

    /** 图集 id（如 {@code minecraft:block}）。 */
    private final String atlasId;
    /** 图集内 sprite 查找表：iconName → CatSprite。 */
    private final Map<String, CatSprite> sprites = new LinkedHashMap<>();
    /** GL 纹理对象 id（-1 = 未分配）。 */
    private int glTextureId = -1;
    /** 最终图集尺寸（2^n）。 */
    private int atlasWidth;
    private int atlasHeight;
    /** 上传缓冲区（grow-only 复用，避免每次 stitch 重新分配 direct buffer）。 */
    private ByteBuffer uploadBuffer;

    /**
     * @param atlasId 图集 id（{@code IAtlas#getAtlasName()} 即此值）
     */
    public CatAtlas(String atlasId) {
        this.atlasId = atlasId;
    }

    /**
     * 执行布局与上传（主线程、GL 上下文存活时调用）。
     * <p>
     * 流程：计算全局 mip（最小 sprite 拖累全局，降级时警告）→ CatStitcher 布局 →
     * 2^n 最终尺寸 → 一次性 CPU 组装 → glTexImage2D level-0 → 回调 sprite.complete。
     *
     * @param sprites        待缝合 sprite 列表（内容像素已就绪）
     * @param maxTextureSize 尺寸上限（min(GL_MAX_TEXTURE_SIZE, 16384)，调用方传入）
     */
    public void stitch(List<CatSprite> sprites, int maxTextureSize) {
        if (sprites.isEmpty()) {
            // 空图集：仍分配 16×16 占位，保证渲染层绑定有效
            CatFrame.logger.warn("[CatAtlas] '{}' has no sprites, uploading 16x16 placeholder", atlasId);
            uploadPlaceholder();
            return;
        }

        int mipLevel = computeGlobalMipLevel(sprites);
        int anisotropyBit = Minecraft.getMinecraft().gameSettings.anisotropicFiltering;
        CatFrame.logger.info("[CatAtlas] '{}' stitching {} sprites | mip={} aniso={}",
                atlasId, sprites.size(), mipLevel, anisotropyBit);

        CatStitcher stitcher = new CatStitcher(maxTextureSize, maxTextureSize, mipLevel, anisotropyBit);
        for (CatSprite sprite : sprites) {
            stitcher.registerSprite(sprite);
        }
        stitcher.stitch();

        // 最终尺寸：存储包围盒向上取 2^n（GL2.1 下 NPOT + mipmap 不兼容，恒 2^n）
        this.atlasWidth = smallestEncompassingPowerOfTwo(stitcher.getWidth());
        this.atlasHeight = smallestEncompassingPowerOfTwo(stitcher.getHeight());
        if (atlasWidth <= 0) this.atlasWidth = 16;
        if (atlasHeight <= 0) this.atlasHeight = 16;

        // ===== CPU 组装：一次性分配 ARGB 图集缓冲区（fill(0) = 透明 padding） =====
        int[] atlasARGB = new int[atlasWidth * atlasHeight];
        Arrays.fill(atlasARGB, 0);
        final int padding = stitcher.getPadding();
        final int w = atlasWidth;
        final int h = atlasHeight;
        // 单次遍历：写入 UV 数据（complete）并逐行拷贝内容像素到图集缓冲区
        stitcher.gatherSprites((sprite, x, y, pad) -> {
            sprite.complete(x, y, pad, w, h);
            int[] pixels = sprite.getPixels();
            int contentW = sprite.getIconWidth();
            int contentH = sprite.getIconHeight();
            int dstX = x + pad;
            int dstY = y + pad;
            for (int row = 0; row < contentH; row++) {
                System.arraycopy(pixels, row * contentW,
                        atlasARGB, (dstY + row) * w + dstX, contentW);
            }
        });

        upload(atlasARGB);

        this.sprites.clear();
        for (CatSprite sprite : sprites) {
            this.sprites.put(sprite.getIconName(), sprite);
        }

        CatFrame.logger.info("[CatAtlas] '{}' stitched: atlas {}x{} | sprites={} | padding={}",
                atlasId, atlasWidth, atlasHeight, sprites.size(), padding);
    }

    /**
     * 计算全局 mip 级别：min(游戏 mipmap 设置, 全部 sprite 的最小个人 mip)。
     * 个人 mip = floor(log2(min(min(w,h), lowestOneBit))) —— 小尺寸纹理拖累全局时警告。
     */
    private int computeGlobalMipLevel(List<CatSprite> sprites) {
        int gameMip = Math.max(0, Minecraft.getMinecraft().gameSettings.mipmapLevels);
        int global = Integer.MAX_VALUE;
        String limiter = null;
        for (CatSprite sprite : sprites) {
            int v = Math.min(sprite.getIconWidth(), sprite.getIconHeight());
            int mip = Integer.numberOfTrailingZeros(Integer.lowestOneBit(v));
            if (mip < global) {
                global = mip;
                limiter = sprite.getIconName();
            }
        }
        int mipLevel = Math.min(gameMip, global == Integer.MAX_VALUE ? 0 : global);
        if (mipLevel < gameMip && limiter != null) {
            CatFrame.logger.warn("[CatAtlas] '{}' texture '{}' ({}) limits atlas mip level from {} to {}",
                    atlasId, limiter,
                    findSize(sprites, limiter), gameMip, mipLevel);
        }
        return mipLevel;
    }

    private static String findSize(List<CatSprite> sprites, String name) {
        for (CatSprite s : sprites) {
            if (name.equals(s.getIconName())) {
                return s.getIconWidth() + "x" + s.getIconHeight();
            }
        }
        return "?";
    }

    /**
     * 上传 level-0（ARGB int[] → RGBA ByteBuffer → glTexImage2D）。
     * 上传前后保存/恢复 glBindTexture，避免污染 Pre 阶段 vanilla GL 状态。
     */
    private void upload(int[] atlasARGB) {
        int prevBound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            if (glTextureId == -1) {
                glTextureId = GL11.glGenTextures();
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);

            // ARGB int[] → RGBA 字节序（与 AtlasPixelCache 回读方向相反）
            int byteCount = atlasWidth * atlasHeight * 4;
            if (uploadBuffer == null || uploadBuffer.capacity() < byteCount) {
                uploadBuffer = BufferUtils.createByteBuffer(byteCount);
            } else {
                uploadBuffer.clear();
            }
            for (int argb : atlasARGB) {
                uploadBuffer.put((byte) (argb >> 16 & 0xFF)); // R
                uploadBuffer.put((byte) (argb >> 8 & 0xFF));  // G
                uploadBuffer.put((byte) (argb & 0xFF));       // B
                uploadBuffer.put((byte) (argb >> 24 & 0xFF)); // A
            }
            uploadBuffer.flip();

            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                    atlasWidth, atlasHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, uploadBuffer);
            // M1 无 mipmap：线性过滤 + CLAMP（M4 per-atlas mipmap policy 在此切换 filter 并逐级上传）
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } finally {
            // 恢复调用方 GL 状态（绑定纹理与像素行对齐），避免污染 Pre 阶段后续原版缝合的 GL 状态
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevBound);
        }
    }

    /**
     * 空图集兜底：上传 16×16 透明占位纹理，保证渲染层绑定到的 GL 对象有效。
     */
    private void uploadPlaceholder() {
        int[] pixels = new int[16 * 16];
        this.atlasWidth = 16;
        this.atlasHeight = 16;
        upload(pixels);
    }

    // ==================== IAtlas / ITextureObject 契约 ====================

    /** 图集 id（如 {@code minecraft:block}），即 {@code IAtlas#getAtlasName()}。 */
    @Override
    public String getAtlasName() {
        return atlasId;
    }

    /**
     * GL 纹理对象 id（首次访问时惰性分配）。实现 ITextureObject 契约。
     */
    @Override
    public int getGlTextureId() {
        if (glTextureId == -1) {
            glTextureId = GL11.glGenTextures();
        }
        return glTextureId;
    }

    /**
     * 删除 GL 纹理对象（资源重载/图集重建时由 CatAtlasManager 调用，防 GL 泄漏）。
     */
    public void deleteGlTexture() {
        if (glTextureId != -1) {
            GL11.glDeleteTextures(glTextureId);
            glTextureId = -1;
        }
    }

    /**
     * 空实现：纹理内容由 {@link #stitch} 在 stitch 编排中上传，
     * TextureManager 仅作注册与绑定用途（loadTexture 契约）。
     */
    @Override
    public void loadTexture(IResourceManager resourceManager) {
        // 上传由 CatAtlasManager 在 stitch 编排中显式完成
    }

    // ==================== 查找 ====================

    /** 按 icon 名称查找 sprite。 */
    public CatSprite getSprite(String iconName) {
        return sprites.get(iconName);
    }

    /** 图集内全部 sprite（iconName → CatSprite，只读约定）。 */
    public Map<String, CatSprite> getSprites() {
        return Collections.unmodifiableMap(sprites);
    }

    /** 最终图集宽度（2^n）。 */
    public int getAtlasWidth() {
        return atlasWidth;
    }

    /** 最终图集高度（2^n）。 */
    public int getAtlasHeight() {
        return atlasHeight;
    }

    /** 不小于 value 的最小 2^n。 */
    private static int smallestEncompassingPowerOfTwo(int value) {
        int i = Integer.highestOneBit(Math.max(1, value));
        return i >= value ? i : i << 1;
    }
}
