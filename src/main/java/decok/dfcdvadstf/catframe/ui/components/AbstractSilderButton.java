package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/**
 * <p>
 * 滑动按钮抽象基类 —— 对标 26.1.2 {@code AbstractSliderButton}。<br>
 * 继承 {@link AbstractComponent.WithInactiveMessage}，由子类实现
 * {@link #updateMessage()} 与 {@link #applyValue()} 两个抽象钩子，
 * 值域归一化为 {@code [0.0, 1.0]}。
 * </p>
 * <p>
 * Abstract slider button — counterpart of the 26.1.2
 * {@code AbstractSliderButton}. Subclasses implement the
 * {@link #updateMessage()} and {@link #applyValue()} hooks; the normalized
 * value ranges over {@code [0.0, 1.0]}.
 * </p>
 * <p>
 * 1.7.10 适配说明 / Porting notes:
 * <ul>
 * <li>vanilla 的 narration（语音播报）与 {@code CursorTypes} 光标请求在 1.7.10
 * 无对应设施，未移植。</li>
 * <li>vanilla 依据 {@code InputType} 判定聚焦来源（鼠标 / Tab），1.7.10 无该
 * 机制，改为：任何用户驱动的聚焦均视为可改值（见
 * {@link #setFocused(boolean)}），Enter 仍可切换。</li>
 * <li>点击音效在释放时播放，对标 vanilla {@code onRelease} 行为。</li>
 * </ul>
 * </p>
 * <p>
 * Porting notes:
 * <ul>
 * <li>Vanilla narration and {@code CursorTypes} cursor requests have no
 * 1.7.10 counterpart and are not ported.</li>
 * <li>Vanilla decides the focus source (mouse / Tab) via {@code InputType};
 * 1.7.10 lacks that mechanism, so any user-driven focus is treated as
 * value-changeable (see {@link #setFocused(boolean)}), and Enter still
 * toggles.</li>
 * <li>The click sound is played on release, mirroring vanilla
 * {@code onRelease}.</li>
 * </ul>
 * </p>
 */
public abstract class AbstractSilderButton extends AbstractComponent.WithInactiveMessage {

    private static final ResourceLocation SLIDER_SPRITE = new ResourceLocation("catframe", "textures/gui/sprites/widgets/silder.png");
    private static final ResourceLocation HIGHLIGHTED_SLIDER_SPRITE = new ResourceLocation("catframe", "textures/gui/sprites/widgets/silder_highlighted.png");
    private static final ResourceLocation SLIDER_HANDLE_SPRITE = new ResourceLocation("catframe", "textures/gui/sprites/widgets/silder_handle.png");
    private static final ResourceLocation HIGHLIGHTED_SLIDER_HANDLE_SPRITE = new ResourceLocation("catframe", "textures/gui/sprites/widgets/silder_handle_highlighted.png");
    protected static final int TEXT_MARGIN = 2;
    public static final int DEFAULT_HEIGHT = 20;
    protected static final int HANDLE_WIDTH = 8;
    private static final int HANDLE_HALF_WIDTH = 4;

    /** mcmeta fallback sizes / 图集元数据缺失时的回退尺寸（与 .mcmeta 定义一致） */
    private static final int SLIDER_DEFAULT_W = 200;
    private static final int SLIDER_DEFAULT_H = 20;
    private static final int SLIDER_EDGE = 2;
    private static final int HANDLE_DEFAULT_W = 8;
    private static final int HANDLE_DEFAULT_H = 20;

    /** Message colour mirrors the modern-widget default foreground. / 消息文本颜色 */
    private static final int TEXT_COLOR_ENABLED = 0xE0E0E0;
    private static final int TEXT_COLOR_DISABLED = 0xA0A0A0;

    protected double value;
    protected boolean canChangeValue;
    private boolean dragging;

    protected AbstractSilderButton(final int x, final int y, final int width, final int height, final Text message, final double initalValue) {
        super(x, y, width, height, message);
        this.value = initalValue;
    }

