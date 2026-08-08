package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionHelper;

/**
 * 药水颜色 tint。
 * <p>1.7.10 中通过 {@code PotionHelper} 根据药水 damage 值计算液体颜色，
 * 对应模型中标记 {@code "tintindex"} 的液体层面（layer0，对齐原版：
 * 染色液体 overlay 在底层先画，玻璃瓶 layer1 不染色叠在上层）。
 */
public class PotionTint implements ItemTint {
    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        if (stack == null) return 0xFFFFFF;
        // 按药水 damage 值经 PotionHelper 计算液体颜色（对齐原版 ItemPotion 着色）。
        // Compute the potion liquid color from the damage value via PotionHelper
        // (matches vanilla ItemPotion coloring).
        return PotionHelper.func_77915_a(stack.getItemDamage(), false) & 0xFFFFFF;
    }
}
