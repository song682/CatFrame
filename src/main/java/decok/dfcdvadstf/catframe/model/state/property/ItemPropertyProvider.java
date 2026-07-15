package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * 物品属性提供者函数式接口。
 * <p>
 * 每个提供者根据 {@link ItemStack} 和 {@link RenderPhase} 计算一个属性值。
 * 在 {@link ItemPropertyRegistry} 中注册后，由 {@link ItemProperties#buildProperties}
 * 的惰性 Map 在决策树实际访问时才调用。
 */
@FunctionalInterface
public interface ItemPropertyProvider {

    /**
     * 计算属性值。
     *
     * @param stack 当前渲染的 ItemStack（可能为 null）
     * @param phase 当前渲染阶段
     * @return 属性值，不为 null
     */
    Comparable<?> compute(ItemStack stack, RenderPhase phase);
}
