package decok.dfcdvadstf.catframe.model.state.property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An integer {@link MetaBlockProperty} whose value occupies a metadata bit field defined by a
 * {@code mask}/{@code shift} pair (aligned with GTNHLib's
 * {@code IntegerBlockProperty.meta(name, mask, shift)}).
 *
 * <p>The raw field is read as {@code (meta & mask) >> shift} and mapped to the value domain by
 * adding {@code min}; the result is clamped into {@code [min, max]} so every meta in 0..15 is safe.
 * The field is written back as {@code ((value - min) << shift) & mask}, preserving other bits.
 *
 * <p>Typical cases: crop {@code age} ({@code min=0,max=7,mask=0xF,shift=0}),
 * repeater {@code delay} ({@code min=1,max=4,mask=0xC,shift=2}).
 */
public final class IntegerRangeProperty extends MetaBlockProperty<Integer> {

    private final int min;
    private final int max;
    private final int mask;
    private final int shift;

    private IntegerRangeProperty(String name, int min, int max, int mask, int shift) {
        super(name, Integer.class, values(min, max));
        this.min = min;
        this.max = max;
        this.mask = mask;
        this.shift = shift;
    }

    /**
     * Creates a meta-backed integer property.
     *
     * @param name  the property name (e.g. "age", "level")
     * @param min   the minimum value (inclusive)
     * @param max   the maximum value (inclusive)
     * @param mask  the metadata bit mask selecting this property's field
     * @param shift the number of low bits the field is shifted by
     */
    public static IntegerRangeProperty meta(String name, int min, int max, int mask, int shift) {
        if (min > max) {
            throw new IllegalArgumentException("min > max for IntegerRangeProperty '" + name + "'");
        }
        return new IntegerRangeProperty(name, min, max, mask, shift);
    }

    @Override
    public Integer decode(int meta) {
        int raw = (meta & mask) >> shift;
        int v = raw + min;
        if (v < min) v = min;
        if (v > max) v = max;
        return v;
    }

    @Override
    public int getMeta(Integer value, int existing) {
        int v = value;
        if (v < min) v = min;
        if (v > max) v = max;
        int raw = v - min;
        return (existing & ~mask) | ((raw << shift) & mask);
    }

    @Override
    public boolean needsExisting() {
        // A full-nibble field owns the entire meta, so the existing value need not be read.
        return (mask & 0xF) != 0xF;
    }

    @Override
    public String getName(Integer value) {
        return value.toString();
    }

    @Override
    public Integer getValueByName(String valueName) {
        if (valueName == null) return null;
        try {
            int v = Integer.parseInt(valueName);
            return (v >= min && v <= max) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Integer> values(int min, int max) {
        List<Integer> vals = new ArrayList<Integer>();
        for (int i = min; i <= max; i++) {
            vals.add(i);
        }
        return Collections.unmodifiableList(vals);
    }
}
