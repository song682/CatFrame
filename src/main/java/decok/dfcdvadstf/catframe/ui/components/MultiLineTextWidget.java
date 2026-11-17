package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.StringSplitter;
import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * Multi-line read-only text widget — counterpart of the high-version Minecraft
 * {@code MultiLineTextWidget}. Extends {@link AbstractStringWidget}; folds its message
 * through {@link StringSplitter} so every line fits {@link #setMaxWidth(int)}, draws the
 * lines one font height apart, and reports a height that grows with the line count.
 * </p>
 * <p>
 * The message is flattened once to its legacy {@code §} form
 * ({@link Text#getFormattedString()}) and both measured and drawn in that form, so
 * styles reach the renderer and the codes cost no width — the same assumption the
 * splitter folds with. {@link #setCentered(boolean)} centres every line inside the
 * reported width, and {@link #setMaxRows(int)} drops the lines beyond the limit while
 * ending the last kept line with {@code ...}.
 * </p>
 */
public class MultiLineTextWidget extends AbstractStringWidget {

    /**
     * Appended to the last line when {@link #setMaxRows(int)} truncates. The reference
     * uses {@code CommonComponents.ELLIPSIS} ({@code …}); three periods are used here
     * because the 1.7.10 default font is ASCII-only.
     */
    private static final String ELLIPSIS = "...";

    /** Base colour for runs that carry no {@code §} colour code of their own. */
    private static final int DEFAULT_COLOR = 0xFFFFFF;

    private final StringSplitter splitter;
    /** Width limit; {@code <= 0} means unlimited (a single line). */
    private int maxWidth;
    /** Row limit; {@code <= 0} means unlimited. */
    private int maxRows;
    private boolean centered;
    private boolean cacheDirty = true;
    private int cachedWidth;
    @Nullable
    private List<String> cachedLines;

    /**
     * Creates a multi-line widget for a {@link Text}, anchored at the origin.
     */
    public MultiLineTextWidget(Text message) {
        this(0, 0, message);
    }

    /**
     * Creates a multi-line widget for a {@link Text} at the given position.
     */
    public MultiLineTextWidget(int x, int y, Text message) {
        super(x, y, 0, 0, message, Minecraft.getMinecraft().fontRenderer);
        this.splitter = StringSplitter.forFont(getFont());
    }

    @Override
    public void setMessage(Text message) {
        super.setMessage(message);
        this.cacheDirty = true;
    }

    /**
     * Limits the width of every drawn line. A value of {@code 0} or less disables the
     * limit, leaving the message on a single line.
     */
    public MultiLineTextWidget setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        this.cacheDirty = true;
        return this;
    }

    /**
     * Limits how many folded lines are drawn. A value of {@code 0} or less disables the
     * limit; when it truncates, the last kept line ends with {@link #ELLIPSIS}.
     */
    public MultiLineTextWidget setMaxRows(int maxRows) {
        this.maxRows = maxRows;
        this.cacheDirty = true;
        return this;
    }

    /**
     * Centres every line inside the reported width instead of drawing from the left edge.
     */
    public MultiLineTextWidget setCentered(boolean centered) {
        this.centered = centered;
        return this;
    }

    public boolean isCentered() {
        return this.centered;
    }

    public int getMaxWidth() {
        return this.maxWidth;
    }

    public int getMaxRows() {
        return this.maxRows;
    }

    /**
     * Width of the widest folded line, clamped to {@link #setMaxWidth(int)} — the reference
     * {@code MultiLineLabel#getWidth()} contract.
     */
    @Override
    public int getWidth() {
        this.reflow();
        return this.cachedWidth;
    }

    /**
     * Height grows with the content: the folded line count times the font height, mirroring
     * the reference {@code MultiLineTextWidget#getHeight()}.
     */
    @Override
    public int getHeight() {
        this.reflow();
        List<String> lines = this.cachedLines;
        return (lines != null ? lines.size() : 0) * getFont().FONT_HEIGHT;
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        this.reflow();
        List<String> lines = this.cachedLines;
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int reportedWidth = this.cachedWidth;
        int lineY = getTextY();
        for (String line : lines) {
            int lineX = getTextX();
            if (this.centered) {
                lineX += (reportedWidth - Math.round(this.splitter.stringWidth(line))) / 2;
            }
            getFont().drawStringWithShadow(line, lineX, lineY, DEFAULT_COLOR);
            lineY += getFont().FONT_HEIGHT;
        }
    }

    /**
     * Left edge the lines are drawn from. Kept overridable so subclasses can offset the
     * block without touching the widget position.
     */
    protected int getTextX() {
        return getX();
    }

    /**
     * Top edge the first line is drawn from.
     */
    protected int getTextY() {
        return getY();
    }

    /**
     * Folds the message once per message, width limit or row limit change.
     */
    private void reflow() {
        if (!this.cacheDirty) {
            return;
        }
        Layout layout = layout(getMessage(), this.maxWidth, this.maxRows,
                Math.round(this.splitter.stringWidth(ELLIPSIS)), this.splitter);
        this.cachedLines = layout.lines;
        this.cachedWidth = layout.width;
        this.cacheDirty = false;
    }

    /**
     * Folds a message into the lines to draw and the width it reports. Pure — the message
     * is flattened to legacy {@code §} codes and measured with the injected splitter — so
     * the folding and truncation rules are testable without a {@code FontRenderer}.
     *
     * @param message       the message ({@code null} is treated as empty)
     * @param maxWidth      the width limit; {@code <= 0} means unlimited
     * @param maxRows       the row limit; {@code <= 0} means unlimited
     * @param ellipsisWidth the width of {@link #ELLIPSIS}
     * @param splitter      the splitter used for measuring, folding and clipping
     * @return the drawn lines (carrying {@code §} codes, empty for an empty message) and
     * the reported width
     */
    static Layout layout(@Nullable Text message, int maxWidth, int maxRows, int ellipsisWidth,
                         StringSplitter splitter) {
        String text = (message != null) ? message.getFormattedString() : "";
        List<String> lines;
        if (maxWidth > 0) {
            lines = splitter.splitLines(text, maxWidth, Style.EMPTY);
        } else if (text.isEmpty()) {
            lines = Collections.emptyList();
        } else {
            lines = Collections.singletonList(text);
        }
        if (maxRows > 0 && lines.size() > maxRows) {
            List<String> kept = new ArrayList<String>(lines.subList(0, maxRows));
            int last = maxRows - 1;
            String lastLine = kept.get(last);
            // The reference clips the last kept line to its own width minus the ellipsis,
            // not to maxWidth, so a short line loses as little as possible.
            int budget = Math.round(splitter.stringWidth(lastLine)) - ellipsisWidth;
            if (budget >= 0) {
                // A negative budget means the line is narrower than the ellipsis itself: keeping
                // it whole is the only choice that does not make it wider than it already was.
                kept.set(last, splitter.plainHeadByWidth(lastLine, budget, Style.EMPTY) + ELLIPSIS);
            }
            lines = kept;
        }
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, Math.round(splitter.stringWidth(line)));
        }
        int width = (maxWidth > 0) ? Math.min(maxWidth, widest) : widest;
        return new Layout(lines, width);
    }

    /**
     * Result of {@link #layout}: the folded lines and the width the widget reports.
     */
    static final class Layout {
        /** Lines to draw in order; every line carries its own {@code §} prefix when needed. */
        final List<String> lines;
        /** Widest line, clamped to the width limit. */
        final int width;

        Layout(List<String> lines, int width) {
            this.lines = lines;
            this.width = width;
        }
    }
}
