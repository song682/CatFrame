package decok.dfcdvadstf.catframe.core;

/**
 * Contract for a value that has a stable serialized (string) form, aligned with modern
 * {@code net.minecraft.util.StringRepresentable}.
 *
 * <p>Enum-based state properties use this to derive the variant-key value string, so the
 * generated key exactly matches the blockstate JSON instead of relying on {@code Enum#toString()}
 * (which would yield the uppercase constant name).
 */
public interface StringRepresentable {

    /**
     * Returns the lowercase, JSON-facing name of this value (e.g. {@code "north"}, {@code "y"}).
     */
    String getSerializedName();
}
