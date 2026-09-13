package decok.dfcdvadstf.catframe.model.render.api;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;
import java.util.Map;

/**
 * 一次 quad 渲染的上下文，扩展链 {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry#apply(RenderContext)}
 * 会按注册顺序遍历 {@link IModelRenderExtension}，每个扩展都可以读 / 改本对象。
 * <p>
 * <b>两个使用层次（2026-09 定案）</b>：
 * <ul>
 *   <li><b>per-quad ctx</b>：{@code apply} 阶段由 QuadWriter 每 quad 构造，quad 级数据齐全，
 *       输出字段由发射器消费。</li>
 *   <li><b>提交级 ctx</b>：{@code beforePart / afterPart} 阶段每提交项构造一次，
 *       {@link #quad} 为 null，quad 级字段（{@link #aoBrightness} / shade 等）无渲染语义；
 *       对其输出字段的写入不会传递到 per-quad ctx（整组级桥接由扩展自行 ThreadLocal 接力，
 *       见内建 LightPolicyExtension）。</li>
 * </ul>
 *
 * <h3>字段语义</h3>
 * <ul>
 *   <li>输入字段（final）：quad、phase、world/x/y/z/block、stack 等环境信息。</li>
 *   <li>输出字段（可变）：
 *     <ul>
 *       <li>{@link #skip}：true 时丢弃该 quad（用于面剔除）。</li>
 *       <li>{@link #color}：0xRRGGBB 颜色乘数，默认 0xFFFFFF。
 *           推荐用 {@link #mulColor(int)} 累积，多个扩展可叠加。</li>
 *       <li>{@link #brightnessOverride}：≥0 时强制使用该亮度（用于阴影/自发光），
 *           -1 表示沿用 {@link #baselineBrightness}。</li>
 *       <li>{@link #shade}：方向光照系数（顶面 1.0、侧面 0.8、底面 0.5 等），
 *           会与 color 一起送入 Tessellator。</li>
 *       <li>{@link #aoBrightness} 和 {@link #aoColorMul}：逐顶点 AO 数据（每面 4 个值），
 *           仅 BLOCK_WORLD 阶段可用。当所有值为 -1 时退化到 uniform 渲染。</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class RenderContext {
    // ==================== Inputs ====================
    public final RenderPhase phase;
    public final BakedQuad quad;

    // Block-world only (item phases: world=null, x=y=z=0, block=null)
    public final IBlockAccess world;
    public final int x, y, z;
    public final Block block;

    // Item phases only (block phase: stack=null)
    public final ItemStack stack;

    /**
     * 方块状态属性（blockstate properties），由 CatFrame 的动态属性解析器
     * （{@code VanillaBlockResolvers}）在模型解析时计算，例如玻璃板/铁栏杆的
     * north/east/south/west 连接状态、楼梯的 facing/half/shape、红石线的 power 等。
     * <p>
     * 仅 {@link RenderPhase#BLOCK_WORLD} / {@link RenderPhase#BLOCK_DESTROY} 阶段可用；
     * 物品阶段或未计算动态属性的方块为 null。
     * <p>
     * 只读约定：返回不可修改视图，扩展不得修改；引用仅在扩展链调用期间有效
     * （同帧同线程），不得跨帧保留。
     */
    @Nullable
    public final Map<String, String> blockstateProps;

    /**
     * 物品属性（item properties），由 CatFrame 的物品属性系统
     * （{@code ItemProperties.buildProperties}）计算，例如 damage / max_damage /
     * using_item / use_duration / display_context 等，供 items JSON 决策树求值使用。
     * <p>
     * 仅 {@code ITEM_*} 阶段可用；方块阶段为 null。
     * <p>
     * 只读惰性 Map（{@code LazyPropertyMap}）：读取某个 key 才会触发对应 provider 的
     * 计算并缓存，请避免 {@code entrySet()/values()} 全量遍历（会触发全部求值）。
     * 引用仅在扩展链调用期间有效（同帧同线程），不得跨帧保留。
     */
    @Nullable
    public final Map<String, Comparable<?>> itemProps;

    /**
     * [S1] 方块的 metadata 值，由渲染管线在提交时携带（历史上用于 GUI 方块染色）。
     * 默认为 0，由渲染管线在已知 metadata 时设置。
     */
    public int metadata = 0;

    /**
     * 由渲染器预先计算的基础亮度（来自相邻方块光照）。扩展可读不改。
     */
    public final int baselineBrightness;
    /**
     * 逐顶点 AO 亮度（packed int，skyLight<<16 | blockLight 格式）。
     * -1 表示该顶点无逐顶点数据，退化到 {@link #effectiveBrightness()} 统一亮度。
     * 仅 {@link RenderPhase#BLOCK_WORLD} 阶段由 VanillaModelManager 填入。
     */
    public final int[] aoBrightness = {-1, -1, -1, -1};
    /**
     * 逐顶点 AO 遮挡系数（0.0~1.0，1.0=无遮挡），与原版 block.getAmbientOcclusionLightValue() 等效。
     * 渲染时会乘入最终颜色：{@code finalColor = color * shade * aoColorMul[i]}。
     */
    public final float[] aoColorMul = {1.0f, 1.0f, 1.0f, 1.0f};
    // ==================== Outputs (mutable) ====================
    public boolean skip = false;
    public int color = 0xFFFFFF;
    public int brightnessOverride = -1;
    public float shade;
    /**
     * 纹理覆盖。当此字段非 null 时，渲染器将使用此 IIcon 代替
     * {@link BakedQuad#icon} 进行 UV 采样。
     * 适用于运行时的纹理切换（例如根据画质切换树叶纹理）。
     */
    public IIcon iconOverride = null;

    /**
     * Display transform 矩阵（向量空间）。
     * 由 {@link decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension}
     * 在扩展链中计算并设置，管线在提交顶点前应用此矩阵变换顶点坐标。
     */
    @Nullable
    public Matrix4d displayTransform = null;

    /**
     * 旧签名构造器兼容 shim：blockstateProps / itemProps 均为 null。
     */
    public RenderContext(RenderPhase phase, BakedQuad quad,
                         IBlockAccess world, int x, int y, int z, Block block,
                         ItemStack stack,
                         int baselineBrightness, float defaultShade) {
        this(phase, quad, world, x, y, z, block, stack,
                baselineBrightness, defaultShade, null, null);
    }

    public RenderContext(RenderPhase phase, BakedQuad quad,
                         IBlockAccess world, int x, int y, int z, Block block,
                         ItemStack stack,
                         int baselineBrightness, float defaultShade,
                         @Nullable Map<String, String> blockstateProps,
                         @Nullable Map<String, Comparable<?>> itemProps) {
        this.phase = phase;
        this.quad = quad;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.block = block;
        this.stack = stack;
        this.baselineBrightness = baselineBrightness;
        this.shade = defaultShade;
        this.blockstateProps = blockstateProps;
        this.itemProps = itemProps;
    }

    /**
     * 将给定 0xRRGGBB 颜色按通道乘入当前 {@link #color}。
     * 适合"叠加"风格的扩展（如 Tint + 暗化护甲）。
     */
    public void mulColor(int rgb) {
        int r0 = (color >> 16) & 0xFF, g0 = (color >> 8) & 0xFF, b0 = color & 0xFF;
        int r1 = (rgb >> 16) & 0xFF, g1 = (rgb >> 8) & 0xFF, b1 = rgb & 0xFF;
        int r = (r0 * r1) / 255;
        int g = (g0 * g1) / 255;
        int b = (b0 * b1) / 255;
        this.color = (r << 16) | (g << 8) | b;
    }

    /**
     * 当前使用的最终亮度（override 优先，否则 baseline）。
     */
    public int effectiveBrightness() {
        return brightnessOverride >= 0 ? brightnessOverride : baselineBrightness;
    }
}
