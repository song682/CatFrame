package decok.dfcdvadstf.catframe.model.state.item;

/**
 * ItemState JSON root-level parse result.
 * <p>
 * Encapsulates the decision tree root node and the wiki-spec root-level JSON fields:
 * <ul>
 *   <li>{@code model} — decision tree root node (required)</li>
 *   <li>{@code hand_animation_on_swap} — whether to play the hand animation when swapping items (default true)</li>
 *   <li>{@code oversized_in_gui} — whether to render oversized in the GUI (default false)</li>
 *   <li>{@code swap_animation_scale} — swap animation scale factor (default 1.0)</li>
 * </ul>
 */
public class ItemStateRoot {

    /** Decision tree root node */
    public final ItemStateNode model;

    /** Whether to play the hand animation when swapping items. Default true. */
    public final boolean handAnimationOnSwap;

    /** Whether to render oversized in the GUI. Default false. */
    public final boolean oversizedInGui;

    /** Swap animation scale factor. Default 1.0. */
    public final float swapAnimationScale;

    public ItemStateRoot(ItemStateNode model, boolean handAnimationOnSwap,
                          boolean oversizedInGui, float swapAnimationScale) {
        this.model = model;
        this.handAnimationOnSwap = handAnimationOnSwap;
        this.oversizedInGui = oversizedInGui;
        this.swapAnimationScale = swapAnimationScale;
    }

    /**
     * Convenience constructor: specifies only the decision tree root node, using defaults for
     * the remaining fields.
     */
    public ItemStateRoot(ItemStateNode model) {
        this(model, true, false, 1.0f);
    }
}
