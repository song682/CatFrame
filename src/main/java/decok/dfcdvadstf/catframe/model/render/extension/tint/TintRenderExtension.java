package decok.dfcdvadstf.catframe.model.render.extension.tint;

import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.RenderContext;

/**
 * 内建渲染扩展：处理 JSON face 中的 {@code "tintindex"} 字段。
 * 根据 {@link RenderContext#phase} 分别从 {@link TintRegistry} 拿
 * 方块 / 物品颜色，乘入 {@link RenderContext#color}。
 *
 * <p>本扩展由 {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry}
 * 在首次使用时自动安装到链头，模组通常不需要直接接触它——只需通过
 * {@link TintRegistry} 注册自定义染色规则即可。</p>
 */
public final class TintRenderExtension implements IModelRenderExtension {
    @Override
    public void apply(RenderContext ctx) {
        int idx = ctx.quad.tintIndex;
        if (idx < 0) return;

        int rgb;
        switch (ctx.phase) {
            case BLOCK_WORLD:
                rgb = TintRegistry.getBlockTint(ctx.world, ctx.x, ctx.y, ctx.z, ctx.block, idx);
                break;
            case BLOCK_GUI:
                // GUI 中方块无世界上下文，使用 Block.getRenderColor(metadata) 获取对应染色
                // [S1 修复] 使用 ctx.metadata 代替硬编码 0，支持不同 metadata 变体的染色
                if (ctx.block != null) {
                    rgb = ctx.block.getRenderColor(ctx.metadata) & 0xFFFFFF;
                } else {
                    return;
                }
                break;
            case ITEM_GUI:
            case ITEM_HAND_FIRST_PERSON:
            case ITEM_HAND_THIRD_PERSON:
            case DROPPED_ITEM_GROUND:
                rgb = TintRegistry.getItemTint(ctx.stack, idx);
                break;
            case DROPPED_BLOCK_GROUND:
                // 落地方块：优先使用世界上下文获取生物群系染色，
                // 无世界上下文时回退到 Block.getRenderColor(0) 或物品染色
                if (ctx.block != null && ctx.world != null) {
                    rgb = TintRegistry.getBlockTint(ctx.world, ctx.x, ctx.y, ctx.z, ctx.block, idx);
                } else if (ctx.block != null) {
                    rgb = ctx.block.getRenderColor(0) & 0xFFFFFF;
                } else if (ctx.stack != null) {
                    // 无世界上下文时退回到物品染色（如树叶掉落物）
                    rgb = TintRegistry.getItemTint(ctx.stack, idx);
                } else {
                    return;
                }
                break;
            default:
                return;
        }

        if (rgb != 0xFFFFFF) ctx.mulColor(rgb);
    }
}
