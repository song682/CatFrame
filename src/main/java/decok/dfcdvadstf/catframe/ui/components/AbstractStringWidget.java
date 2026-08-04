package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.gui.FontRenderer;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * <p>
 * 文本组件抽象基类 —— 提供消息持有、字体引用与样式点击回调等公共能力。<br>
 * 对标高版本 Minecraft 的 {@code AbstractStringWidget}。
 * </p>
 * <p>
 * Abstract base class for text widgets — provides message storage, a font
 * reference, and a style-click callback.<br>
 * Counterpart of the high-version Minecraft {@code AbstractStringWidget}.
 * </p>
 */
public abstract class AbstractStringWidget extends AbstractComponent {

    @Nullable
    private Consumer<Style> componentClickHandler = null;
    private final FontRenderer font;
    private Text message;

    public AbstractStringWidget(int x, int y, int width, int height, Text message, FontRenderer font) {
        super(x, y, width, height);
        this.message = message;
        this.font = font;
    }

    /**
     * 获取当前消息文本。
     * <p>
     * Returns the current message.
     * </p>
     */
    public Text getMessage() {
        return message;
    }

    /**
     * 设置消息文本，并随文本重新测量组件宽度。
     * <p>
     * Sets the message and re-measures the widget width from the text.
     * 对标高版本 {@code AbstractStringWidget#setMessage}。
     * </p>
     */
    public void setMessage(Text message) {
        this.message = message;
        this.width = font.getStringWidth(message != null ? message.getString() : "");
    }

    /**
     * 设置组件点击回调 —— 点击组件时以消息样式调用。
     * <p>
     * Sets the component click handler — invoked with the message style
     * when the widget is clicked.
     * </p>
     *
     * @param componentClickHandler 点击回调 / click callback（可为 null 表示取消）
     */
    public AbstractStringWidget setComponentClickHandler(@Nullable Consumer<Style> componentClickHandler) {
        this.componentClickHandler = componentClickHandler;
        return this;
    }

    /**
     * 获取组件点击回调。
     * <p>
     * Returns the component click handler.
     * </p>
     */
    @Nullable
    protected Consumer<Style> getComponentClickHandler() {
        return componentClickHandler;
    }

    /**
     * 获取字体渲染器。
     * <p>
     * Returns the font renderer.
     * </p>
     */
    protected final FontRenderer getFont() {
        return font;
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        Consumer<Style> handler = componentClickHandler;
        if (handler == null || mouseButton != 0 || !isMouseOver(mouseX, mouseY)) {
            return;
        }
        // 1.7.10 无 ActiveTextCollector 逐字符命中检测，简化回调为消息整体样式。
        // No per-character hit detection (no ActiveTextCollector on 1.7.10);
        // the callback receives the message's overall style instead.
        Style style = message != null ? message.getStyle() : Style.EMPTY;
        handler.accept(style);
    }
}
