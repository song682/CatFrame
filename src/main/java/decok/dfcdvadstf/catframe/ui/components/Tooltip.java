package decok.dfcdvadstf.catframe.ui.components;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.world.tooltip.TooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Tooltip data holder — provides tooltip content caching and component support for the widget layer.
 * <p>
 * Counterpart of the modern Minecraft {@code net.minecraft.client.gui.components.Tooltip}.
 */
@SideOnly(Side.CLIENT)
public class Tooltip {

    private static final int MAX_WIDTH = 170;

    /** Raw tooltip text (supports § formatting) */
    private final String message;

    /** Optional tooltip image/structure component */
    private final Optional<TooltipComponent> component;

    /** Optional tooltip style identifier (counterpart of the modern Minecraft {@code Tooltip.style()}) */
    @Nullable
    private final ResourceLocation style;

    /** Cache: text lines split for the current language */
    @Nullable
    private List<String> cachedTooltip;

    /** Cache: language code used for the last split */
    @Nullable
    private String cachedLanguage;

    private Tooltip(String message, Optional<TooltipComponent> component, @Nullable ResourceLocation style) {
        this.message = message;
        this.component = component;
        this.style = style;
    }

    // ========== Factory methods ==========

    public static Tooltip create(String message) {
        return new Tooltip(message, Optional.empty(), null);
    }

    public static Tooltip create(String message, Optional<TooltipComponent> component, @Nullable ResourceLocation style) {
        return new Tooltip(message, component, style);
    }

    // ========== Accessors ==========

    public Optional<TooltipComponent> getComponent() {
        return component;
    }

    @Nullable
    public ResourceLocation getStyle() {
        return style;
    }

    // ========== Tooltip text ==========

    /**
     * Gets the split tooltip lines (cached).
     */
    public List<String> getLines(Minecraft mc) {
        String currentLang = mc.getLanguageManager().getCurrentLanguage().getLanguageCode();
        if (cachedTooltip == null || !currentLang.equals(cachedLanguage)) {
            cachedTooltip = splitTooltip(mc, message);
            cachedLanguage = currentLang;
        }
        return cachedTooltip;
    }

    /**
     * Splits text into multiple lines (each no wider than MAX_WIDTH pixels).
     */
    @SuppressWarnings("unchecked")
    public static List<String> splitTooltip(Minecraft mc, String message) {
        return mc.fontRenderer.listFormattedStringToWidth(message, MAX_WIDTH);
    }
}
