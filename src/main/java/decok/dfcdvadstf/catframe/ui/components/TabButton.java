package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.ContentPanelRenderer;
import decok.dfcdvadstf.catframe.ui.GuiDrawing;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.tab.Tab;
import decok.dfcdvadstf.catframe.ui.components.tab.TabManager;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * Tab button — four-state nine-patch sprites (selected / normal × plain /
 * highlighted) plus selected-state decoration (menu-background fill and focus
 * underline) and a centred title. Counterpart of the high-version
 * {@code TabButton}.
 * </p>
 * <p>
 * Selection uses reference equality ({@code tabManager.getCurrentTab() == tab});
 * the switch action is driven by {@code TabNavigationBar}'s focus hooks, so this
 * widget handles no clicks itself.
 * </p>
 */
public class TabButton extends AbstractComponent.WithInactiveMessage {

    /**
     * Four-state sprites — correspond one-to-one with the high-version
     * {@code widget/tab_selected | tab |
     * tab_selected_highlighted | tab_highlighted}.
     */
    private static final WidgetSprites SPRITES = new WidgetSprites(
            new ResourceLocation("catframe", "textures/gui/sprites/tabs/tab_selected.png"),
            new ResourceLocation("catframe", "textures/gui/sprites/tabs/tab.png"),
            new ResourceLocation("catframe", "textures/gui/sprites/tabs/tab_selected_highlighted.png"),
            new ResourceLocation("catframe", "textures/gui/sprites/tabs/tab_highlighted.png"));

    /** Nine-patch fallback parameters when the mcmeta is missing (nine_patch / 130x24 / edge 2) */
    private static final int TEX_DEFAULT_W = 130;
    private static final int TEX_DEFAULT_H = 24;
    private static final int TEX_DEFAULT_EDGE = 2;

    private static final int SELECTED_OFFSET = 3;
    private static final int TEXT_MARGIN = 1;
    private static final int UNDERLINE_HEIGHT = 1;
    private static final int UNDERLINE_MARGIN_X = 4;
    private static final int UNDERLINE_MARGIN_BOTTOM = 2;

    private final TabManager tabManager;
    private final Tab tab;

    public TabButton(TabManager tabManager, Tab tab, int width, int height) {
        super(0, 0, width, height, tab.getTabTitle());
        this.tabManager = tabManager;
        this.tab = tab;
    }

    /**
     * Draws the tab — four-state sprites, selected-state decoration and title.
     * Visibility and hover state are already handled by {@link #extractRenderState}.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        TextureStretching.drawAutoNinePatch(
                SPRITES.get(this.isSelected(), this.isHovered() || this.isFocused()),
                this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                TEX_DEFAULT_W, TEX_DEFAULT_H, TEX_DEFAULT_EDGE);

        int underlineColor = this.active ? -1 : -6250336;
        if (this.isSelected()) {
            this.renderMenuBackground();
            this.renderFocusUnderline(underlineColor);
        }

        this.renderLabel();
    }

    /**
     * Inner fill of the selected tab ({@code x+2, y+2, x+w-2, y+h}) — covers the
     * separator, mirroring the high-version {@code Screen.MENU_BACKGROUND}.
     */
    private void renderMenuBackground() {
        ContentPanelRenderer.drawPanelBackground(this.getX() + 2, this.getY() + 2,
                this.getWidth() - 4, this.getHeight() - 2);
    }

    /**
     * Underline of the selected tab — width = min(text width, button width - 4),
     * horizontally centred, 1px tall, sitting 2px above the button's bottom edge.
     */
    private void renderFocusUnderline(int color) {
        int width = Math.min(FontHelper.width(this.getMessage()), this.getWidth() - UNDERLINE_MARGIN_X);
        int left = this.getX() + (this.getWidth() - width) / 2;
        int top = this.getY() + this.getHeight() - UNDERLINE_MARGIN_BOTTOM;
        GuiDrawing.drawRect(left, top, left + width, top + UNDERLINE_HEIGHT, color);
    }

    /**
     * Centred title — horizontal band {@code [x+1, x+w-1]}, vertical band
     * {@code [top, y+h]} (top shifted down {@link #SELECTED_OFFSET}px when not
     * selected).
     */
    private void renderLabel() {
        Text message = this.getMessage();
        int left = this.getX() + TEXT_MARGIN;
        int right = this.getX() + this.getWidth() - TEXT_MARGIN;
        int top = this.getY() + (this.isSelected() ? 0 : SELECTED_OFFSET);
        int bottom = this.getY() + this.getHeight();
        int textX = left + (right - left - FontHelper.width(message)) / 2;
        int textY = top + (bottom - top - Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT) / 2;
        FontHelper.draw(message, textX, textY);
    }

    /**
     * @return the tab this button represents
     */
    public Tab tab() {
        return this.tab;
    }

    /**
     * @return whether this button's tab is the current tab (reference equality)
     */
    public boolean isSelected() {
        return this.tabManager.getCurrentTab() == this.tab;
    }
}
