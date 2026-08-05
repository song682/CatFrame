package decok.dfcdvadstf.catframe.ui.components;

import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * 部件状态精灵集合 —— 对标高版本 Minecraft 的 {@code WidgetSprites}。<br>
 * 持有启用 / 禁用 / 高亮 / 高亮禁用四张纹理，按组件的
 * {@code active} 与 {@code hoveredOrFocused} 状态选择对应纹理。
 * </p>
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
     * 所有状态共用同一纹理。
     * <p>
     * All states share a single texture.
     * </p>
     */
    public WidgetSprites(ResourceLocation sprite) {
        this(sprite, sprite, sprite, sprite);
    }

    /**
     * 三态纹理：高亮禁用时回退到高亮纹理。
     * <p>
     * Three-state textures: highlighted-disabled falls back to highlighted.
     * </p>
     */
    public WidgetSprites(ResourceLocation enabled, ResourceLocation disabled, ResourceLocation highlighted) {
        this(enabled, disabled, highlighted, highlighted);
    }

    /**
     * 完整四态纹理。
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
     * 按组件状态选择纹理 —— 对标高版本 {@code WidgetSprites#get(boolean, boolean)}。
     * <p>
     * Selects the texture by widget state.
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
