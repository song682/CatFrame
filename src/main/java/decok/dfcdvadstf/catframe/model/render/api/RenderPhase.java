package decok.dfcdvadstf.catframe.model.render.api;

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
     * <p>
     * 
     * @deprecated 由于 Item Model 的加入，方块在 GUI 中渲染的场景越来越少。<br>
     *             基本上切换到了物品渲染，此阶段已很少使用，仅保留向后兼容。<br>
     *             使用 {@link #ITEM_GUI} 替代。
     * </p>
     *  方块在 GUI 中渲染（有 BlockAccess）。
     */
    @Deprecated
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
     * 方块破坏贴花阶段（破坏动画覆盖层），仅由破坏渲染路径使用。
     * <p>
     * 运行于原版 {@code RenderGlobal.drawBlockDamageTexture} 的破坏批次 GL 上下文内
     * （乘法混合 + polygon offset + blocks atlas 已由原版设置），本阶段只写顶点：
     * <ul>
     *   <li>内建扩展按 phase 门控天然失效：FaceCull（无剔除 → 全量 quads）、
     *       AOCompute（无 AO）、Tint（无染色）、DisplayTransform（无 display 变换）；</li>
     *   <li>{@link decok.dfcdvadstf.catframe.model.render.extension.BlockDestroyExtension}
     *       仅在本阶段生效：覆盖破坏图标（iconOverride）、全亮、白色顶点。</li>
     * </ul>
     */
    BLOCK_DESTROY,
    /**
     * @deprecated 使用 {@link #ITEM_HAND_FIRST_PERSON} 或
     *             {@link #ITEM_HAND_THIRD_PERSON} 替代。
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
