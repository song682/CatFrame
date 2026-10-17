package decok.dfcdvadstf.catframe.model.render.extension.tint;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;

/**
 * Decides the colour when a block is rendered in the world, based on the
 * {@code "tintindex"} on the JSON face. Implementations may return 0xRRGGBB based
 * on biome, coordinates, the block's own properties, etc.
 *
 * <p>Implementations should override {@link #register()} to register themselves with
 * {@link TintRegistry}, replacing the old static {@code XxxProvider.register()}
 * pattern.</p>
 */
public interface IBlockTintProvider {
    /**
     * @param world     the current world
     * @param x         block world X
     * @param y         block world Y
     * @param z         block world Z
     * @param block     the current block
     * @param tintIndex the {@code "tintindex"} value in the JSON face (≥ 0)
     * @return 0xRRGGBB colour multiplier; {@code 0xFFFFFF} means no tint
     */
    int getTint(IBlockAccess world, int x, int y, int z, Block block, int tintIndex);

    /**
     * Registers the current instance with {@link TintRegistry}. Implementations should
     * call {@link TintRegistry#registerBlockTint(Block, IBlockTintProvider)} etc. in this
     * method to perform their own registration.
     */
    default void register() {
        // Subclasses override this to implement their registration logic
    }
}
