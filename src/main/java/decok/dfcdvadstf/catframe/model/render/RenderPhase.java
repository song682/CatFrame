package decok.dfcdvadstf.catframe.model.render;

import javax.annotation.Nullable;

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
     * 落地物品渲染（有 ItemStack）。
     */
    DROPPED_ITEM_GROUND,
    /**
     * 落地方块渲染（有 BlockAccess）。
     */
    DROPPED_BLOCK_GROUND,
    /**
     * 物品在展示框（Item Frame）中渲染（有 ItemStack）。
     * 对应 JSON model 的 fixed。
     */
    ITEM_FIXED,
    /**
     * @deprecated 使用 {@link #ITEM_HAND_FIRST_PERSON} 或 {@link #ITEM_HAND_THIRD_PERSON} 替代。
     */
    @Deprecated
    ITEM_HAND;

    /**
     * 将此渲染阶段映射到 JSON model 的 display 键名。
     * <p>
     * [S2] 当前 1.7.10 无副手系统，因此仅映射 righthand 变体。
     *
     * @return display 键名（如 "gui", "firstperson_righthand"），若无对应返回 null
     */
    @Nullable
    public String getDisplayKey() {
        switch (this) {
            case ITEM_GUI:
            case BLOCK_GUI:
                return "gui";
            case ITEM_HAND:
            case ITEM_HAND_FIRST_PERSON:
                return "firstperson_righthand";
            case ITEM_HAND_THIRD_PERSON:
                return "thirdperson_righthand";
            case DROPPED_ITEM_GROUND:
            case DROPPED_BLOCK_GROUND:
                return "ground";
            case ITEM_FIXED:
                return "fixed";
            default:
                return null;
        }
    }
}
