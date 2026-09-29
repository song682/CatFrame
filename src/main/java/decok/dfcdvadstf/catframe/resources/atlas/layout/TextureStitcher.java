package decok.dfcdvadstf.catframe.resources.atlas.layout;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.resources.atlas.CatSprite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Texture atlas bin-packing layout engine — a direct 1.7.10 port of the 26.1.2
 * {@code Stitcher}.
 * <p>
 * Algorithm semantics (identical to the higher versions):
 * <ul>
 *   <li>sort: height desc → width desc → name (List.sort is stable, so equal
 *       names/sizes keep insertion order);</li>
 *   <li>region split: a recursive quadtree of Regions — exact fits are occupied
 *       directly; otherwise the exact-size sub-region is placed first, then
 *       {@code max(height, spareWidth) >= max(width, spareHeight)} decides whether
 *       to split into "right column + bottom strip" or "bottom strip + right column";</li>
 *   <li>expansion: the storage bounding box grows incrementally, each axis rounding
 *       up to the next power of two; when both axes can grow, the shorter edge grows
 *       first;</li>
 *   <li>padding: {@code 1 << mipLevel << clamp(anisotropyBit - 1, 0, 4)} per side of
 *       each sprite, preventing mipmap sampling from bleeding between neighbouring
 *       sprites.</li>
 * </ul>
 * Pure CPU layout that never touches GL. Throws {@link TextureStitchException}
 * (carrying the full list of unplaced sprites) when capacity is insufficient.
 */
@SideOnly(Side.CLIENT)
public class TextureStitcher {

    /** Sort comparator: height desc → width desc → name (matches the 26.1.2 HOLDER_COMPARATOR). */
    private static final Comparator<Holder> HOLDER_COMPARATOR = Comparator
            .comparingInt((Holder h) -> -h.height)
            .thenComparingInt(h -> -h.width)
            .thenComparing(h -> h.sprite.getIconName());

    private final int mipLevel;
    /** Sprites awaiting stitching (registration order). */
    private final List<Holder> texturesToBeStitched = new ArrayList<>();
    /** Expanded storage regions (each region is a strip produced by expand). */
    private final List<Region> storage = new ArrayList<>();
    /** Used storage bounding box (not rounded to 2^n). */
    private int storageX;
    private int storageY;
    private final int maxWidth;
    private final int maxHeight;
    private final int padding;

