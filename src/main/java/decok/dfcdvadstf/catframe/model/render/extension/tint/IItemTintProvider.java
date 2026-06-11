package decok.dfcdvadstf.catframe.model.render.extension.tint;

import net.minecraft.item.ItemStack;

/**
 * 物品（GUI / 手持）渲染时根据 JSON face 上的 {@code "tintindex"} 决定颜色。
 * 由于参数是 {@link ItemStack}，实现可读取 NBT、损耗、附魔等做精细化染色
 * （例如染色护甲、不同药水）。
 */
public interface IItemTintProvider {
    /**
     * @param stack     正在渲染的物品堆
     * @param tintIndex JSON face 中的 {@code "tintindex"} 值（≥0）
     * @return 0xRRGGBB 颜色乘数；返回 {@code 0xFFFFFF} 表示不染色
     */
    int getTint(ItemStack stack, int tintIndex);
}
