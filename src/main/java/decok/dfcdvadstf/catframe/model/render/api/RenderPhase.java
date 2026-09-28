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
     * 破坏贴图渲染（有 world/x/y/z，复用方块模型几何作贴花投影）。
     * 仅在 {@code RenderGlobal.drawBlockDamageTexture} 批次内出现。
     * Destroy overlay rendering: reuses the block's own model geometry as the
     * decal projection; only appears inside the vanilla destroy batch.
     */
    BLOCK_DESTROY,
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
     * 物品附着在实体头部槽渲染（有 ItemStack）。
     * <p>
     * 1.7.10 共有三处头部宿主以 EQUIPPED 类型进入物品渲染：玩家头盔槽
     * （{@code RenderPlayer}）、怪物头顶装备槽（{@code RenderBiped}）、
     * 雪傀儡南瓜（{@code RenderSnowMan}）。三者对应 26.1 的
     * {@code CustomHeadLayer.translateToHead}（雪傀儡为独立的
     * {@code SnowGolemHeadLayer}）变换链，display 取 JSON model 的 head 条目
     * （如 {@code generated.json} 的 {@code "head"} 定义）。
     * Item worn on an entity head slot (player helmet slot / mob head slot /
     * snow golem pumpkin); maps to the JSON model "head" display transform.
     */
    ITEM_HEAD;

    /**
     * 是否手持渲染阶段（第一人称 / 第三人称）。
     * <p>
     * 手持阶段不启用 GL_LIGHTING（避免与烘焙阴影双重着色，对标 1.7.10 物品路径的
     * {@code glDisable(GL_LIGHTING)} 语义），但亮度（lightmap）取持有者位置的世界光照
     * （本地玩家取玩家位置光；怪物 / 其他玩家取持有实体自身光照，见 {@code RenderPhasePolicy}
     * 私有 holderBrightness），完全无外部光照时物品渲染为全黑。
     * Whether this phase renders an item held in hand (first/third person);
     * hand phases skip GL_LIGHTING, but their brightness comes from the world
     * light at the holder's position (the local player, or the owning entity for
     * mobs / other players; black in fully dark areas).
     */
    public boolean isHandPhase() {
        return this == ITEM_HAND_FIRST_PERSON
                || this == ITEM_HAND_THIRD_PERSON;
    }

    /**
     * 是否附着渲染阶段（手持 / 头部槽）。
     * <p>
     * 附着阶段均不启用 GL_LIGHTING（避免与烘焙阴影双重着色，对标 1.7.10 物品路径的
     * {@code glDisable(GL_LIGHTING)} 语义），但亮度（lightmap）取附着实体位置的世界光照
     * （见 {@code RenderPhasePolicy} 私有 holderBrightness），完全无外部光照时物品渲染为全黑。
     * Whether this phase renders an item attached to an entity (held in a hand
     * or worn on the head slot); attached phases skip GL_LIGHTING, but their
     * brightness comes from the world light at the attached entity's position.
     */
    public boolean isAttachedItemPhase() {
        return isHandPhase() || this == ITEM_HEAD;
    }

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
                return "gui";
            case ITEM_HAND_FIRST_PERSON:
                return "firstperson_righthand";
            case ITEM_HAND_THIRD_PERSON:
                return "thirdperson_righthand";
            case DROPPED_ITEM_GROUND:
            case DROPPED_BLOCK_GROUND:
                return "ground";
            case ITEM_FIXED:
                return "fixed";
            case ITEM_HEAD:
                return "head";
            default:
                return null;
        }
    }
}
