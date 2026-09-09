package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderPhasePolicy;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import java.util.List;

/**
 * 内建渲染扩展：阶段默认亮度政策（光政策统一居住于扩展链的收口扩展）。
 * <p>
 * 收编提交端（UniformRenderPipeline / RenderDispatcher）原亮度解析职责：
 * 每提交项的基础亮度（GUI 恒 255 / 手持取玩家位置世界光 / 掉落与展示框取实体位置
 * 世界光 / 方块世界 AO 退化 quad 取方块混合亮度）由本扩展写入
 * {@link RenderContext#brightnessOverride}，供发射器经
 * {@link RenderContext#effectiveBrightness()} 消费 —— QuadWriter 与提交端不再
 * 做任何阶段亮度决策（DBF 化：政策外移的扩展侧形态）。
 * <p>
 * <b>接力协议（为什么不用 apply 直接现算）</b>：物品阶段亮度是<b>每提交项一次</b>
 * 的常量（同一提交的所有 quad 共享），而 {@code apply} 是每 quad 时机 —— 若在
 * apply 现算，同模型 N 个 quad 会重复 N 次世界光照查表。因此：
 * <ol>
 *   <li>{@code beforePart}（每提交项一次）对物品阶段计算一次亮度存入
 *       {@link ThreadLocal}（接力缓存）；</li>
 *   <li>{@code apply}（每 quad）只读缓存、写 {@code brightnessOverride}（int 赋值，零查表）；</li>
 *   <li>{@code afterPart} 清除缓存（GuiLightExtension 同款清理协议，防跨提交项残留）。</li>
 * </ol>
 * ThreadLocal 理由：渲染路径可从任意线程进入（Beddium 多线程区块编译），且同一线程内
 * 提交项交替出现（flushGroup 逐提交项 beforePart→apply×N→afterPart），实例字段会互相
 * 覆盖（扩展链容器 COW 亦要求扩展实例无跨线程共享状态）。
 * <p>
 * <b>阶段分支</b>：
 * <ul>
 *   <li>物品阶段（非方块 / 非 destroy）→ 写 beforePart 接力值；接力缺失（极端异常
 *       路径）不写，留给 ctx baseline 兜底；</li>
 *   <li>{@code BLOCK_WORLD} → 仅 AO 退化 quad（{@code aoBrightness[0] < 0}，
 *       非逐顶点路径）现算方块混合亮度（退化 quad 量少，成本可忽略；与
 *       AOShadeExtension 的 {@code ambientOcclusion=false} 分支同值重叠无害）；
 *       逐顶点 AO quad 由发射器走 aoBrightness 通道，不经本扩展；</li>
 *   <li>{@code BLOCK_DESTROY} → 跳过（破坏贴花全亮由 BlockDestroyExtension 全权）。</li>
 * </ul>
 * 值来源 = {@link RenderPhasePolicy#baselineBrightness}（唯一计算工具，语义逐位复刻
 * 迁移前的 QuadWriter 分支）；本扩展就绪后其 override 恒优先于
 * {@code RenderSubmit.baselineBrightness}。
 * <p>
 * Built-in extension: the phase-default brightness policy, written into
 * brightnessOverride per quad from a beforePart-computed relay value.
 */
public final class LightPolicyExtension implements IModelRenderExtension {

    /**
     * beforePart 计算的提交级亮度接力缓存：null = 本提交项无提交级常量
     * （方块 destroy / 异常跳过），apply 不写 override。
     */
    private final ThreadLocal<Integer> pending = new ThreadLocal<>();

    @Override
    public void beforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {
        if (isItemPhase(phase)) {
            // 物品阶段亮度是提交级常量：GUI 恒 255（便宜）；手持 / 掉落 / 展示框
            // 在 RenderPhasePolicy 内部经 Minecraft 玩家单例与 RenderJsonItemModel
            // 掉落实体 thread-local 自取上下文（write 期 flush 与 submit 同处
            // 实体窗口内，见 RenderPhasePolicy 类注释）。
            pending.set(RenderPhasePolicy.baselineBrightness(phase, null, 0, 0, 0, null));
        } else {
            // BLOCK_WORLD / BLOCK_DESTROY / null：无提交级常量（方块亮度需要坐标，
            // 只能在 apply 对退化 quad 现算；destroy 由 BlockDestroyExtension 全权）。
            pending.remove();
        }
    }

    @Override
    public void apply(RenderContext ctx) {
        if (ctx.phase == RenderPhase.BLOCK_WORLD) {
            // AO 逐顶点 quad（aoBrightness[0] >= 0）由发射器直接消费，不经本路径；
            // 仅退化 / 未算 quad 需要默认方块亮度（同提交同坐标 → 每次算出的值相同，
            // 仅量少时触发，等价 AOShadeExtension false 分支的现算语义）。
            if (ctx.aoBrightness[0] < 0 && ctx.world != null && ctx.block != null) {
                ctx.brightnessOverride = ctx.block.getMixedBrightnessForBlock(
                        ctx.world, ctx.x, ctx.y, ctx.z);
            }
            return;
        }
        if (!isItemPhase(ctx.phase)) {
            // BLOCK_DESTROY：破坏贴花全亮由 BlockDestroyExtension 管理（恒 15728880）。
            return;
        }
        Integer v = pending.get();
        if (v != null) {
            ctx.brightnessOverride = v;
        }
        // pending 为 null（beforePart 异常跳过的极端路径）：不写 override，
        // 亮度回退 ctx baseline（QuadWriter 的 -1 链缺失兜底）。
    }

    @Override
    public void afterPart() {
        pending.remove();
    }

    /**
     * 是否为物品渲染阶段（非方块世界 / 非破坏贴花）。
     * 与 {@link RenderPhasePolicy#isItemGlLit} 不同：GUI 与手持阶段同样需要默认亮度
     * （255 / 玩家光），故此处是全集判定而非 GL 光照判定。
     */
    private static boolean isItemPhase(RenderPhase phase) {
        return phase != null
                && phase != RenderPhase.BLOCK_WORLD
                && phase != RenderPhase.BLOCK_DESTROY;
    }
}
