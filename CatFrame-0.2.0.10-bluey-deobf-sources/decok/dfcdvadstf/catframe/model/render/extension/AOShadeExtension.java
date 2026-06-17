package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;

/**
 * 内建渲染扩展：处理 JSON element 中的 {@code "ambientocclusion"} 和 {@code "shade"} 字段。
 *
 * <h3>逐顶点 AO 原理</h3>
 * <p>在 {@link RenderPhase#BLOCK_WORLD} 阶段，{@link decok.dfcdvadstf.catframe.model.VanillaModelManager}
 * 会预先计算逐顶点的 AO 亮度（{@link RenderContext#aoBrightness}）和遮挡系数
 * （{@link RenderContext#aoColorMul}），算法模拟原版
 * {@code RenderBlocks.renderStandardBlockWithAmbientOcclusion}：
 * 对每个面顶点采样 3 个相邻方块的亮度 + AO 遮挡值，混合得出该顶点的最终亮度和颜色。</p>
 *
 * <h3>行为规则</h3>
 * <ul>
 *   <li><strong>默认行为（{@code null}）：AO 生效</strong> — 逐顶点 AO 数据保持不动，
 *     渲染器会按 4 个顶点各自独立发射（每个顶点不同亮度和颜色）。
 *     大部分方块无需额外配置。</li>
 *   <li>{@code ambientOcclusion = false}：清除所有逐顶点数据，退化到 uniform 全亮度渲染
 *     （{@code brightnessOverride = 0xF000F0}，自发光效果）。</li>
 *   <li>{@code shadeEnabled = false}：禁用方向阴影，强制 {@code shade = 1.0f}（均匀照明）。
 *     逐顶点 AO 数据保留，仅方向系数被抹平。</li>
 * </ul>
 *
 * <p>本扩展由 {@link ModelRenderRegistry} 在首次使用时自动安装到链头，
 * 位于 {@link decok.dfcdvadstf.catframe.model.render.extension.tint.TintRenderExtension} 之前。</p>
 */
public final class AOShadeExtension implements IModelRenderExtension {
    @Override
    public void apply(RenderContext ctx) {
        // 处理 ambientocclusion
        if (ctx.quad.ambientOcclusion != null && !ctx.quad.ambientOcclusion) {
            // 显式禁用 AO：清除逐顶点数据 → 退化到 uniform 全亮
            for (int i = 0; i < 4; i++) {
                ctx.aoBrightness[i] = -1;
                ctx.aoColorMul[i] = 1.0f;
            }
            ctx.brightnessOverride = 0xF000F0;
        }

        // 处理 shade
        if (ctx.quad.shadeEnabled != null && !ctx.quad.shadeEnabled) {
            // 禁用方向阴影：抹平方向系数，逐顶点 AO 数据保留
            ctx.shade = 1.0f;
        }
    }
}
