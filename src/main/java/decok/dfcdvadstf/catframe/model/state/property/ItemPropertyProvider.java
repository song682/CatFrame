package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * Item property provider functional interface.
 * <p>
 * Each provider computes a property value from an {@link ItemStack} and a {@link RenderPhase}.
 * After being registered in {@link ItemPropertyRegistry}, it is invoked by the lazy Map of
 * {@link ItemProperties#buildProperties} only when the decision tree actually accesses it.
 */
@FunctionalInterface
public interface ItemPropertyProvider {

    /**
     * Computes the property value.
     *
     * @param stack the ItemStack currently being rendered (may be null)
     * @param phase the current render phase
     * @return the property value, never null
     */
    Comparable<?> compute(ItemStack stack, RenderPhase phase);
}
