package decok.dfcdvadstf.catframe.model.state.item;

/**
 * ItemState JSON 根级解析结果。
 * <p>
 * 封装决策树根节点及 wiki 规范定义的根级 JSON 字段：
 * <ul>
 *   <li>{@code model} — 决策树根节点（必填）</li>
 *   <li>{@code hand_animation_on_swap} — 切换物品时是否播放手持动画（默认 true）</li>
 *   <li>{@code oversized_in_gui} — GUI 中是否超大渲染（默认 false）</li>
 *   <li>{@code swap_animation_scale} — 切换动画缩放系数（默认 1.0）</li>
 * </ul>
 */
public class ItemStateRoot {

    /** 决策树根节点 */
    public final ItemStateNode model;

    /** 切换物品时是否播放手持动画。默认 true。 */
    public final boolean handAnimationOnSwap;

    /** GUI 中是否超大渲染。默认 false。 */
    public final boolean oversizedInGui;

    /** 切换动画缩放系数。默认 1.0。 */
    public final float swapAnimationScale;

    public ItemStateRoot(ItemStateNode model, boolean handAnimationOnSwap,
                          boolean oversizedInGui, float swapAnimationScale) {
        this.model = model;
        this.handAnimationOnSwap = handAnimationOnSwap;
        this.oversizedInGui = oversizedInGui;
        this.swapAnimationScale = swapAnimationScale;
    }

    /**
     * 便捷构造：仅指定决策树根节点，其余字段使用默认值。
     */
    public ItemStateRoot(ItemStateNode model) {
        this(model, true, false, 1.0f);
    }
}
