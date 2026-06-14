package decok.dfcdvadstf.catframe.model.render.extension.ao;

import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;

/**
 * 内建渲染扩展：处理 JSON element 中的 {@code "ambientocclusion"} 和 {@code "shade"} 字段。
 *
 * <h3>逐顶点 AO 原理（对齐 26.1 BlockModelLighter 算法）</h3>
 * <p>在 {@link RenderPhase#BLOCK_WORLD} 阶段，
 * {@link decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline#computeVertexAO}
 * 按 26.1 {@code BlockModelLighter.prepareQuadAmbientOcclusion} 算法预先计算逐顶点数据：
 * 面形状分析（faceCubic / facePartial）、9 位置采样（含透明边角回退）、
 * 部分面加权混合。每个面顶点取相邻 3 个角 + 中心的 shade brightness 平均，
 * 再混合 packed 光照坐标。</p>
 *
 * <h3>行为规则</h3>
 * <ul>
 *   <li><strong>默认行为（{@code null}）：AO 生效</strong> — 逐顶点 AO 数据保持不动，
 *     渲染器会按 4 个顶点各自独立发射（每个顶点不同亮度和颜色）。</li>
 *   <li>{@code ambientOcclusion = false}：清除逐顶点数据，退化到 flat 平面光照
 *     （使用方块自身亮度，非自发光）。对应 26.1 中
 *     {@code ModelBlockRenderer} 不满足 AO 条件时的 flat 路径。</li>
 *   <li>{@code shadeEnabled = false}：禁用方向阴影，强制 {@code shade = 1.0f}（均匀照明）。
 *     逐顶点 AO 数据保留，仅方向系数被抹平。</li>
 * </ul>
 *
 * <p>本扩展由 {@link ModelRenderRegistry} 在首次使用时自动安装。
 */
public final class AOShadeExtension implements IModelRenderExtension {
    @Override
    public void apply(RenderContext ctx) {
        // 处理 ambientocclusion
        if (ctx.quad.ambientOcclusion != null && !ctx.quad.ambientOcclusion) {
            // 显式禁用 AO：清除逐顶点数据 → 退化到 flat 平面光照
            // 26.1 flat 路径使用方块自身亮度（非自发光 0xF000F0）
            for (int i = 0; i < 4; i++) {
                ctx.aoBrightness[i] = -1;
                ctx.aoColorMul[i] = 1.0f;
            }
            if (ctx.phase == RenderPhase.BLOCK_WORLD && ctx.block != null && ctx.world != null) {
                ctx.brightnessOverride = ctx.block.getMixedBrightnessForBlock(
                        ctx.world, ctx.x, ctx.y, ctx.z);
            }
        }

        // 处理 shade
        if (ctx.quad.shadeEnabled != null && !ctx.quad.shadeEnabled) {
            // 禁用方向阴影：抹平方向系数，逐顶点 AO 数据保留
            ctx.shade = 1.0f;
        }
    }
}
