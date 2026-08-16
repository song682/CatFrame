package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
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
 * M3 动画里程碑已落地：多帧 sprite 的帧像素存于 {@link #framePixels}（null = 单帧），
 * 帧推进由 {@link #updateAnimationTick()} 按客户端 tick 驱动（游戏暂停时跳过），
 * 帧切换后由 {@link CatAtlas#updateAnimationRegion} 做区域 glTexSubImage2D 重传。
 * 烘焙线程经 {@link #getPixels()} 读取<b>当前帧</b>，与图集区域更新保持同一可见帧。
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
    /** icon 名称：发布键本身（合并键已是数据驱动解析结果，如 {@code minecraft:blocks/ladder}）。 */
    private final String name;
    /** 内容像素（flat ARGB int[]，row-major，大小 = contentWidth × contentHeight）。 */
    private final int[] pixels;
    /** 内容区域宽度（像素）。 */
    private final int contentWidth;
    /** 内容区域高度（像素）。 */
    private final int contentHeight;
    /** 所属图集 id（如 {@code minecraft:blocks} / {@code minecraft:items}），供 TextureSlots 归类。 */
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

    // ===== M3 动画状态（单帧 sprite：framePixels = null，像素直存 pixels） =====

    /** 动画帧像素（null = 单帧；多帧时每帧 flat ARGB，尺寸 = contentWidth × contentHeight）。 */
    private final int[][] framePixels;
    /** 动画帧总数（单帧恒为 1）。 */
    private final int frameCount;
    /** 当前动画帧索引（tick 驱动推进；volatile 供异步烘焙线程读取）。 */
    private volatile int frameIndex;
    /** 每帧持续时间（ms），长度 = frameCount（单帧为空数组）。 */
    private final int[] frameTimeMs;
    /** 上次动画 tick 的系统时间（ms，{@link Minecraft#getSystemTime}）。 */
    private long lastAnimationTickMs;
    /** 累积未消费的动画时间（ms），帧推进按它逐级扣减。 */
    private long accumulatedAnimationMs;

    /**
     * 构造一个内容 sprite（单帧）。
     *
     * @param texturePath   完整纹理路径（textureIcons 发布键）
     * @param name          icon 名称（发布键本身，数据驱动解析结果）
     * @param pixels        内容像素（flat ARGB，调用方拥有，不再被修改）
     * @param contentWidth  内容宽度
     * @param contentHeight 内容高度
     * @param atlasId       所属图集 id
     */
    public CatSprite(String texturePath, String name, int[] pixels,
                     int contentWidth, int contentHeight, String atlasId) {
        this(texturePath, name, null, pixels, contentWidth, contentHeight, atlasId,
                1, new int[0]);
    }

    /**
     * 完整构造（多帧动画入口，M3）。
     * <p>
     * 各帧尺寸必须等于 {@code contentWidth × contentHeight}；调用方（CatSpriteLoader）
     * 负责把 .mcmeta 帧表展开为与帧一一对应的时长数组。
     *
     * @param framePixels 动画帧像素（长度 = 帧数，每帧 flat ARGB）；null 视为单帧
     * @param frameTimeMs 每帧时长（ms），长度 = 帧数
     */
    CatSprite(String texturePath, String name, int[][] framePixels,
              int contentWidth, int contentHeight, String atlasId, int[] frameTimeMs) {
        this(texturePath, name, framePixels,
                framePixels != null && framePixels.length > 0 ? framePixels[0] : null,
                contentWidth, contentHeight, atlasId,
                framePixels != null ? framePixels.length : 1, frameTimeMs);
    }

    /** 私有完整构造：单帧（framePixels=null）与多帧共用。 */
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
                // 2×2 格（每格 8×8）：左上/右下紫，右上/左下黑，与原版 missingno 一致
                // 2x2 cells of 8x8 px: magenta top-left/bottom-right, black otherwise
                boolean black = ((x >> 3) + (y >> 3)) % 2 != 0;
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
     * 动画 tick 推进：按系统时间增量累积帧时长并推进 {@link #frameIndex}。
     * <p>
     * 帧切换后返回 true，调用方（CatAtlasManager → CatAtlas.updateAnimationRegion）
     * 负责该 sprite 区域的 glTexSubImage2D 重传。时钟回拨或长时间卡顿（>1s）时
     * 重置累积器避免跳帧风暴（此时返回 false，不触发重传）。
     *
     * @return 帧索引是否发生切换（需要重传图集区域）
     */
    public boolean updateAnimationTick() {
        if (frameCount <= 1) {
            return false;
        }
        long now = Minecraft.getSystemTime();
        long delta = lastAnimationTickMs == 0 ? 0 : now - lastAnimationTickMs;
        lastAnimationTickMs = now;
        if (delta <= 0 || delta > 1000) {
            // 首次 tick / 时钟回拨 / 长时间卡顿：仅记录基准，不推进帧
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

    /** 是否为多帧动画 sprite。 */
    public boolean isAnimated() {
        return framePixels != null;
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

    /**
     * 内容像素（flat ARGB int[]，只读约定）。
     * 多帧 sprite 返回<b>当前帧</b>像素，与图集区域更新保持同一可见帧。
     */
    public int[] getPixels() {
        return framePixels != null ? framePixels[frameIndex] : pixels;
    }

    /** 所属图集 id（如 {@code minecraft:blocks}）。 */
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

    /** 当前动画帧索引（单帧恒 0）。 */
    public int getFrameIndex() {
        return frameIndex;
    }

    /** 动画帧总数（单帧恒 1）。 */
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
