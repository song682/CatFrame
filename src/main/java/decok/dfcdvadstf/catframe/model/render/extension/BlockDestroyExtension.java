package decok.dfcdvadstf.catframe.model.render.extension;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.util.IIcon;

/**
 * 渲染扩展：破坏贴花（destroy overlay）。
 * <p>
 * 在 {@link RenderPhase#BLOCK_DESTROY} 阶段（即原版
 * {@code RenderGlobal.drawBlockDamageTexture} 批次内）对每个 quad：
 * <ul>
 *   <li>将 {@link RenderContext#iconOverride} 设为当前破坏阶段的
 *       {@code destroy_stage_0~9} IIcon（由
 *       {@link decok.dfcdvadstf.catframe.model.RenderDispatcher#renderBlockDestroy}
 *       经 {@link #setCurrentIcon} 注入）；</li>
 *   <li>强制全亮光照（15728880）、无方向阴影（shade=1.0）、纯白顶点色——
 *       对标 26.1.2 {@code BlockFeatureRenderer} 的破坏覆盖层语义，
 *       暗处破坏贴花依然清晰，裂缝效果纯靠贴图黑白像素与乘法混合呈现。</li>
 * </ul>
 * <p>
 * Destroy decal extension: in the BLOCK_DESTROY phase, overrides the quad icon
 * with the current destroy-stage sprite and forces full brightness / white
 * vertex color, matching the 26.1.2 BlockFeatureRenderer overlay semantics.
 * <p>
 * <b>线程安全</b>：{@link #CURRENT_ICON} 使用 ThreadLocal ——
 * {@code renderBlockUsingTexture} 仅在主线程的 {@code drawBlockDamageTexture}
 * 中被调用，且 set/clear 由调用方以 try/finally 包住同步的 flushInline，
 * 无线程泄漏。
 */
@SideOnly(Side.CLIENT)
public final class BlockDestroyExtension implements IModelRenderExtension {

    /** 当前线程正在渲染的破坏图标（destroy_stage_0~9 之一）。 */
    private static final ThreadLocal<IIcon> CURRENT_ICON = new ThreadLocal<>();

    public BlockDestroyExtension() {
    }

    /**
     * 设置当前线程的破坏图标。必须在 flush 前调用、flush 后
     * {@link #clearCurrentIcon()}（调用方以 try/finally 保证）。
     *
     * @param icon 当前破坏阶段的 IIcon（null 安全，忽略）
     */
    public static void setCurrentIcon(IIcon icon) {
        CURRENT_ICON.set(icon);
    }

    /**
     * 清除当前线程的破坏图标（flush 后调用）。
     */
    public static void clearCurrentIcon() {
        CURRENT_ICON.remove();
    }

    @Override
    public void apply(RenderContext ctx) {
        // 仅破坏贴花阶段生效
        // Only active during the destroy overlay phase
        if (ctx.phase != RenderPhase.BLOCK_DESTROY) return;

        IIcon icon = CURRENT_ICON.get();
        if (icon != null) {
            ctx.iconOverride = icon;
        }
        // 对标 26.1.2 BlockFeatureRenderer：全亮 + 纯白顶点色，暗处破坏贴花依然清晰
        // Match 26.1.2 BlockFeatureRenderer: full light + white vertex color
        ctx.brightnessOverride = 15728880;
        ctx.shade = 1.0f;
        ctx.color = 0xFFFFFF;
    }
}
