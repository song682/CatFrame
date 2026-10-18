package decok.dfcdvadstf.catframe.tags;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/**
 * Unique identifier of a tag, mirroring the modern {@code TagKey<T>}.
 * <p>
 * A key is a (registry, location) pair. Instances are interned, so equal keys
 * are always the same object and can be compared by identity in hot paths.
 * </p>
 *
 * @param <T> element type (item or block)
 */
public final class TagKey<T> {

    private static final Interner<TagKey<?>> VALUES = Interners.newWeakInterner();

    private final TagRegistry<T> registry;
    private final ResourceLocation location;

    private TagKey(TagRegistry<T> registry, ResourceLocation location) {
        this.registry = registry;
        this.location = location;
    }

    /** Creates or returns the interned key for the given registry and location. */
    @SuppressWarnings("unchecked")
    public static <T> TagKey<T> create(TagRegistry<T> registry, ResourceLocation location) {
        return (TagKey<T>) VALUES.intern(new TagKey<>(registry, location));
    }

    /** Creates or returns the interned key for the given registry and location. */
    public static <T> TagKey<T> create(TagRegistry<T> registry, String namespace, String path) {
        return create(registry, new ResourceLocation(namespace, path));
    }

    /** Item tag key. Kept for source compatibility with existing callers. */
    public static TagKey<Item> createItem(String namespace, String name) {
        return create(TagRegistry.ITEMS, namespace, name);
    }

    /** Item tag key. Kept for source compatibility with existing callers. */
    public static TagKey<Item> createItem(ResourceLocation location) {
        return create(TagRegistry.ITEMS, location);
    }

    /** Block tag key. Kept for source compatibility with existing callers. */
    public static TagKey<Block> createBlock(String namespace, String name) {
        return create(TagRegistry.BLOCKS, namespace, name);
    }

    /** Block tag key. Kept for source compatibility with existing callers. */
    public static TagKey<Block> createBlock(ResourceLocation location) {
        return create(TagRegistry.BLOCKS, location);
    }

    /** The registry this tag belongs to. */
    public TagRegistry<T> registry() {
        return registry;
    }

    /** The tag location. */
    public ResourceLocation location() {
        return location;
    }

    /** @return true when this key belongs to the given registry. */
    public boolean isFor(TagRegistry<?> registry) {
        return this.registry.equals(registry);
    }

    /** @return true when this key belongs to the registry with the given name. */
    public boolean isFor(String registryName) {
        return this.registry.getName().equals(registryName);
    }

    /** Compatibility accessor. */
    public TagRegistry<T> getRegistry() {
        return registry;
    }

    /** Compatibility accessor. */
    public String getRegistryType() {
        return registry.getName();
    }

    /** Compatibility accessor. */
    public ResourceLocation getLocation() {
        return location;
    }

    /** Compatibility accessor. */
    public String getFullIdentifier() {
        return location.toString();
    }

    @Override
    public String toString() {
        return "TagKey[" + registry.getName() + " / " + location + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TagKey)) {
            return false;
        }
        TagKey<?> other = (TagKey<?>) obj;
        return registry.equals(other.registry) && location.equals(other.location);
    }

    @Override
    public int hashCode() {
        return registry.hashCode() * 31 + location.hashCode();
    }
}
