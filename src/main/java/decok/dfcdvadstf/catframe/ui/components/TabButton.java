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
 * 页签按钮组件 —— 四态九宫格精灵（选中 / 未选中 × 普通 / 高亮）+ 选中态装饰
 * （菜单背景填充 + 焦点下划线）+ 居中标题。<br>
 * 对标 26.1.2 {@code net.minecraft.client.gui.components.TabButton}。
 * </p>
 * <p>
 * Tab button — four-state nine-patch sprites (selected / normal × plain /
 * highlighted) plus selected-state decoration (menu-background fill and focus
 * underline) and a centred title. Counterpart of the high-version
 * {@code TabButton}.
 * </p>
 * <p>
 * 选中判定为引用相等（{@code tabManager.getCurrentTab() == tab}）；切换动作由
 * {@code TabNavigationBar} 的焦点钩子驱动，故本组件自身不处理点击。
 * </p>
 */
public class TabButton extends AbstractComponent.WithInactiveMessage {

    /**
     * 四态精灵 —— 与高版本 {@code widget/tab_selected | tab |
     * tab_selected_highlighted | tab_highlighted} 一一对应。
     */
    private static final WidgetSprites SPRITES = new WidgetSprites(
            new ResourceLocation("catframe", "textures/gui/tabs/tab_selected.png"),
            new ResourceLocation("catframe", "textures/gui/tabs/tab.png"),
            new ResourceLocation("catframe", "textures/gui/tabs/tab_selected_highlighted.png"),
            new ResourceLocation("catframe", "textures/gui/tabs/tab_highlighted.png"));

    /** mcmeta 缺失时的九宫格回退参数（nine_patch / 130x24 / edge 2） */
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
     * 绘制页签 —— 四态精灵、选中态装饰与标题。可见性与悬停状态已由
     * {@link #extractRenderState} 在调用前处理。
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
     * 选中页签的内部填充（{@code x+2, y+2, x+w-2, y+h}）—— 盖住分隔线，对应高版本
     * {@code Screen.MENU_BACKGROUND}。
     */
    private void renderMenuBackground() {
        ContentPanelRenderer.drawPanelBackground(this.getX() + 2, this.getY() + 2,
                this.getWidth() - 4, this.getHeight() - 2);
    }

    /**
     * 选中页签的下划线 —— 宽 = min(文本宽, 按钮宽 - 4)，水平居中，高 1px，
     * 贴按钮底边上方 2px。
     */
    private void renderFocusUnderline(int color) {
        int width = Math.min(FontHelper.width(this.getMessage()), this.getWidth() - UNDERLINE_MARGIN_X);
        int left = this.getX() + (this.getWidth() - width) / 2;
        int top = this.getY() + this.getHeight() - UNDERLINE_MARGIN_BOTTOM;
        GuiDrawing.drawRect(left, top, left + width, top + UNDERLINE_HEIGHT, color);
    }

    /**
     * 居中标题 —— 水平带 {@code [x+1, x+w-1]}，垂直带 {@code [top, y+h]}
     * （未选中时顶部下移 {@link #SELECTED_OFFSET}px）。
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
     * @return the tab this button represents / 此按钮代表的标签页
     */
    public Tab tab() {
        return this.tab;
    }

    /**
     * @return whether this button's tab is the current tab (reference equality)
     *         / 此按钮的标签页是否为当前页（引用相等）
     */
    public boolean isSelected() {
        return this.tabManager.getCurrentTab() == this.tab;
    }
}
