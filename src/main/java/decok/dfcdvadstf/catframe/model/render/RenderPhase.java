package decok.dfcdvadstf.catframe.model.render;

/**
 * 描述当前 quad 正在哪种渲染场景中被处理。
 * 扩展可以根据阶段决定是否生效（例如仅作用于方块世界渲染）。
 */
public enum RenderPhase {
  /** 方块在世界中渲染（有 world/x/y/z）。 */
  BLOCK_WORLD,
  /** 物品在 GUI / 物品栏中渲染（有 ItemStack）。 */
  ITEM_GUI,
  /** 物品在玩家手中（第一/第三人称）渲染（有 ItemStack）。 */
  ITEM_HAND
}
