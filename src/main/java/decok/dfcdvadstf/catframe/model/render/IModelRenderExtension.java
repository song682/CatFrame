package decok.dfcdvadstf.catframe.model.render;

/**
 * 模组可注册的渲染扩展接口。每个被烘焙的 quad 在送进 Tessellator 之前，
 * 都会按注册顺序遍历所有扩展，每个扩展通过修改 {@link RenderContext} 字段
 * 来影响最终渲染（着色、亮度、是否剔除等）。
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
     * 处理单个 quad 的上下文。
     * 若设置了 {@link RenderContext#skip skip=true}，后续扩展将被跳过且该 quad 不渲染。
     */
    void apply(RenderContext ctx);
}
