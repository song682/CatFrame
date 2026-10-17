package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * Item tint color provider interface.
 * <p>
 * Each implementation computes an RGB color value based on {@link ItemStack} and
 * {@link RenderPhase}. In JSON item model mappings, tints are applied to the ModelLeaf as a
 * high-priority overlay.
 * <p>
 * Works alongside the existing {@code TintRegistry}/{@code TintRenderExtension} system:
 * JSON tints take precedence over TintRegistry.
 */
public interface ItemTint {

    /**
     * Computes the tint color.
     *
     * @param stack the ItemStack being rendered (may be null)
     * @param phase the current render phase
     * @return RGB color value (0xRRGGBB); alpha channel is ignored
     */
    int compute(ItemStack stack, RenderPhase phase);
}
