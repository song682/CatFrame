package decok.dfcdvadstf.catframe.core.tooltip;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Item tooltip image provider registry — 1.7.10 static replacement for
 * 26.1.2 {@code Item#getTooltipImage(ItemStack)}.
 * <p>
 * 1.7.10 cannot add overridable methods to vanilla {@code Item}, so this registry
 * registers providers keyed by Item, queried by the item tooltip collection path;
 * items may register their own tooltip image components during initialization
 * (mirrors 26.1.2 {@code BundleItem} providing {@code BundleTooltip}).
 * </p>
 * <p>
 * Static registry replacing 26.1.2 {@code Item.getTooltipImage(ItemStack)},
 * which cannot be added to the vanilla {@code Item} class under 1.7.10.
 * </p>
 */
public final class ItemTooltipImages {

    /**
     * Image provider — returns tooltip image component for an ItemStack.
     * <p>Mirrors 26.1.2 {@code Item#getTooltipImage(ItemStack)} return value.</p>
     */
    @FunctionalInterface
    public interface Provider {
        Optional<TooltipComponent> get(ItemStack stack);
    }

    /** Item → Provider (registered at init, read at render). */
    private static final Map<Item, Provider> PROVIDERS = new IdentityHashMap<>();

    private ItemTooltipImages() {}

    /**
     * Register an item's tooltip image provider.
     *
     * @param item     target item
     * @param provider image provider
     */
    public static synchronized void register(final Item item, final Provider provider) {
        PROVIDERS.put(item, provider);
    }

    /**
     * Query ItemStack's tooltip image component.
     * <p>Mirrors 26.1.2 {@code ItemStack#getTooltipImage()}.</p>
     *
     * @param stack target item stack (may be null)
     * @return image component; empty if no provider registered or no image
     */
    public static Optional<TooltipComponent> get(final ItemStack stack) {
        if (stack == null) {
            return Optional.empty();
        }
        final Provider provider = PROVIDERS.get(stack.getItem());
        return provider != null ? provider.get(stack) : Optional.empty();
    }
}
