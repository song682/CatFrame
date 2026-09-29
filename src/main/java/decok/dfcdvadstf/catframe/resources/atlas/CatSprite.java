package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;

/**
 * CatFrame custom-atlas sprite — a lightweight POJO implementing the vanilla
 * {@link IIcon} contract.
 * <p>
 * Key differences from the 1.7.10 {@code TextureAtlasSprite}:
 * <ul>
 *   <li>pixels are owned by the CPU ({@code int[] ARGB}); async bake threads read
 *       them directly with no GPU read-back;</li>
 *   <li>{@link #getIconWidth()} / {@link #getIconHeight()} report the <b>content
 *       size</b> rather than the physical storage size — actively avoiding the
 *       1.7.10 anisotropic-filtering trap that pads physical storage to
 *       (content+16)²;</li>
 *   <li>UV coordinates are computed from the layout result (physical origin +
 *       padding + atlas size) and written once via
 *       {@link #complete(int, int, int, int, int)} after stitching; all fields are
 *       immutable thereafter (safe for concurrent read-only access from bake threads).</li>
 * </ul>
 * <p>
 * The M3 animation milestone is in place: frame pixels of a multi-frame sprite
 * live in {@link #framePixels} (null = single frame), frame advancement is driven
 * per client tick by {@link #updateAnimationTick()} (skipped while the game is
 * paused), and after a frame switch {@link CatAtlas#updateAnimationRegion}
 * re-uploads the region via glTexSubImage2D. Bake threads read the <b>current
 * frame</b> through {@link #getPixels()}, keeping the same visible frame as the
 * atlas region update.
 */
@SideOnly(Side.CLIENT)
public class CatSprite implements IIcon {

    /** Icon name of the missing-texture fallback sprite (matching the vanilla missingno semantics). */
    public static final String MISSING_NAME = "missingno";
    /** Content size of the missing-texture fallback sprite (a 16×16 purple-black square). */
    private static final int MISSING_SIZE = 16;

    /** Publish key: the full texture path (e.g. {@code minecraft:block/stone}), i.e. the textureIcons key. */
    private final String texturePath;
    /** Icon name: the publish key itself (the merged key is already the data-driven resolution result, e.g. {@code minecraft:blocks/ladder}). */
    private final String name;
    /** Content pixels (flat ARGB int[], row-major, size = contentWidth × contentHeight). */
    private final int[] pixels;
    /** Content region width (pixels). */
    private final int contentWidth;
    /** Content region height (pixels). */
    private final int contentHeight;
    /** Owning atlas id (e.g. {@code minecraft:blocks} / {@code minecraft:items}), used by TextureSlots for classification. */
    private final String atlasId;

    // ===== Written after stitching (set once by complete, immutable afterwards) =====

    /** Physical storage origin X (atlas coordinates including the padding border). */
    private int originX;
    /** Physical storage origin Y (atlas coordinates including the padding border). */
    private int originY;
    /** Per-side padding width (layout formula {@code 1<<mip << clamp(anisotropy-1,0,4)}). */
    private int padding;
    /** Owning atlas width (final 2^n size). */
    private int atlasWidth;
    /** Owning atlas height (final 2^n size). */
    private int atlasHeight;

    // ===== M3 animation state (single-frame sprites: framePixels = null, pixels stored directly) =====

    /** Animation frame pixels (null = single frame; for multi-frame each frame is flat ARGB, size = contentWidth × contentHeight). */
    private final int[][] framePixels;
    /** Total animation frame count (always 1 for single-frame sprites). */
    private final int frameCount;
    /** Current animation frame index (advanced per tick; volatile so async bake threads can read it). */
    private volatile int frameIndex;
    /** Per-frame duration (ms), length = frameCount (an empty array for single-frame sprites). */
    private final int[] frameTimeMs;
    /** System time of the last animation tick (ms, {@link Minecraft#getSystemTime}). */
    private long lastAnimationTickMs;
    /** Accumulated unconsumed animation time (ms), decremented step by step as frames advance. */
    private long accumulatedAnimationMs;

