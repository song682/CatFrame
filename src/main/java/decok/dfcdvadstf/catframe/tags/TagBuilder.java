package decok.dfcdvadstf.catframe.tags;

import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fluent builder for tag entries, mirroring the modern {@code TagBuilder}.
 * <p>
 * This is the entry builder used to construct tag files in code, not the
 * registry facade. Use {@link TagManager} for querying and hard-coded
 * registration.
 * </p>
 */
public final class TagBuilder {

    private final List<TagEntry> entries = new ArrayList<>();
    private boolean replace = false;

    private TagBuilder() {
    }

    public static TagBuilder create() {
        return new TagBuilder();
    }

    /** Snapshot of the entries added so far. */
    public List<TagEntry> build() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** @return whether this builder should replace lower-priority contributions. */
    public boolean shouldReplace() {
        return replace;
    }

    public TagBuilder setReplace(boolean replace) {
        this.replace = replace;
        return this;
    }

    public TagBuilder add(TagEntry entry) {
        this.entries.add(entry);
        return this;
    }

    public TagBuilder addElement(ResourceLocation id) {
        return add(TagEntry.element(id));
    }

    public TagBuilder addOptionalElement(ResourceLocation id) {
        return add(TagEntry.optionalElement(id));
    }

    public TagBuilder addTag(ResourceLocation id) {
        return add(TagEntry.tag(id));
    }

    public TagBuilder addOptionalTag(ResourceLocation id) {
        return add(TagEntry.optionalTag(id));
    }

    /** Converts this builder into a {@link TagFile}. */
    public TagFile toTagFile() {
        return new TagFile(entries, replace);
    }
}
