package decok.dfcdvadstf.catframe.model.state.property;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A boolean {@link MetaBlockProperty} backed by a single metadata bit mask (aligned with
 * GTNHLib's {@code BooleanBlockProperty.flag(name, flag)}).
 *
 * <p>The value is {@code true} when every bit in {@code mask} is set in the meta
 * ({@code (meta & mask) == mask}), and {@code false} otherwise. Typical vanilla cases:
 * a slab {@code half} bit, {@code powered}/{@code open} flags.
 */
public final class BooleanFlagProperty extends MetaBlockProperty<Boolean> {

    /** Value order matches {@link BooleanProperty}: {@code true} then {@code false}. */
    private static final List<Boolean> VALUES = Collections.unmodifiableList(Arrays.asList(Boolean.TRUE, Boolean.FALSE));

    private final int mask;

    private BooleanFlagProperty(String name, int mask) {
        super(name, Boolean.class, VALUES);
        this.mask = mask;
    }

    /**
     * Creates a boolean property reading the given metadata bit mask.
     *
     * @param name the property name (e.g. "powered", "open")
     * @param mask the bit mask selecting this property's flag (e.g. {@code 0x8})
     */
    public static BooleanFlagProperty flag(String name, int mask) {
        return new BooleanFlagProperty(name, mask);
    }

    @Override
    public Boolean decode(int meta) {
        return (meta & mask) == mask ? Boolean.TRUE : Boolean.FALSE;
    }

    @Override
    public int getMeta(Boolean value, int existing) {
        int m = existing & ~mask;
        if (value) m |= mask;
        return m;
    }

    @Override
    public String getName(Boolean value) {
        return value.toString();
    }

    @Override
    public Boolean getValueByName(String valueName) {
        if ("true".equals(valueName)) return Boolean.TRUE;
        if ("false".equals(valueName)) return Boolean.FALSE;
        return null;
    }
}
