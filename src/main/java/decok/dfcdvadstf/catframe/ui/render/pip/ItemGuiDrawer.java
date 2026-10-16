package decok.dfcdvadstf.catframe.ui.render.pip;

import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * GUI item model drawing callback — implemented by {@code GuiGraphicsExtractor} so that
 * {@link OversizedItemPipRenderer} inside the pip package can reuse its item rendering
 * logic (matrix restore + GL settings + model rendering + enchantment glint),
 * avoiding a reverse dependency from the pip package on {@code GuiGraphicsExtractor}.
 */
public interface ItemGuiDrawer {

    /**
     * Draws a GUI item model.
     *
     * @param stack          the item stack
     * @param pose           the modelview matrix snapshot captured at extraction time; may be null
     * @param allowOversized {@code true} to skip overflow detection/clamping (items with oversized_in_gui=true take this path)
     */
    void draw(ItemStack stack, @Nullable float[] pose, boolean allowOversized);
}
