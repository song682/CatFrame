package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * 物品 Tint 颜色提供者接口。
 * <p>
 * 每个实现根据 {@link ItemStack} 和 {@link RenderPhase} 计算一个 RGB 颜色值。
 * 在 JSON 物品模型映射中，tints 作为高优先级覆盖层应用于 ModelLeaf。
 * <p>
 * 与现有 {@code TintRegistry}/{@code TintRenderExtension} 系统共存：
 * JSON tints 优先于 TintRegistry 应用。
 */
public interface ItemTint {

    /**
     * 计算 tint 颜色。
     *
     * @param stack 当前渲染的 ItemStack（可能为 null）
     * @param phase 当前渲染阶段
     * @return RGB 颜色值（0xRRGGBB），alpha 通道忽略
     */
    int compute(ItemStack stack, RenderPhase phase);
}