    /**
     * Constructs a content sprite (single frame).
     *
     * @param texturePath   full texture path (the textureIcons publish key)
     * @param name          icon name (the publish key itself, the data-driven resolution result)
     * @param pixels        content pixels (flat ARGB, owned by the caller, no longer modified)
     * @param contentWidth  content width
     * @param contentHeight content height
     * @param atlasId       owning atlas id
     */
    public CatSprite(String texturePath, String name, int[] pixels,
                     int contentWidth, int contentHeight, String atlasId) {
        this(texturePath, name, null, pixels, contentWidth, contentHeight, atlasId,
                1, new int[0]);
    }

    /**
     * Full constructor (multi-frame animation entry point, M3).
     * <p>
     * Every frame must be sized {@code contentWidth × contentHeight}; the caller
     * (CatSpriteLoader) expands the .mcmeta frame table into a duration array
     * matching the frames one-to-one.
     *
     * @param framePixels animation frame pixels (length = frame count, each frame flat ARGB); null means single frame
     * @param frameTimeMs per-frame duration (ms), length = frame count
     */
    CatSprite(String texturePath, String name, int[][] framePixels,
              int contentWidth, int contentHeight, String atlasId, int[] frameTimeMs) {
        this(texturePath, name, framePixels,
                framePixels != null && framePixels.length > 0 ? framePixels[0] : null,
                contentWidth, contentHeight, atlasId,
                framePixels != null ? framePixels.length : 1, frameTimeMs);
    }

    /** Private full constructor: shared by single-frame (framePixels=null) and multi-frame. */
    private CatSprite(String texturePath, String name, int[][] framePixels, int[] pixels,
                      int contentWidth, int contentHeight, String atlasId,
                      int frameCount, int[] frameTimeMs) {
        this.texturePath = texturePath;
        this.name = name;
        this.framePixels = framePixels;
        this.pixels = pixels;
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        this.atlasId = atlasId;
        this.frameCount = frameCount;
        this.frameIndex = 0;
        this.frameTimeMs = frameTimeMs;
        this.lastAnimationTickMs = 0;
        this.accumulatedAnimationMs = 0;
    }

    /**
     * Built-in missing-texture fallback sprite (16×16 purple-black square; pixel
     * pattern matches the vanilla builtin/missing). The caller uses it as a
     * placeholder when a texture file is missing / fails to decode, so that no
     * lookup or bake ever crashes.
     *
     * @param atlasId     owning atlas id
     * @param texturePath original texture path (kept so the publish key stays intact)
     */
    public static CatSprite missing(String atlasId, String texturePath) {
        int[] pixels = new int[MISSING_SIZE * MISSING_SIZE];
        for (int y = 0; y < MISSING_SIZE; y++) {
            for (int x = 0; x < MISSING_SIZE; x++) {
                // 2x2 cells of 8x8 px: magenta top-left/bottom-right, black otherwise
                boolean black = ((x >> 3) + (y >> 3)) % 2 != 0;
                pixels[y * MISSING_SIZE + x] = black ? 0xFF000000 : 0xFFFF00FF;
            }
        }
        return new CatSprite(texturePath, MISSING_NAME, pixels,
                MISSING_SIZE, MISSING_SIZE, atlasId);
    }

    /**
     * Stitch callback: writes the physical origin, padding and atlas size; UV data
     * becomes usable afterwards. Called once by {@link CatAtlas} after layout
     * completes and the final atlas size is fixed.
     *
     * @param x           physical storage origin X (region origin; content origin = x + padding)
     * @param y           physical storage origin Y
     * @param padding     per-side padding width
     * @param atlasWidth  final atlas width (2^n)
     * @param atlasHeight final atlas height (2^n)
     */
    public void complete(int x, int y, int padding, int atlasWidth, int atlasHeight) {
        this.originX = x;
        this.originY = y;
        this.padding = padding;
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
    }

