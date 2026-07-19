package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * 内建渲染扩展：处理 JSON model 中的 {@code "gui_light"} 字段。
 *
 * <p>根据 JSON model 的 {@code gui_light} 设置来控制方向阴影和 OpenGL GL_LIGHTING 状态：
 * <ul>
 *   <li>{@code "front"} — 正面光照，无方向阴影（{@link RenderContext#shade} = 1.0），
 *       关闭 GL_LIGHTING（平面光照，如物品）。{@code afterPart} 自动恢复。</li>
 *   <li>{@code "side"} — 侧面光照，保留默认方向阴影（{@code shade} 由法线决定），
 *       不修改 GL_LIGHTING 状态。</li>
 *   <li>未设置 — 默认 {@code "side"} 行为。</li>
 * </ul>
 *
 * <h3>与原版对齐</h3>
 * <p>原版 {@code RenderBlocks} 在 {@code renderStandardBlockWithColorMultiplier}
 * 和 {@code renderStandardBlockWithAmbientOcclusion} 中均不管理 GL_LIGHTING 状态，
 * GL_LIGHTING 由外层 {@code RenderGlobal/EntityRenderer} 统一管理。
 * 本扩展仅模拟原版 {@code RenderItem} 中为 gui_light="front" 物品关闭 GL_LIGHTING 的行为，
 * 绝不主动开启 GL_LIGHTING，避免破坏外层已设定的 OpenGL 光照状态。</p>
 *
 * <h3>生命周期</h3>
 * <p>本扩展通过 {@link IModelRenderExtension#beforePart(List, RenderPhase)} 和
 * {@link IModelRenderExtension#afterPart()} 生命周期回调管理 GL_LIGHTING 状态，
 * 由 {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry} 统一调度。
 */
public final class GuiLightExtension implements IModelRenderExtension {

    /** {@code true} 表示 {@code beforePart} 修改了 GL_LIGHTING 状态，{@code afterPart} 需要恢复 */
    private boolean changedLighting = false;

    @Override
    @SuppressWarnings("deprecation")
    public void beforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {

        // 方块渲染：按模型 gui_light 字段决定
        boolean frontLight = needsFrontLighting(allQuads);
        if (frontLight) {
            changedLighting = true;
            GL11.glDisable(GL11.GL_LIGHTING);
        } else {
            changedLighting = false;
        }
    }

    @Override
    public void apply(RenderContext ctx) {
        String guiLight = ctx.quad.guiLight;

        // "front" 光照模式：无方向阴影，所有面均匀受光
        if ("front".equals(guiLight)) {
            ctx.shade = 1.0f;
            return;
        }

        // "side" 或 null：保留默认方向阴影（由 CardinalLighting.byFace 计算）
        // 注意：不修改 ctx.quad.guiLight，避免副作用影响其他渲染通道
    }

    @Override
    public void afterPart() {
        if (changedLighting) {
            GL11.glEnable(GL11.GL_LIGHTING);
            changedLighting = false;
        }
    }

    /**
     * 检查模型的 quads 是否需要正面光照（{@code gui_light = "front"}）。
     * 根据原版惯例，只需检查第一个 quad 的 guiLight 即可判定整个模型的光照模式。
     */
    private static boolean needsFrontLighting(List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) return false;
        return "front".equals(quads.get(0).guiLight);
    }
}