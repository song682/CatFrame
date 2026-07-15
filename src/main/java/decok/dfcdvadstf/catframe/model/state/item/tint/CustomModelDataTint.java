package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 从 NBT custom_model_data.colors[index] 读取 tint 颜色。
 * <p>JSON: {@code {"type": "minecraft:custom_model_data", "index": 0}}
 */
public class CustomModelDataTint implements ItemTint {

    private final int index;

    public CustomModelDataTint(int index) {
        this.index = index;
    }

    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        if (stack == null) return 0xFFFFFF;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("CustomModelData", 10)) return 0xFFFFFF;
        NBTTagCompound cmd = tag.getCompoundTag("CustomModelData");
        if (!cmd.hasKey("colors", 11)) return 0xFFFFFF;
        int[] colors = cmd.getIntArray("colors");
        if (index < 0 || index >= colors.length) return 0xFFFFFF;
        return colors[index] & 0xFFFFFF;
    }
}
