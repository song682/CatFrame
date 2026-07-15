package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * 烟火之星颜色平均 tint。
 * <p>1.7.10 中从烟火之星 NBT 读取颜色数组并平均。初版占位返回白色。
 */
public class FireworkTint implements ItemTint {
    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        // TODO: 从 Fireworks/Explosion NBT 读取颜色并平均
        return 0xFFFFFF;
    }
}
