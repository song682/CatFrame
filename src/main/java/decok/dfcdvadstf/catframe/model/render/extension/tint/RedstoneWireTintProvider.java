package decok.dfcdvadstf.catframe.model.render.extension.tint;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.IBlockAccess;

/**
 * Redstone wire block tinting: replicates the {@code setColorOpaque_F(f1, f2, f3)}
 * colour computation of 1.7.10 {@code RenderBlocks#renderBlockRedstoneWire}, so that
 * as the signal strength (power) changes, the redstone wire fades from dark red
 * (power=0, f1=0.3) to bright red (power=15, f1=1.0).
 * <p>
 * The 1.7.10 redstone wire textures (redstone_dust_cross/line) are white greyscale
 * themselves, and the colour is provided entirely by the vertex colour; the
 * {@code "tintindex": 0} on the model face injects that vertex colour through this
 * provider, reproducing the same brightness gradient as vanilla.
 */
@SideOnly(Side.CLIENT)
public final class RedstoneWireTintProvider implements IBlockTintProvider {

    @Override
    public int getTint(IBlockAccess world, int x, int y, int z, Block block, int tintIndex) {
        if (world == null || block != Blocks.redstone_wire) {
            // No world context: fall back to the power=0 dark red
            return 0x4D0000;
        }
        int meta = world.getBlockMetadata(x, y, z);
        float f = (float) meta / 15.0F;
        float f1 = f * 0.6F + 0.4F;
        if (meta == 0) {
            f1 = 0.3F;
        }
        float f2 = Math.max(0.0F, f * f * 0.7F - 0.5F);
        float f3 = Math.max(0.0F, f * f * 0.6F - 0.7F);
        return ((int) (f1 * 255.0F) << 16) | ((int) (f2 * 255.0F) << 8) | (int) (f3 * 255.0F);
    }

    @Override
    public void register() {
        TintRegistry.registerBlockTint(Blocks.redstone_wire, this);
    }
}
