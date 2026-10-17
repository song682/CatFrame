package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionHelper;

/**
 * Potion color tint.
 * <p>In 1.7.10, {@code PotionHelper} computes the liquid color from the potion damage value,
 * corresponding to the liquid layer marked with {@code "tintindex"} in the model (layer0;
 * aligned with vanilla: the tinted liquid overlay is drawn first, while the glass bottle
 * layer1 is drawn on top and is not tinted).
 */
public class PotionTint implements ItemTint {
    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        if (stack == null) return 0xFFFFFF;
        // Compute the potion liquid color from the damage value via PotionHelper
        // (matches vanilla ItemPotion coloring).
        return PotionHelper.func_77915_a(stack.getItemDamage(), false) & 0xFFFFFF;
    }
}
