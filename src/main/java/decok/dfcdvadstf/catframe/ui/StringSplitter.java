package decok.dfcdvadstf.catframe.ui;

import net.minecraft.client.gui.FontRenderer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * Width-aware measuring, truncating and line folding of styled text — backport of the
 * high-version {@code net.minecraft.client.StringSplitter}.<br>
 * The class renders nothing; it answers "how wide is this text", "where must it be cut
 * to fit a width", "where may a line break" and "how does it fold into lines", so that
 * GUI code never has to estimate width from the character count.
 * </p>
 * <p>
 * Deviations from the reference implementation, all forced by 1.7.10:
 * </p>
 * <ul>
 *   <li>There is no {@code FormattedText} here; styled text is the legacy
 *       {@code §}-coded String, so that is the document type of this class. Formatting
 *       codes are resolved into a running {@link Style} and advance no width, and the
 *       lines returned by {@link #splitLines(String, int, Style)} are prefixed with the
 *       codes active at the break point — the counterpart of the reference's
 *       per-segment styles, and the reason a folded line keeps its colour and flags.</li>
 *   <li>Positions are UTF-16 indices into the flat input string, formatting codes
 *       included, so they stay usable with {@link String#substring(int)}.</li>
 *   <li>Supplementary code points cannot be rendered by the {@code char}-based 1.7.10
 *       {@link FontRenderer}; they are measured as zero width.</li>
 * </ul>
 * <p>
 * Line breaking follows the reference state machine: the width budget is consumed per
 * code point, the last space encountered is remembered, and a code point that overflows
 * falls back to that space so the break lands on a word boundary; without a space the
 * break is hard. Two guards are kept from the reference: zero-width code points never
 * open a line on their own ({@code hadNonZeroWidthChar}), so leading combining marks do
 * not produce a blank line, and the budget is floored at 1 to keep zero or negative
 * widths from looping forever.
 * </p>
 */
public final class StringSplitter {

    /** The legacy formatting-code prefix. */
    private static final char CODE_PREFIX = '\u00a7';

    /** Legacy colour codes in index order — {@code 0-9a-f}. */
    private static final Style.TextFormat[] COLOR_FORMATS = {
        Style.TextFormat.BLACK, Style.TextFormat.DARK_BLUE, Style.TextFormat.DARK_GREEN,
        Style.TextFormat.DARK_AQUA, Style.TextFormat.DARK_RED, Style.TextFormat.DARK_PURPLE,
        Style.TextFormat.GOLD, Style.TextFormat.GRAY, Style.TextFormat.DARK_GRAY,
        Style.TextFormat.BLUE, Style.TextFormat.GREEN, Style.TextFormat.AQUA,
        Style.TextFormat.RED, Style.TextFormat.LIGHT_PURPLE, Style.TextFormat.YELLOW,
        Style.TextFormat.WHITE
    };

    private final WidthProvider widthProvider;

    /**
     * @param widthProvider externalises "how wide is one code point in this style" —
     *                      the reference design, so this class itself knows nothing
     *                      about fonts
     */
    public StringSplitter(WidthProvider widthProvider) {
        this.widthProvider = widthProvider;
    }

    // ──── Factories ────

    /**
     * Creates a splitter whose widths come from the given {@link FontRenderer},
     * mirroring {@code FontRenderer#getStringWidth}: the per-glyph advance of
     * {@code getCharWidth}, plus one pixel while the style is bold — exactly how 1.7.10
     * fakes bold by drawing the glyph twice. Formatting codes, which vanilla reports as
     * {@code -1}, contribute no width.
     */
    public static StringSplitter forFont(final FontRenderer font) {
        return new StringSplitter((codepoint, style) -> {
            if (!Character.isBmpCodePoint(codepoint)) {
                return 0.0F;
            }
            int charWidth = font.getCharWidth((char) codepoint);
            if (charWidth <= 0) {
                return 0.0F;
            }
            return style.isBold() ? charWidth + 1.0F : charWidth;
        });
    }

    // ──── Measuring ────

    /**
     * Measures the rendered width of a legacy {@code §}-coded string.
     * Counterpart of the reference {@code stringWidth(String)}.
     */
    public float stringWidth(@Nullable String str) {
        if (str == null) {
            return 0.0F;
        }
        final float[] width = {0.0F};
        scan(str, 0, Style.EMPTY, (position, style, codepoint) -> {
            width[0] += this.widthProvider.getWidth(codepoint, style);
            return true;
        });
        return width[0];
    }

    /**
     * Measures the rendered width of a {@link Text} tree, styles resolved through
     * {@link Text#getFormattedString()}.
     */
    public float stringWidth(@Nullable Text text) {
        return text == null ? 0.0F : this.stringWidth(text.getFormattedString());
    }

    // ──── Truncating by width ────

    /**
     * Returns the index up to which {@code str} fits into {@code maxWidth}.
     * Counterpart of the reference {@code plainIndexAtWidth}; unlike the reference, the
     * input's {@code §} codes are honoured as zero-width formatting rather than counted
     * as literal {@code §} characters, because 1.7.10 strings carry them.
     *
     * @return an index into {@code str}; trailing formatting codes past the last
     *         fitting code point are excluded, as in the reference
     */
    public int plainIndexAtWidth(@Nullable String str, int maxWidth, @Nullable Style initialStyle) {
        if (str == null) {
            return 0;
        }
        final float[] budget = {maxWidth};
        final int[] index = {0};
        scan(str, 0, initialStyle, (position, style, codepoint) -> {
            budget[0] -= this.widthProvider.getWidth(codepoint, style);
            if (budget[0] >= 0.0F) {
                index[0] = position + Character.charCount(codepoint);
                return true;
            }
            return false;
        });
        return index[0];
    }

    /**
     * Returns the head of {@code str} that fits into {@code maxWidth}.
     * Counterpart of the reference {@code plainHeadByWidth}.
     */
    public String plainHeadByWidth(@Nullable String str, int maxWidth, @Nullable Style initialStyle) {
        if (str == null) {
            return "";
        }
        return str.substring(0, this.plainIndexAtWidth(str, maxWidth, initialStyle));
    }

    /**
     * Returns the tail of {@code str} that fits into {@code maxWidth}.
     * Counterpart of the reference {@code plainTailByWidth}. The formatting codes
     * immediately preceding the first kept character are kept with it, so the tail does
     * not lose the colour it is rendered in.
     */
    public String plainTailByWidth(@Nullable String str, int maxWidth, @Nullable Style initialStyle) {
        if (str == null) {
            return "";
        }
        final float total = this.stringWidth(str);
        final float[] before = {0.0F};
        final int[] start = {str.length()};
        scan(str, 0, initialStyle, (position, style, codepoint) -> {
            if (total - before[0] <= maxWidth) {
                start[0] = position;
                return false;
            }
            before[0] += this.widthProvider.getWidth(codepoint, style);
            return true;
        });
        return str.substring(rewindOverCodes(str, start[0]));
    }

    /**
     * Returns the head of a {@link Text} that fits into {@code width}, as a rendered
     * legacy string. Counterpart of the reference {@code headByWidth}.
     */
    public String headByWidth(@Nullable Text text, int width, @Nullable Style initialStyle) {
        if (text == null) {
            return "";
        }
        String flat = text.getFormattedString(initialStyle != null ? initialStyle : Style.EMPTY);
        return this.plainHeadByWidth(flat, width, Style.EMPTY);
    }

    // ──── Finding the break point ────

    /**
     * Returns the index a line has to break at to fit {@code maxWidth}, preferring the
     * last space before the overflow. Counterpart of the reference
     * {@code findLineBreak}.
     */
    public int findLineBreak(@Nullable String input, int maxWidth, @Nullable Style initialStyle) {
        if (input == null) {
            return 0;
        }
        LineBreakFinder finder = new LineBreakFinder(maxWidth);
        scan(input, 0, initialStyle, finder);
        return finder.getSplitPosition();
    }

    // ──── Folding into lines ────

    /**
     * Folds {@code input} into lines no wider than {@code maxWidth}, reporting each line
     * as an index range into {@code input}.
     * Counterpart of the reference
     * {@code splitLines(String, int, Style, boolean, LinePosConsumer)}.
     *
     * @param includeAll whether the reported range swallows the trailing space or
     *                   {@code \n} of the break ({@code true}) or stops before it
     *                   ({@code false}, the reference default)
     * @param output     receives the style active at the line start plus its range
     */
    public void splitLines(@Nullable String input, int maxWidth, @Nullable Style initialStyle,
                           boolean includeAll, LinePosConsumer output) {
        if (input == null) {
            return;
        }
        int start = 0;
        int size = input.length();
        Style workStyle = (initialStyle != null) ? initialStyle : Style.EMPTY;

        while (start < size) {
            LineBreakFinder finder = new LineBreakFinder(maxWidth);
            boolean endOfText = scan(input, start, workStyle, finder);
            if (endOfText) {
                output.accept(workStyle, start, size);
                break;
            }

            int lineBreak = finder.getSplitPosition();
            char tailChar = input.charAt(lineBreak);
            int adjustedBreak = (tailChar != '\n' && tailChar != ' ') ? lineBreak : lineBreak + 1;
            output.accept(workStyle, start, includeAll ? adjustedBreak : lineBreak);
            start = adjustedBreak;
            workStyle = finder.getSplitStyle();
        }
    }

    /**
     * Folds a legacy {@code §}-coded string into renderable lines. Each line carries the
     * formatting codes active at its start, so the lines can be drawn independently with
     * {@code FontRenderer#drawString} without losing styles.
     * Counterpart of the reference {@code splitLines(String, int, Style)}.
     */
    public List<String> splitLines(@Nullable final String input, int maxWidth, @Nullable Style initialStyle) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<String>();
        this.splitLines(input, maxWidth, initialStyle, false,
            (style, start, end) -> result.add(Text.legacyCodes(style) + input.substring(start, end)));
        return result;
    }

    /**
     * Folds a {@link Text} into renderable lines, styles resolved through
     * {@link Text#getFormattedString()}.
     * Counterpart of the reference {@code splitLines(FormattedText, int, Style)}.
     */
    public List<String> splitLines(@Nullable Text input, int maxWidth, @Nullable Style initialStyle) {
        if (input == null) {
            return Collections.emptyList();
        }
        String flat = input.getFormattedString(initialStyle != null ? initialStyle : Style.EMPTY);
        return this.splitLines(flat, maxWidth, Style.EMPTY);
    }

    // ──── Cursor movement by word ────

    /**
     * Returns the index reached by moving {@code dir} words from {@code from}, the
     * counterpart of {@code TextFieldHelper}'s Ctrl+Left/Right stepping.
     * Ported as-is from the reference; the {@code stripSpaces} flag skips runs of spaces
     * and {@code \n} surrounding each step.
     */
    public static int getWordPosition(String text, int dir, int from, boolean stripSpaces) {
        int result = from;
        boolean reverse = dir < 0;
        int abs = Math.abs(dir);

        for (int i = 0; i < abs; i++) {
            if (reverse) {
                while (stripSpaces && result > 0
                    && (text.charAt(result - 1) == ' ' || text.charAt(result - 1) == '\n')) {
                    result--;
                }
                while (result > 0 && text.charAt(result - 1) != ' ' && text.charAt(result - 1) != '\n') {
                    result--;
                }
            } else {
                int length = text.length();
                int spaceIndex = text.indexOf(' ', result);
                int newlineIndex = text.indexOf('\n', result);
                if (spaceIndex == -1 && newlineIndex == -1) {
                    result = -1;
                } else if (spaceIndex != -1 && newlineIndex != -1) {
                    result = Math.min(spaceIndex, newlineIndex);
                } else if (spaceIndex != -1) {
                    result = spaceIndex;
                } else {
                    result = newlineIndex;
                }

                if (result == -1) {
                    result = length;
                } else {
                    while (stripSpaces && result < length
                        && (text.charAt(result) == ' ' || text.charAt(result) == '\n')) {
                        result++;
                    }
                }
            }
        }

        return result;
    }

    // ──── Internals ────

    /**
     * Walks the visible code points of a legacy {@code §}-coded string, folding every
     * formatting code into the running style before the next visible code point reaches
     * the sink.
     *
     * @param str          the string to scan (may be null: nothing is scanned)
     * @param from         the index to start at
     * @param initialStyle the style in effect at {@code from}
     * @param sink         per-code-point consumer
     * @return true when the whole string was consumed, false when the sink stopped it
     */
    private static boolean scan(@Nullable String str, int from, @Nullable Style initialStyle,
                                CodepointSink sink) {
        if (str == null) {
            return true;
        }
        Style style = (initialStyle != null) ? initialStyle : Style.EMPTY;
        int length = str.length();
        int i = from;
        while (i < length) {
            char c = str.charAt(i);
            if (c == CODE_PREFIX && i + 1 < length) {
                Style.TextFormat format = legacyFormat(str.charAt(i + 1));
                if (format != null) {
                    // 1.7.10 semantics: a colour code also clears every flag, §r clears all.
                    style = style.applyLegacyFormat(format);
                    i += 2;
                    continue;
                }
            }
            int codepoint = str.codePointAt(i);
            if (!sink.accept(i, style, codepoint)) {
                return false;
            }
            i += Character.charCount(codepoint);
        }
        return true;
    }

    /**
     * Resolves one legacy formatting character ({@code 0-9 a-f k-o r}, either case) to
     * its format, or {@code null} when the character is not a formatting code.
     */
    @Nullable
    private static Style.TextFormat legacyFormat(char code) {
        char c = Character.toLowerCase(code);
        if (c >= '0' && c <= '9') {
            return COLOR_FORMATS[c - '0'];
        }
        if (c >= 'a' && c <= 'f') {
            return COLOR_FORMATS[10 + (c - 'a')];
        }
        switch (c) {
            case 'k':
                return Style.TextFormat.OBFUSCATED;
            case 'l':
                return Style.TextFormat.BOLD;
            case 'm':
                return Style.TextFormat.STRIKETHROUGH;
            case 'n':
                return Style.TextFormat.UNDERLINE;
            case 'o':
                return Style.TextFormat.ITALIC;
            case 'r':
                return Style.TextFormat.RESET;
            default:
                return null;
        }
    }

    /**
     * Steps back over formatting codes that sit directly before {@code position}, so a
     * substring starting there still knows which style it is rendered in.
     */
    private static int rewindOverCodes(String str, int position) {
        int from = position;
        while (from >= 2 && str.charAt(from - 2) == CODE_PREFIX
            && legacyFormat(str.charAt(from - 1)) != null) {
            from -= 2;
        }
        return from;
    }

    // ──── Nested types ────

    /**
     * Externalised glyph metrics — the reference's core abstraction: the splitter
     * itself never touches a font.
     */
    @FunctionalInterface
    public interface WidthProvider {
        /**
         * @param codepoint the code point to measure
         * @param style     the style in effect at that code point
         * @return the advance width in pixels
         */
        float getWidth(int codepoint, Style style);
    }

    /**
     * Consumer of one folded line, expressed in input coordinates.
     * Counterpart of the reference {@code StringSplitter.LinePosConsumer}.
     */
    @FunctionalInterface
    public interface LinePosConsumer {
        /**
         * @param style the style active at the line start
         * @param start the index of the first character of the line
         * @param end   the index just past the last character of the line
         */
        void accept(Style style, int start, int end);
    }

    /** Per-code-point consumer used by {@link #scan}. */
    private interface CodepointSink {
        boolean accept(int position, Style style, int codepoint);
    }

    /**
     * The reference's width-budget state machine: it accumulates width per code point,
     * remembers the last space, and ends the iteration at the word boundary or hard at
     * the overflowing code point.
     */
    private final class LineBreakFinder implements CodepointSink {
        private final float maxWidth;
        private int lineBreak = -1;
        private Style lineBreakStyle = Style.EMPTY;
        private boolean hadNonZeroWidthChar;
        private float width;
        private int lastSpace = -1;
        private Style lastSpaceStyle = Style.EMPTY;
        private int nextChar;

        LineBreakFinder(int maxWidth) {
            // A budget of 0 or less would never let the first code point through.
            this.maxWidth = Math.max(maxWidth, 1.0F);
        }

        @Override
        public boolean accept(int position, Style style, int codepoint) {
            if (codepoint == '\n') {
                return this.finishIteration(position, style);
            }
            if (codepoint == ' ') {
                // Remembered as the fallback break point; the space itself still takes width.
                this.lastSpace = position;
                this.lastSpaceStyle = style;
            }
            float charWidth = StringSplitter.this.widthProvider.getWidth(codepoint, style);
            this.width += charWidth;
            if (!this.hadNonZeroWidthChar || !(this.width > this.maxWidth)) {
                this.hadNonZeroWidthChar |= charWidth != 0.0F;
                this.nextChar = position + Character.charCount(codepoint);
                return true;
            }
            return this.lastSpace != -1
                ? this.finishIteration(this.lastSpace, this.lastSpaceStyle)
                : this.finishIteration(position, style);
        }

        private boolean finishIteration(int breakPosition, Style style) {
            this.lineBreak = breakPosition;
            this.lineBreakStyle = style;
            return false;
        }

        boolean lineBreakFound() {
            return this.lineBreak != -1;
        }

        int getSplitPosition() {
            return this.lineBreakFound() ? this.lineBreak : this.nextChar;
        }

        Style getSplitStyle() {
            return this.lineBreakStyle;
        }
    }
}
