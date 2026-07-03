package decok.dfcdvadstf.catframe.model.render.extension.tint;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

/**
 * 方块在世界中渲染时，根据 JSON face 上的 {@code "tintindex"} 决定颜色。
 * 实现可基于生物群系、坐标、方块自身属性等返回 0xRRGGBB。
 *
 * <p>实现类应覆写 {@link #register()} 以将自身注册到 {@link TintRegistry}，
 * 替代旧的静态 {@code XxxProvider.register()} 模式。</p>
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

    /**
     * 将当前实例注册到 {@link TintRegistry}。实现类应在此方法中调用
     * {@link TintRegistry#registerBlockTint(Block, IBlockTintProvider)} 等
     * 完成自身注册。
     */
    default void register() {
        // 子类覆写以实现注册逻辑
    }
}
