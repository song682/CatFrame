package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * Biome grass color tint (grass, leaves, etc.).
 * <p>
 * In 1.7.10, the color is obtained from
 * {@code net.minecraft.world.biome.BiomeGenBase#getBiomeGrassColor}. This initial version
 * returns the default grass color {@code 0x7CBD6B}.
 */
public class GrassTint implements ItemTint {

    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        // TODO: Query the biome grass color based on the player's position
        return 0x7CBD6B;
    }
}
