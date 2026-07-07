package decok.dfcdvadstf.catframe.model.render.extension.ao;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.extension.ao.light.BlockModelLighter;

/**
 * 内建渲染扩展：执行逐顶点 AO（环境光遮蔽）计算。
 *
 * <p>委托给 {@link BlockModelLighter#prepareQuadAmbientOcclusion}，
 * 对齐 26.1.2 {@code BlockModelLighter.prepareQuadAmbientOcclusion} 算法：
 * 面形状分析 → 9 点采样 → 边角回退 → smoothBlend 平滑混合 → 部分面加权。
 *
 * <p>输出写入 {@link RenderContext#aoBrightness}[4] 和 {@link RenderContext#aoColorMul}[4]。
 * aoColorMul 仅含 AO shade，方向光照（CardinalLighting）由 {@link RenderContext#shade} 承载。
 *
 * <p>本扩展强制为扩展链的第一个扩展，确保后续扩展（如 AOShadeExtension）
 * 可以读取/修改 AO 计算结果。
 */
public final class AOComputeExtension implements IModelRenderExtension {

    @Override
    public void apply(RenderContext ctx) {
        // 仅在世界渲染阶段执行 AO 计算
        if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
        if (ctx.world == null || ctx.block == null) return;

        BakedQuad q = ctx.quad;
        if (q.face == null) return;

        BlockModelLighter.get().prepareQuadAmbientOcclusion(
                ctx.world, ctx.x, ctx.y, ctx.z,
                ctx.block, q, ctx);
    }
}
