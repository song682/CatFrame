package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.StringSplitter;
import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * String display widget — counterpart of the high-version Minecraft
 * {@code StringWidget}. Extends {@link AbstractStringWidget}; accepts
 * {@link Text} objects and renders its message with no interaction currently.
 * Its drawn width is limited through {@link #setMaxWidth(int, TextOverflow)}, which also
 * chooses what happens to the overflowing text (ellipsis, hard cut, folding into
 * lines, or scrolling). Can be added directly to the layout system.
 * </p>
 */
public class StringWidget extends AbstractStringWidget {

    /**
     * Horizontal inset used by the scrolling mode, matching the reference
     * {@code extractScrollingStringOverContents(output, message, 2)}.
     */
    private static final int TEXT_MARGIN = 2;

    /**
     * Appended by {@link TextOverflow#CLAMPED}. The reference uses
     * {@code CommonComponents.ELLIPSIS} ({@code …}); three periods are used here because
     * the 1.7.10 default font is ASCII-only and resource packs are not required to ship
     * the unicode pages a single-glyph ellipsis needs.
     */
    private static final String ELLIPSIS = "...";

    private final StringSplitter splitter;
    private int color;
    private boolean shadow = true;
    /** Width limit; {@code <= 0} means unlimited. */
    private int maxWidth;
    private TextOverflow textOverflow = TextOverflow.CLAMPED;
    private int cachedWidth;
    private int cachedTextWidth;
    private boolean cacheDirty = true;
    @Nullable
    private List<String> cachedLines;

    /**
     * Creates a StringWidget from a {@link Text} and a colour.
     */
    public StringWidget(Text text, int color) {
        super(0, 0, Minecraft.getMinecraft().fontRenderer.getStringWidth(text.getString()),
                Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT,
                text, Minecraft.getMinecraft().fontRenderer);
        this.splitter = StringSplitter.forFont(getFont());
        this.color = color;
    }

    /**
     * Convenience constructor from a raw string.
     */
    public StringWidget(String string, int color) {
        this(Text.literal(string), color);
    }

    public StringWidget setText(Text text) {
        setMessage(text);
        this.height = getFont().FONT_HEIGHT;
        return this;
    }

    @Override
    public void setMessage(Text message) {
        super.setMessage(message);
        this.cacheDirty = true;
    }

    /**
     * Limits the drawn width with the default {@link TextOverflow#CLAMPED} overflow mode.
     * Counterpart of the reference {@code StringWidget#setMaxWidth(int)}.
     */
    public StringWidget setMaxWidth(int maxWidth) {
        return this.setMaxWidth(maxWidth, TextOverflow.CLAMPED);
    }

    /**
     * Limits the drawn width and chooses how overflowing text is handled. A value of
     * {@code 0} or less disables the limit.
     *
     * @param maxWidth     the width limit in pixels
     * @param textOverflow the overflow mode ({@code null} falls back to CLAMPED)
     */
    public StringWidget setMaxWidth(int maxWidth, @Nullable TextOverflow textOverflow) {
        this.maxWidth = maxWidth;
        this.textOverflow = (textOverflow != null) ? textOverflow : TextOverflow.CLAMPED;
        this.cacheDirty = true;
        return this;
    }

    public int getMaxWidth() {
        return this.maxWidth;
    }

    public TextOverflow getTextOverflow() {
        return this.textOverflow;
    }

    public Text getText() {
        return getMessage();
    }

    public StringWidget setColor(int color) {
        this.color = color;
        return this;
    }

    public int getColor() {
        return color;
    }

    public StringWidget setShadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    @Override
    public int getWidth() {
        if (this.maxWidth > 0) {
            this.reflow();
            return this.cachedWidth;
        }
        return super.getWidth();
    }

    /**
     * Wrapped content grows vertically: in {@link TextOverflow#WRAP} mode the height is
     * the folded line count times the font height, mirroring the reference
     * {@code MultiLineTextWidget#getHeight()}. Every other mode keeps the fixed height.
     */
    @Override
    public int getHeight() {
        if (this.maxWidth > 0 && this.textOverflow == TextOverflow.WRAP) {
            this.reflow();
            List<String> lines = this.cachedLines;
            return (lines != null ? lines.size() : 1) * getFont().FONT_HEIGHT;
        }
        return super.getHeight();
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        this.reflow();
        List<String> lines = this.cachedLines;
        if (lines == null) {
            return;
        }
        if (this.textOverflow == TextOverflow.SCROLLING
                && this.maxWidth > 0 && this.cachedTextWidth > this.maxWidth) {
            this.renderScrolling(lines.get(0));
            return;
        }
        int lineY = this.y;
        for (String line : lines) {
            if (shadow) {
                getFont().drawStringWithShadow(line, x, lineY, color);
            } else {
                getFont().drawString(line, x, lineY, color);
            }
            lineY += getFont().FONT_HEIGHT;
        }
    }

    /**
     * Draws the text sliding inside the {@link #TEXT_MARGIN}-inset box and clipped to it.
     * Counterpart of the reference {@code AbstractWidget#extractScrollingStringOverContents}
     * combined with {@code ActiveTextCollector#defaultScrollingHelper} (the overflowing
     * branch only — {@code StringWidget} never enters the centred branch).
     */
    private void renderScrolling(String text) {
        int left = this.x + TEXT_MARGIN;
        int right = this.x + getWidth() - TEXT_MARGIN;
        int top = this.y;
        int bottom = this.y + getHeight();
        int available = right - left;
        if (available <= 0) {
            return;
        }
        int maxPosition = this.cachedTextWidth - available;
        int offset = scrollingOffset(maxPosition, Minecraft.getSystemTime());
        int drawX = left - offset;

        // Scissor rectangles are framebuffer pixels with a bottom-left origin, so the
        // GUI-scale geometry is scaled here (same recipe as AbstractTextAreaWidget).
        Minecraft mc = Minecraft.getMinecraft();
        int scaleFactor = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(left * scaleFactor,
                mc.displayHeight - bottom * scaleFactor,
                available * scaleFactor,
                (bottom - top) * scaleFactor);
        if (shadow) {
            getFont().drawStringWithShadow(text, drawX, this.y, color);
        } else {
            getFont().drawString(text, drawX, this.y, color);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /**
     * Horizontal scroll offset of the scrolling mode at a point in time. Pure so the
     * timing curve is testable without a widget.
     * <p>
     * The period and the eased sine are the reference
     * {@code ActiveTextCollector#defaultScrollingHelper} formulas: one period lasts
     * {@code max(0.5 * maxPosition, 3)} seconds and the offset sweeps between {@code 0}
     * and {@code maxPosition} with a cosine-eased sine, starting at {@code maxPosition}.
     * </p>
     *
     * @param maxPosition the furthest the text has to travel, in pixels
     * @param millis      the current wall-clock time in milliseconds
     * @return the offset in {@code [0, maxPosition]}, or {@code 0} when nothing overflows
     */
    static int scrollingOffset(int maxPosition, long millis) {
        if (maxPosition <= 0) {
            return 0;
        }
        double period = Math.max(maxPosition * 0.5, 3.0);
        double time = millis / 1000.0;
        double alpha = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * time / period)) / 2.0 + 0.5;
        return (int) (maxPosition * alpha);
    }

    /**
     * Recomputes the cached width and drawn lines after the message or the width limit
     * changed. The invalidate-and-lazily-recompute shape mirrors the reference
     * {@code cachedWidthDirty} flag.
     */
    private void reflow() {
        if (!this.cacheDirty) {
            return;
        }
        String text = (getMessage() != null) ? getMessage().getString() : "";
        int textWidth = getFont().getStringWidth(text);
        this.cachedTextWidth = textWidth;
        if (this.maxWidth > 0) {
            this.cachedWidth = Math.min(this.maxWidth, textWidth);
            this.cachedLines = fold(text, this.maxWidth, textWidth,
                    getFont().getStringWidth(ELLIPSIS), this.textOverflow, this.splitter);
        } else {
            this.cachedWidth = textWidth;
            this.cachedLines = Collections.singletonList(text);
        }
        this.cacheDirty = false;
    }

    /**
     * Applies one overflow mode to an already measured text. Pure — the widths and the
     * splitter come in as parameters — so all three modes stay testable without a
     * {@code FontRenderer}.
     *
     * @param text          the message text
     * @param maxWidth      the width limit (positive)
     * @param textWidth     the full width of {@code text}
     * @param ellipsisWidth the width of {@link #ELLIPSIS}
     * @param overflow      the overflow mode
     * @param splitter      the splitter used to clip and fold
     * @return one or more lines to draw, none wider than {@code maxWidth}
     */
    static List<String> fold(String text, int maxWidth, int textWidth, int ellipsisWidth,
                             @Nullable TextOverflow overflow, StringSplitter splitter) {
        if (text.isEmpty()) {
            return Collections.singletonList("");
        }
        if (textWidth <= maxWidth) {
            return Collections.singletonList(text);
        }
        TextOverflow mode = (overflow != null) ? overflow : TextOverflow.CLAMPED;
        switch (mode) {
            case TRUNCATED:
                return Collections.singletonList(splitter.plainHeadByWidth(text, maxWidth, Style.EMPTY));
            case SCROLLING:
                // Scrolling keeps the full text; renderWidget clips and offsets it.
                return Collections.singletonList(text);
            case WRAP: {
                List<String> lines = splitter.splitLines(text, maxWidth, Style.EMPTY);
                return lines.isEmpty() ? Collections.singletonList("") : lines;
            }
            case CLAMPED:
            default:
                return Collections.singletonList(
                        splitter.plainHeadByWidth(text, maxWidth - ellipsisWidth, Style.EMPTY) + ELLIPSIS);
        }
    }

    /**
     * What happens to a message that does not fit into {@link #setMaxWidth(int)}.
     * <p>
     * The reference 26.1.2 enum is {@code {CLAMPED, SCROLLING}}: CLAMPED clips the text
     * and appends an ellipsis ({@code ComponentRenderUtils#clipText}), SCROLLING slides
     * the text back and forth inside the widget. This port keeps both with the same
     * meaning and adds {@link #TRUNCATED} (a hard cut without a suffix) and {@link #WRAP}
     * — the single-widget form of the reference's separate {@code MultiLineTextWidget}.
     * </p>
     */
    public enum TextOverflow {
        /** Clips to the limit and appends {@link StringWidget#ELLIPSIS} — reference CLAMPED semantics. */
        CLAMPED,
        /** Slides the text back and forth, clipped to the width — reference SCROLLING semantics. */
        SCROLLING,
        /** Hard cut at the limit, nothing appended. */
        TRUNCATED,
        /** Folds into as many lines as needed; {@link #getHeight()} grows accordingly. */
        WRAP
    }
}
