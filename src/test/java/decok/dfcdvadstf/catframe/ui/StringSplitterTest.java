package decok.dfcdvadstf.catframe.ui;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Covers the layout rules of {@link StringSplitter}: width measurement over legacy
 * {@code §}-coded Strings, truncation by width, break-point selection and line folding.
 * <p>
 * A synthetic {@link StringSplitter.WidthProvider} replaces the font, so nothing here
 * needs a {@code FontRenderer}: every code point is one pixel wide, two while the style
 * is bold — enough to reproduce each branch of the reference state machine.
 * </p>
 */
public class StringSplitterTest {

    private static final StringSplitter SPLITTER =
            new StringSplitter((codepoint, style) -> style.isBold() ? 2.0F : 1.0F);

    // ──── helpers ────

    private static List<String> lines(String input, int maxWidth) {
        return SPLITTER.splitLines(input, maxWidth, Style.EMPTY);
    }

    /** Collects the reported [start, end) ranges of the index-reporting overload. */
    private static List<int[]> ranges(String input, int maxWidth) {
        final List<int[]> result = new ArrayList<int[]>();
        SPLITTER.splitLines(input, maxWidth, Style.EMPTY, false,
                (style, start, end) -> result.add(new int[]{start, end}));
        return result;
    }

    // ──── measuring ────

    @Test
    public void formattingCodesAdvanceNoWidth() {
        assertEquals(3.0F, SPLITTER.stringWidth("abc"), 0.0F);
        assertEquals(3.0F, SPLITTER.stringWidth("\u00a7cabc"), 0.0F);
        assertEquals(0.0F, SPLITTER.stringWidth("\u00a7c"), 0.0F);
        assertEquals(0.0F, SPLITTER.stringWidth((String) null), 0.0F);
    }

    @Test
    public void boldWidensEveryCodePointUnderIt() {
        assertEquals(6.0F, SPLITTER.stringWidth("\u00a7labc"), 0.0F);
        // §r clears the flag again, so only the leading two characters stay bold.
        assertEquals(2.0F + 2.0F + 1.0F + 1.0F + 1.0F, SPLITTER.stringWidth("\u00a7lab\u00a7rcde"), 0.0F);
    }

    @Test
    public void colourCodeClearsFlagsLikeVanilla() {
        // A colour code resets bold in 1.7.10, so 'b' is narrow again.
        assertEquals(2.0F + 1.0F, SPLITTER.stringWidth("\u00a7la\u00a7cb"), 0.0F);
    }

    @Test
    public void textIsMeasuredThroughItsLegacyForm() {
        assertEquals(3.0F, SPLITTER.stringWidth(Text.literal("abc")), 0.0F);
        assertEquals(4.0F, SPLITTER.stringWidth(Text.literal("ab", Style.EMPTY.withBold(true))), 0.0F);
    }

    // ──── truncating ────

    @Test
    public void indexStopsAtTheFirstOverflowingCodePoint() {
        assertEquals(3, SPLITTER.plainIndexAtWidth("abcdef", 3, Style.EMPTY));
        assertEquals(0, SPLITTER.plainIndexAtWidth("abcdef", 0, Style.EMPTY));
        assertEquals(6, SPLITTER.plainIndexAtWidth("abcdef", 100, Style.EMPTY));
    }

    @Test
    public void headKeepsTheFormattingCodesItCrossed() {
        assertEquals("abc", SPLITTER.plainHeadByWidth("abcdef", 3, Style.EMPTY));
        assertEquals("\u00a7cab", SPLITTER.plainHeadByWidth("\u00a7cab cd", 2, Style.EMPTY));
        assertEquals("\u00a7lab", SPLITTER.plainHeadByWidth("\u00a7labc", 4, Style.EMPTY));
    }

    @Test
    public void headOfTextIsTruncatedToWidth() {
        assertEquals("ab", SPLITTER.headByWidth(Text.literal("abcdef"), 2, Style.EMPTY));
        assertEquals("", SPLITTER.headByWidth((Text) null, 2, Style.EMPTY));
    }

