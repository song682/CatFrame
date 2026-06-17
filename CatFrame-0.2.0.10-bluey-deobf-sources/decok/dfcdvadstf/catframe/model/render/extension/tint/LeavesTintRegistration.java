package decok.dfcdvadstf.catframe.model.render.extension.tint;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.IBlockAccess;

/**
 * 树叶方块染色注册。通过 {@link TintRegistry} 为所有树叶方块注册生物群系着色和固定色。
 *
 * <p>完全对齐原版 {@link net.minecraft.block.BlockLeaves} 行为：
 * <ul>
 *   <li>橡木/丛林木：3x3 区域平均生物群系 foliage 色；</li>
 *   <li>云杉：{@link ColorizerFoliage#getFoliageColorPine()}；</li>
 *   <li>白桦：{@link ColorizerFoliage#getFoliageColorBirch()}；</li>
 *   <li>物品形态：{@link ColorizerFoliage#getFoliageColorBasic()}。</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class LeavesTintRegistration {

    private static boolean registered = false;

    private LeavesTintRegistration() {
    }

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
            return getBlockColor(block, meta, world, x, y, z);
        });

        // ——— 物品染色 ———
        Item item = Item.getItemFromBlock(Blocks.leaves);
        if (item != null) {
            TintRegistry.registerItemTint(item, (stack, tintIndex) -> ColorizerFoliage.getFoliageColorBasic());
        }
    }

    // ==================== Blocks.leaves2 (acacia, dark_oak) — 可能不存在 ====================

    private static void registerLeaves2() {
        Block leaves2 = getLeaves2Block();
        if (leaves2 == null) return;

        TintRegistry.registerBlockTint(leaves2, (world, x, y, z, block, tintIndex) -> {
            int meta = world.getBlockMetadata(x, y, z) & 3;
            return getBlockColor(block, meta, world, x, y, z);
        });

        Item item = Item.getItemFromBlock(leaves2);
        if (item != null) {
            TintRegistry.registerItemTint(item, (stack, tintIndex) -> ColorizerFoliage.getFoliageColorBasic());
        }
    }

    // ==================== 颜色查询（对齐原版 BlockLeaves） ====================

    /**
     * 根据方块类型、metadata 和世界位置获取树叶颜色。
     *
     * @param block 方块实例（用于区分 leaves vs leaves2）
     * @param meta  方块 metadata（已 masking & 3）
     * @param world 世界引用（可为 null，null 时返回默认 foliage 色）
     * @param x,y,z 方块坐标
     * @return 0xRRGGBB 颜色
     */
    private static int getBlockColor(Block block, int meta, IBlockAccess world, int x, int y, int z) {
        // 优先判断方块类型
        Block leaves2 = getLeaves2Block();
        if (leaves2 != null && block == leaves2) {
            return getLeaves2Color(meta);
        }
        // Blocks.leaves
        switch (meta) {
            case 0:
            case 3:
                if (world != null) {
                    return averageFoliageColor(world, x, y, z);
                }
                return ColorizerFoliage.getFoliageColorBasic();

            case 1:
                return ColorizerFoliage.getFoliageColorPine();

            case 2:
                return ColorizerFoliage.getFoliageColorBirch();

            default:
                return ColorizerFoliage.getFoliageColorBasic();
        }
    }

    /**
     * leaves2 的固定色（金合欢 == 白桦色，深色橡木 == 深绿）。
     */
    private static int getLeaves2Color(int meta) {
        switch (meta) {
            case 0:
                return ColorizerFoliage.getFoliageColorBirch();
            case 1:
                return 0x597C3B;
            default:
                return ColorizerFoliage.getFoliageColorBasic();
        }
    }

    /**
     * 原版 3x3 区域平均生物群系 foliage 色。
     * 直接复制自 {@link net.minecraft.block.BlockLeaves#colorMultiplier}。
     */
    private static int averageFoliageColor(IBlockAccess world, int x, int y, int z) {
        int r = 0, g = 0, b = 0;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                int color = world.getBiomeGenForCoords(x + dx, z + dz)
                        .getBiomeFoliageColor(x + dx, y, z + dz);
                r += (color >> 16) & 0xFF;
                g += (color >> 8) & 0xFF;
                b += color & 0xFF;
            }
        }
        return ((r / 9) & 0xFF) << 16 | ((g / 9) & 0xFF) << 8 | (b / 9) & 0xFF;
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