    private ResourceLocation getSprite() {
        return this.isActive() && this.isFocused() && !this.canChangeValue ? HIGHLIGHTED_SLIDER_SPRITE : SLIDER_SPRITE;
    }

    private ResourceLocation getHandleSprite() {
        return !this.isActive() || !this.isHovered && !this.canChangeValue ? SLIDER_HANDLE_SPRITE : HIGHLIGHTED_SLIDER_HANDLE_SPRITE;
    }

    /**
     * Render the track, the handle and the scrolling message.
     * <p>
     * 绘制滑轨、把手与居中（超宽时滚动截断）的消息文本。<br>
     * 对标 26.1.2 {@code AbstractSliderButton#extractWidgetRenderState}：
     * 两次 blitSprite 以 nine-patch 自动拉伸纹理（mcmeta 驱动）替代，
     * {@code extractScrollingStringOverContents} 以 {@code TEXT_MARGIN} 边距
     * 内的居中 + clamp 实现。
     * </p>
     * <p>
     * Counterpart of 26.1.2 {@code AbstractSliderButton#extractWidgetRenderState}:
     * the two blitSprite calls become mcmeta-driven nine-patch stretching, and
     * {@code extractScrollingStringOverContents} becomes centring plus a clamp
     * within the {@code TEXT_MARGIN} insets.
     * </p>
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        // Track — full widget width / 滑轨：整组件宽度
        TextureStretching.drawAutoNinePatch(this.getSprite(), this.x, this.y, this.width, this.height,
                SLIDER_DEFAULT_W, SLIDER_DEFAULT_H, SLIDER_EDGE);
        // Handle — clamped by the normalized value / 把手：按归一化值定位
        int handleX = this.x + (int) (this.value * (this.width - HANDLE_WIDTH));
        TextureStretching.drawAutoNinePatch(this.getHandleSprite(), handleX, this.y, HANDLE_WIDTH, this.height,
                HANDLE_DEFAULT_W, HANDLE_DEFAULT_H, SLIDER_EDGE);

        // Scrolling message over contents / 内容区上的滚动文本
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String text = getMessage() != null ? getMessage().getString() : "";
        int available = this.width - TEXT_MARGIN * 2;
        int textWidth = font.getStringWidth(text);
        int offset = (this.width - textWidth) / 2;
        if (textWidth > available) {
            // Too wide: scroll within the margins instead of overflowing.
            // 过宽时在边距内滚动，避免溢出组件。
            offset = MathHelper.clamp_int(offset, available - textWidth, 0);
        }
        int textY = this.y + (this.height - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(text, this.x + TEXT_MARGIN + offset, textY,
                this.active ? TEXT_COLOR_ENABLED : TEXT_COLOR_DISABLED);
    }

    /**
     * Begin dragging and jump to the clicked position.
     * <p>
     * 开始拖拽并跳到点击位置 —— 对标 26.1.2 {@code onClick}。
     * 1.7.10 中点击同时会引发聚焦（{@code dispatchMouseClicked} 随后调用
     * {@code setFocused}），因此不在此处显式开启 {@code canChangeValue}。
     * </p>
     * <p>
     * Counterpart of 26.1.2 {@code onClick}. On 1.7.10 a click also triggers
     * focus ({@code dispatchMouseClicked} calls {@code setFocused} right
     * after), so {@code canChangeValue} is not enabled explicitly here.
     * </p>
     */
    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!this.active || mouseButton != 0) {
            return;
        }
        this.dragging = true;
        this.setValueFromMouse(mouseX);
    }

    /**
     * Track-following drag while the button is held.
     * <p>
     * 按住拖动时跟随鼠标更新值 —— 对标 26.1.2 {@code onDrag}。
     * </p>
     * <p>
     * Updates the value from the mouse while held — counterpart of 26.1.2
     * {@code onDrag}.
     * </p>
     */
    @Override
    public void mouseDrag(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        if (this.dragging) {
            this.setValueFromMouse(mouseX);
        }
    }

    /**
     * Stop dragging and play the click sound on release.
     * <p>
     * 松开时停止拖拽并播放点击音效 —— 对标 26.1.2 {@code onRelease}
     * （vanilla 在 {@code onClick} 抑制音效、到 {@code onRelease} 才播放）。
     * </p>
     * <p>
     * Counterpart of 26.1.2 {@code onRelease}: vanilla suppresses the sound in
     * {@code onClick} and only plays it once the button is released.
     * </p>
     */
    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        this.dragging = false;
        if (mouseButton == 0) {
            Minecraft.getMinecraft().getSoundHandler().playSound(
                    PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
        }
    }

    /**
     * Resolve whether the slider may change its value while focused.
     * <p>
     * 设定聚焦后的可改值状态 —— 对标 26.1.2 {@code setFocused}。vanilla 通过
     * {@code InputType}（MOUSE / KEYBOARD_TAB）判定聚焦来源，1.7.10 无该机制，
     * 此处将任何聚焦均视为用户驱动、可改值；Enter 键仍可切换该状态。
     * </p>
     * <p>
     * Counterpart of 26.1.2 {@code setFocused}. Vanilla gates value-changing on
     * the focus source ({@code InputType}: MOUSE / KEYBOARD_TAB); 1.7.10 has no
     * such mechanism, so any focus is treated as user-driven and
     * value-changeable, and Enter still toggles the state.
     * </p>
     */
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        this.canChangeValue = focused;
    }

    /**
     * Keyboard value stepping.
     * <p>
     * 键盘调值 —— 对标 26.1.2 {@code keyPressed}：Enter 切换
     * {@code canChangeValue}；左右方向键按轨宽步进取值。
     * {@code isSelection()} 在 1.7.10 以 {@code KEY_RETURN} 近似。
     * </p>
     * <p>
     * Counterpart of 26.1.2 {@code keyPressed}: Enter toggles
     * {@code canChangeValue}; left/right arrows step by the track width.
     * {@code isSelection()} is approximated by {@code KEY_RETURN} on 1.7.10.
     * </p>
     */
    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == Keyboard.KEY_RETURN) {
            this.canChangeValue = !this.canChangeValue;
            return true;
        }
        if (this.canChangeValue) {
            boolean left = keyCode == Keyboard.KEY_LEFT;
            boolean right = keyCode == Keyboard.KEY_RIGHT;
            if (left || right) {
                double direction = left ? -1.0D : 1.0D;
                this.setValue(this.value + direction / (this.width - HANDLE_WIDTH));
                return true;
            }
        }
        return false;
    }

    /**
     * Map an absolute mouse X to the normalized value.
     * <p>
     * 将鼠标绝对 X 坐标映射为归一化值 —— 对标 {@code setValueFromMouse}。
     * </p>
     * <p>
     * Maps an absolute mouse X to the normalized value — counterpart of
     * {@code setValueFromMouse}.
     * </p>
     */
    private void setValueFromMouse(int mouseX) {
        this.setValue((mouseX - (this.getX() + HANDLE_HALF_WIDTH)) / (double) (this.width - HANDLE_WIDTH));
    }

    /**
     * Set the normalized value, clamped to {@code [0, 1]}.
     * <p>
     * 设置归一化值并钳制到 {@code [0, 1]}；仅当值实际变化时回调
     * {@link #applyValue()}，随后总是刷新消息 —— 对标 26.1.2
     * {@code setValue}。
     * </p>
     * <p>
     * Clamps the value to {@code [0, 1]}; {@link #applyValue()} fires only when
     * the value actually changes, while the message is always refreshed —
     * counterpart of 26.1.2 {@code setValue}.
     * </p>
     */
    protected void setValue(final double newValue) {
        double oldValue = this.value;
        this.value = MathHelper.clamp_double(newValue, 0.0D, 1.0D);
        if (oldValue != this.value) {
            this.applyValue();
        }
        this.updateMessage();
    }

    /**
     * @return the current normalized value / 当前归一化值
     */
    public double getVal() {
        return this.value;
    }

    /** Rebuild the displayed message from the current value. / 根据当前值重建显示消息 */
    protected abstract void updateMessage();

    /** Push the current value to the backing setting. / 将当前值写回底层配置 */
    protected abstract void applyValue();
}