    @Test
    public void tailKeepsTheColourOfItsFirstKeptCharacter() {
        // The §c pair sits before the kept suffix; dropping it would render the tail white.
        assertEquals("\u00a7cdef", SPLITTER.plainTailByWidth("abc\u00a7cdef", 3, Style.EMPTY));
        assertEquals("def", SPLITTER.plainTailByWidth("abcdef", 3, Style.EMPTY));
        assertEquals("abcdef", SPLITTER.plainTailByWidth("abcdef", 100, Style.EMPTY));
    }

    // ──── break points ────

    @Test
    public void breakPrefersTheLastSpace() {
        assertEquals(5, SPLITTER.findLineBreak("aa bb cc", 5, Style.EMPTY));
        assertEquals(2, SPLITTER.findLineBreak("aa bb cc", 3, Style.EMPTY));
    }

    @Test
    public void breakIsHardWithoutASpace() {
        assertEquals(3, SPLITTER.findLineBreak("abcdefgh", 3, Style.EMPTY));
    }

    @Test
    public void newlineIsAnUnconditionalBreak() {
        assertEquals(2, SPLITTER.findLineBreak("ab\ncd", 100, Style.EMPTY));
    }

    // ──── folding ────

    @Test
    public void linesArePrefixedWithTheStyleActiveAtTheBreak() {
        assertEquals(java.util.Arrays.asList("\u00a7caa", "\u00a7cbb"), lines("\u00a7caa bb", 4));
    }

    @Test
    public void continuationLineDoesNotStartWithTheSpace() {
        assertEquals(java.util.Arrays.asList("aa bb", "cc"), lines("aa bb cc", 5));
    }

    @Test
    public void hardNewlineSplitsWithoutSwallowingTheCharacter() {
        assertEquals(java.util.Arrays.asList("ab", "cd"), lines("ab\ncd", 100));
    }

    @Test
    public void trailingNewlineIsTheCallersEmptyLine() {
        // The reference (and therefore this port) folds only existing lines; the caller
        // appends the empty line a trailing '\n' opens.
        assertEquals(java.util.Arrays.asList("ab"), lines("ab\n", 100));
    }

    @Test
    public void reportedRangesIndexOfTheInput() {
        List<int[]> result = ranges("aa bb cc", 5);
        assertEquals(2, result.size());
        assertEquals(0, result.get(0)[0]);
        assertEquals(5, result.get(0)[1]);
        assertEquals(6, result.get(1)[0]);
        assertEquals(8, result.get(1)[1]);
    }

    @Test
    public void textIsFoldedThroughItsLegacyForm() {
        assertEquals(java.util.Arrays.asList("aa bb", "cc"),
                SPLITTER.splitLines(Text.literal("aa bb cc"), 5, Style.EMPTY));
    }

    @Test
    public void emptyInputHasNoLines() {
        assertTrue(lines("", 10).isEmpty());
        assertTrue(SPLITTER.splitLines((Text) null, 10, Style.EMPTY).isEmpty());
    }

    @Test
    public void zeroWidthLeadingCodePointDoesNotOpenABlankLine() {
        // A leading combining mark must not produce an empty first line.
        StringSplitter zeroWidthMark = new StringSplitter(
                (codepoint, style) -> codepoint == 0x0301 ? 0.0F : 5.0F);
        assertEquals(1, zeroWidthMark.splitLines("\u0301a", 4, Style.EMPTY).size());
    }

    @Test
    public void zeroWidthBudgetStillTerminates() {
        // Floored at 1, so every single code point gets its own line instead of looping.
        assertEquals(3, lines("abc", 0).size());
        assertEquals(3, lines("abc", -5).size());
    }

    // ──── word stepping ────

    @Test
    public void wordSteppingCrossesWholeWords() {
        String text = "aa bb cc";
        assertEquals(3, StringSplitter.getWordPosition(text, 1, 0, true));
        assertEquals(6, StringSplitter.getWordPosition(text, 1, 3, true));
        assertEquals(8, StringSplitter.getWordPosition(text, 1, 6, true));
        assertEquals(6, StringSplitter.getWordPosition(text, -1, 8, true));
    }
}
