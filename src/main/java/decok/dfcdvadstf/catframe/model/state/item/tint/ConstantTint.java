package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * 固定 RGB 颜色 tint。
 * <p>JSON: {@code {"type": "minecraft:constant", "value": 0xFF0000}}
 */
public class ConstantTint implements ItemTint {

    private final int color;

    public ConstantTint(int color) {
        this.color = color & 0xFFFFFF;
    }

    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        return color;
    }
}
