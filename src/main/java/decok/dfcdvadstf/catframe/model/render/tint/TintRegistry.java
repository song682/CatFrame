package decok.dfcdvadstf.catframe.model.render.tint;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;

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
  private static final Map<Block, IBlockTintProvider> BLOCK_PROVIDERS = new HashMap<>();
  private static final Map<Item, IItemTintProvider> ITEM_PROVIDERS = new HashMap<>();

  private TintRegistry() {}

  // ==================== 注册 API ====================

  public static void registerBlockTint(Block block, IBlockTintProvider provider) {
    if (block == null || provider == null) return;
    BLOCK_PROVIDERS.put(block, provider);
  }

  public static void registerItemTint(Item item, IItemTintProvider provider) {
    if (item == null || provider == null) return;
    ITEM_PROVIDERS.put(item, provider);
  }

  public static void unregisterBlockTint(Block block) {
    if (block != null) BLOCK_PROVIDERS.remove(block);
  }

  public static void unregisterItemTint(Item item) {
    if (item != null) ITEM_PROVIDERS.remove(item);
  }

  // ==================== 查询 API（供 TintRenderExtension 调用） ====================

  /** 方块染色查询。无匹配时回退到 {@link Block#colorMultiplier}。 */
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

  /** 物品染色查询。无匹配时若是 ItemBlock，回退到 {@link Block#getRenderColor}。 */
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
