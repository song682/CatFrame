package decok.dfcdvadstf.catframe.ui.screens.inventory.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.components.FontHelper;
import net.minecraft.client.gui.FontRenderer;

/**
 * Text tooltip component — renders a single line of text.
 * <p>
 * Mirrors 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip}.
 * Drawn using the UI package's {@link FontHelper}, consistent with the UI package style system.
 */
@SideOnly(Side.CLIENT)
public class ClientTextTooltip implements ClientTooltipComponent {

    private final String text;

    public ClientTextTooltip(String text) {
        this.text = text;
    }

    @Override
    public int getWidth(FontRenderer font) {
        return font.getStringWidth(text);
    }

    @Override
    public int getHeight(FontRenderer font) {
        return 10;
    }

    @Override
    public void renderText(FontRenderer font, int x, int y) {
        FontHelper.draw(text, x, y, null);
    }
}
