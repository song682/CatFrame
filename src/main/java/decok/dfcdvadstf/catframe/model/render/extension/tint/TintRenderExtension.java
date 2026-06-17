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
                // GUI 中方块无世界上下文，使用 Block.getRenderColor(0) 获取默认染色
                // （与原版 renderBlockAsItem 行为一致；metadata 信息在渲染管线中不可用，使用 0 作为默认变体）
                if (ctx.block != null) {
                    rgb = ctx.block.getRenderColor(0) & 0xFFFFFF;
                } else {
                    return;
                }
                break;
            case ITEM_GUI:
            case ITEM_HAND_FIRST_PERSON:
            case ITEM_HAND_THIRD_PERSON:
                rgb = TintRegistry.getItemTint(ctx.stack, idx);
                break;
            default:
                return;
        }

        if (rgb != 0xFFFFFF) ctx.mulColor(rgb);
    }
}
