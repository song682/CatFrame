package decok.dfcdvadstf.catframe.model.render.extension.tint;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.IBlockAccess;

/**
 * 红石线方块染色：复刻 1.7.10 {@code RenderBlocks#renderBlockRedstoneWire} 的
 * {@code setColorOpaque_F(f1, f2, f3)} 颜色计算，使信号强度（power）变化时
 * 红石线从深红（power=0，f1=0.3）渐变到亮红（power=15，f1=1.0）。
 * <p>
 * 1.7.10 的红石线纹理（redstone_dust_cross/line）本身是白色灰度，
 * 颜色完全由顶点色提供；模型 face 上的 {@code "tintindex": 0} 经本 provider
 * 注入该顶点色，实现与原版一致的亮度渐变。
 */
@SideOnly(Side.CLIENT)
public final class RedstoneWireTintProvider implements IBlockTintProvider {

    @Override
    public int getTint(IBlockAccess world, int x, int y, int z, Block block, int tintIndex) {
        if (world == null || block != Blocks.redstone_wire) {
            // 无世界上下文：按 power=0 的深红兜底
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
