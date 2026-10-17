package decok.dfcdvadstf.catframe.model.render.extension.special;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-item full self-draw provider registry (symmetric to {@code TintRegistry}).
 * <p>
 * Copy-on-write provider table: writes are rare (mod init) and synchronized; reads
 * happen on the render path and see a stable immutable snapshot from any thread,
 * without locking.
 * <p>
 * Besides registering the provider, {@link #registerItem(Item, IItemRenderSpecialProvider)}
 * also routes that item to {@link RenderJsonItemModel}
 * ({@code MinecraftForgeClient.registerItemRenderer}); otherwise a pure self-draw item
 * with no general model registered would not enter the CatFrame item rendering entry point.
 */
@SideOnly(Side.CLIENT)
public final class SpecialRenderRegistry {

    /**
     * Copy-on-write provider table. Writes are rare (mod init) and synchronized;
     * reads happen on the render path from arbitrary threads and see a stable
     * immutable map snapshot without locking.
     */
    private static volatile Map<Item, IItemRenderSpecialProvider> ITEM_PROVIDERS = Collections.emptyMap();

    private SpecialRenderRegistry() {
    }

    // ==================== Registration API ====================

    /**
     * Registers a per-item full self-draw provider and ensures the item is routed to the
     * CatFrame item rendering entry point.
     *
     * @param item     the target item
     * @param provider the self-draw provider
     */
    public static synchronized void registerItem(Item item, IItemRenderSpecialProvider provider) {
        if (item == null || provider == null) return;
        Map<Item, IItemRenderSpecialProvider> copy = new HashMap<>(ITEM_PROVIDERS);
        copy.put(item, provider);
        ITEM_PROVIDERS = copy;
        // A pure self-draw item may not be in registeredItemModels, so explicitly take over the Forge render route
        MinecraftForgeClient.registerItemRenderer(item, RenderJsonItemModel.INSTANCE);
    }

    /**
     * Unregisters a provider (does not reclaim the Forge render route; that is managed by
     * the general registration process).
     *
     * @param item the target item
     */
    public static synchronized void unregisterItem(Item item) {
        if (item == null || !ITEM_PROVIDERS.containsKey(item)) return;
        Map<Item, IItemRenderSpecialProvider> copy = new HashMap<>(ITEM_PROVIDERS);
        copy.remove(item);
        ITEM_PROVIDERS = copy;
    }

    // ==================== Provider lookup ====================

    /** The current item's self-draw provider, or {@code null} when none is registered. */
    public static IItemRenderSpecialProvider getProvider(Item item) {
        return item == null ? null : ITEM_PROVIDERS.get(item);
    }
}
