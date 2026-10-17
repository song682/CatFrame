package decok.dfcdvadstf.catframe.model.render.api;

import javax.annotation.Nullable;

/**
 * Describes which rendering scenario the current quad is being processed in.
 * Extensions can decide whether to take effect based on the phase
 * (e.g. only for block world rendering).
 */
public enum RenderPhase {
    /**
     * Block rendered in the world (has world/x/y/z).
     */
    BLOCK_WORLD,
    /**
     * Destroy overlay rendering: reuses the block's own model geometry as the
     * decal projection; only appears inside the vanilla destroy batch.
     */
    BLOCK_DESTROY,
    /**
     * Item rendered in the GUI / inventory (has an ItemStack).
     */
    ITEM_GUI,
    /**
     * Item rendered in the player's hand (first person, has an ItemStack).
     * Maps to the JSON model's firstperson_righthand / firstperson_lefthand.
     */
    ITEM_HAND_FIRST_PERSON,
    /**
     * Item rendered in the player's hand (third person, has an ItemStack).
     * Maps to the JSON model's thirdperson_righthand / thirdperson_lefthand.
     */
    ITEM_HAND_THIRD_PERSON,
    /**
     * Dropped item rendered on the ground (has an ItemStack).
     */
    DROPPED_ITEM_GROUND,
    /**
     * Dropped block rendered on the ground (has a BlockAccess).
     */
    DROPPED_BLOCK_GROUND,
    /**
     * Item rendered in an item frame (has an ItemStack).
     * Maps to the JSON model's fixed.
     */
    ITEM_FIXED,
    /**
     * Item worn on an entity head slot (player helmet slot / mob head slot /
     * snow golem pumpkin); maps to the JSON model "head" display transform.
     */
    ITEM_HEAD;

    /**
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
     * Whether this phase renders an item attached to an entity (held in a hand
     * or worn on the head slot); attached phases skip GL_LIGHTING, but their
     * brightness comes from the world light at the attached entity's position.
     */
    public boolean isAttachedItemPhase() {
        return isHandPhase() || this == ITEM_HEAD;
    }

    /**
     * Maps this render phase to the JSON model display key.
     * <p>
     * [S2] 1.7.10 currently has no offhand system, so only the righthand
     * variants are mapped.
     *
     * @return display key (e.g. "gui", "firstperson_righthand"), or null if there is no match
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