    /**
     * @param maxWidth      maximum atlas width (min(GL_MAX_TEXTURE_SIZE, 16384), read by the caller on the main thread)
     * @param maxHeight     maximum atlas height
     * @param mipLevel      global mip level (decides padding and minimum texel alignment)
     * @param anisotropyBit anisotropic filtering level (1 = disabled)
     */
    public TextureStitcher(int maxWidth, int maxHeight, int mipLevel, int anisotropyBit) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.mipLevel = mipLevel;
        this.padding = 1 << mipLevel << clamp(anisotropyBit - 1, 0, 4);
    }

    /** Currently used storage width (bounding box, not rounded to 2^n). */
    public int getWidth() {
        return this.storageX;
    }

    /** Currently used storage height (bounding box, not rounded to 2^n). */
    public int getHeight() {
        return this.storageY;
    }

    /** Per-side padding of each sprite (layout formula {@code 1<<mip << clamp(anisotropy-1,0,4)}). */
    public int getPadding() {
        return padding;
    }

    /**
     * Registers a sprite awaiting stitching (content size + both-side padding,
     * mip-aligned to the holder size).
     */
    public void registerSprite(CatSprite sprite) {
        Holder holder = new Holder(
                sprite,
                smallestFittingMinTexel(sprite.getIconWidth() + this.padding * 2, this.mipLevel),
                smallestFittingMinTexel(sprite.getIconHeight() + this.padding * 2, this.mipLevel));
        this.texturesToBeStitched.add(holder);
    }

    /**
     * Performs the layout. Throws {@link TextureStitchException} when any holder
     * cannot be placed (an axis would exceed its limit); the exception carries the
     * full list of unplaced sprites.
     */
    public void stitch() {
        List<Holder> holders = new ArrayList<>(this.texturesToBeStitched);
        holders.sort(HOLDER_COMPARATOR);

        for (Holder holder : holders) {
            if (!this.addToStorage(holder)) {
                List<String> unplaced = new ArrayList<>(holders.size());
                for (Holder h : holders) {
                    unplaced.add(h.sprite.getIconName());
                }
                throw new TextureStitchException(holder.sprite.getIconName(), unplaced,
                        this.storageX, this.storageY, this.maxWidth, this.maxHeight);
            }
        }
    }

    /**
     * Walks the placed regions depth-first, invoking the callback with each
     * sprite's physical origin and padding. Must be called after a successful
     * {@link #stitch()}.
     *
     * @param loader placement callback (x/y are the physical origin; the content origin = x + padding)
     */
    public void gatherSprites(SpriteLoader loader) {
        for (Region topRegion : this.storage) {
            topRegion.walk(loader, this.padding);
        }
    }

    /**
     * Rounds the input up to a multiple of 2^mip (26.1.2 smallestFittingMinTexel).
     */
    private static int smallestFittingMinTexel(int input, int maxMipLevel) {
        return ((input >> maxMipLevel) + ((input & (1 << maxMipLevel) - 1) == 0 ? 0 : 1)) << maxMipLevel;
    }

    /**
     * Tries the existing regions first, and expands the storage when all fail.
     */
    private boolean addToStorage(Holder holder) {
        for (Region region : this.storage) {
            if (region.add(holder)) {
                return true;
            }
        }
        return this.expand(holder);
    }

    /**
     * Incremental expansion: computes the next power-of-two size per axis; when
     * both axes can grow, the currently shorter edge grows first (26.1.2 expand
     * semantics).
     */
    private boolean expand(Holder holder) {
        int xCurrentSize = smallestEncompassingPowerOfTwo(this.storageX);
        int yCurrentSize = smallestEncompassingPowerOfTwo(this.storageY);
        int xNewSize = smallestEncompassingPowerOfTwo(this.storageX + holder.width);
        int yNewSize = smallestEncompassingPowerOfTwo(this.storageY + holder.height);
        boolean xCanGrow = xNewSize <= this.maxWidth;
        boolean yCanGrow = yNewSize <= this.maxHeight;
        if (!xCanGrow && !yCanGrow) {
            return false;
        }
        boolean xWillGrow = xCanGrow && xCurrentSize != xNewSize;
        boolean yWillGrow = yCanGrow && yCurrentSize != yNewSize;
        boolean growOnX;
        if (xWillGrow ^ yWillGrow) {
            growOnX = xWillGrow;
        } else {
            growOnX = xCanGrow && xCurrentSize <= yCurrentSize;
        }

        Region slot;
        if (growOnX) {
            if (this.storageY == 0) {
                this.storageY = yNewSize;
            }
            slot = new Region(this.storageX, 0, xNewSize - this.storageX, this.storageY);
            this.storageX = xNewSize;
        } else {
            slot = new Region(0, this.storageY, this.storageX, yNewSize - this.storageY);
            this.storageY = yNewSize;
        }
        slot.add(holder);
        this.storage.add(slot);
        return true;
    }

    /** Smallest power of two ≥ value (Mth.smallestEncompassingPowerOfTwo). */
    private static int smallestEncompassingPowerOfTwo(int value) {
        int i = Integer.highestOneBit(value);
        return i >= value ? i : i << 1;
    }

    /** Interval clamp (Mth.clamp). */
    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    /** Placement callback: sprite + physical origin + per-side padding. */
    public interface SpriteLoader {
        void load(CatSprite sprite, int x, int y, int padding);
    }

    /** Pending placement: sprite + physical size already including both-side padding and mip-aligned. */
    private static final class Holder {
        final CatSprite sprite;
        final int width;
        final int height;

        Holder(CatSprite sprite, int width, int height) {
            this.sprite = sprite;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Storage region — a node of the recursive quadtree (a direct port of the
     * 26.1.2 Region). A region holding a holder accepts no more; an unoccupied
     * region splits into the exact sub-region + one or two remainder strips.
     */
    private static final class Region {
        private final int originX;
        private final int originY;
        private final int width;
        private final int height;
        private List<Region> subSlots;
        private Holder holder;

        Region(int originX, int originY, int width, int height) {
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
        }

        int getX() {
            return this.originX;
        }

        int getY() {
            return this.originY;
        }

        /**
         * Tries to place the holder: an exact fit occupies directly; otherwise the
         * exact sub-region is occupied first and the remainder is split by
         * {@code max(height, spareWidth) >= max(width, spareHeight)} into
         * "right column + bottom strip" or "bottom strip + right column", then tried
         * recursively.
         */
        boolean add(Holder holder) {
            if (this.holder != null) {
                return false;
            }
            int textureWidth = holder.width;
            int textureHeight = holder.height;
            if (textureWidth <= this.width && textureHeight <= this.height) {
                if (textureWidth == this.width && textureHeight == this.height) {
                    this.holder = holder;
                    return true;
                }
                if (this.subSlots == null) {
                    this.subSlots = new ArrayList<>(1);
                    this.subSlots.add(new Region(this.originX, this.originY, textureWidth, textureHeight));
                    int spareWidth = this.width - textureWidth;
                    int spareHeight = this.height - textureHeight;
                    if (spareHeight > 0 && spareWidth > 0) {
                        int right = Math.max(this.height, spareWidth);
                        int bottom = Math.max(this.width, spareHeight);
                        if (right >= bottom) {
                            this.subSlots.add(new Region(this.originX, this.originY + textureHeight, textureWidth, spareHeight));
                            this.subSlots.add(new Region(this.originX + textureWidth, this.originY, spareWidth, this.height));
                        } else {
                            this.subSlots.add(new Region(this.originX + textureWidth, this.originY, spareWidth, textureHeight));
                            this.subSlots.add(new Region(this.originX, this.originY + textureHeight, this.width, spareHeight));
                        }
                    } else if (spareWidth == 0) {
                        this.subSlots.add(new Region(this.originX, this.originY + textureHeight, textureWidth, spareHeight));
                    } else if (spareHeight == 0) {
                        this.subSlots.add(new Region(this.originX + textureWidth, this.originY, spareWidth, textureHeight));
                    }
                }
                for (Region subSlot : this.subSlots) {
                    if (subSlot.add(holder)) {
                        return true;
                    }
                }
                return false;
            }
            return false;
        }

        /** Depth-first emission of placed sprites' physical origins and padding. */
        void walk(SpriteLoader output, int padding) {
            if (this.holder != null) {
                output.load(this.holder.sprite, this.getX(), this.getY(), padding);
            } else if (this.subSlots != null) {
                for (Region subSlot : this.subSlots) {
                    subSlot.walk(output, padding);
                }
            }
        }

        @Override
        public String toString() {
            return "Region{originX=" + this.originX + ", originY=" + this.originY
                    + ", width=" + this.width + ", height=" + this.height
                    + ", holder=" + (this.holder != null ? this.holder.sprite.getIconName() : null)
                    + ", subSlots=" + (this.subSlots != null ? this.subSlots.size() : 0) + "}";
        }
    }
}
