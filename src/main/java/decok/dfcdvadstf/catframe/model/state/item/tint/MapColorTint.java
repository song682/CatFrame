package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * 地图颜色 tint。
 * <p>1.7.10 中地图物品根据世界数据渲染不同颜色。初版占位返回白色。
 */
public class MapColorTint implements ItemTint {
    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        // TODO: 从地图数据读取颜色
        return 0xFFFFFF;
    }
}
