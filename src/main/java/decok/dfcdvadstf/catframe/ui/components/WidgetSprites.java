package decok.dfcdvadstf.catframe.ui.components;

import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * Widget state sprites — counterpart of the high-version Minecraft
 * {@code WidgetSprites}. Holds enabled / disabled / highlighted /
 * highlighted-disabled textures and picks one by the widget's
 * {@code active} and {@code hoveredOrFocused} states.
 * </p>
 */
public class WidgetSprites {

    private final ResourceLocation enabled;
    private final ResourceLocation disabled;
    private final ResourceLocation highlighted;
    private final ResourceLocation highlightedDisabled;

    /**
     * <p>
     * All states share a single texture.
     * </p>
     */
    public WidgetSprites(ResourceLocation sprite) {
        this(sprite, sprite, sprite, sprite);
    }

    /**
     * <p>
     * Three-state textures: highlighted-disabled falls back to highlighted.
     * </p>
     */
    public WidgetSprites(ResourceLocation enabled, ResourceLocation disabled, ResourceLocation highlighted) {
        this(enabled, disabled, highlighted, highlighted);
    }

    /**
     * <p>
     * Full four-state textures.
     * </p>
     */
    public WidgetSprites(ResourceLocation enabled, ResourceLocation disabled,
            ResourceLocation highlighted, ResourceLocation highlightedDisabled) {
        this.enabled = enabled;
        this.disabled = disabled;
        this.highlighted = highlighted;
        this.highlightedDisabled = highlightedDisabled;
    }

    /**
     * <p>
     * Selects the texture by widget state — counterpart of the modern Minecraft
     * {@code WidgetSprites#get(boolean, boolean)}.
     * </p>
     */
    public ResourceLocation get(boolean active, boolean hoveredOrFocused) {
        if (active) {
            return hoveredOrFocused ? this.highlighted : this.enabled;
        } else {
            return hoveredOrFocused ? this.highlightedDisabled : this.disabled;
        }
    }
}
