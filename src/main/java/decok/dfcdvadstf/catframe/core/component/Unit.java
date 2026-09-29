package decok.dfcdvadstf.catframe.core.component;

/**
 * Marker value — used for components that only need a presence/absence flag.
 * <p>
 * Similar to 26.1.2's {@code net.minecraft.core.component.DataComponentType.Unit}.
 * Uses a boolean to represent presence, equivalent to {@link Boolean#TRUE}.
 */
public final class Unit {

    public static final Unit INSTANCE = new Unit();

    private Unit() {}

    @Override
    public String toString() {
        return "Unit";
    }

    /**
     * Parse a boolean to Unit.
     */
    public static Unit of(boolean present) {
        return present ? INSTANCE : null;
    }
}
