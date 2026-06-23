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

    // ──── Instance-level texture fields (default to static constants) ────

    private ResourceLocation texNormal       = TAB_TEXTURE;
    private ResourceLocation texHighlighted  = TAB_HIGHLIGHTED_TEXTURE;
    private ResourceLocation texSelected     = TAB_SELECTED_TEXTURE;
    private ResourceLocation texSelHighlight = TAB_SELECTED_HIGHLIGHTED_TEXTURE;

    // ──── Text colours (instance-level, customizable) ────

    /** Text colour when selected / 选中时文本颜色 */
    private int colorSelected = 0xFFFFFF;
    /** Text colour when hovered / 悬停时文本颜色 */
    private int colorHovered = 0xFFFF55;
    /** Text colour in normal state / 普通状态文本颜色 */
    private int colorNormal   = 0xA0A0A0;

    // ──── Fields ────

    private final Tab tab;
    private boolean selected;
    private Runnable onPress = () -> {};

    // ──── Constructor ────

    public TabButton(Tab tab) {
        super(0, 0, 0, TabBar.NAV_HEIGHT);
        if (tab == null) throw new IllegalArgumentException("tab must not be null");
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

    // ──── Colour customization ────

    /**
     * Set the text colour for the selected state.
     * <p>设置选中状态文本颜色。</p>
     */
    public void setColorSelected(int color) { this.colorSelected = color; }

    /**
     * Set the text colour for the hovered state.
     * <p>设置悬停状态文本颜色。</p>
     */
    public void setColorHovered(int color) { this.colorHovered = color; }

    /**
     * Set the text colour for the normal state.
     * <p>设置普通状态文本颜色。</p>
     */
    public void setColorNormal(int color) { this.colorNormal = color; }

    // ──── Texture customization ────

    /**
     * Set custom state textures for this button.
     * <p>为此按钮设置自定义状态纹理。传入 null 的项保留当前值。</p>
     */
    public void setStateTexture(ResourceLocation normal, ResourceLocation highlighted,
                                ResourceLocation selected, ResourceLocation selectedHighlighted) {
        if (normal != null)              this.texNormal       = normal;
        if (highlighted != null)         this.texHighlighted  = highlighted;
        if (selected != null)            this.texSelected     = selected;
        if (selectedHighlighted != null) this.texSelHighlight = selectedHighlighted;
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
        // Priority 1: Four-state textures from Tab interface (highest priority)
        Tab.TabTextures textures = tab.getTabTextures();
        if (textures != null) {
            if (!active) return textures.normal;
            if (selected && isHovered) return textures.selectedHighlighted;
            if (selected) return textures.selected;
            if (isHovered) return textures.highlighted;
            return textures.normal;
        }
        // Priority 2: Single custom texture from Tab interface
        ResourceLocation custom = tab.getTabTexture();
        if (custom != Tab.DEFAULT_TAB_TEXTURE) {
            return custom;
        }
        // Priority 3: Instance-level four-state textures (from setStateTexture or TabBar push)
        if (!active) return texNormal;
        if (selected && isHovered) return texSelHighlight;
        if (selected) return texSelected;
        if (isHovered) return texHighlighted;
        return texNormal;
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
            textColor = colorSelected;
        } else if (isHovered) {
            textColor = colorHovered;
        } else {
            textColor = colorNormal;
        }

        int textWidth = font.getStringWidth(titleStr);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(titleStr, textX, textY, textColor);
    }
}
