package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * 物品染色颜色 tint（皮革盔甲等）。
 * <p>1.7.10 中从物品 NBT 的 {@code display.color} 读取。
 */
public class DyeTint implements ItemTint {
    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        if (stack == null) return 0xFFFFFF;
        if (!stack.hasTagCompound()) return 0xFFFFFF;
        net.minecraft.nbt.NBTTagCompound display = stack.getTagCompound().getCompoundTag("display");
        if (display == null || !display.hasKey("color")) return 0xFFFFFF;
        return display.getInteger("color") & 0xFFFFFF;
    }
}
