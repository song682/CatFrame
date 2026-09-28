package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.api.IRenderGroupHandler;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.api.RenderSubmitView;
import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 命令执行器 / 批量 flush，对标原版 26w+ 管线中的 {@code FeatureRenderDispatcher}。
 * <p>
 * 消费 {@link SubmitNodeStorage} 里累积的 {@link RenderSubmit}，按注册表排序快照
 * （{@link RenderTypeKey#sortKey()} 升序，solid 先于 translucent）执行绘制。
 *
 * <h3>批处理与零回归的折中</h3>
 * <p>同一 {@link RenderTypeKey} 分组内<b>纹理绑定与 GL 状态（混合/剔除）只设置一次</b>，
 * 并按排序键升序 flush，从而减少纹理切换、修正半透明叠加顺序。
 * <p>但每个 {@link RenderSubmit} 仍各自 {@code applyBeforePart → startDrawingQuads →
 * write → draw → applyAfterPart}，严格保留原逐部件生命周期：
 * <ul>
 *   <li>{@code GuiLightExtension} 在 {@code beforePart} 切换 {@code GL_LIGHTING}，
 *       必须在该部件 {@code draw()} 时生效、{@code afterPart} 恢复；</li>
 *   <li>{@code DisplayTransformExtension} 在 {@code beforePart} 计算 display 矩阵，
 *       在 {@code apply} 逐 quad 写入 {@link decok.dfcdvadstf.catframe.model.render.api.RenderContext#displayTransform}。</li>
 * </ul>
 * 逐部件 draw 与原单部件路径完全等价，仅共享纹理绑定并重排顺序，故零渲染回归。
 */
public final class FeatureRenderDispatcher {

    /** 复用查询缓冲：读取 GL_ALPHA_TEST_REF（客户端渲染单线程，无并发）。
     * <p>注意：必须分配 16 个元素 —— LWJGL2 的 {@code glGetFloat} 底层共用 {@code glGetFloatv}
     * （矩阵查询最多返回 16 个浮点），{@code BufferChecks.checkBuffer} 无视实际枚举
     * 返回数量、无条件要求缓冲区至少 16 个元素，否则抛 IllegalArgumentException。 */
    private static final FloatBuffer STATE_QUERY = BufferUtils.createFloatBuffer(16);

    private FeatureRenderDispatcher() {
    }

    /**
     * 批量 flush 命令缓冲（用于物品 / GUI 独立绘制作用域）。
     * 按注册表排序快照顺序（solid 先于 translucent）遍历非空分组。
     * <p>
     * <b>分组认领 SPI</b>：仲裁出的 {@link IRenderGroupHandler} 认领某分组时，整组以
     * {@link RenderSubmitView} 只读视图移交 handler 绘制（CatFrame 不执行内建 flush 逻辑）；
     * 无 handler / handler 抛异常（含 Error）→ 回退内建 {@link #flushGroup}（默认路径零回归）。
     */
    public static void flushBatched(SubmitNodeStorage storage) {
        for (Map.Entry<RenderTypeKey, List<RenderSubmit>> entry : storage.groups()) {
            List<RenderSubmit> group = entry.getValue();
            if (group == null || group.isEmpty()) continue;
            RenderTypeKey type = entry.getKey();

            // 认领路径：handler 全权接管该分组（自管理 GL 状态，组后恢复）。
            IRenderGroupHandler handler = RenderGroupHandlerRegistry.handlerFor(type);
            if (handler != null) {
                try {
                    handler.flush(new ArrayList<RenderSubmitView>(group));
                    continue;
                } catch (Throwable t) {
                    // 错误隔离：handler 异常记日志并回退内建 flushGroup，不拖死整场渲染
                    CatFrame.logger.warn("[FeatureRenderDispatcher] group handler {} failed for {}: {}",
                            handler.getClass().getName(), type.id(), t.toString(), t);
                }
            }
            flushGroup(type, group);
        }
    }

    /**
     * 内建组级 flush：纹理绑定 + GL 状态一次性设置，逐提交项执行
     * {@code applyBeforePart → startDrawingQuads → write → draw → applyAfterPart} 生命周期。
     * 认领回退时亦复用本路径（行为与改造前逐字节一致）。
     */
    private static void flushGroup(RenderTypeKey type, List<RenderSubmit> group) {
        Tessellator t = Tessellator.instance;

        // 分组内提交项同质（作用域内来自同一物品/GUI 绘制），
        // 剔除标志取首个提交项即可代表整组。
        boolean disableCull = group.get(0).disableCull;
        boolean blend = type.blend();
        // [方案B] GL 光照物品阶段（掉落 / 展示框）会发送逐面法线，
        // 开 GL_NORMALIZE 使法线在 display / 手持变换缩放后仍保持单位长，只依赖方向。
        // 判定单源：RenderPhasePolicy.isItemGlLit（组内提交项同质，取首个即可代表整组）。
        boolean itemGlLit = RenderPhasePolicy.isItemGlLit(group.get(0).phase);

        // ==== 每组一次：纹理绑定 + GL 状态 ====
        Minecraft.getMinecraft().getTextureManager().bindTexture(type.atlas());
        if (disableCull) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        if (blend) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        if (itemGlLit) {
            GL11.glEnable(GL11.GL_NORMALIZE);
        }

        // glint 深度遮罩的写入侧前提：正常 pass 需开启 alpha test，透明纹素（alpha ≤ 0.1）
        // 被裁剪、不写深度，renderEnchantmentGlint 的 GL_EQUAL 才会在透明像素处失败；
        // 深度测试同时确保正常 pass 写入供 GL_EQUAL 比较的深度值。
        // 原版逐次自管（RenderItem.renderItemIntoGUI 开 alpha test、画完即关，renderEffect 再开），
        // Forge 自定义渲染器路径（IItemRenderer）两不管——继承的环境状态不可信
        // （前一次绘制可能刚把它关掉），故按组强制建立、组后精确恢复。
        // Write-side preconditions of the glint depth mask: the normal pass must run with
        // alpha test enabled so transparent texels are trimmed and never write depth, and
        // depth test must be on so the compared depth value exists. Vanilla establishes
        // these per draw; the Forge custom-renderer path inherits unreliable state.
        boolean prevAlphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int prevAlphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        STATE_QUERY.clear();
        GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF, STATE_QUERY);
        float prevAlphaRef = STATE_QUERY.get(0);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        try {
            for (RenderSubmit s : group) {
                // 生命周期：quad 处理前（DisplayTransformExtension 计算矩阵、
                // GuiLightExtension 视需要关闭 GL_LIGHTING）
                ModelRenderRegistry.applyBeforePart(s.part.getAllQuads(), s.phase, s.part);
                try {
                    t.startDrawingQuads();
                    boolean hasSolidColor;
                    if (isBlockPhase(s.phase)) {
                        QuadWriter.writeBlockQuads(s, t);
                        hasSolidColor = false;
                    } else {
                        hasSolidColor = QuadWriter.writeItemQuads(s, t);
                    }
                    // 恒 draw：即使无顶点，draw() 也会安全复位 Tessellator 的 isDrawing 状态，
                    // 避免下一提交项 startDrawingQuads() 抛 "Already tesselating"。
                    t.draw();

                    // 第二遍：solidColor quad（侧面纯色）无纹理渲染
                    // 禁用纹理 → GL_MODULATE 不再把半透明纹素 alpha 乘入 → 侧面恒不透明；
                    // 与背面剔除（cull）正交：剔除继续由分组 GL 状态控制，两者互不影响。
                    if (hasSolidColor) {
                        GL11.glDisable(GL11.GL_TEXTURE_2D);
                        t.startDrawingQuads();
                        QuadWriter.writeSolidColorQuads(s, t);
                        t.draw();
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                    }

                    // 附魔光效 pass：必须在 applyAfterPart() 之前执行 ——
                    // beforePart 状态（display 矩阵等）仍有效，重放几何才能与正常 pass 重合。
                    // 实现位于 GuiGraphicsExtractor，内部 glPushAttrib 全量保护（含纹理绑定）。
                    // Enchantment glint pass: must run before applyAfterPart() so the
                    // beforePart state (display matrix, etc.) is still valid for geometry replay.
                    if (GuiGraphicsExtractor.glintApplicable(s)) {
                        GuiGraphicsExtractor.renderEnchantmentGlint(s, t);
                    }
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
            if (itemGlLit) {
                GL11.glDisable(GL11.GL_NORMALIZE);
            }
            // 恢复本组前的 alpha test（含 func/ref——glPushAttrib 不覆盖 alpha func）与深度测试开关
            GL11.glAlphaFunc(prevAlphaFunc, prevAlphaRef);
            if (prevAlphaTest) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
            } else {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }
            if (!prevDepthTest) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
        }
    }

    /**
     * 内联 flush（用于 {@link RenderPhase#BLOCK_DESTROY} 与 {@link RenderPhase#BLOCK_WORLD}）。
     * <p>
     * [渲染三域架构] 原版后端（唯一路径）：世界方块在 chunk 编译时内联写入原版批次
     * （quad 携带原版图集 UV，批次绑定原版 blocks 图集），与 destroy 贴花共用
     * 同一“只写顶点、不碰 GL 状态”契约。
     * <p>
     * 破坏贴花发生在原版 {@code drawBlockDamageTexture} 的 startDrawingQuads 批次内部
     * （vanilla 已绑定 blocks atlas，GL 状态由其统一管理），因此本方法只写顶点到当前
     * Tessellator，<b>不</b> {@code startDrawingQuads}/{@code draw}、<b>不</b>绑定纹理、
     * <b>不</b>改 GL 状态。破坏图标（destroy_stage_N）属原版图集空间，与批次绑定一致。
     * <p>
     * <b>永不 consult 分组认领 handler</b>（契约措辞：inline 提交不可认领）—— 世界几何体须经
     * 收集端改道（如渲染作用域）后，在执行端作用域内才可被认领。
     * <p>
     * 注意：本方法可能从后台线程进入（如 Beddium 多线程区块编译），绑定纹理属
     * GL 调用且此处本就冗余（vanilla 已绑定 blocks atlas），故刻意不在此绑定。
     */
    public static void flushInline(RenderSubmit s) {
        // 不绑定纹理：vanilla 已绑定 blocks atlas，此处再 bind 属冗余，
        // 且后台线程（Beddium 多线程区块编译）执行 GL 调用会破坏 GL 状态所有权。
        ModelRenderRegistry.applyBeforePart(s.part.getAllQuads(), s.phase, s.part);
        try {
            QuadWriter.writeBlockQuads(s, Tessellator.instance);
        } finally {
            ModelRenderRegistry.applyAfterPart();
        }
    }

    private static boolean isBlockPhase(RenderPhase phase) {
        return phase == RenderPhase.BLOCK_WORLD;
    }
}
