package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.IIcon;

/**
 * CatFrame 自研纹理图集 sprite —— 实现原版 {@link IIcon} 契约的轻量 POJO。
 * <p>
 * 与 1.7.10 {@code TextureAtlasSprite} 的本质区别：
 * <ul>
 *   <li>像素归 CPU 所有（{@code int[] ARGB}），异步烘焙线程直接读取，无需 GPU 回读；</li>
 *   <li>{@link #getIconWidth()} / {@link #getIconHeight()} 返回<b>内容尺寸</b>而非物理存储尺寸
 *       —— 主动避开 1.7.10 各向异性过滤把物理存储扩为 (内容+16)² 的 padding 陷阱；</li>
 *   <li>UV 坐标由布局结果（物理起点 + padding + 图集尺寸）计算，缝合后经
 *       {@link #complete(int, int, int, int, int)} 一次性写入，之后全字段不可变（烘焙线程并发只读安全）。</li>
 * </ul>
 * <p>
 * M3 动画里程碑预留：{@link #frameCount} / {@link #frameIndex} / {@link #frameTimeMs}
 * 字段已声明（M1 恒为单帧），帧状态机与逐帧区域重传届时落地。
 *
 * <p>CatFrame custom-atlas sprite implementing the vanilla {@code IIcon} contract.
 * Owns its pixels on the CPU side (flat ARGB int[]), reports the <em>content</em>
 * size (never the padded storage size), and keeps UV data immutable after stitching.
 */
@SideOnly(Side.CLIENT)
public class CatSprite implements IIcon {

    /** 缺失纹理兜底 sprite 的 icon 名称（与原版 missingno 语义一致）。 */
    public static final String MISSING_NAME = "missingno";
    /** 缺失纹理兜底 sprite 的内容尺寸（16×16 紫黑格）。 */
    private static final int MISSING_SIZE = 16;

    /** 发布键：完整纹理路径（如 {@code minecraft:block/stone}），即 textureIcons 的键。 */
    private final String texturePath;
    /** icon 名称：{@code VanillaModelManager.Utilities.resolveTextureName} 的结果（如 {@code minecraft:stone}）。 */
    private final String name;
    /** 内容像素（flat ARGB int[]，row-major，大小 = contentWidth × contentHeight）。 */
    private final int[] pixels;
    /** 内容区域宽度（像素）。 */
    private final int contentWidth;
    /** 内容区域高度（像素）。 */
    private final int contentHeight;
    /** 所属图集 id（如 {@code minecraft:block} / {@code minecraft:item}），供 TextureSlots 归类。 */
    private final String atlasId;

    // ===== 缝合后写入（complete 一次性设置，之后不可变） =====

    /** 物理存储起点 X（含 padding 边框在内的图集坐标）。 */
    private int originX;
    /** 物理存储起点 Y（含 padding 边框在内的图集坐标）。 */
    private int originY;
    /** 单边 padding 宽度（布局公式 {@code 1<<mip << clamp(anisotropy-1,0,4)}）。 */
    private int padding;
    /** 所属图集宽度（最终 2^n 尺寸）。 */
    private int atlasWidth;
    /** 所属图集高度（最终 2^n 尺寸）。 */
    private int atlasHeight;

    // ===== M3 动画占位（M1 恒单帧，帧状态机后续里程碑落地） =====

    /** 动画帧总数（M1 恒为 1；M3 从 .mcmeta animation 解析）。 */
    private final int frameCount;
    /** 当前动画帧索引（M3 由 tick 驱动推进；volatile 供异步读取）。 */
    private volatile int frameIndex;
    /** 每帧持续时间（ms），长度 = frameCount（M3 使用；M1 为空数组）。 */
    private final int[] frameTimeMs;

    /**
     * 构造一个内容 sprite。
     *
     * @param texturePath   完整纹理路径（textureIcons 发布键）
     * @param name          icon 名称（resolveTextureName 结果）
     * @param pixels        内容像素（flat ARGB，调用方拥有，不再被修改）
     * @param contentWidth  内容宽度
     * @param contentHeight 内容高度
     * @param atlasId       所属图集 id
     */
    public CatSprite(String texturePath, String name, int[] pixels,
                     int contentWidth, int contentHeight, String atlasId) {
        this(texturePath, name, pixels, contentWidth, contentHeight, atlasId,
                1, new int[0]);
    }

