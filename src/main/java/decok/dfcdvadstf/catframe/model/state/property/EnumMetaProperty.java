package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.core.StringRepresentable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * An enum-valued {@link MetaBlockProperty} (aligned with GTNHLib's enum/direction/orientation
 * properties), where each enum constant implements {@link StringRepresentable} so its serialized
 * name is used as the blockstate variant-key value.
 *
 * <p>Two construction modes cover the whole vanilla space:
 * <ul>
 *   <li><b>Field mode</b> ({@link #create}): the metadata field {@code (meta & mask) >> shift}
 *       indexes the (ordered) value list, with a wrap or clamp policy — e.g. a {@code facing}
 *       whose meta bits map directly onto an ordered direction set.</li>
 *   <li><b>Table mode</b> ({@link #withTable}): an explicit {@code raw -> value} table expresses
 *       the non-linear vanilla switch mappings (torch/lever/ladder/dispenser/hopper) where a raw
 *       code such as {@code 5} or {@code 6} may collapse onto the same facing.</li>
 * </ul>
 *
 * @param <T> the enum value type
 */
public final class EnumMetaProperty<T extends Enum<T> & StringRepresentable> extends MetaBlockProperty<T> {

    private final int mask;
    private final int shift;
    private final boolean wrap;
    /** Non-null in table mode: maps a raw field value to the enum constant. */
    private final T[] table;
    private final T fallback;

    private EnumMetaProperty(String name, Class<T> clazz, List<T> values, int mask, int shift,
                             boolean wrap, T[] table, T fallback) {
        super(name, clazz, values);
        this.mask = mask;
        this.shift = shift;
        this.wrap = wrap;
        this.table = table;
        this.fallback = fallback;
    }

    /**
     * Field-mode constructor: the ordered value list is indexed by the metadata bit field.
     *
     * @param wrap when true the field index wraps by modulo, when false it clamps to the last value
     */
    public static <T extends Enum<T> & StringRepresentable> EnumMetaProperty<T> create(
            String name, Class<T> clazz, List<T> values, int mask, int shift, boolean wrap) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("EnumMetaProperty '" + name + "' needs at least one value");
        }
        List<T> copy = Collections.unmodifiableList(new ArrayList<T>(values));
        return new EnumMetaProperty<T>(name, clazz, copy, mask, shift, wrap, null, copy.get(0));
    }

    /**
     * Table-mode constructor for non-linear switch mappings. {@code valueByRaw[i]} is the constant
     * for raw field value {@code i}; {@code null} entries fall back to {@code fallback}. The value
     * domain is the distinct set of constants present in the table (plus the fallback).
     *
     * @param fallback the constant used when the raw field is out of range or maps to null
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T extends Enum<T> & StringRepresentable> EnumMetaProperty<T> withTable(
            String name, int mask, int shift, T fallback, T... valueByRaw) {
        if (valueByRaw == null || valueByRaw.length == 0) {
            throw new IllegalArgumentException("EnumMetaProperty '" + name + "' needs a non-empty table");
        }
        LinkedHashSet<T> distinct = new LinkedHashSet<T>();
        for (T v : valueByRaw) {
            if (v != null) distinct.add(v);
        }
        distinct.add(fallback);
        List<T> values = Collections.unmodifiableList(new ArrayList<T>(distinct));
        @SuppressWarnings("unchecked")
        T[] table = valueByRaw.clone();
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) fallback.getClass();
        return new EnumMetaProperty<T>(name, clazz, values, mask, shift, false, table, fallback);
    }

    @Override
    public T decode(int meta) {
        int raw = (meta & mask) >> shift;
        if (table != null) {
            if (raw >= 0 && raw < table.length && table[raw] != null) {
                return table[raw];
            }
            return fallback;
        }
        List<T> vals = getValues();
        int size = vals.size();
        int idx = wrap ? ((raw % size) + size) % size : Math.min(Math.max(raw, 0), size - 1);
        return vals.get(idx);
    }

    @Override
    public int getMeta(T value, int existing) {
        int raw;
        if (table != null) {
            raw = 0;
            for (int i = 0; i < table.length; i++) {
                if (table[i] == value) {
                    raw = i;
                    break;
                }
            }
        } else {
            raw = getInternalIndex(value);
        }
        return (existing & ~mask) | ((raw << shift) & mask);
    }

    @Override
    public boolean needsExisting() {
        return (mask & 0xF) != 0xF;
    }

    @Override
    public String getName(T value) {
        return value.getSerializedName();
    }

    @Override
    public T getValueByName(String valueName) {
        if (valueName == null) return null;
        for (T value : getValues()) {
            if (value.getSerializedName().equals(valueName)) {
                return value;
            }
        }
        return null;
    }
}
