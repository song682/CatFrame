package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * <p>
 * 开关按钮组件 —— 继承 {@link AbstractButton} 的布尔切换控件。<br>
 * 使用 CatFrame 四态开关纹理（未开启/开启 × 普通/高亮），左侧显示标签文本，
 * 右侧显示开关；点击在开/关之间切换并回调。
 * </p>
 * <p>
 * Toggle button component — a boolean toggle widget extending
 * {@link AbstractButton}.<br>
 * Renders the CatFrame four-state toggle textures (off/on × normal/highlighted)
 * with the label on the left and the switch on the right; clicking toggles
 * between on/off and fires the callback.
 * </p>
 */
public class ToggleButton extends AbstractButton {

    private static final ResourceLocation TOGGLE_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/toggle_swticher.png");
    private static final ResourceLocation TOGGLE_HIGHLIGHTED_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/toggle_swticher_highlighted.png");
    private static final ResourceLocation TOGGLE_TOGGLED_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/toggle_swticher_toggled.png");
    private static final ResourceLocation TOGGLE_TOGGLED_HIGHLIGHTED_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/toggle_swticher_toggled_highlighted.png");

    /**
     * Rendered switch size in screen pixels (2x upscale of the 16x8 source) /
     * 开关渲染尺寸（屏幕像素，16x8 源图 2 倍放大）
     */
    private static final int TOGGLE_W = 32;
    private static final int TOGGLE_H = 16;
    /** Source texture size in pixels / 源纹理尺寸（像素） */
    private static final int TOGGLE_TEX_W = 16;
    private static final int TOGGLE_TEX_H = 8;
    /** Margin between the label and the switch / 标签与开关之间的边距 */
    private static final int LABEL_MARGIN = 8;

    private boolean selected;
    @Nullable
    private final OnToggle onToggle;

    /**
     * Creates a toggle button with the given label and initial state.
     * <p>
     * 使用给定标签和初始状态创建开关按钮。
     * </p>
     *
     * @param x        left X / 左 X
     * @param y        top Y / 上 Y
     * @param width    widget width / 组件宽度
     * @param height   widget height / 组件高度
     * @param message  label text / 标签文本
     * @param selected initial toggle state / 初始开关状态
     * @param onToggle state-change callback, or {@code null} / 状态变更回调，可为 null
     */
    public ToggleButton(int x, int y, int width, int height, Text message,
            boolean selected, @Nullable OnToggle onToggle) {
        super(x, y, width, height, message);
        this.selected = selected;
        this.onToggle = onToggle;
    }

    @Override
    public void onPress() {
        this.selected = !this.selected;
        if (onToggle != null) {
            onToggle.onToggle(this, this.selected);
        }
    }

    /**
     * Returns whether the toggle is currently on.
     * <p>
     * 返回开关当前是否开启。
     * </p>
     */
    public boolean selected() {
        return this.selected;
    }

    /**
     * Sets the toggle state programmatically (without firing the callback).
     * <p>
     * 以编程方式设置开关状态（不触发回调）。
     * </p>
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        // Label — left-aligned, vertically centred
        // 标签 — 左对齐，垂直居中
        String label = getMessage() != null ? getMessage().getString() : "";
        int textY = y + (height - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(label, x + LABEL_MARGIN, textY,
                active ? TEXT_COLOR_ENABLED : TEXT_COLOR_DISABLED);

        // Switch — right-aligned, vertically centred
        // 开关 — 右对齐，垂直居中
        ResourceLocation tex;
        if (this.selected) {
            tex = (isHovered || focused) ? TOGGLE_TOGGLED_HIGHLIGHTED_TEXTURE : TOGGLE_TOGGLED_TEXTURE;
        } else {
            tex = (isHovered || focused) ? TOGGLE_HIGHLIGHTED_TEXTURE : TOGGLE_TEXTURE;
        }
        int switchX = x + width - LABEL_MARGIN - TOGGLE_W;
        int switchY = y + (height - TOGGLE_H) / 2;

        // 16x8 独立纹理按整数倍（2 倍）放大绘制。
        // 16x8 standalone texture upscaled by an integer factor (2x).
        TextureStretching.drawStatic(tex, switchX, switchY, TOGGLE_W, TOGGLE_H, TOGGLE_TEX_W, TOGGLE_TEX_H, this.alpha);
    }

    /**
     * Callback invoked when the toggle state changes.
     * <p>
     * 开关状态变化时调用的回调。
     * </p>
     */
    @FunctionalInterface
    public interface OnToggle {
        void onToggle(ToggleButton button, boolean selected);
    }
}
