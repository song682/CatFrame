package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * 药水颜色 tint。
 * <p>1.7.10 中通过 {@code PotionHelper} 根据药水 damage 值计算颜色。
 * 初版占位返回白色，待后续完善。
 */
public class PotionTint implements ItemTint {
    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        // TODO: 1.7.10 PotionHelper 药水颜色计算
        return 0xFFFFFF;
    }
}
