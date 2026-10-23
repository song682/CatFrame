package decok.dfcdvadstf.catframe.model.state.property;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Boolean property. The value domain is {@code true} / {@code false}.
 *
 * <p>Internal index: {@code true → 0, false → 1}.
 *
 * <p>Usage:
 * <pre>{@code
 * public static final Property<Boolean> LIT = BooleanProperty.create("lit");
 * }</pre>
 */
public final class BooleanProperty extends Property<Boolean> {

    private static final List<Boolean> VALUES = Collections.unmodifiableList(Arrays.asList(true, false));

    private BooleanProperty(String name) {
        super(name, Boolean.class, VALUES);
    }

    @Override
    public int getInternalIndex(Boolean value) {
        return value ? 0 : 1;
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

    /**
     * Creates a boolean property.
     *
     * @param name the property name (e.g. "lit", "waterlogged")
     * @return a new BooleanProperty instance
     */
    public static BooleanProperty create(String name) {
        return new BooleanProperty(name);
    }
}
