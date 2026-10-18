package decok.dfcdvadstf.catframe.tags;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

/**
 * Lightweight stand-in for the modern {@code ResourceKey<? extends Registry<T>>}.
 * <p>
 * 1.7.10 has no registry keys, so a tag registry is identified by its name
 * ({@code item}/{@code block}) and by the directory its tag files live in.
 * Both built-in registries use the modern singular directory names, i.e.
 * {@code data/<namespace>/tags/item/...} and
 * {@code data/<namespace>/tags/block/...}.
 * </p>
 *
 * @param <T> element type (item or block)
 */
public final class TagRegistry<T> {

    /** Item registry: {@code data/<ns>/tags/item}. */
    public static final TagRegistry<Item> ITEMS = new TagRegistry<>("item", "item");

    /** Block registry: {@code data/<ns>/tags/block}. */
    public static final TagRegistry<Block> BLOCKS = new TagRegistry<>("block", "block");

    private final String name;
    private final String directory;

    private TagRegistry(String name, String directory) {
        this.name = name;
        this.directory = directory;
    }

    /** Registry name, used in reports and in {@link TagKey#toString()}. */
    public String getName() {
        return name;
    }

    /** Registry directory below {@code tags/}. */
    public String getDirectory() {
        return directory;
    }

    /** Datapack-relative root of this registry's tag files, e.g. {@code tags/item}. */
    public String getTagsDirectory() {
        return "tags/" + directory;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TagRegistry)) {
            return false;
        }
        return name.equals(((TagRegistry<?>) obj).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "TagRegistry[" + name + "]";
    }
}
