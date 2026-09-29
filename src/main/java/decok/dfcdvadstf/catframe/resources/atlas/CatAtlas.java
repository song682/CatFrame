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
 * CatFrame texture atlas implementation — CPU assembly + OpenGL upload + sprite
 * lookup.
 * <p>
 * Responsibilities (mirrors 26.1.2 {@code TextureAtlas}, adapted to the 1.7.10
 * world without a GPU blit pipeline):
 * <ul>
 *   <li>lay out sprites with {@link TextureStitcher} (sort → region split → 2^n
 *       expansion);</li>
 *   <li>assemble the ARGB int[] atlas buffer in one pass on the main thread
 *       (padding areas cleared to transparent), convert to an RGBA ByteBuffer and
 *       upload level-0 with a single {@code glTexImage2D};</li>
 *   <li>after a successful upload, call {@link CatSprite#complete} to write the UV
 *       data;</li>
 *   <li>also implements {@link ITextureObject} so it can be registered with the
 *       TextureManager as {@code catframe:atlas/<id>}, letting render layers bind
 *       it with zero structural changes.</li>
 * </ul>
 * <p>
 * [Render three-domain architecture] This class is now a <b>pure UI atlas tool</b>
 * (the in-house CatAtlas blocks/items stitching chain is retired): it is driven by
 * {@code UiTextureAtlasManager} to stitch the {@code catframe:gui} atlas. The
 * constructor parameter {@code mipmapEnabled} controls mip-chain generation: the
 * UI-domain red line is <b>no mipmap</b> (GUI assets are drawn 1:1 orthogonally;
 * mips bring no benefit and add bleed risk), so pass {@code false}; the mip path
 * from the old blocks/items era is kept as a generic capability (at mipLevel 0 the
 * whole chain short-circuits automatically).
 * <p>
 * <b>Channel conversion</b>: local pixels are Java int ARGB (bits 24-31=A, 16-23=R,
 * 8-15=G, 0-7=B) while GL expects R,G,B,A byte order — the upload reorders
 * explicitly (the reverse direction of the RGBA→ARGB read-back in
 * {@code AtlasPixelCache}, see its class comment).
 * <p>
 * <b>Sampling strategy</b> (eliminates magnification bilinear blur + minification
 * aliasing):
 * <ul>
 *   <li>level-0 is uploaded in full, then each CPU box-filtered mip is uploaded per
 *       the global mip level (padding is pre-reserved per mip, so no bleeding);</li>
 *   <li>MIN_FILTER = GL_NEAREST_MIPMAP_LINEAR (when mips exist): crisp when
 *       minified;</li>
 *   <li>MAG_FILTER = GL_NEAREST — 16×16 content stays pixel-sharp in magnified
 *       scenarios such as GUI/hand-held, no longer flattened by GL_LINEAR
 *       bilinear interpolation.</li>
 * </ul>
 * <p>
 * <b>M3 animated region re-upload</b>: level-0 pixels are retained in
 * {@link #atlasPixels} (CPU side); after an animated sprite switches frames,
 * {@link #updateAnimationRegion} updates that region and re-uploads the level-0
 * region via glTexSubImage2D, then rebuilds the whole mip chain from level-0 —
 * compared to the vanilla full-image re-upload, only the changed region is
 * touched (an advantage of native CPU pixels).
 */
@SideOnly(Side.CLIENT)
public class CatAtlas implements IAtlas, ITextureObject {

    /** Atlas id (e.g. {@code minecraft:blocks} / {@code catframe:gui}). */
    private final String atlasId;
    /** Whether to generate the mip chain (always false for the UI domain: the no-mipmap red line; when false every mip-related path short-circuits). */
    private final boolean mipmapEnabled;
    /** Sprite lookup table: texturePath (publish key) → CatSprite.
     *  The key is the publish key rather than iconName — a missing sprite's iconName is
     *  always "missingno", so using iconName would make multiple missing sprites
     *  overwrite each other; the publish key is unique. */
    private final Map<String, CatSprite> sprites = new LinkedHashMap<>();
    /** This atlas' built-in missing sprite (purple-black square; recorded at stitch time, always exactly one per atlas, used as fallback for missing lookups). */
    private CatSprite missingSprite;
    /** GL texture object id (-1 = not allocated). */
    private int glTextureId = -1;
    /** Final atlas size (2^n). */
    private int atlasWidth;
    private int atlasHeight;
    /** Upload buffer (grow-only reuse, avoiding a fresh direct-buffer allocation per stitch). */
    private ByteBuffer uploadBuffer;
    /** Animation region re-upload buffer (separate from uploadBuffer to avoid interference during ticks). */
    private ByteBuffer regionBuffer;
    /** level-0 atlas pixels (retained CPU-side; the source for M3 animated region updates + mip rebuild). */
    private int[] atlasPixels;
    /** Global mip level (recorded at upload time; the mip chain is rebuilt from it after animated region updates). */
    private int mipLevel;

    /**
     * @param atlasId       atlas id (this value is what {@code IAtlas#getAtlasName()} returns)
     * @param mipmapEnabled whether to generate the mip chain; pass false for the UI atlas ({@code catframe:gui}, the no-mipmap red line)
     */
    public CatAtlas(String atlasId, boolean mipmapEnabled) {
        this.atlasId = atlasId;
        this.mipmapEnabled = mipmapEnabled;
    }

    /**
     * Performs layout and upload (called on the main thread while the GL context is
     * alive).
     * <p>
     * Flow: compute the global mip (the smallest sprite drags the global level down,
     * warned when degraded) → CatStitcher layout → 2^n final size → one-pass CPU
     * assembly → glTexImage2D level-0 → callback sprite.complete.
     *
     * @param sprites        sprites awaiting stitching (content pixels already prepared)
     * @param maxTextureSize size limit (min(GL_MAX_TEXTURE_SIZE, 16384), supplied by the caller)
     */
    public void stitch(List<CatSprite> sprites, int maxTextureSize) {
        if (sprites.isEmpty()) {
            // Empty atlas: still allocate a 16×16 placeholder so render-layer bindings stay valid
            CatFrame.logger.warn("[CatAtlas] '{}' has no sprites, uploading 16x16 placeholder", atlasId);
            uploadPlaceholder();
            return;
        }

        int mipLevel = mipmapEnabled ? computeGlobalMipLevel(sprites) : 0;
        int anisotropyBit = Minecraft.getMinecraft().gameSettings.anisotropicFiltering;
        CatFrame.logger.info("[CatAtlas] '{}' stitching {} sprites | mip={} aniso={}",
                atlasId, sprites.size(), mipLevel, anisotropyBit);

        TextureStitcher stitcher = new TextureStitcher(maxTextureSize, maxTextureSize, mipLevel, anisotropyBit);
        for (CatSprite sprite : sprites) {
            stitcher.registerSprite(sprite);
        }
        stitcher.stitch();

        // Final size: the storage bounding box rounds up to 2^n (NPOT + mipmap are incompatible under GL2.1, so always 2^n)
        this.atlasWidth = smallestEncompassingPowerOfTwo(stitcher.getWidth());
        this.atlasHeight = smallestEncompassingPowerOfTwo(stitcher.getHeight());
        if (atlasWidth <= 0) this.atlasWidth = 16;
        if (atlasHeight <= 0) this.atlasHeight = 16;

        // ===== CPU assembly: allocate the ARGB atlas buffer in one pass (fill(0) = transparent padding) =====
        int[] atlasARGB = new int[atlasWidth * atlasHeight];
        Arrays.fill(atlasARGB, 0);
        final int padding = stitcher.getPadding();
        final int w = atlasWidth;
        final int h = atlasHeight;
        // Single pass: write UV data (complete) and copy content pixels row by row into the atlas buffer
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
     * Computes the global mip level: min(game mipmap setting, the smallest
     * per-sprite mip across all sprites). Per-sprite mip =
     * floor(log2(min(min(w,h), lowestOneBit))) — warned when a small texture drags
     * the global level down.
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
     * Uploads level-0 and the box-filtered mip levels (ARGB int[] → RGBA ByteBuffer
     * → glTexImage2D).
     * <p>
     * See the class JavaDoc for the sampling strategy: MIN uses mipmap-linear
     * (crisp when minified), MAG uses NEAREST (pixel-sharp when magnified,
     * eliminating bilinear blur for enlarged 16×16 content).
     * glBindTexture is saved/restored around the upload to avoid polluting vanilla
     * GL state during the Pre stage.
     *
     * @param atlasARGB level-0 atlas pixels (flat ARGB, row-major)
     * @param mipLevel  global mip level (the same value used for CatStitcher layout; padding is pre-reserved)
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

            // Magnification stays NEAREST so enlarged 16×16 sprites keep hard
            // pixel edges instead of bilinear smearing.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } finally {
            // Restore the caller's GL state (bound texture and pixel row alignment) to avoid polluting vanilla stitching later in the Pre stage
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevBound);
        }
    }

    /**
     * Generates and uploads the box-filtered mip chain and sampling parameters,
     * assuming the texture is bound and level-0 is uploaded.
     * <p>
     * Downsamples level by level (2×2 separate-channel average) and uploads mips
     * 1..mips; the level count never exceeds min(mipLevel, log2(atlas size)), and
     * sizes follow the GL spec floor/2.
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
     * M3 animated region update: writes the sprite's current-frame pixels into the
     * atlas region and re-uploads the level-0 region, then rebuilds the whole mip
     * chain from {@link #atlasPixels} (mips depend on level-0 content and cannot be
     * partially updated).
     * <p>
     * Called by CatAtlasManager.tickAnimations after an animation frame switch (main
     * thread). GL state is saved/restored around the upload so the render loop is
     * not polluted.
     *
     * @param sprite the animated sprite whose frame advanced (a member of this atlas)
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
            // CPU-side sync: copy the region rows into the level-0 pixels (the source for mip rebuild)
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
     * Re-uploads a single level-0 region via glTexSubImage2D (ARGB int[] → RGBA
     * ByteBuffer). The frame row width equals the region width w, which matches the
     * default GL UNPACK_ROW_LENGTH, so no extra setup is needed.
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
     * Rebuilds and re-uploads mips 1..mips entirely from {@link #atlasPixels}
     * (level-0, already containing the latest frame). Only executed when a mip
     * chain exists (mipLevel > 0); animation of mip-less atlases skips this step.
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
     * Uploads one mip level: ARGB int[] → RGBA ByteBuffer → glTexImage2D.
     * Reuses the grow-only upload buffer.
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
     * 2×2 box-filter downsampling (separate-channel average of ARGB).
     * Target sizes follow the GL mipmap spec floor/2; incomplete 2×2 blocks at odd
     * edges pass through the top-left pixel (atlas edges are transparent padding, so
     * this has no visual impact).
     *
     * @param src source pixels (size w×h)
     * @param w   source width
     * @param h   source height
     * @param nw  target width (= max(1, w/2))
     * @param nh  target height (= max(1, h/2))
     * @return target pixels (flat ARGB)
     */
    private static int[] boxFilter(int[] src, int w, int h, int nw, int nh) {
        int[] dst = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                int sx = x * 2, sy = y * 2;
                int a = src[sy * w + sx];
                // At right/bottom edges lacking a full 2×2, fill with the pixel itself (1×2 / 2×1 average)
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
     * Empty-atlas fallback: uploads a 16×16 transparent placeholder texture so the
     * GL object bound by render layers stays valid.
     */
    private void uploadPlaceholder() {
        int[] pixels = new int[16 * 16];
        this.atlasWidth = 16;
        this.atlasHeight = 16;
        upload(pixels, 0);
    }

    // ==================== IAtlas / ITextureObject contract ====================

    /** Atlas id (e.g. {@code minecraft:blocks}), i.e. {@code IAtlas#getAtlasName()}. */
    @Override
    public String getAtlasName() {
        return atlasId;
    }

    /**
     * GL texture object id (lazily allocated on first access). Implements the
     * ITextureObject contract.
     */
    @Override
    public int getGlTextureId() {
        if (glTextureId == -1) {
            glTextureId = GL11.glGenTextures();
        }
        return glTextureId;
    }

    /**
     * Deletes the GL texture object (called by CatAtlasManager on resource reload /
     * atlas rebuild to prevent GL leaks).
     */
    public void deleteGlTexture() {
        if (glTextureId != -1) {
            GL11.glDeleteTextures(glTextureId);
            glTextureId = -1;
        }
    }

    /**
     * No-op: the texture content is uploaded by {@link #stitch} as part of the
     * stitch orchestration; the TextureManager only provides registration and
     * binding (the loadTexture contract).
     */
    @Override
    public void loadTexture(IResourceManager resourceManager) {
        // Upload is performed explicitly by CatAtlasManager in the stitch orchestration
    }

    // ==================== Lookup ====================

    /** Looks up a sprite by publish key (texturePath). */
    public CatSprite getSprite(String texturePath) {
        return sprites.get(texturePath);
    }

    /** This atlas' built-in missing sprite (the final fallback for missing lookups; always non-null after stitching). */
    public CatSprite getMissingSprite() {
        return missingSprite;
    }

    /** All sprites in the atlas (texturePath → CatSprite, read-only by convention). */
    public Map<String, CatSprite> getSprites() {
        return Collections.unmodifiableMap(sprites);
    }

    /** Final atlas width (2^n). */
    public int getAtlasWidth() {
        return atlasWidth;
    }

    /** Final atlas height (2^n). */
    public int getAtlasHeight() {
        return atlasHeight;
    }

    /** Smallest power of two ≥ value. */
    private static int smallestEncompassingPowerOfTwo(int value) {
        int i = Integer.highestOneBit(Math.max(1, value));
        return i >= value ? i : i << 1;
    }
}
