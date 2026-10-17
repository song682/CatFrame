package decok.dfcdvadstf.catframe.model.state.property;

import java.util.Arrays;

/**
 * Commonly used blockstate property constants and convenient construction utilities.
 * <p>
 * For reuse by vanilla and external mods to define type-safe properties. Property values are
 * always {@link String} so that their {@code toString()} exactly matches the variant key values
 * in blockstate JSON (avoiding the default uppercase enum {@code toString} breaking matching).
 */
public final class StateDefinitions {

    private StateDefinitions() {}

    // ==================== Convenient construction ====================

    /**
     * Creates a string-valued property. The value's {@code toString()} is the string itself,
     * directly aligned with the variant key values in blockstate JSON.
     *
     * @param name   the property name
     * @param values the values (order determines the internal index and natural meta encoding)
     * @return the string property
     */
    public static Property<String> stringProp(String name, String... values) {
        return Property.create(name, String.class, Arrays.asList(values));
    }

    // ==================== Shared property constants ====================

    /** Minecraft 1.7.10 wool/dye 16-color order. */
    public static final String[] COLORS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black"
    };

    /** 16-color property (wool / carpet / stained_glass / stained_hardened_clay). */
    public static final Property<String> COLOR = stringProp("color", COLORS);

    /** Log/quartz pillar axis. */
    public static final Property<String> AXIS = stringProp("axis", "y", "x", "z");

    /** Slab half. */
    public static final Property<String> SLAB_HALF = stringProp("half", "bottom", "top");
}
