package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * Average color tint of fireworks stars.
 * <p>In 1.7.10, the color array is read from the fireworks star NBT and averaged. This
 * initial placeholder returns white.
 */
public class FireworkTint implements ItemTint {
    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        // TODO: Read colors from Fireworks/Explosion NBT and average them
        return 0xFFFFFF;
    }
}
