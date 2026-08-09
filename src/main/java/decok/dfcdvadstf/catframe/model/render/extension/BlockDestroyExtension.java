package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.render.api.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.util.IIcon;

/**
 * 破坏贴花扩展：仅在 {@link RenderPhase#BLOCK_DESTROY} 阶段生效。
 * <p>
 * 运行于原版 {@code RenderGlobal.drawBlockDamageTexture} 的破坏批次内
 * （乘法混合、polygon offset、blocks atlas 绑定均由原版完成），本扩展负责
 * 把破坏图标覆盖到 quad 上，并按 26.1.2 语义强制全亮 + 白色顶点：
 * <ul>
 *   <li>{@link RenderContext#iconOverride} = 当前破坏阶段图标（destroy_stage_0~9）；</li>
 *   <li>{@link RenderContext#brightnessOverride} = 15728880（全亮）；</li>
 *   <li>{@link RenderContext#shade} = 1.0（无方向阴影）；</li>
 *   <li>{@link RenderContext#color} = 0xFFFFFF（防染色，Tint 对 BLOCK_DESTROY 本就 return）。</li>
 * </ul>
 * 其余内建扩展按 phase 门控天然失效：FaceCull 无剔除（全量 quads）、AOCompute 无 AO、
 * DisplayTransform 无 display 变换。
 *
 * <p>线程安全：破坏图标经静态方法写入 ThreadLocal（渲染可从任意线程进入），
 * 单次调用结束后在 {@link #afterPart()} 清除，防止脏值泄漏到后续渲染。
 */
public class BlockDestroyExtension implements IModelRenderExtension {

    /** 全亮光照值（与原版 RenderHelper / 物品渲染一致）。 */
    private static final int FULL_BRIGHTNESS = 15728880;

    /** 当前破坏阶段图标（主线程破坏批次每帧设置）。 */
    private final ThreadLocal<IIcon> destroyIcon = new ThreadLocal<>();

    /**
     * 设置当前破坏阶段图标（破坏渲染路径调用）。
     * 图标来自原版 {@code RenderGlobal.destroyBlockIcons[progress]}（0~9）。
     *
     * @param icon 破坏阶段图标（destroy_stage_0~9），null 时后续 apply 不覆盖纹理
     */
    public static void setDestroyIcon(IIcon icon) {
        INSTANCE.destroyIcon.set(icon);
    }

    private static final BlockDestroyExtension INSTANCE = new BlockDestroyExtension();

    /**
     * 获取内建单例（供 ModelRenderRegistry 注册与静态图标传递共用同一实例）。
     */
    public static BlockDestroyExtension getInstance() {
        return INSTANCE;
    }

    private BlockDestroyExtension() {
    }

    @Override
    public void apply(RenderContext ctx) {
        if (ctx.phase != RenderPhase.BLOCK_DESTROY) return;
        IIcon icon = destroyIcon.get();
        if (icon != null) ctx.iconOverride = icon;
        ctx.brightnessOverride = FULL_BRIGHTNESS;
        ctx.shade = 1.0f;
        ctx.color = 0xFFFFFF;
    }

    @Override
    public void afterPart() {
        // 清除 ThreadLocal，防止脏值泄漏到后续渲染批次
        destroyIcon.remove();
    }
}
