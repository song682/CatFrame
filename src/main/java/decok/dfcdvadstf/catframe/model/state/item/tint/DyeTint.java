package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.core.component.predicates.DyedItemColor;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

/**
 * Item dye tint (leather armor, etc.). Mirrors vanilla {@code ItemArmor#getColor}:
 * reads {@code display.color} from NBT for the dyeable layer, falling back to a default
 * color when undyed (vanilla leather default is {@code 10511680} = {@code 0xA06540}). Color
 * is read through CatFrame's {@link DyedItemColor} DataComponent (equivalent to the
 * high-version {@code dyed_color} component). {@code SERIALIZER} is used to read NBT fresh
 * each frame instead of the cached component map, avoiding stale colors after re-dyeing.
 *
 * <p>JSON: {@code {"type": "minecraft:dye", "default": 10511680}}
 */
public class DyeTint implements ItemTint {

    /** Vanilla default color for undyed leather armor ({@code 0xA06540}). */
    public static final int DEFAULT_LEATHER_COLOR = 0xA06540; // 10511680

    /** Default color used when no dye information is available (aligned with the {@code default} field of the wiki dye tint). */
    private final int defaultColor;

    public DyeTint() {
        this(DEFAULT_LEATHER_COLOR);
    }

    public DyeTint(int defaultColor) {
        this.defaultColor = defaultColor & 0xFFFFFF;
    }

    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        if (stack == null || !stack.hasTagCompound()) return defaultColor;
        // Read display.color via the DyedItemColor serializer; null (undyed) → default.
        DyedItemColor dyed = DyedItemColor.SERIALIZER.read(stack.getTagCompound());
        return dyed != null ? dyed.getRgb() & 0xFFFFFF : defaultColor;
    }
}
