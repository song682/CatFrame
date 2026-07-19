package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

/**
 * 命令执行器 / 批量 flush，对标原版 26w+ 管线中的 {@code FeatureRenderDispatcher}。
 * <p>
 * 消费 {@link SubmitNodeStorage} 里累积的 {@link RenderSubmit}，按 {@link RenderType}
 * 分组（solid → translucent 顺序）执行绘制。
 *
 * <h3>批处理与零回归的折中</h3>
 * <p>同一 {@link RenderType} 分组内<b>纹理绑定与 GL 状态（混合/剔除）只设置一次</b>，
 * 并按 solid→translucent 排序，从而减少纹理切换、修正半透明叠加顺序。
 * <p>但每个 {@link RenderSubmit} 仍各自 {@code applyBeforePart → startDrawingQuads →
 * write → draw → applyAfterPart}，严格保留原逐部件生命周期：
 * <ul>
 *   <li>{@code GuiLightExtension} 在 {@code beforePart} 切换 {@code GL_LIGHTING}，
 *       必须在该部件 {@code draw()} 时生效、{@code afterPart} 恢复；</li>
 *   <li>{@code DisplayTransformExtension} 在 {@code beforePart} 计算 display 矩阵，
 *       在 {@code apply} 逐 quad 写入 {@link decok.dfcdvadstf.catframe.model.render.RenderContext#displayTransform}。</li>
 * </ul>
 * 逐部件 draw 与原单部件路径完全等价，仅共享纹理绑定并重排顺序，故零渲染回归。
 */
public final class FeatureRenderDispatcher {

    private FeatureRenderDispatcher() {
    }

    /**
     * 批量 flush 命令缓冲（用于物品 / GUI 独立绘制作用域）。
     * 按 {@link RenderType} 声明顺序（solid 先于 translucent）遍历非空分组。
     */
    public static void flushBatched(SubmitNodeStorage storage) {
        for (Map.Entry<RenderType, List<RenderSubmit>> entry : storage.groups()) {
            List<RenderSubmit> group = entry.getValue();
            if (group == null || group.isEmpty()) continue;
            RenderType type = entry.getKey();

            Tessellator t = Tessellator.instance;

            // 分组内提交项同质（作用域内来自同一物品/GUI 绘制），
            // 剔除标志取首个提交项即可代表整组。
            boolean disableCull = group.get(0).disableCull;
            boolean blend = type.blend();

            // ==== 每组一次：纹理绑定 + GL 状态 ====
            Minecraft.getMinecraft().getTextureManager().bindTexture(type.atlas());
            if (disableCull) {
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
            if (blend) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            try {
                for (RenderSubmit s : group) {
                    // 生命周期：quad 处理前（DisplayTransformExtension 计算矩阵、
                    // GuiLightExtension 视需要关闭 GL_LIGHTING）
                    ModelRenderRegistry.applyBeforePart(s.part.getAllQuads(), s.phase, s.part);
                    try {
                        t.startDrawingQuads();
                        if (isBlockPhase(s.phase)) {
                            QuadWriter.writeBlockQuads(s, t);
                        } else {
                            QuadWriter.writeItemQuads(s, t);
                        }
                        // 恒 draw：即使无顶点，draw() 也会安全复位 Tessellator 的 isDrawing 状态，
                        // 避免下一提交项 startDrawingQuads() 抛 "Already tesselating"。
                        t.draw();
                    } finally {
                        // 生命周期：quad 处理后（GuiLightExtension 恢复 GL_LIGHTING、
                        // DisplayTransformExtension 清矩阵）
                        ModelRenderRegistry.applyAfterPart();
                    }
                }
            } finally {
                // ==== 每组一次：恢复 GL 状态 ====
                if (disableCull) {
                    GL11.glEnable(GL11.GL_CULL_FACE);
                }
                if (blend) {
                    GL11.glDisable(GL11.GL_BLEND);
                }
            }
        }
    }

    /**
     * 内联 flush（用于 {@link RenderPhase#BLOCK_WORLD}）。
     * <p>
     * 世界渲染发生在原版 chunk 的 {@link Tessellator} 大批次内部（vanilla 已
     * {@code startDrawingQuads}，绑定 blocks atlas，并在 chunk 末尾统一 {@code draw}），
     * 因此本方法只写顶点到当前 Tessellator，<b>不</b> {@code startDrawingQuads}/{@code draw}、
     * <b>不</b>改 GL 状态。行为等同改造前 {@code renderBlockQuads} 的 BLOCK_WORLD 路径。
     */
    public static void flushInline(RenderSubmit s) {
        // 与改造前一致：世界渲染硬编码绑定 blocks atlas
        Minecraft.getMinecraft().getTextureManager().bindTexture(type(s).atlas());
        ModelRenderRegistry.applyBeforePart(s.part.getAllQuads(), s.phase, s.part);
        try {
            QuadWriter.writeBlockQuads(s, Tessellator.instance);
        } finally {
            ModelRenderRegistry.applyAfterPart();
        }
    }

    private static RenderType type(RenderSubmit s) {
        return s.type != null ? s.type : RenderType.BLOCK_ATLAS_SOLID;
    }

    private static boolean isBlockPhase(RenderPhase phase) {
        return phase == RenderPhase.BLOCK_WORLD || phase == RenderPhase.BLOCK_GUI;
    }
}
