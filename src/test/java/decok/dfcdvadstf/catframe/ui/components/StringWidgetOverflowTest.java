package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.StringSplitter;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
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
    public void clampFallsBackToBareEllipsisWhenNothingElseFits() {
        assertEquals("...", fold("abcdefghij", 2, StringWidget.TextOverflow.CLAMPED).get(0));
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
    public void emptyTextYieldsOneEmptyLine() {
        for (StringWidget.TextOverflow mode : StringWidget.TextOverflow.values()) {
            List<String> lines = StringWidget.fold("", 5, 0, ELLIPSIS_WIDTH, mode, SPLITTER);
            assertEquals(mode.name(), 1, lines.size());
            assertEquals(mode.name(), "", lines.get(0));
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
    public void scrollingOffsetNeverLeavesTheTravelDistance() {
        int maxPosition = 11;
        for (long millis = 0L; millis <= 20000L; millis += 37L) {
            int offset = StringWidget.scrollingOffset(maxPosition, millis);
            assertTrue("t=" + millis + " offset=" + offset, offset >= 0 && offset <= maxPosition);
        }
        assertEquals(0, StringWidget.scrollingOffset(0, 1234L));
        assertEquals(0, StringWidget.scrollingOffset(-5, 1234L));
    }
}
