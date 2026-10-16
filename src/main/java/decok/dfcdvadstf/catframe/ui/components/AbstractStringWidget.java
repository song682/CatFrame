package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.gui.FontRenderer;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
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
     * Returns the current message.
     */
    public Text getMessage() {
        return message;
    }

    /**
     * <p>
     * Sets the message and re-measures the widget width from the text.
     * Counterpart of the high-version {@code AbstractStringWidget#setMessage}.
     * </p>
     */
    public void setMessage(Text message) {
        this.message = message;
        this.width = font.getStringWidth(message != null ? message.getString() : "");
    }

    /**
     * <p>
     * Sets the component click handler — invoked with the message style
     * when the widget is clicked.
     * </p>
     *
     * @param componentClickHandler click callback (may be null to cancel)
     */
    public AbstractStringWidget setComponentClickHandler(@Nullable Consumer<Style> componentClickHandler) {
        this.componentClickHandler = componentClickHandler;
        return this;
    }

    /**
     * Returns the component click handler.
     */
    @Nullable
    protected Consumer<Style> getComponentClickHandler() {
        return componentClickHandler;
    }

    /**
     * Returns the font renderer.
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
        // No per-character hit detection (no ActiveTextCollector on 1.7.10);
        // the callback receives the message's overall style instead.
        Style style = message != null ? message.getStyle() : Style.EMPTY;
        handler.accept(style);
    }
}
