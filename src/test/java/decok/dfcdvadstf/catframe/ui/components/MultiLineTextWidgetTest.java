package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.StringSplitter;
import decok.dfcdvadstf.catframe.ui.Style;
import decok.dfcdvadstf.catframe.ui.Text;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link MultiLineTextWidget}'s folding, truncation and width reporting through the
 * pure {@link MultiLineTextWidget#layout} helper, so no {@code FontRenderer} or Minecraft
 * runtime is needed. Every glyph advances 1px (2px while bold), mirroring the other
 * splitter tests.
 */
public class MultiLineTextWidgetTest {

    private static final StringSplitter SPLITTER = new StringSplitter(
            (codepoint, style) -> style.isBold() ? 2.0F : 1.0F);

    /** Width of the three-period ellipsis under the synthetic provider. */
    private static final int ELLIPSIS_WIDTH = 3;

    private static MultiLineTextWidget.Layout layout(String text, int maxWidth, int maxRows) {
        return MultiLineTextWidget.layout(Text.literal(text), maxWidth, maxRows, ELLIPSIS_WIDTH, SPLITTER);
    }

    @Test
    public void unlimitedWidthKeepsASingleLine() {
        MultiLineTextWidget.Layout layout = layout("aa bb", 0, 0);

        assertEquals(1, layout.lines.size());
        assertEquals("aa bb", layout.lines.get(0));
        assertEquals(5, layout.width);
    }

    @Test
    public void foldsEveryLineToTheWidth() {
        MultiLineTextWidget.Layout layout = layout("aaaa bbbb", 4, 0);

        assertEquals(2, layout.lines.size());
        assertEquals("aaaa", layout.lines.get(0));
        assertEquals("bbbb", layout.lines.get(1));
        assertEquals(4, layout.width);
    }

    @Test
    public void noDrawnLineExceedsTheWidth() {
        MultiLineTextWidget.Layout layout = layout("the quick brown fox jumps over", 7, 0);

        assertTrue(layout.lines.size() > 1);
        int widest = 0;
        for (String line : layout.lines) {
            int width = Math.round(SPLITTER.stringWidth(line));
            assertTrue(line, width <= 7);
            widest = Math.max(widest, width);
        }
        // the reported width is the widest folded line, not the limit itself
        assertEquals(widest, layout.width);
    }

    @Test
    public void maxRowsDropsTheTailAndEllipsizesTheLastLine() {
        MultiLineTextWidget.Layout layout = layout("aa bb cc dd ee", 5, 2);

        assertEquals(2, layout.lines.size());
        assertEquals("aa bb", layout.lines.get(0));
        // "cc dd" is exactly 5 wide, so only the last 3 pixels go to make room for "..."
        assertEquals("cc...", layout.lines.get(1));
        assertEquals(5, layout.width);
    }

    @Test
    public void truncationKeepsTheStyleCodesOfTheLine() {
        MultiLineTextWidget.Layout layout = layout("§cbb cc dd", 5, 1);

        assertEquals(1, layout.lines.size());
        assertEquals("§cbb...", layout.lines.get(0));
    }

    @Test
    public void aRowLimitAtOrAboveTheLineCountChangesNothing() {
        assertEquals(2, layout("aaaa bbbb", 4, 2).lines.size());
        assertEquals(2, layout("aaaa bbbb", 4, 5).lines.size());
        assertEquals("bbbb", layout("aaaa bbbb", 4, 5).lines.get(1));
    }

    @Test
    public void emptyMessageDrawsNothing() {
        MultiLineTextWidget.Layout layout = layout("", 10, 0);

        assertTrue(layout.lines.isEmpty());
        assertEquals(0, layout.width);
    }

    @Test
    public void styledTextIsMeasuredInItsLegacyForm() {
        Text bold = Text.literal("aa").withStyle(Style.EMPTY.withBold(true));

        MultiLineTextWidget.Layout layout = MultiLineTextWidget.layout(bold, 0, 0, ELLIPSIS_WIDTH, SPLITTER);

        assertEquals("§laa", layout.lines.get(0));
        assertEquals(4, layout.width);
    }

    @Test
    public void wrappedLinesCarryTheCodeInEffectAtTheirStart() {
        List<String> lines = layout("§caa bb cc", 5, 0).lines;

        assertEquals(2, lines.size());
        assertEquals("§caa bb", lines.get(0));
        assertEquals("§ccc", lines.get(1));
    }
}