    /**
     * Animation tick advancement: accumulates frame durations from the system-time
     * delta and advances {@link #frameIndex}.
     * <p>
     * Returns true after a frame switch; the caller (CatAtlasManager →
     * CatAtlas.updateAnimationRegion) is responsible for the glTexSubImage2D
     * re-upload of that sprite's region. On clock rollback or a long stall (>1s)
     * the accumulator is reset to avoid a frame-skip storm (returns false and no
     * re-upload is triggered in that case).
     *
     * @return whether the frame index switched (the atlas region needs re-upload)
     */
    public boolean updateAnimationTick() {
        if (frameCount <= 1) {
            return false;
        }
        long now = Minecraft.getSystemTime();
        long delta = lastAnimationTickMs == 0 ? 0 : now - lastAnimationTickMs;
        lastAnimationTickMs = now;
        if (delta <= 0 || delta > 1000) {
            // First tick / clock rollback / long stall: only record the baseline, do not advance frames
            accumulatedAnimationMs = 0;
            return false;
        }
        accumulatedAnimationMs += delta;
        int old = frameIndex;
        while (accumulatedAnimationMs >= frameTimeMs[frameIndex]) {
            accumulatedAnimationMs -= frameTimeMs[frameIndex];
            frameIndex = (frameIndex + 1) % frameCount;
        }
        return frameIndex != old;
    }

    /** Whether this is a multi-frame animated sprite. */
    public boolean isAnimated() {
        return framePixels != null;
    }

    // ==================== IIcon contract ====================

    /**
     * Content width (not the physical storage width) — the key distinction from the
     * 1.7.10 anisotropic padding trap.
     */
    @Override
    public int getIconWidth() {
        return contentWidth;
    }

    /**
     * Content height (not the physical storage height).
     */
    @Override
    public int getIconHeight() {
        return contentHeight;
    }

    @Override
    public float getMinU() {
        return (originX + padding) / (float) atlasWidth;
    }

    @Override
    public float getMaxU() {
        return (originX + padding + contentWidth) / (float) atlasWidth;
    }

    @Override
    public float getMinV() {
        return (originY + padding) / (float) atlasHeight;
    }

    @Override
    public float getMaxV() {
        return (originY + padding + contentHeight) / (float) atlasHeight;
    }

    /**
     * 0 → minU, 16 → maxU, linear interpolation in between (the standard abstract
     * space for quad vertex UVs).
     */
    @Override
    public float getInterpolatedU(double u) {
        return getMinU() + (getMaxU() - getMinU()) * (float) u / 16.0F;
    }

    /**
     * 0 → minV, 16 → maxV, linear interpolation in between.
     */
    @Override
    public float getInterpolatedV(double v) {
        return getMinV() + (getMaxV() - getMinV()) * (float) v / 16.0F;
    }

    @Override
    public String getIconName() {
        return name;
    }

    // ==================== Accessors ====================

    /** The publish key (full texture path). */
    public String getTexturePath() {
        return texturePath;
    }

    /**
     * Content pixels (flat ARGB int[], read-only by convention).
     * For multi-frame sprites returns the <b>current frame</b> pixels, keeping the
     * same visible frame as the atlas region update.
     */
    public int[] getPixels() {
        return framePixels != null ? framePixels[frameIndex] : pixels;
    }

    /** Owning atlas id (e.g. {@code minecraft:blocks}). */
    public String getAtlasId() {
        return atlasId;
    }

    /** Whether this is the missing-texture fallback sprite. */
    public boolean isMissing() {
        return MISSING_NAME.equals(name);
    }

    /** Physical storage origin X. */
    public int getOriginX() {
        return originX;
    }

    /** Physical storage origin Y. */
    public int getOriginY() {
        return originY;
    }

    /** Per-side padding width. */
    public int getPadding() {
        return padding;
    }

    /** Current animation frame index (always 0 for single-frame sprites). */
    public int getFrameIndex() {
        return frameIndex;
    }

    /** Total animation frame count (always 1 for single-frame sprites). */
    public int getFrameCount() {
        return frameCount;
    }

    @Override
    public String toString() {
        return "CatSprite{" + name + " " + contentWidth + "x" + contentHeight
                + " @(" + originX + "," + originY + ") pad=" + padding
                + " atlas=" + atlasWidth + "x" + atlasHeight + "}";
    }
}
