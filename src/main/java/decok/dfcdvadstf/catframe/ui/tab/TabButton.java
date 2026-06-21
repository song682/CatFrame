package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.AbstractButton;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * Tab 按钮组件 —— 一个托管到 TabBar 的独立按钮控件，
 * 使用九宫格拉伸渲染四种状态的纹理（normal / highlighted / selected / selected+highlighted）。
 * </p>
 * <p>
 * Tab button component — an independent button widget managed by TabBar,
 * renders four state textures (normal / highlighted / selected / selected+highlighted)
 * with nine-patch stretching.
 * </p>
 */
public class TabButton extends AbstractButton {

    // ──── Constants ────

    /** Nine-patch edge size in texture pixels / 九宫格边缘尺寸（纹理像素） */
    private static final int EDGE = 2;

    /** Default texture dimensions / 默认纹理尺寸 */
    private static final int TEX_W = 130;
    private static final int TEX_H = 24;

    // ──── State textures ────

    private static final ResourceLocation TAB_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/tabs/tab.png");
    private static final ResourceLocation TAB_HIGHLIGHTED_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/tabs/tab_highlighted.png");
    private static final ResourceLocation TAB_SELECTED_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/tabs/tab_selected.png");
    private static final ResourceLocation TAB_SELECTED_HIGHLIGHTED_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/tabs/tab_selected_highlighted.png");

    // ──── Text colours ────

    private static final int COLOR_SELECTED = 0xFFFFFF;
    private static final int COLOR_HOVERED = 0xFFFF55;
    private static final int COLOR_NORMAL  = 0xA0A0A0;

    // ──── Fields ────

    private final Tab tab;
    private boolean selected;
    private Runnable onPress = () -> {};

    // ──── Constructor ────

    public TabButton(Tab tab) {
        super(0, 0, 0, TabBar.NAV_HEIGHT);
        this.tab = tab;
    }

    // ──── State ────

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isSelected() {
        return selected;
    }

    public Tab getTab() {
        return tab;
    }

    // ──── Callback ────

    public void setOnPress(Runnable onPress) {
        this.onPress = onPress != null ? onPress : () -> {};
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    // ──── Rendering ────

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        updateHoverState(mouseX, mouseY);
        renderBackground(mouseX, mouseY, partialTicks);
        renderTitle();
    }

    @Override
    protected void renderBackground(int mouseX, int mouseY, float partialTicks) {
        if (width <= 0 || height <= 0) return;

        ResourceLocation tex = getStateTexture();

        // Render with nine-patch stretching
        TextureStretching.drawNinePatch(tex, x, y, width, height,
                EDGE, EDGE, EDGE, EDGE, TEX_W, TEX_H);
    }

    /**
     * Pick the correct texture for the current button state.
     * <p>根据当前按钮状态选择正确的纹理。</p>
     */
    private ResourceLocation getStateTexture() {
        if (!active) {
            // Inactive — use normal texture (or could add a disabled variant)
            return TAB_TEXTURE;
        }
        if (selected && isHovered) return TAB_SELECTED_HIGHLIGHTED_TEXTURE;
        if (selected) return TAB_SELECTED_TEXTURE;
        if (isHovered) return TAB_HIGHLIGHTED_TEXTURE;
        return TAB_TEXTURE;
    }

    /**
     * Draw the tab title text centered in the button.
     * <p>在按钮中央绘制 Tab 标题文本。</p>
     */
    private void renderTitle() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        Text title = tab.getTabTitle();
        String titleStr = title != null ? title.getString() : tab.getTabName();

        int textColor;
        if (selected) {
            textColor = COLOR_SELECTED;
        } else if (isHovered) {
            textColor = COLOR_HOVERED;
        } else {
            textColor = COLOR_NORMAL;
        }

        int textWidth = font.getStringWidth(titleStr);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(titleStr, textX, textY, textColor);
    }
}
