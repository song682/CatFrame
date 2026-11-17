package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.StringSplitter;
import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the three {@link StringWidget.TextOverflow} modes through the pure
 * {@link StringWidget#fold} helper, so no {@code FontRenderer} or Minecraft runtime is
 * needed. Every glyph advances 1px (2px while bold), mirroring {@code StringSplitterTest}.
 */
public class StringWidgetOverflowTest {

    private static final StringSplitter SPLITTER = new StringSplitter(
            (codepoint, style) -> style.isBold() ? 2.0F : 1.0F);

    /** Width of the three-period ellipsis under the synthetic provider. */
    private static final int ELLIPSIS_WIDTH = 3;

    private static List<String> fold(String text, int maxWidth, StringWidget.TextOverflow mode) {
        return StringWidget.fold(text, maxWidth, text.length(), ELLIPSIS_WIDTH, mode, SPLITTER);
    }

    @Test
    public void textThatFitsIsDrawnVerbatimInEveryMode() {
        for (StringWidget.TextOverflow mode : StringWidget.TextOverflow.values()) {
            assertEquals(mode.name(), 1, fold("abc", 10, mode).size());
            assertEquals(mode.name(), "abc", fold("abc", 10, mode).get(0));
        }
    }

    @Test
    public void clampedKeepsOnlyWhatFitsNextToTheEllipsis() {
        List<String> lines = fold("abcdefghij", 6, StringWidget.TextOverflow.CLAMPED);

        assertEquals(1, lines.size());
        assertEquals("abc...", lines.get(0));
        assertTrue(SPLITTER.stringWidth(lines.get(0)) <= 6);
    }

    @Test
    public void truncatedCutsWithoutSuffix() {
        List<String> lines = fold("abcdefghij", 6, StringWidget.TextOverflow.TRUNCATED);

        assertEquals(1, lines.size());
        assertEquals("abcdef", lines.get(0));
    }

    @Test
    public void clampedHardCutsWhenTheEllipsisDoesNotFit() {
        // The limit (2) is narrower than the ellipsis (3), so a suffix could not be drawn inside
        // it: the text is cut instead of overflowing the width the widget reports.
        List<String> lines = fold("abcdefghij", 2, StringWidget.TextOverflow.CLAMPED);

        assertEquals(1, lines.size());
        assertEquals("ab", lines.get(0));
        assertTrue(SPLITTER.stringWidth(lines.get(0)) <= 2);
    }

    @Test
    public void clampedHardCutsWhenTheEllipsisOnlyJustFits() {
        // Limit == ellipsis width: the suffix would fit but leave no room for a glyph, so the cut
        // text is kept whole rather than being replaced by a bare "...".
        assertEquals("abc", fold("abcdefghij", 3, StringWidget.TextOverflow.CLAMPED).get(0));
    }

    @Test
    public void clampFallsBackToBareEllipsisOnlyWhenItStillFits() {
        // Bold advances 2px per glyph, so a 1px budget lets no glyph through — yet "..." (3px)
        // still fits the 4px limit, which is the only case the bare ellipsis is allowed in.
        Text bold = Text.literal("abcdef").withStyle(Style.EMPTY.withBold(true));

        StringWidget.Layout layout = StringWidget.layout(bold, 4, StringWidget.TextOverflow.CLAMPED,
                ELLIPSIS_WIDTH, SPLITTER);

        assertEquals("...", layout.lines.get(0));
        assertTrue(SPLITTER.stringWidth(layout.lines.get(0)) <= 4);
    }

    @Test
    public void wrapBreaksHardWhenNoSpaceIsAvailable() {
        List<String> lines = fold("abcdefghij", 6, StringWidget.TextOverflow.WRAP);

        assertEquals(2, lines.size());
        assertEquals("abcdef", lines.get(0));
        assertEquals("ghij", lines.get(1));
    }

    @Test
    public void wrapPrefersWordBoundaries() {
        // The space that overflows is itself the break point, so the line keeps "aa bb"
        // (width 5) and the space it broke at is dropped instead of being drawn.
        List<String> lines = fold("aa bb cc", 5, StringWidget.TextOverflow.WRAP);

        assertEquals(2, lines.size());
        assertEquals("aa bb", lines.get(0));
        assertEquals("cc", lines.get(1));
    }

    @Test
    public void wrapNeverExceedsTheLimit() {
        List<String> lines = fold("the quick brown fox jumps", 7, StringWidget.TextOverflow.WRAP);

        assertTrue(lines.size() > 1);
        for (String line : lines) {
            assertTrue(line, SPLITTER.stringWidth(line) <= 7);
        }
    }

    @Test
    public void emptyTextYieldsNoLines() {
        for (StringWidget.TextOverflow mode : StringWidget.TextOverflow.values()) {
            List<String> lines = StringWidget.fold("", 5, 0, ELLIPSIS_WIDTH, mode, SPLITTER);
            assertTrue(mode.name(), lines.isEmpty());
        }
    }

    @Test
    public void emptyTextReportsNoLinesInEitherWidthMode() {
        for (StringWidget.TextOverflow mode : StringWidget.TextOverflow.values()) {
            assertTrue(mode.name(), StringWidget.layout(Text.literal(""), 5, mode,
                    ELLIPSIS_WIDTH, SPLITTER).lines.isEmpty());
            assertTrue(mode.name(), StringWidget.layout(Text.literal(""), 0, mode,
                    ELLIPSIS_WIDTH, SPLITTER).lines.isEmpty());
        }
    }

    @Test
    public void emptyMessageAgreesWithTheMultiLineWidget() {
        // Both widgets report zero lines for an empty message, so a WRAP StringWidget and a
        // MultiLineTextWidget no longer disagree on height (9 vs 0) in the same layout.
        for (int maxWidth : new int[] {0, 5}) {
            assertTrue("maxWidth=" + maxWidth,
                    StringWidget.layout(Text.literal(""), maxWidth, StringWidget.TextOverflow.WRAP,
                            ELLIPSIS_WIDTH, SPLITTER).lines.isEmpty());
            assertTrue("maxWidth=" + maxWidth,
                    MultiLineTextWidget.layout(Text.literal(""), maxWidth, 0,
                            ELLIPSIS_WIDTH, SPLITTER).lines.isEmpty());
        }
    }

    @Test
    public void nullModeFallsBackToClamped() {
        assertEquals("abc...", StringWidget.fold("abcdefghij", 6, 10, ELLIPSIS_WIDTH, null, SPLITTER).get(0));
    }

    @Test
    public void scrollingKeepsTheWholeTextBecauseDrawingClipsIt() {
        List<String> lines = fold("abcdefghij", 6, StringWidget.TextOverflow.SCROLLING);

        assertEquals(1, lines.size());
        assertEquals("abcdefghij", lines.get(0));
    }

    @Test
    public void scissorBoxCoversTheShadowRowBelowTheLine() {
        int[] box = StringWidget.scissorBox(10, 20, 60, 29, 2, 400);

        assertEquals(20, box[0]);
        // one extra pixel below the bottom edge so the shadow drawn at y + 1 is not shaved
        assertEquals(400 - (29 + 1) * 2, box[1]);
        assertEquals((60 - 10) * 2, box[2]);
        assertEquals((29 - 20 + 1) * 2, box[3]);
        // the top edge stays where the widget's top is: y + height == displayHeight - top * scale
        assertEquals(400 - 20 * 2, box[1] + box[3]);
    }

    @Test
    public void scrollingOffsetStartsAtTheEndOfTravel() {
        // period = max(0.5 * 8, 3) = 4s; at t = 0 the eased sine peaks at alpha = 1
        assertEquals(8, StringWidget.scrollingOffset(8, 0L));
    }

    @Test
    public void scrollingOffsetReturnsToZeroAtHalfPeriod() {
        assertEquals(0, StringWidget.scrollingOffset(8, 2000L));
        // the 3s floor applies below maxPosition = 6
        assertEquals(0, StringWidget.scrollingOffset(2, 1500L));
    }

    @Test
    public void scrollingFallbackCutsToTheReportedWidth() {
        // A limit of 6 leaves at most 2px between the insets, so scrolling degrades to a cut
        // instead of drawing nothing.
        assertEquals("abcdef", StringWidget.scrollingFallback("abcdefghij", 6, SPLITTER));
        assertEquals(2, Math.round(SPLITTER.stringWidth(
                StringWidget.scrollingFallback("abcdefghij", 2, SPLITTER))));
        assertEquals("", StringWidget.scrollingFallback(null, 6, SPLITTER));
    }

    @Test
    public void scrollingOffsetNeverLeavesTheTravelDistance() {
        int maxPosition = 11;
        for (long millis = 0L; millis <= 20000L; millis += 37L) {
            int offset = StringWidget.scrollingOffset(maxPosition, millis);
            assertTrue("t=" + millis + " offset=" + offset, offset >= 0 && offset <= maxPosition);
        }
        assertEquals(0, StringWidget.scrollingOffset(0, 1234L));
        assertEquals(0, StringWidget.scrollingOffset(-5, 1234L));
    }

    // ──── Style-aware path: layout() works on the legacy § form ────

    @Test
    public void layoutMeasuresAndDrawsTheLegacyFormSoStylesReachTheRenderer() {
        Text bold = Text.literal("abc").withStyle(Style.EMPTY.withBold(true));

        StringWidget.Layout layout = StringWidget.layout(bold, 0, StringWidget.TextOverflow.CLAMPED,
                ELLIPSIS_WIDTH, SPLITTER);

        assertEquals("§labc", layout.lines.get(0));
        // bold advances 2px per glyph under the synthetic provider
        assertEquals(6, layout.textWidth);
        assertEquals(6, layout.width);
    }

    @Test
    public void wrapLinesCarryTheCodeInEffectAtTheirStart() {
        StringWidget.Layout layout = StringWidget.layout(Text.literal("§caa bb cc"), 5,
                StringWidget.TextOverflow.WRAP, ELLIPSIS_WIDTH, SPLITTER);

        assertEquals(2, layout.lines.size());
        assertEquals("§caa bb", layout.lines.get(0));
        assertEquals("§ccc", layout.lines.get(1));
        // the codes cost no width: 8 visible glyphs, reported width clamped to the limit
        assertEquals(8, layout.textWidth);
        assertEquals(5, layout.width);
    }

    @Test
    public void clampedAppendsTheSuffixAfterTheSurvivingCodes() {
        StringWidget.Layout layout = StringWidget.layout(Text.literal("§cabcdefghij"), 6,
                StringWidget.TextOverflow.CLAMPED, ELLIPSIS_WIDTH, SPLITTER);

        assertEquals(1, layout.lines.size());
        assertEquals("§cabc...", layout.lines.get(0));
        assertFalse(layout.lines.get(0).endsWith("§"));
    }

    @Test
    public void scrollingKeepsTheStyledTextWhole() {
        StringWidget.Layout layout = StringWidget.layout(Text.literal("§caa bb cc"), 5,
                StringWidget.TextOverflow.SCROLLING, ELLIPSIS_WIDTH, SPLITTER);

        assertEquals(1, layout.lines.size());
        assertEquals("§caa bb cc", layout.lines.get(0));
        assertEquals(8, layout.textWidth);
        assertEquals(5, layout.width);
    }

    @Test
    public void clippingNeverLeavesADanglingCodePrefix() {
        String text = "§cab§lcd§refgh";

        for (int limit = 1; limit <= 12; limit++) {
            for (StringWidget.TextOverflow mode : StringWidget.TextOverflow.values()) {
                StringWidget.Layout layout = StringWidget.layout(Text.literal(text), limit, mode,
                        ELLIPSIS_WIDTH, SPLITTER);
                assertFalse(mode + "@" + limit, layout.lines.isEmpty());
                for (String line : layout.lines) {
                    assertFalse(mode + "@" + limit + " -> " + line, line.endsWith("§"));
                }
            }
        }
    }

    @Test
    public void reportedWidthMatchesLayoutForStyledText() {
        Text bold = Text.literal("aa").withStyle(Style.EMPTY.withBold(true));

        assertEquals(4, StringWidget.formattedWidth(bold, SPLITTER));
        assertEquals(4, StringWidget.layout(bold, 0, StringWidget.TextOverflow.CLAMPED,
                ELLIPSIS_WIDTH, SPLITTER).textWidth);
    }

    @Test
    public void reportedWidthAndLayoutAgreeOnNonBmpCharacters() {
        // An astral code point: the font counts both surrogate halves while the splitter
        // counts neither, so this is where the two metrologies used to disagree.
        StringSplitter nonBmpAware = new StringSplitter(
                (codepoint, style) -> Character.isBmpCodePoint(codepoint) ? 1.0F : 0.0F);
        Text message = Text.literal("a\uD835\uDD1Eb");

        int reported = StringWidget.formattedWidth(message, nonBmpAware);
        StringWidget.Layout layout = StringWidget.layout(message, 0,
                StringWidget.TextOverflow.CLAMPED, ELLIPSIS_WIDTH, nonBmpAware);

        assertEquals(2, reported);
        assertEquals(reported, layout.textWidth);
        assertEquals(reported, layout.width);
    }
}
