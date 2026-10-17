package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.extension.tint.IItemTintProvider;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import net.minecraft.item.ItemStack;

/**
 * Render bridge: delegates JSON-declared tints on an ItemState tree to the render-side
 * {@link decok.dfcdvadstf.catframe.model.render.extension.tint.TintRegistry} so the existing
 * {@code TintRenderExtension} applies them by each quad's {@code "tintindex"}. The array
 * position itself is the tintIndex (per-layer semantics, aligned with modern item model
 * definitions).
 */
public final class ItemStateTintBridge implements IItemTintProvider {

    private final ItemStateModel model;

    public ItemStateTintBridge(ItemStateModel model) {
        this.model = model;
    }

    @Override
    public int getTint(ItemStack stack, int tintIndex) {
        // Item tints are phase-independent (driven purely by ItemStack NBT/damage),
        // so a neutral render phase is used here.
        return model.resolveTint(stack, RenderPhase.ITEM_GUI, tintIndex);
    }
}
