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
 * 公共"染色"便利 API：为 JSON face 中带 {@code "tintindex"} 的面提供颜色。
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * // 客户端 init：
 * TintRegistry.registerBlockTint(myFoliage,
 *     (world, x, y, z, b, idx) -> BiomeGenBase
 *         .getBiomeGenAt(world.getBiomeGenForCoords(x, z))
 *         .getBiomeFoliageColor(x, y, z));
 *
 * TintRegistry.registerItemTint(Item.getItemFromBlock(myFoliage),
 *     (stack, idx) -> 0x79C05A);
 * }</pre>
 *
 * <h3>默认回退（未注册时）</h3>
 * <ul>
 *   <li>方块：调用 {@link Block#colorMultiplier(IBlockAccess, int, int, int)}，
 *       原版草/树叶/水已经能给出正确生物群系颜色，无需额外注册。</li>
 *   <li>物品：若是 {@link ItemBlock}，回退到 {@link Block#getRenderColor(int)}，
 *       让方块物品形态自动获得默认草色等。</li>
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

    // ==================== 注册 API ====================

    /**
     * 注册实现 {@link IBlockTintProvider} 的实例，委托其 {@link IBlockTintProvider#register()} 完成具体注册。
     * 对齐 {@code ModelRenderRegistry.register(new XxxExtension())} 风格。
     */
    public static void register(IBlockTintProvider provider) {
        if (provider != null) provider.register();
    }

    /**
     * 注册实现 {@link IItemTintProvider} 的实例，委托其 {@link IItemTintProvider#register()} 完成具体注册。
     * 对齐 {@code ModelRenderRegistry.register(new XxxExtension())} 风格。
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

    // ==================== 查询 API（供 TintRenderExtension 调用） ====================

    /**
     * 方块染色查询。无匹配时回退到 {@link Block#colorMultiplier}。
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
     * 物品染色查询。无匹配时若是 ItemBlock，回退到 {@link Block#getRenderColor}。
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
