package decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.world.tooltip.TooltipComponent;
import net.minecraft.client.gui.FontRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Client tooltip component interface — renders the text or image lines of a tooltip.
 * <p>
 * Counterpart of 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent}.
 *
 * @see ClientTextTooltip
 */
@SideOnly(Side.CLIENT)
public interface ClientTooltipComponent {

    /**
     * Creates a text component from a formatted string.
     */
    static ClientTooltipComponent create(String text) {
        return new ClientTextTooltip(text);
    }

    /**
     * Creates a client render component from a structured component.
     * <p>
     * Mirrors 26.1.2 {@code ClientTooltipComponent.create(TooltipComponent)};
     * 1.7.10 has no sealed type hierarchy, so a runtime type dispatch table is
     * used instead (same strategy as the PiP dispatch table), with render
     * factories registered via {@link #register}.
     *
     * @param component the structured tooltip component
     * @return the corresponding client render component
     * @throws IllegalArgumentException no render factory registered for that component type
     */
    static ClientTooltipComponent create(TooltipComponent component) {
        final Function<TooltipComponent, ClientTooltipComponent> factory = Dispatch.FACTORIES.get(component.getClass());
        if (factory == null) {
            throw new IllegalArgumentException("Unknown TooltipComponent: " + component.getClass().getName());
        }
        return factory.apply(component);
    }

    /**
     * Registers the render factory for a structured component type (1.7.10 extension mechanism).
     *
     * @param type    the component's runtime type
     * @param factory factory creating a client render component from components of that type
     */
    static void register(final Class<? extends TooltipComponent> type,
                         final Function<TooltipComponent, ClientTooltipComponent> factory) {
        Dispatch.FACTORIES.put(type, factory);
    }

    /**
     * Gets the text width (used to compute the tooltip's overall width).
     */
    int getWidth(FontRenderer font);

    /**
     * Gets this row's height (usually 10).
     */
    int getHeight(FontRenderer font);

    /**
     * Whether to still show the tooltip while an item is held in hand.
     */
    default boolean showTooltipWithItemInHand() {
        return false;
    }

    /**
     * Renders the text.
     */
    default void renderText(FontRenderer font, int x, int y) {
    }

    /**
     * Renders the image component.
     */
    default void renderImage(FontRenderer font, int x, int y, int w, int h) {
    }

    /**
     * Dispatch table holder — interface fields are implicitly public static final,
     * so the mutable state lives in the nested class.
     */
    final class Dispatch {

        private Dispatch() {}

        /** TooltipComponent runtime type → client render component factory. */
        static final Map<Class<? extends TooltipComponent>, Function<TooltipComponent, ClientTooltipComponent>> FACTORIES =
                new HashMap<>();
    }
}
