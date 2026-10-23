package decok.dfcdvadstf.catframe.model.state.property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link MetaBlockProperty} for any comparable value type whose meta&rarr;value mapping is a
 * non-linear lookup table rather than a contiguous ordinal range.
 *
 * <p>The byte field extracted by {@code (meta &amp; mask) >> shift} is used as a raw index into a
 * small table: each raw slot maps to a specific value, and any raw not present in the table (or
 * mapped to a {@code null} slot) resolves to a declared {@code fallback}. Unlike
 * {@link MappedMetaProperty} (which assumes values are laid out contiguously by their index in the
 * value list), this class covers the {@code meta==X ? A : meta==Y ? B : else C} shapes that vanilla
 * uses for {@code dirt}, {@code tallgrass} and {@code quartz_block}.</p>
 *
 * <p>Encoding reverses the table: {@code getMeta} returns the smallest raw slot whose decoded value
 * equals the requested value, so a value that lives at a non-ordinal table position (or is only
 * reachable through the fallback) still round-trips.</p>
 *
 * @param <V> the value type
 */
public final class TableMetaProperty<V extends Comparable<V>> extends MetaBlockProperty<V> {

    private final V[] valueByRaw;
    private final V fallback;
    private final int mask;
    private final int shift;

    @SafeVarargs
    private TableMetaProperty(String name, Class<V> valueClass, List<V> values, int mask, int shift, V fallback,
            V... valueByRaw) {
        super(name, valueClass, values);
        this.mask = mask;
        this.shift = shift;
        this.fallback = fallback;
        this.valueByRaw = valueByRaw; // entries may be null; a null raw slot resolves to fallback
    }

    /**
     * Creates a table-backed meta property.
     *
     * @param name       the property name (e.g. "variant", "type", "axis")
     * @param values     allowed values; used as the encode fallback ordinal and for state enumeration
     * @param mask       bit mask selecting the meta field
     * @param shift      right-shift applied after masking
     * @param fallback   value used for a raw not covered by {@code valueByRaw} (or a {@code null} entry)
     * @param valueByRaw value per raw index; {@code null} means "use fallback for this raw"
     */
    @SafeVarargs
    public static <V extends Comparable<V>> TableMetaProperty<V> of(String name, List<V> values, int mask, int shift,
            V fallback, V... valueByRaw) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("TableMetaProperty '" + name + "' needs at least one value");
        }
        List<V> copy = Collections.unmodifiableList(new ArrayList<V>(values));
        @SuppressWarnings("unchecked")
        Class<V> clazz = (Class<V>) copy.get(0).getClass();
        return new TableMetaProperty<V>(name, clazz, copy, mask, shift, fallback, valueByRaw);
    }

    @Override
    public V decode(int meta) {
        int raw = (meta & mask) >> shift;
        if (raw >= 0 && raw < valueByRaw.length && valueByRaw[raw] != null) {
            return valueByRaw[raw];
        }
        return fallback;
    }

    @Override
    public int getMeta(V value, int existing) {
        // Reverse the decode: return the smallest raw slot whose decoded value equals the request,
        // so a value at a non-ordinal table position (or reached only via the fallback) round-trips.
        for (int r = 0; r < valueByRaw.length; r++) {
            V decoded = valueByRaw[r] != null ? valueByRaw[r] : fallback;
            if (decoded != null && decoded.equals(value)) {
                return (existing & ~mask) | ((r << shift) & mask);
            }
        }
        return (existing & ~mask) | ((getInternalIndex(value) << shift) & mask);
    }

    @Override
    public boolean needsExisting() {
        return (mask & 0xF) != 0xF;
    }
}
