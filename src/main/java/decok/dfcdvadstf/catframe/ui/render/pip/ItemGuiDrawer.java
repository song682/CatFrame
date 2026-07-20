package decok.dfcdvadstf.catframe.ui.render.pip;

import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * GUI 物品模型绘制回调 — 由 {@code GuiGraphicsExtractor} 提供实现，供 pip 包内的
 * {@link OversizedItemPipRenderer} 复用其物品渲染逻辑（矩阵恢复 + GL 设置 + 模型渲染 +
 * 附魔光效），避免 pip 包反向依赖 {@code GuiGraphicsExtractor}。
 */
public interface ItemGuiDrawer {

    /**
     * 绘制一个 GUI 物品模型。
     *
     * @param stack          物品栈
     * @param pose           采集时的 modelview 矩阵快照，可为 null
     * @param allowOversized {@code true} 时不做溢出检测/钳制（oversized_in_gui=true 物品走此路径）
     */
    void draw(ItemStack stack, @Nullable float[] pose, boolean allowOversized);
}
