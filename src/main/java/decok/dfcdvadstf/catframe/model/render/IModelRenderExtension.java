package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;

import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import java.util.List;

/**
 * 模组可注册的渲染扩展接口。每个被烘焙的 quad 在送进 Tessellator 之前，
 * 都会按注册顺序遍历所有扩展，每个扩展通过修改 {@link RenderContext} 字段
 * 来影响最终渲染（着色、亮度、是否剔除等）。
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@link #beforePart(List, RenderPhase)} — 处理一组 quad 之前调用一次，
 *       适用于 GL 状态设置等全局操作。</li>
 *   <li>{@link #apply(RenderContext)} — 对每个 quad 依次调用。</li>
 *   <li>{@link #afterPart()} — 一组 quad 全部处理完后调用一次，
 *       适用于 GL 状态恢复等清理操作。</li>
 * </ol>
 *
 * <p>除了 {@link #apply(RenderContext)} 为强制实现外，其余两个方法都有默认空实现，
 * 扩展只需按需覆写。
 *
 * <h3>常见用法示例</h3>
 * <pre>{@code
 * // 1. 让某种方块顶面叠加一层暖色（仅世界渲染）
 * ModelRenderRegistry.register(ctx -> {
 *     if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
 *     if (ctx.block != MyBlocks.LAVA_ROCK) return;
 *     if (ctx.quad.face != EnumFacing.UP) return;
 *     ctx.mulColor(0xFFAA66);
 * });
 *
 * // 2. 自定义阴影：所有手持物品亮度降一半
 * ModelRenderRegistry.register(ctx -> {
 *     if (ctx.phase == RenderPhase.ITEM_HAND) ctx.brightnessOverride = 0x800080;
 * });
 *
 * // 3. 面剔除：当 quad 朝向北侧且北侧邻居是不透明方块时不渲染
 * ModelRenderRegistry.register(ctx -> {
 *     if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
 *     if (ctx.quad.face != EnumFacing.NORTH) return;
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
     *
     * @param allQuads 当前部件的所有 BakedQuad
     * @param phase    当前渲染阶段
     * @param part     当前渲染的 BlockStateModelPart（提供 part 级别元数据）
     */
    default void beforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {
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
     */
    default void afterPart() {
    }

    /**
     * 旧版两参数 beforePart，默认委托给三参数版本（part=null）。
     * 实现类应优先覆写 {@link #beforePart(List, RenderPhase, BlockStateModelPart)}。
     */
    default void beforePart(List<BakedQuad> allQuads, RenderPhase phase) {
        beforePart(allQuads, phase, null);
    }
}
