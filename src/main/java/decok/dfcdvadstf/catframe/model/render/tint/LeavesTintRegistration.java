package decok.dfcdvadstf.catframe.model.render.tint;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * 树叶方块染色注册。通过 {@link TintRegistry} 为所有树叶方块注册生物群系着色和固定色。
 *
 * <h3>颜色规则</h3>
 * <table>
 *   <tr><th>树叶类型</th><th>Meta</th><th>世界颜色</th><th>物品颜色</th></tr>
 *   <tr><td>橡木 (Oak)</td>      <td>0</td><td>生物群系 foliage 色</td><td>无染色</td></tr>
 *   <tr><td>云杉 (Spruce)</td>   <td>1</td><td>0x619961 固定</td><td>无染色</td></tr>
 *   <tr><td>白桦 (Birch)</td>    <td>2</td><td>0x80A755 固定</td><td>无染色</td></tr>
 *   <tr><td>丛林 (Jungle)</td>   <td>3</td><td>生物群系 foliage 色</td><td>无染色</td></tr>
 *   <tr><td>金合欢 (Acacia)</td> <td>0</td><td>0x80A755 固定</td><td>无染色</td></tr>
 *   <tr><td>深色橡木 (Dark Oak)</td><td>1</td><td>0x597C3B 固定</td><td>无染色</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public final class LeavesTintRegistration {

  private static final int LEAF_SPRUCE  = 0x619961;
  private static final int LEAF_BIRCH   = 0x80A755;
  private static final int LEAF_ACACIA  = 0x80A755;
  private static final int LEAF_DARK_OAK = 0x597C3B;

  private static boolean registered = false;

  private LeavesTintRegistration() {}

  /**
   * 注册所有树叶染色。应在客户端初始化时调用（如 FMLInitializationEvent）。
   */
  public static void register() {
    if (registered) return;
    registered = true;

    registerLeaves1();
    registerLeaves2();

    CatFrame.logger.info("LeavesTintRegistration: registered all leaf tints");
  }

  // ==================== Blocks.leaves (oak, spruce, birch, jungle) ====================

  private static void registerLeaves1() {
    if (Blocks.leaves == null) return;

    // ——— 方块染色 ———
    TintRegistry.registerBlockTint(Blocks.leaves, (world, x, y, z, block, tintIndex) -> {
      int meta = world.getBlockMetadata(x, y, z) & 3;
      return getBlockColor(meta, world, x, y, z);
    });

    // ——— 物品染色 ———
    Item item = Item.getItemFromBlock(Blocks.leaves);
    if (item != null) {
      TintRegistry.registerItemTint(item, (stack, tintIndex) -> 0xFFFFFF);
    }
  }

  // ==================== Blocks.leaves2 (acacia, dark_oak) — 可能不存在 ====================

  private static void registerLeaves2() {
    Block leaves2 = getLeaves2Block();
    if (leaves2 == null) return;

    TintRegistry.registerBlockTint(leaves2, (world, x, y, z, block, tintIndex) -> {
      int meta = world.getBlockMetadata(x, y, z) & 3;
      return getBlockColor(meta, world, x, y, z);
    });

    Item item = Item.getItemFromBlock(leaves2);
    if (item != null) {
      TintRegistry.registerItemTint(item, (stack, tintIndex) -> 0xFFFFFF);
    }
  }

  // ==================== 颜色查询 ====================

  /**
   * 根据 metadata 和世界位置获取树叶方块颜色。
   *
   * @param meta   方块 metadata（已 masking & 3）
   * @param world  世界引用（可为 null）
   * @param x,y,z  方块坐标
   * @return 0xRRGGBB 颜色
   */
  private static int getBlockColor(int meta, IBlockAccess world, int x, int y, int z) {
    switch (meta) {
      case 0: // Oak — 生物群系 foliage 色
        if (world != null) {
          BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
          if (biome != null) return biome.getBiomeFoliageColor(x, y, z);
        }
        return 0x48B518; // 默认 foliage 绿

      case 1: // Spruce — 固定深绿
        return LEAF_SPRUCE;

      case 2: // Birch — 固定浅绿
        return LEAF_BIRCH;

      case 3: // Jungle — 生物群系 foliage 色
        if (world != null) {
          BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
          if (biome != null) return biome.getBiomeFoliageColor(x, y, z);
        }
        return 0x48B518;

      default:
        return 0xFFFFFF;
    }
  }

  /**
   * 安全获取 leaves2 方块（1.7.10 可能不存在，返回 null）。
   */
  public static Block getLeaves2Block() {
    try {
      return (Block) Block.blockRegistry.getObject("leaves2");
    } catch (Exception e) {
      return null;
    }
  }
}
