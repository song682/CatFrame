package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import java.util.List;

/**
 * 模组可注册的渲染扩展接口。每个被烘焙的 quad 在送进 Tessellator 之前，
 * 都会按优先级（同优先级按注册顺序）遍历所有扩展，每个扩展通过修改 {@link RenderContext} 字段
 * 来影响最终渲染（着色、亮度、是否剔除等）。
 * <p>
 * <b>线程安全约定（v0.5+）</b>：渲染路径可在任意线程进入（如 Beddium 多线程区块编译），
 * 扩展实例不得持有跨线程共享的可变状态；per-part 临时数据请使用 ThreadLocal 或写入
 * {@link RenderContext}。扩展链对单个扩展的异常做了隔离，不会拖死整场渲染。
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@link #beforePart(List, RenderContext, BlockStateModelPart)} — 处理一组 quad 之前
 *       调用一次，适用于 GL 状态设置等全局操作（收到提交级 ctx）。</li>
 *   <li>{@link #apply(RenderContext)} — 对每个 quad 依次调用（per-quad ctx）。</li>
 *   <li>{@link #afterPart(RenderContext)} — 一组 quad 全部处理完后调用一次，
 *       适用于 GL 状态恢复等清理操作（与 beforePart 配对的同一提交级 ctx）。</li>
 * </ol>
 *
 * <p>除了 {@link #apply(RenderContext)} 为强制实现外，其余两个方法都有默认空实现，
 * 扩展只需按需覆写。
 *
 * <h3>常见用法示例</h3>
 * <pre>{@code
 * // 1. 让某种方块顶面叠加一层暖色（仅世界渲染）
 * ModelRenderExtensions.register(ctx -> {
 *     if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
 *     if (ctx.block != MyBlocks.LAVA_ROCK) return;
 *     if (ctx.quad.face != Direction.UP) return;
 *     ctx.mulColor(0xFFAA66);
 * });
 *
 * // 2. 自定义阴影：所有手持物品亮度降一半
 * ModelRenderExtensions.register(ctx -> {
 *     if (ctx.phase.isHandPhase()) ctx.brightnessOverride = 0x800080;
 * });
 *
 * // 3. 面剔除：当 quad 朝向北侧且北侧邻居是不透明方块时不渲染
 * ModelRenderExtensions.register(ctx -> {
 *     if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
 *     if (ctx.quad.face != Direction.NORTH) return;
 *     if (ctx.world.getBlock(ctx.x, ctx.y, ctx.z - 1).isOpaqueCube()) ctx.skip = true;
 * });
 * }</pre>
 */
public interface IModelRenderExtension {

    /**
     * 处理一组 quad 之前的生命周期回调。在遍历 quad 列表前调用一次。
     * <p>
     * 适用于需要在处理所有 quad 之前执行的全局操作，
     * 例如检测模型光照模式并设置 GL_LIGHTING 状态。
     * <p>
     * {@code ctx} 为<b>提交级上下文</b>（每提交项构造一次）：携带 phase / world / 坐标 /
     * block / stack / blockstateProps / itemProps 等提交级信息；{@link RenderContext#quad}
     * 为 null，quad 级字段（aoBrightness / shade 等）无渲染语义。对其输出字段的写入
     * <b>不会</b>传递到 {@link #apply(RenderContext)} 的 per-quad ctx —— 需要整组级桥接时
     * 请自行用 ThreadLocal 接力（参见内建 LightPolicyExtension 接力协议）。
     *
     * @param allQuads 当前部件的所有 BakedQuad
     * @param ctx      提交级渲染上下文（quad 为 null）
     * @param part     当前渲染的 BlockStateModelPart（提供 part 级别元数据）
     */
    default void beforePart(List<BakedQuad> allQuads, RenderContext ctx, BlockStateModelPart part) {
    }

    /**
     * 处理单个 quad 的上下文。
     * 若设置了 {@link RenderContext#skip skip=true}，后续扩展将被跳过且该 quad 不渲染。
     */
    void apply(RenderContext ctx);

    /**
     * 一组 quad 全部处理完后的生命周期回调。
     * <p>
     * 适用于需要在处理完所有 quad 之后执行的清理操作，
     * 例如恢复 {@code beforePart} 中修改的 GL 状态。
     * <p>
     * 收到的 {@code ctx} 与 {@link #beforePart(List, RenderContext, BlockStateModelPart)}
     * 的为同一提交级实例（配对使用）。
     */
    default void afterPart(RenderContext ctx) {
    }
}
