package decok.dfcdvadstf.catframe.ui;

import org.junit.Test;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Covers the template decomposition rules of {@link Text}: which specifiers stay
 * splittable (bare {@code %s}, indexed {@code %N$s}, {@code %%}) and which ones force
 * the whole template onto the flat {@link String#format} path (flags, width,
 * precision, {@code t}/{@code T} prefixes, other conversions, failures).
 * <p>
 * These methods deliberately exercise the package-private
 * {@link Text#decomposeTemplate(String, Object[])} / {@link Text#formatFlat(String, Object[])}
 * pair instead of building a {@code Text} instance, so no {@code StatCollector}
 * lookup or language table is involved.
 * </p>
 */
public class TextTemplateTest {

    // ──── helpers ────

    private static List<Object> split(String template, Object... args) {
        return Text.decomposeTemplate(template, args);
    }

    /** Concatenates the parts, reading embedded components through their raw literal. */
    private static String join(List<Object> parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            sb.append(part instanceof Text ? ((Text) part).getKey() : part);
        }
        return sb.toString();
    }

    private static boolean hasComponent(List<Object> parts) {
        for (Object part : parts) {
            if (part instanceof Text) {
                return true;
            }
        }
        return false;
    }

    /** Asserts the template degraded to exactly one flat string and returns it. */
    private static String flatAssert(List<Object> parts) {
        assertEquals("expected a single flat segment", 1, parts.size());
        assertTrue("flat segment must be a String", parts.get(0) instanceof String);
        return (String) parts.get(0);
    }

    // ──── A: baseline, splitting must not change ────

    @Test
    public void bareStringSpecifierStillSplits() {
        assertEquals("Hello X", join(split("Hello %s", "X")));
        assertEquals("X and Y", join(split("%s and %s", "X", "Y")));
    }

    @Test
    public void textArgumentStaysAComponent() {
        Text arg = Text.literal("X");
        List<Object> parts = split("%s", arg);
        assertEquals(1, parts.size());
        assertSame("a bare %s must keep the Text argument as its own segment", arg, parts.get(0));
    }

    @Test
    public void indexedAndSequentialArguments() {
        assertEquals("X", join(split("%1$s", "X")));
        assertEquals("Y then X", join(split("%2$s then %1$s", "X", "Y")));
    }

    @Test
    public void literalPercent() {
        assertEquals("100% done", join(split("100%% done")));
        assertEquals("X%", join(split("%s%%", "X")));
    }

    @Test
    public void templateWithoutSpecifiers() {
        assertEquals("no placeholder", join(split("no placeholder", "ignored")));
    }

    // ──── B: the defect being fixed — flags, width and precision ────

    @Test
    public void widthIsAppliedInsteadOfLeaked() {
        assertEquals("    X|", flatAssert(split("%5s|", "X")));
        assertEquals("X    |", flatAssert(split("%-5s|", "X")));
        assertEquals(" 1.23", flatAssert(split("%1$5.2f", 1.2345)));
    }

    @Test
    public void zeroPadAndGrouping() {
        assertEquals("07", flatAssert(split("%02d", 7)));
        assertEquals("1,234,567", flatAssert(split("%,d", 1234567)));
    }

    @Test
    public void precisionOnString() {
        assertEquals("abc", flatAssert(split("%.3s", "abcdef")));
    }

    @Test
    public void precisionAndEscapedPercentInOneTemplate() {
        // Regression: %% used to be replaced while %.1f leaked, producing a half-substituted string.
        assertEquals("进度 12.3%", flatAssert(split("进度 %.1f%%", 12.34)));
    }

    @Test
    public void widthNeverDegradesToBarePercentS() {
        Text arg = Text.literal("X");
        List<Object> parts = split("%5s", arg);
        assertFalse("a width specifier must not stay segmentable", hasComponent(parts));
        assertEquals("    X", flatAssert(parts));
    }

    // ──── C: other conversions, correct output but flat ────

    @Test
    public void nonStringConversionsUseFormatSemantics() {
        assertEquals("5 items", flatAssert(split("%d items", 5)));
        assertEquals("ABC", flatAssert(split("%S", "abc")));
        assertEquals("ff", flatAssert(split("%x", 255)));
        assertEquals("true", flatAssert(split("%b", true)));
        assertEquals("A", flatAssert(split("%c", 'A')));
    }

    @Test
    public void newlineConversion() {
        assertEquals(System.lineSeparator(), flatAssert(split("%n")));
    }

    @Test
    public void datePrefixConversion() {
        assertEquals("1970", flatAssert(split("%tY", new Date(0L))));
    }

    // ──── D: prose percent signs must not be mistaken for specifiers ────

    @Test
    public void prosePercentIsNotASpecifier() {
        assertEquals("50% off", join(split("50% off", "X")));
        assertEquals("100% sure", join(split("100% sure", "X")));
        assertEquals("A % B", join(split("A % B", "X")));
    }

    @Test
    public void prosePercentPlusRealSpecifier() {
        Text arg = Text.literal("X");
        List<Object> parts = split("50% off %s", arg);
        assertTrue("the real %s must still be split", hasComponent(parts));
        assertEquals("50% off X", join(parts));
    }

    @Test
    public void trailingPercentStaysFormatError() {
        assertEquals("Format error: 50%", flatAssert(split("50%", "X")));
    }

    // ──── E: failure paths degrade to the vanilla prefix, never throw ────

    @Test
    public void failuresDegradeToFormatError() {
        assertEquals("Format error: %s %s", flatAssert(split("%s %s", "X")));
        assertEquals("Format error: %5$s", flatAssert(split("%5$s", "a", "b")));
        assertEquals("Format error: %-08.2f", flatAssert(split("%-08.2f", 1.2345)));
        assertEquals("Format error: %08s", flatAssert(split("%08s", "ab")));
        assertEquals("Format error: %0d", flatAssert(split("%0d", 5)));
    }

    // ──── F: index/sequential mixing must match String.format ────

    @Test
    public void mixedIndexAndSequentialMatchesStringFormat() {
        assertEquals(String.format("%1$s %s", "X", "Y"), join(split("%1$s %s", "X", "Y")));
        assertEquals(String.format("%s %1$s", "X", "Y"), join(split("%s %1$s", "X", "Y")));
    }

    // ──── G: documented trade-off of the 1a rule ────

    @Test
    public void styledComponentSurvivesThroughPlainSpecifier() {
        Text styled = Text.literal("styled", Style.EMPTY.withColor(0xFFFF5555));
        List<Object> parts = split("[%s]", styled);
        assertTrue(hasComponent(parts));
        assertNotNull(((Text) parts.get(1)).getStyle());
        assertEquals("[styled]", join(parts));
    }

    @Test
    public void decoratorSpecifierDropsComponentSegmentation() {
        // Intentional 1a behaviour: one unsplittable specifier flattens the whole template,
        // so the styled argument loses its segment. Change this test if 1b is ever implemented.
        Text styled = Text.literal("styled", Style.EMPTY.withColor(0xFFFF5555));
        List<Object> parts = split("%s %.2f", styled, 1.5);
        assertFalse(hasComponent(parts));
        assertEquals("styled 1.50", flatAssert(parts));
    }

    // ──── H: boundaries ────

    @Test
    public void emptyTemplate() {
        assertEquals("", join(split("", "X")));
    }

    @Test
    public void lonePercent() {
        assertEquals("Format error: %", flatAssert(split("%", "X")));
    }

    @Test
    public void extraArgumentsAreIgnored() {
        assertEquals("a X", join(split("a %s", "X", "Y", "Z")));
    }

    @Test
    public void missingArgumentOnEmptyArray() {
        assertEquals("Format error: %s", flatAssert(split("%s")));
    }
}
