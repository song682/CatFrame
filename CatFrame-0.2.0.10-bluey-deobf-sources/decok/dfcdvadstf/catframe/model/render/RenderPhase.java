package decok.dfcdvadstf.catframe.model.render;

/**
 * 描述当前 quad 正在哪种渲染场景中被处理。
 * 扩展可以根据阶段决定是否生效（例如仅作用于方块世界渲染）。
 */
public enum RenderPhase {
    /**
     * 方块在世界中渲染（有 world/x/y/z）。
     */
    BLOCK_WORLD,
    /**
     * 方块在 GUI 中渲染（有 BlockAccess）。
     */
    BLOCK_GUI,
    /**
     * 物品在 GUI / 物品栏中渲染（有 ItemStack）。
     */
    ITEM_GUI,
    /**
     * 物品在玩家手中渲染（第一人称，有 ItemStack）。
     * 对应 JSON model 的 firstperson_righthand / firstperson_lefthand。
     */
    ITEM_HAND_FIRST_PERSON,
    /**
     * 物品在玩家手中渲染（第三人称，有 ItemStack）。
     * 对应 JSON model 的 thirdperson_righthand / thirdperson_lefthand。
     */
    ITEM_HAND_THIRD_PERSON,
    /**
     * @deprecated 使用 {@link #ITEM_HAND_FIRST_PERSON} 或 {@link #ITEM_HAND_THIRD_PERSON} 替代。
     */
    @Deprecated
    ITEM_HAND
}
