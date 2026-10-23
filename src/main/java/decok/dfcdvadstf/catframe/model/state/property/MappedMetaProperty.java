package decok.dfcdvadstf.catframe.model.state.property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered-value {@link MetaBlockProperty} that maps its value list onto a metadata bit field
 * defined by {@code mask}/{@code shift} (aligned with GTNHLib's integer-to-string
 * {@code MappedBlockProperty}, generalized to any value type).
 *
 * <p>The field {@code raw = (meta & mask) >> shift} selects a value by index. Two out-of-range
 * policies cover the vanilla quirks:
 * <ul>
 *   <li>{@code wrap = true}: {@code idx = raw % size} — e.g. log {@code axis} ({@code (meta>>2)%3})
 *       where raw may be 3 but only 3 values exist.</li>
 *   <li>{@code wrap = false}: {@code idx = min(raw, size-1)} — e.g. sapling/planks variants where
 *       vanilla clamps ({@code min(meta&7, 5)}).</li>
 * </ul>
 *
 * <p>Values are typically {@link String} so their serialized form matches the blockstate variant
 * key; a custom {@code getName(value)} may be supplied for other value types.
 *
 * @param <V> the value type
 */
public final class MappedMetaProperty<V extends Comparable<V>> extends MetaBlockProperty<V> {

    private final int mask;
    private final int shift;
    private final boolean wrap;

    private MappedMetaProperty(String name, Class<V> valueClass, List<V> values, int mask, int shift, boolean wrap) {
        super(name, valueClass, values);
        this.mask = mask;
        this.shift = shift;
        this.wrap = wrap;
    }

    /**
     * Creates a mapped meta property.
     *
     * @param name   the property name (e.g. "color", "axis", "variant")
     * @param values the ordered value list; index 0 corresponds to field value 0
     * @param mask   the metadata bit mask selecting this property's field
     * @param shift  the number of low bits the field is shifted by
     * @param wrap   when true the field index wraps by modulo; when false it clamps to the last value
     */
    @SuppressWarnings("unchecked")
    public static <V extends Comparable<V>> MappedMetaProperty<V> of(String name, List<V> values,
                                                                      int mask, int shift, boolean wrap) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("MappedMetaProperty '" + name + "' needs at least one value");
        }
        List<V> copy = Collections.unmodifiableList(new ArrayList<V>(values));
        Class<V> clazz = (Class<V>) copy.get(0).getClass();
        return new MappedMetaProperty<V>(name, clazz, copy, mask, shift, wrap);
    }

    @Override
    public V decode(int meta) {
        int raw = (meta & mask) >> shift;
        List<V> vals = getValues();
        int size = vals.size();
        int idx;
        if (wrap) {
            idx = ((raw % size) + size) % size;
        } else {
            idx = raw < 0 ? 0 : Math.min(raw, size - 1);
        }
        return vals.get(idx);
    }

    @Override
    public int getMeta(V value, int existing) {
        int idx = getInternalIndex(value);
        return (existing & ~mask) | ((idx << shift) & mask);
    }

    @Override
    public boolean needsExisting() {
        return (mask & 0xF) != 0xF;
    }
}
