package decok.dfcdvadstf.catframe.model.render.extension.tint;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Public "tinting" convenience API: supplies colours for JSON faces carrying a
 * {@code "tintindex"}.
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 * // client init:
 * TintRegistry.registerBlockTint(myFoliage,
 *     (world, x, y, z, b, idx) -> BiomeGenBase
 *         .getBiomeGenAt(world.getBiomeGenForCoords(x, z))
 *         .getBiomeFoliageColor(x, y, z));
 *
 * TintRegistry.registerItemTint(Item.getItemFromBlock(myFoliage),
 *     (stack, idx) -> 0x79C05A);
 * }</pre>
 *
 * <h3>Default fallbacks (when not registered)</h3>
 * <ul>
 *   <li>Block: calls {@link Block#colorMultiplier(IBlockAccess, int, int, int)}; vanilla
 *       grass/leaves/water already yield the correct biome colours, so no extra
 *       registration is needed.</li>
 *   <li>Item: if it is an {@link ItemBlock}, falls back to {@link Block#getRenderColor(int)},
 *       so the block item form automatically gets the default grass colour etc.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class TintRegistry {
    /**
     * Copy-on-write provider tables. Writes are rare (mod init) and synchronized;
     * reads happen on the render path from arbitrary threads and see a stable
     * immutable map snapshot without locking.
     */
    private static volatile Map<Block, IBlockTintProvider> BLOCK_PROVIDERS = Collections.emptyMap();
    private static volatile Map<Item, IItemTintProvider> ITEM_PROVIDERS = Collections.emptyMap();

    private TintRegistry() {
    }

    // ==================== Registration API ====================

    /**
     * Registers an instance implementing {@link IBlockTintProvider}, delegating the actual
     * registration to its {@link IBlockTintProvider#register()}. Mirrors the
     * {@code ModelRenderRegistry.register(new XxxExtension())} style.
     */
    public static void register(IBlockTintProvider provider) {
        if (provider != null) provider.register();
    }

    /**
     * Registers an instance implementing {@link IItemTintProvider}, delegating the actual
     * registration to its {@link IItemTintProvider#register()}. Mirrors the
     * {@code ModelRenderRegistry.register(new XxxExtension())} style.
     */
    public static void register(IItemTintProvider provider) {
        if (provider != null) provider.register();
    }

    public static synchronized void registerBlockTint(Block block, IBlockTintProvider provider) {
        if (block == null || provider == null) return;
        Map<Block, IBlockTintProvider> copy = new HashMap<>(BLOCK_PROVIDERS);
        copy.put(block, provider);
        BLOCK_PROVIDERS = copy;
    }

    public static synchronized void registerItemTint(Item item, IItemTintProvider provider) {
        if (item == null || provider == null) return;
        Map<Item, IItemTintProvider> copy = new HashMap<>(ITEM_PROVIDERS);
        copy.put(item, provider);
        ITEM_PROVIDERS = copy;
    }

    public static synchronized void unregisterBlockTint(Block block) {
        if (block == null || !BLOCK_PROVIDERS.containsKey(block)) return;
        Map<Block, IBlockTintProvider> copy = new HashMap<>(BLOCK_PROVIDERS);
        copy.remove(block);
        BLOCK_PROVIDERS = copy;
    }

    public static synchronized void unregisterItemTint(Item item) {
        if (item == null || !ITEM_PROVIDERS.containsKey(item)) return;
        Map<Item, IItemTintProvider> copy = new HashMap<>(ITEM_PROVIDERS);
        copy.remove(item);
        ITEM_PROVIDERS = copy;
    }

    // ==================== Provider lookup ====================

    /** Current block provider, or {@code null} when none is registered. */
    public static IBlockTintProvider getBlockProvider(Block block) {
        return block == null ? null : BLOCK_PROVIDERS.get(block);
    }

    /** Current item provider, or {@code null} when none is registered. */
    public static IItemTintProvider getItemProvider(Item item) {
        return item == null ? null : ITEM_PROVIDERS.get(item);
    }

    // ==================== Query API (called by TintRenderExtension) ====================

    /**
     * Block tint query. Falls back to {@link Block#colorMultiplier} when there is no match.
     */
    public static int getBlockTint(IBlockAccess world, int x, int y, int z,
                                   Block block, int tintIndex) {
        IBlockTintProvider p = BLOCK_PROVIDERS.get(block);
        if (p != null) {
            return p.getTint(world, x, y, z, block, tintIndex) & 0xFFFFFF;
        }
        if (block != null && world != null) {
            return block.colorMultiplier(world, x, y, z) & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }

    /**
     * Item tint query. Falls back to {@link Block#getRenderColor} when there is no
     * match and the item is an ItemBlock.
     */
    public static int getItemTint(ItemStack stack, int tintIndex) {
        if (stack == null) return 0xFFFFFF;
        Item item = stack.getItem();
        IItemTintProvider p = ITEM_PROVIDERS.get(item);
        if (p != null) {
            return p.getTint(stack, tintIndex) & 0xFFFFFF;
        }
        if (item instanceof ItemBlock) {
            Block b = ((ItemBlock) item).field_150939_a;
            if (b != null) {
                return b.getRenderColor(stack.getItemDamage()) & 0xFFFFFF;
            }
        }
        return 0xFFFFFF;
    }
}
