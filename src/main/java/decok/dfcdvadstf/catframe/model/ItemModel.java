package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * 物品模型接口。类似于 1.21.5 的 ItemModel，
 * 定义物品在不同渲染上下文中的渲染行为。
 * <p>通过 {@link VanillaModelManager.ModelRegistration#registerItemModel} 注册。
 * 渲染时由 {@link decok.dfcdvadstf.catframe.model.render.RenderJsonItemRenderer}（Forge IItemRenderer）根据
 * {@link #handles(RenderPhase)} 决定是否接管渲染。
 */
public interface ItemModel {

    /**
     * 渲染物品。
     *
     * @param stack 物品栈（含 NBT、damage 等信息）
     * @param phase 渲染上下文（GUI / 手持）
     */
    void render(ItemStack stack, RenderPhase phase);

    /**
     * 查询 ItemModel 是否处理指定的渲染阶段。
     * <p>
     * {@link decok.dfcdvadstf.catframe.model.render.RenderJsonItemRenderer} 遵循以下策略：
     * <ol>
     *   <li>先调用 {@link #handles(RenderPhase)} 检查 ItemModel 是否接管此阶段</li>
     *   <li>如果返回 {@code true} → 调用 {@link #render(ItemStack, RenderPhase)} 并取消原版渲染</li>
     *   <li>如果返回 {@code false} → 不取消原版渲染，走 vanilla 路径</li>
     * </ol>
     * <p>
     * 默认实现中，除 {@link RenderPhase#ITEM_HAND_FIRST_PERSON} 外的所有阶段返回 {@code true}。
     * 第一人称手持默认走原版渲染路径，避免破坏弓、刷怪蛋等原版物品的第一人称显示。
     * 需要自定义第一人称手持 3D 的物品应重写此方法：
     * <pre>{@code
     * public boolean handles(RenderPhase phase) {
     *     return phase != RenderPhase.ITEM_GUI;
     * }
     * }</pre>
     *
     * @param phase 渲染阶段
     * @return true 表示 ItemModel 接管此阶段，false 表示走原版路径
     */
    default boolean handles(RenderPhase phase) {
        return phase != RenderPhase.ITEM_HAND_FIRST_PERSON;
    }
}