    /**
     * 完整构造（M3 动画帧数据入口）。
     */
    CatSprite(String texturePath, String name, int[] pixels,
              int contentWidth, int contentHeight, String atlasId,
              int frameCount, int[] frameTimeMs) {
        this.texturePath = texturePath;
        this.name = name;
        this.pixels = pixels;
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        this.atlasId = atlasId;
        this.frameCount = frameCount;
        this.frameIndex = 0;
        this.frameTimeMs = frameTimeMs;
    }

    /**
     * 内置缺失纹理兜底 sprite（16×16 紫黑格，像素行为与原版 builtin/missing 一致）。
     * 纹理文件缺失/解码失败时调用方用它占位，保证任何查找与烘焙不崩溃。
     *
     * @param atlasId     所属图集 id
     * @param texturePath 原始纹理路径（保留以维持发布键完整性）
     */
    public static CatSprite missing(String atlasId, String texturePath) {
        int[] pixels = new int[MISSING_SIZE * MISSING_SIZE];
        for (int y = 0; y < MISSING_SIZE; y++) {
            for (int x = 0; x < MISSING_SIZE; x++) {
                boolean black = ((x >> 1) + (y >> 1)) % 2 == 0;
                pixels[y * MISSING_SIZE + x] = black ? 0xFF000000 : 0xFFFF00FF;
            }
        }
        return new CatSprite(texturePath, MISSING_NAME, pixels,
                MISSING_SIZE, MISSING_SIZE, atlasId);
    }

    /**
     * 缝合回调：写入物理起点、padding 与图集尺寸，此后 UV 数据可用。
     * 由 {@link CatAtlas} 在布局完成、最终图集尺寸确定后调用，仅可调用一次。
     *
     * @param x          物理存储起点 X（region 起点，内容起点 = x + padding）
     * @param y          物理存储起点 Y
     * @param padding    单边 padding 宽度
     * @param atlasWidth 最终图集宽度（2^n）
     * @param atlasHeight 最终图集高度（2^n）
     */
    public void complete(int x, int y, int padding, int atlasWidth, int atlasHeight) {
        this.originX = x;
        this.originY = y;
        this.padding = padding;
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
    }

    /**
     * M3 动画 tick 入口占位：推进 {@link #frameIndex} 并返回是否需要重传纹理区域。
     * M1 恒单帧返回 false；M3 帧状态机落地后按帧间隔推进。
     */
    public boolean updateAnimationTick() {
        return false;
    }

    // ==================== IIcon 契约 ====================

    /**
     * 内容宽度（非物理存储宽度）—— 与 1.7.10 各向异性填充陷阱的关键区别。
     */
    @Override
    public int getIconWidth() {
        return contentWidth;
    }

    /**
     * 内容高度（非物理存储高度）。
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
     * 0 → minU，16 → maxU，中间值线性插值（quad 顶点 UV 的标准抽象空间）。
     */
    @Override
    public float getInterpolatedU(double u) {
        return getMinU() + (getMaxU() - getMinU()) * (float) u / 16.0F;
    }

    /**
     * 0 → minV，16 → maxV，中间值线性插值。
     */
    @Override
    public float getInterpolatedV(double v) {
        return getMinV() + (getMaxV() - getMinV()) * (float) v / 16.0F;
    }

    @Override
    public String getIconName() {
        return name;
    }

    // ==================== 访问器 ====================

    /** 发布键（完整纹理路径）。 */
    public String getTexturePath() {
        return texturePath;
    }

    /** 内容像素（flat ARGB int[]，只读约定）。 */
    public int[] getPixels() {
        return pixels;
    }

    /** 所属图集 id（如 {@code minecraft:block}）。 */
    public String getAtlasId() {
        return atlasId;
    }

    /** 是否缺失纹理兜底 sprite。 */
    public boolean isMissing() {
        return MISSING_NAME.equals(name);
    }

    /** 物理存储起点 X。 */
    public int getOriginX() {
        return originX;
    }

    /** 物理存储起点 Y。 */
    public int getOriginY() {
        return originY;
    }

    /** 单边 padding 宽度。 */
    public int getPadding() {
        return padding;
    }

    /** 当前动画帧索引（M1 恒 0）。 */
    public int getFrameIndex() {
        return frameIndex;
    }

    @Override
    public String toString() {
        return "CatSprite{" + name + " " + contentWidth + "x" + contentHeight
                + " @(" + originX + "," + originY + ") pad=" + padding
                + " atlas=" + atlasWidth + "x" + atlasHeight + "}";
    }
}
