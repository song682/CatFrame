package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * Map color tint.
 * <p>In 1.7.10, map items render different colors based on world data. This initial
 * placeholder returns white.
 */
public class MapColorTint implements ItemTint {
    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        // TODO: Read color from map data
        return 0xFFFFFF;
    }
}
