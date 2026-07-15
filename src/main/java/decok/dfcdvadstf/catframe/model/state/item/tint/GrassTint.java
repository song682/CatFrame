package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * 生物群系植物颜色 tint（草、树叶等）。
 * <p>
 * 1.7.10 中通过 {@code net.minecraft.world.biome.BiomeGenBase#getBiomeGrassColor}
 * 获取。初版返回默认草色 {@code 0x7CBD6B}。
 */
public class GrassTint implements ItemTint {

    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        // TODO: 根据玩家位置查询生物群系草色
        return 0x7CBD6B;
    }
}
