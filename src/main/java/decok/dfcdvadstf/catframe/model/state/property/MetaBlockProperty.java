package decok.dfcdvadstf.catframe.model.state.property;

import java.util.List;

/**
 * A {@link Property} whose value is derived solely from block metadata, aligned with
 * GTNHLib's {@code MetaBlockProperty}.
 *
 * <p>This is the seam the vanilla adapter used to express as a single positional
 * {@code MetaCodec} lambda over the whole definition. Here the byte mapping is pushed down
 * into each property: a property that implements this interface owns the encoding/decoding
 * of the metadata bit field(s) it occupies, so a mod developer exposes the same low-level
 * mapping simply by implementing (or declaring) a property — no positional array lambda and
 * no editing of internal tables.
 *
 * <p>{@link #decode(int)} reads this property's value out of a raw meta value, touching only
 * its own bit field. {@link #getMeta(Comparable, int)} writes this property's value back into
 * an existing meta, preserving the bits that belong to other properties (see
 * {@link #needsExisting()}).
 *
 * @param <T> the value type
 */
public abstract class MetaBlockProperty<T extends Comparable<T>> extends Property<T> {

    protected MetaBlockProperty(String name, Class<T> valueClass, List<T> values) {
        super(name, valueClass, values);
    }

    /**
     * Decodes this property's value from the given raw block metadata (0-15). Implementations
     * must be safe for every value in 0..15 (the definition pre-fills a resident table over the
     * full nibble), typically by clamping or wrapping into the value domain.
     *
     * @param meta the raw block metadata
     * @return the decoded property value
     */
    public abstract T decode(int meta);

    /**
     * Encodes this property's value into the metadata, given the existing meta so that other
     * properties' bits are preserved. When {@link #needsExisting()} is {@code false} the
     * {@code existing} argument may be ignored.
     *
     * @param value    the value to encode
     * @param existing the existing metadata (may be 0 when {@link #needsExisting()} is false)
     * @return the combined metadata
     */
    public abstract int getMeta(T value, int existing);

    /**
     * Whether {@link #getMeta(Comparable, int)} must read the existing meta to preserve the bit
     * fields of other properties. Defaults to {@code true}; a property occupying the whole nibble
     * may return {@code false}.
     */
    public boolean needsExisting() {
        return true;
    }
}
