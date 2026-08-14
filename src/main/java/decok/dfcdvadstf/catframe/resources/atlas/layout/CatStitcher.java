package decok.dfcdvadstf.catframe.resources.atlas.layout;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.resources.atlas.CatSprite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 纹理图集 bin packing 布局器 —— 26.1.2 {@code Stitcher} 的 1.7.10 直移植。
 * <p>
 * 算法语义（与高版本完全一致）：
 * <ul>
 *   <li>排序：高度降序 → 宽度降序 → 名称（List.sort 稳定，同名等尺寸按插入序）；</li>
 *   <li>区域分割：Region 递归四叉分裂树 —— 精确匹配直接占用，否则先放 exact-size 子区，
 *       再按 {@code max(height, spareWidth) >= max(width, spareHeight)} 决定
 *       "右列+下条"或"下条+右列"两分；</li>
 *   <li>扩展：增量计算存储包围盒，任一轴扩到下一个 2^n；两轴都能扩时优先扩较短边；</li>
 *   <li>padding：{@code 1 << mipLevel << clamp(anisotropyBit - 1, 0, 4)} 每 sprite 单边，
 *       防止 mipmap 采样在相邻 sprite 间渗色。</li>
 * </ul>
 * 纯 CPU 布局，不触碰 GL。容量不足时抛 {@link CatStitchException}（携带全部未放置 sprite 清单）。
 *
 * <p>Direct port of the 26.1.2 {@code Stitcher}: height-desc → width-desc → name
 * sort, recursive region splitting, short-edge-first power-of-two expansion.
 * Pure CPU layout with no GL access.
 */
@SideOnly(Side.CLIENT)
public class CatStitcher {

    /** 排序比较器：高度降序 → 宽度降序 → 名称（与 26.1.2 HOLDER_COMPARATOR 一致）。 */
    private static final Comparator<Holder> HOLDER_COMPARATOR = Comparator
            .comparingInt((Holder h) -> -h.height)
            .thenComparingInt(h -> -h.width)
            .thenComparing(h -> h.sprite.getIconName());

    private final int mipLevel;
    /** 待缝合 sprite 列表（注册序）。 */
    private final List<Holder> texturesToBeStitched = new ArrayList<>();
    /** 已扩展的存储区域列表（每块区域是一个 expand 出的条带）。 */
    private final List<Region> storage = new ArrayList<>();
    /** 已用存储包围盒（未 2^n 化）。 */
    private int storageX;
    private int storageY;
    private final int maxWidth;
    private final int maxHeight;
    private final int padding;

    /**
     * @param maxWidth      最大图集宽（min(GL_MAX_TEXTURE_SIZE, 16384)，由调用方在主线程读取）
     * @param maxHeight     最大图集高
     * @param mipLevel      全局 mip 级别（决定 padding 与最小纹素对齐）
     * @param anisotropyBit 各向异性过滤级别（1 = 关闭）
     */
    public CatStitcher(int maxWidth, int maxHeight, int mipLevel, int anisotropyBit) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.mipLevel = mipLevel;
        this.padding = 1 << mipLevel << clamp(anisotropyBit - 1, 0, 4);
    }

    /** 当前已用存储宽度（包围盒，未 2^n 化）。 */
    public int getWidth() {
        return this.storageX;
    }

    /** 当前已用存储高度（包围盒，未 2^n 化）。 */
    public int getHeight() {
        return this.storageY;
    }

    /** 每 sprite 单边 padding（布局公式 {@code 1<<mip << clamp(anisotropy-1,0,4)}）。 */
    public int getPadding() {
        return padding;
    }

    /**
     * 注册一个待缝合 sprite（内容尺寸 + 双边 padding 后按 mip 对齐为 holder 尺寸）。
     */
    public void registerSprite(CatSprite sprite) {
        Holder holder = new Holder(
                sprite,
                smallestFittingMinTexel(sprite.getIconWidth() + this.padding * 2, this.mipLevel),
                smallestFittingMinTexel(sprite.getIconHeight() + this.padding * 2, this.mipLevel));
        this.texturesToBeStitched.add(holder);
    }

    /**
     * 执行布局。任一个 holder 无法放入（任一轴将超过上限）时抛 {@link CatStitchException}，
     * 异常携带全部未放置 sprite 清单。
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
                throw new CatStitchException(holder.sprite.getIconName(), unplaced,
                        this.storageX, this.storageY, this.maxWidth, this.maxHeight);
            }
        }
    }

    /**
     * 深度优先遍历已放置区域，回调每个 sprite 的物理起点与 padding。
     * 必须在 {@link #stitch()} 成功后调用。
     *
     * @param loader 放置回调（x/y 为物理起点，内容起点 = x + padding）
     */
    public void gatherSprites(SpriteLoader loader) {
        for (Region topRegion : this.storage) {
            topRegion.walk(loader, this.padding);
        }
    }

    /**
     * 将输入向上对齐到 2^mip 的倍数（26.1.2 smallestFittingMinTexel）。
     */
    private static int smallestFittingMinTexel(int input, int maxMipLevel) {
        return ((input >> maxMipLevel) + ((input & (1 << maxMipLevel) - 1) == 0 ? 0 : 1)) << maxMipLevel;
    }

    /**
     * 尝试放入已有区域，全部失败则扩展存储。
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
     * 增量扩展：计算各轴下一个 2^n 尺寸，两轴都能扩时优先扩当前较短边
     * （26.1.2 expand 语义）。
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

    /** 不小于 value 的最小 2^n（Mth.smallestEncompassingPowerOfTwo）。 */
    private static int smallestEncompassingPowerOfTwo(int value) {
        int i = Integer.highestOneBit(value);
        return i >= value ? i : i << 1;
    }

    /** 区间钳制（Mth.clamp）。 */
    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    /** 放置回调：sprite + 物理起点 + 单边 padding。 */
    public interface SpriteLoader {
        void load(CatSprite sprite, int x, int y, int padding);
    }

    /** 待放置项：sprite + 已含双边 padding 并按 mip 对齐的物理尺寸。 */
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
     * 存储区域 —— 递归四叉分裂树节点（26.1.2 Region 直移植）。
     * 已放置 holder 的区域不可再放；未满区域分裂为 exact 子区 + 一个或两个余量条带。
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
         * 尝试放入 holder：精确匹配直接占用；否则先占 exact 子区，余量按
         * {@code max(height, spareWidth) >= max(width, spareHeight)} 分成
         * "右列+下条"或"下条+右列"，再递归尝试。
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

        /** 深度优先回吐已放置 sprite 的物理起点与 padding。 */
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
