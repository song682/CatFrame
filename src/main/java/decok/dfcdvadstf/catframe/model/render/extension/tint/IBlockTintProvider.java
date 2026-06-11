package decok.dfcdvadstf.catframe.model.render.extension.tint;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

/**
 * 方块在世界中渲染时，根据 JSON face 上的 {@code "tintindex"} 决定颜色。
 * 实现可基于生物群系、坐标、方块自身属性等返回 0xRRGGBB。
 */
public interface IBlockTintProvider {
    /**
     * @param world     当前世界
     * @param x         方块世界 X
     * @param y         方块世界 Y
     * @param z         方块世界 Z
     * @param block     当前方块
     * @param tintIndex JSON face 中的 {@code "tintindex"} 值（≥0）
     * @return 0xRRGGBB 颜色乘数；返回 {@code 0xFFFFFF} 表示不染色
     */
    int getTint(IBlockAccess world, int x, int y, int z, Block block, int tintIndex);
}
