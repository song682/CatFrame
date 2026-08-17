package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.resources.atlas.layout.TextureStitcher;
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
 *   <li>用 {@link TextureStitcher} 布局 sprite（排序 → 区域分割 → 2^n 扩展）；</li>
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
 * <b>采样策略</b>（消除放大双线性模糊 + 缩小混叠）：
 * <ul>
 *   <li>level-0 全量上传，随后按全局 mip 级别逐级上传 CPU box-filter 生成的 mip
 *       （padding 已按 mip 预留，无渗色）；</li>
 *   <li>MIN_FILTER = GL_NEAREST_MIPMAP_LINEAR（有 mip 时），缩小场景清晰；</li>
 *   <li>MAG_FILTER = GL_NEAREST —— 16×16 内容在 GUI/手持等放大场景下像素锐利，
 *       不再被 GL_LINEAR 双线性插值抹平细节。</li>
 * </ul>
 * <p>
 * <b>M3 动画区域重传</b>：level-0 像素保留于 {@link #atlasPixels}（CPU 侧），
 * 动画 sprite 帧切换后由 {@link #updateAnimationRegion} 更新该区域并
 * glTexSubImage2D 重传 level-0 区域，随后从 level-0 全量重建 mip 链 ——
 * 相比原版整图重传，只动发生变化的区域（原生 CPU 像素优势）。
 *
 * <p>CPU-assembled, GL-uploaded atlas; also serves as the registered
 * {@code ITextureObject} so render layers bind it via {@code catframe:atlas/<id>}.
 */
@SideOnly(Side.CLIENT)
public class CatAtlas implements IAtlas, ITextureObject {

    /** 图集 id（如 {@code minecraft:blocks}）。 */
    private final String atlasId;
    /** 图集内 sprite 查找表：texturePath（发布键）→ CatSprite。
     *  键用发布键而非 iconName —— missing sprite 的 iconName 恒为 "missingno"，
     *  用 iconName 会让多个缺失 sprite 互相覆盖；发布键唯一。 */
    private final Map<String, CatSprite> sprites = new LinkedHashMap<>();
    /** 本图集的 built-in missing sprite（紫黑格；stitch 时记录，图集恒含一个，供缺失查找兜底）。 */
    private CatSprite missingSprite;
    /** GL 纹理对象 id（-1 = 未分配）。 */
    private int glTextureId = -1;
    /** 最终图集尺寸（2^n）。 */
    private int atlasWidth;
    private int atlasHeight;
    /** 上传缓冲区（grow-only 复用，避免每次 stitch 重新分配 direct buffer）。 */
    private ByteBuffer uploadBuffer;
    /** 动画区域重传缓冲区（与 uploadBuffer 独立，避免 tick 期间互扰）。 */
    private ByteBuffer regionBuffer;
    /** level-0 图集像素（CPU 侧保留；M3 动画区域更新 + mip 重建的源）。 */
    private int[] atlasPixels;
    /** 全局 mip 级别（upload 时记录，动画区域更新后按它重建 mip 链）。 */
    private int mipLevel;

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

        TextureStitcher stitcher = new TextureStitcher(maxTextureSize, maxTextureSize, mipLevel, anisotropyBit);
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

        upload(atlasARGB, mipLevel);
        this.atlasPixels = atlasARGB;

        this.sprites.clear();
        this.missingSprite = null;
        for (CatSprite sprite : sprites) {
            this.sprites.put(sprite.getTexturePath(), sprite);
            if (sprite.isMissing()) {
                this.missingSprite = sprite;
            }
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
     * 上传 level-0 与 box-filter mip 层级（ARGB int[] → RGBA ByteBuffer → glTexImage2D）。
     * <p>
     * 采样策略见类 JavaDoc：MIN 用 mipmap 线性（缩小清晰），MAG 用 NEAREST
     * （放大像素锐利，消除 16×16 内容放大时的双线性插值模糊）。
     * 上传前后保存/恢复 glBindTexture，避免污染 Pre 阶段 vanilla GL 状态。
     *
     * @param atlasARGB level-0 图集像素（flat ARGB，row-major）
     * @param mipLevel  全局 mip 级别（CatStitcher 布局用的同一值，padding 已预留）
     */
    private void upload(int[] atlasARGB, int mipLevel) {
        this.mipLevel = mipLevel;
        int prevBound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            if (glTextureId == -1) {
                glTextureId = GL11.glGenTextures();
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);

            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            uploadLevel(0, atlasARGB, atlasWidth, atlasHeight);
            uploadMips(atlasARGB, mipLevel);

            // MAG=NEAREST：放大场景（GUI/手持/掉落/展示框）像素锐利，
            // 消除 GL_LINEAR 在 16×16 内容放大时的双线性插值模糊。
            // Magnification stays NEAREST so enlarged 16×16 sprites keep hard
            // pixel edges instead of bilinear smearing.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } finally {
            // 恢复调用方 GL 状态（绑定纹理与像素行对齐），避免污染 Pre 阶段后续原版缝合的 GL 状态
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevBound);
        }
    }

    /**
     * 在已绑定纹理、已上传 level-0 的前提下生成并上传 box-filter mip 链与采样参数。
     * <p>
     * 逐级下采样（2×2 分离通道平均），上传 mip 1..mips；层级数不超过
     * min(mipLevel, log2(图集尺寸))，尺寸按 GL 规范 floor/2。
     */
    private void uploadMips(int[] level0, int mipLevel) {
        int mips = Math.min(mipLevel, Integer.numberOfTrailingZeros(Math.max(atlasWidth, atlasHeight)));
        if (mips > 0) {
            int[] src = level0;
            int w = atlasWidth, h = atlasHeight;
            for (int level = 1; level <= mips; level++) {
                int nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
                src = boxFilter(src, w, h, nw, nh);
                uploadLevel(level, src, nw, nh);
                w = nw;
                h = nh;
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, mips);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
        } else {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        }
    }

    /**
     * M3 动画区域更新：把 sprite 当前帧像素写入图集区域并重传 level-0 区域，
     * 然后从 {@link #atlasPixels} 全量重建 mip 链（mip 依赖 level-0 内容，无法局部更新）。
     * <p>
     * 由 CatAtlasManager.tickAnimations 在动画帧切换后调用（主线程）。
     * 上传前后保存/恢复 GL 状态，不污染渲染循环。
     *
     * @param sprite 帧已推进的动画 sprite（图集内成员）
     */
    public void updateAnimationRegion(CatSprite sprite) {
        if (atlasPixels == null || glTextureId == -1) {
            return;
        }
        int pad = sprite.getPadding();
        int x = sprite.getOriginX() + pad;
        int y = sprite.getOriginY() + pad;
        int w = sprite.getIconWidth();
        int h = sprite.getIconHeight();
        if (x < 0 || y < 0 || x + w > atlasWidth || y + h > atlasHeight) {
            CatFrame.logger.warn("[CatAtlas] '{}' animation region {}x{} @({},{}) outside atlas {}x{}, skip",
                    atlasId, w, h, x, y, atlasWidth, atlasHeight);
            return;
        }
        int[] frame = sprite.getPixels();
        int prevBound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            // CPU 侧同步：区域行拷贝入 level-0 像素（mip 重建的源）
            for (int row = 0; row < h; row++) {
                System.arraycopy(frame, row * w, atlasPixels, (y + row) * atlasWidth + x, w);
            }
            uploadRegion(x, y, w, h, frame);
            rebuildMipsFromLevel0();
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevBound);
        }
    }

    /**
     * glTexSubImage2D 重传 level-0 的单个区域（ARGB int[] → RGBA ByteBuffer）。
     * 帧数据行宽 = 区域宽 w，GL 默认 UNPACK_ROW_LENGTH 即区域宽，无需额外设置。
     */
    private void uploadRegion(int x, int y, int w, int h, int[] pixels) {
        int byteCount = w * h * 4;
        if (regionBuffer == null || regionBuffer.capacity() < byteCount) {
            regionBuffer = BufferUtils.createByteBuffer(byteCount);
        } else {
            regionBuffer.clear();
        }
        for (int argb : pixels) {
            regionBuffer.put((byte) (argb >> 16 & 0xFF)); // R
            regionBuffer.put((byte) (argb >> 8 & 0xFF));  // G
            regionBuffer.put((byte) (argb & 0xFF));       // B
            regionBuffer.put((byte) (argb >> 24 & 0xFF)); // A
        }
        regionBuffer.flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, w, h,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, regionBuffer);
    }

    /**
     * 从 {@link #atlasPixels}（level-0，已含最新帧）全量重建 mip 1..mips 并重传。
     * 仅在存在 mip 链（mipLevel > 0）时执行；无 mip 的图集动画无需该步。
     */
    private void rebuildMipsFromLevel0() {
        if (mipLevel <= 0) {
            return;
        }
        int mips = Math.min(mipLevel, Integer.numberOfTrailingZeros(Math.max(atlasWidth, atlasHeight)));
        int[] src = atlasPixels;
        int w = atlasWidth, h = atlasHeight;
        for (int level = 1; level <= mips; level++) {
            int nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
            src = boxFilter(src, w, h, nw, nh);
            uploadLevel(level, src, nw, nh);
            w = nw;
            h = nh;
        }
    }

    /**
     * 上传单个 mip 层级：ARGB int[] → RGBA ByteBuffer → glTexImage2D。
     * 复用 grow-only 上传缓冲区。
     */
    private void uploadLevel(int level, int[] pixels, int w, int h) {
        int byteCount = w * h * 4;
        if (uploadBuffer == null || uploadBuffer.capacity() < byteCount) {
            uploadBuffer = BufferUtils.createByteBuffer(byteCount);
        } else {
            uploadBuffer.clear();
        }
        for (int argb : pixels) {
            uploadBuffer.put((byte) (argb >> 16 & 0xFF)); // R
            uploadBuffer.put((byte) (argb >> 8 & 0xFF));  // G
            uploadBuffer.put((byte) (argb & 0xFF));       // B
            uploadBuffer.put((byte) (argb >> 24 & 0xFF)); // A
        }
        uploadBuffer.flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA,
                w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, uploadBuffer);
    }

    /**
     * 2×2 box filter 下采样（ARGB 各通道分离平均）。
     * 目标尺寸按 GL mipmap 规范 floor/2；奇数边缘的不完整 2×2 块取左上像素直通
     * （图集边缘为 padding 透明区，对视觉无影响）。
     *
     * @param src  源像素（尺寸 w×h）
     * @param w    源宽度
     * @param h    源高度
     * @param nw   目标宽度（= max(1, w/2)）
     * @param nh   目标高度（= max(1, h/2)）
     * @return 目标像素（flat ARGB）
     */
    private static int[] boxFilter(int[] src, int w, int h, int nw, int nh) {
        int[] dst = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int sx = x * 2, sy = y * 2;
                int a = src[sy * w + sx];
                // 右/下边缘不足 2×2 时用像素自身填充（1×2 / 2×1 平均）
                int b = (sx + 1 < w) ? src[sy * w + sx + 1] : a;
                int c = (sy + 1 < h) ? src[(sy + 1) * w + sx] : a;
                int d = (sx + 1 < w && sy + 1 < h) ? src[(sy + 1) * w + sx + 1] : a;
                int ar = ((a >> 16 & 0xFF) + (b >> 16 & 0xFF) + (c >> 16 & 0xFF) + (d >> 16 & 0xFF)) >> 2;
                int ag = ((a >> 8 & 0xFF) + (b >> 8 & 0xFF) + (c >> 8 & 0xFF) + (d >> 8 & 0xFF)) >> 2;
                int ab = ((a & 0xFF) + (b & 0xFF) + (c & 0xFF) + (d & 0xFF)) >> 2;
                int aa = ((a >> 24 & 0xFF) + (b >> 24 & 0xFF) + (c >> 24 & 0xFF) + (d >> 24 & 0xFF)) >> 2;
                dst[y * nw + x] = (aa << 24) | (ar << 16) | (ag << 8) | ab;
            }
        }
        return dst;
    }

    /**
     * 空图集兜底：上传 16×16 透明占位纹理，保证渲染层绑定到的 GL 对象有效。
     */
    private void uploadPlaceholder() {
        int[] pixels = new int[16 * 16];
        this.atlasWidth = 16;
        this.atlasHeight = 16;
        upload(pixels, 0);
    }

    // ==================== IAtlas / ITextureObject 契约 ====================

    /** 图集 id（如 {@code minecraft:blocks}），即 {@code IAtlas#getAtlasName()}。 */
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

    /** 按发布键（texturePath）查找 sprite。 */
    public CatSprite getSprite(String texturePath) {
        return sprites.get(texturePath);
    }

    /** 本图集的 built-in missing sprite（缺失查找最终兜底；stitch 后恒非 null）。 */
    public CatSprite getMissingSprite() {
        return missingSprite;
    }

    /** 图集内全部 sprite（texturePath → CatSprite，只读约定）。 */
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
