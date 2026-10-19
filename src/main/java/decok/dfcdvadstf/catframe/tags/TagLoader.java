package decok.dfcdvadstf.catframe.tags;

import com.google.gson.JsonObject;
import decok.dfcdvadstf.catframe.datapack.DataPackErrors;
import decok.dfcdvadstf.catframe.datapack.DataPackJson;
import decok.dfcdvadstf.catframe.datapack.DataPackRepository;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads and builds all tags of one registry, mirroring the modern
 * {@code TagLoader<T>}.
 * <p>
 * The work is split into the same two phases as the modern implementation:
 * </p>
 * <ol>
 * <li>{@link #load(DataPackRepository, DataPackErrors)} reads every pack's tag
 *     files, in ascending priority, into raw {@link EntryWithSource} lists.
 *     A {@code "replace": true} file clears lower-priority contributions for
 *     that tag, while ordinary files accumulate.</li>
 * <li>{@link #build()} resolves the raw lists in dependency order. A tag whose
 *     required element or tag reference is missing is rejected as a whole,
 *     exactly like the modern loader.</li>
 * </ol>
 * <p>
 * Hard-coded contents registered through
 * {@link #getOrCreateTagContents(ResourceLocation)} are kept separately from
 * JSON-backed results. They survive reloads and act as pre-built tags for
 * dependency resolution.
 * </p>
 *
 * @param <T> element type (item or block)
 */
public class TagLoader<T> {

    private static final Logger LOGGER = LogManager.getLogger(TagLoader.class);

    private final TagRegistry<T> registry;
    private final ElementLookup<T> elementLookup;

    /** Raw entries from the last load pass, keyed by tag id. */
    private final Map<ResourceLocation, List<EntryWithSource>> builders = new LinkedHashMap<>();

    /** Results of the last build pass, keyed by tag id. */
    private final Map<ResourceLocation, List<T>> built = new LinkedHashMap<>();

    /** Hard-coded contents; never touched by load/build passes. */
    private final Map<ResourceLocation, List<T>> hardcoded = new LinkedHashMap<>();

    private TagProblemSink problemSink;

    public TagLoader(TagRegistry<T> registry, ElementLookup<T> elementLookup) {
        this.registry = registry;
        this.elementLookup = elementLookup;
    }

    public TagRegistry<T> getRegistry() {
        return registry;
    }

    /** Sets the consolidated problem sink (may be {@code null}). */
    public void setProblemSink(TagProblemSink sink) {
        this.problemSink = sink;
    }

    // ==================== Loading ====================

    /**
     * Reads tag files from every enabled pack, lowest priority first.
     *
     * @return number of successfully parsed tag files
     */
    public int load(DataPackRepository repository, DataPackErrors errors) {
        builders.clear();
        built.clear();

        int count = 0;
        String tagsDirectory = registry.getTagsDirectory();
        for (String namespace : repository.listNamespaces()) {
            String prefix = "data/" + namespace + '/' + tagsDirectory + '/';
            for (DataPackRepository.PackResource resource : repository.listResources(prefix)) {
                String path = resource.getPath();
                String relative = path.substring(prefix.length());
                if (!relative.endsWith(".json")) {
                    continue;
                }
                String tagName = relative.substring(0, relative.length() - ".json".length());
                String packId = resource.getPack().getId();

                JsonObject json = DataPackJson.parseObject(resource.read(), packId, path, errors);
                if (json == null) {
                    continue;
                }
                try {
                    ResourceLocation tagId = new ResourceLocation(namespace, tagName);
                    addTagFile(tagId, TagFile.fromJson(json), packId);
                    count++;
                } catch (RuntimeException e) {
                    errors.error(packId, path, "invalid tag file: " + e.getMessage());
                }
            }
        }
        LOGGER.info("Loaded {} {} tag file(s) from data packs", count, registry.getName());
        return count;
    }

    /**
     * Adds one parsed tag file to the raw entries. A replacing file clears all
     * entries accumulated so far (lower-priority packs and earlier files).
     */
    public void addTagFile(ResourceLocation location, TagFile tagFile, String source) {
        if (tagFile.replace()) {
            builders.remove(location);
        }
        List<EntryWithSource> entries = builders.get(location);
        if (entries == null) {
            entries = new ArrayList<>();
            builders.put(location, entries);
        }
        for (TagEntry entry : tagFile.entries()) {
            entries.add(new EntryWithSource(entry, source));
        }
    }

    /** Discards raw entries and built results (hard-coded contents survive). */
    public void unload() {
        builders.clear();
        built.clear();
    }

    // ==================== Building ====================

    /**
     * Resolves all raw tag entries in dependency order and replaces the
     * previous build results.
     *
     * @return unmodifiable view of the newly built tags
     */
    public Map<ResourceLocation, List<T>> build() {
        final Map<ResourceLocation, List<T>> newTags = new LinkedHashMap<>();

        final TagEntry.Lookup<T> lookup = new TagEntry.Lookup<T>() {
            @Override
            public T element(ResourceLocation key, boolean required) {
                return elementLookup.get(key, required).orElse(null);
            }

            @Override
            public Collection<T> tag(ResourceLocation key) {
                List<T> result = newTags.get(key);
                if (result != null) {
                    return result;
                }
                return hardcoded.get(key);
            }
        };

        DependencySorter<ResourceLocation, SortingEntry> sorter = new DependencySorter<>();
        for (Map.Entry<ResourceLocation, List<EntryWithSource>> entry : builders.entrySet()) {
            sorter.addEntry(entry.getKey(), new SortingEntry(entry.getValue()));
        }
        sorter.orderByDependencies(new java.util.function.BiConsumer<ResourceLocation, SortingEntry>() {
            @Override
            public void accept(ResourceLocation id, SortingEntry contents) {
                List<T> tag = tryBuildTag(id, lookup, contents.entries);
                if (tag != null) {
                    newTags.put(id, tag);
                }
            }
        }, new java.util.function.BiConsumer<ResourceLocation, ResourceLocation>() {
            @Override
            public void accept(ResourceLocation from, ResourceLocation to) {
                report(true, from.toString(),
                        "required reference #" + to + " forms a cycle; the cyclic edge was dropped");
            }
        });

        // Empty-tag policy (B5): a built tag without elements is legal but almost
        // always a typo or a stale reference, so it is reported as a warning.
        for (Map.Entry<ResourceLocation, List<T>> built : newTags.entrySet()) {
            if (built.getValue().isEmpty()) {
                report(false, built.getKey().toString(), "tag is empty");
            }
        }

        built.clear();
        built.putAll(newTags);
        LOGGER.info("Built {} {} tag(s)", built.size(), registry.getName());
        return Collections.unmodifiableMap(built);
    }

    private List<T> tryBuildTag(ResourceLocation id, TagEntry.Lookup<T> lookup,
                                List<EntryWithSource> entries) {
        LinkedHashSet<T> values = new LinkedHashSet<>();
        List<EntryWithSource> missing = new ArrayList<>();
        for (EntryWithSource entry : entries) {
            if (!entry.getEntry().build(lookup, values::add)) {
                missing.add(entry);
            }
        }
        if (!missing.isEmpty()) {
            StringBuilder message = new StringBuilder("missing following references: ");
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) {
                    message.append(", ");
                }
                message.append(missing.get(i));
            }
            report(true, id.toString(), message.toString());
            return null;
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private void report(boolean error, String tagId, String message) {
        TagProblemSink sink = this.problemSink;
        if (sink != null) {
            sink.report(error, tagId, message);
        }
    }

    // ==================== Querying ====================

    /** Contents of a tag (JSON-backed plus hard-coded), or an empty list. */
    public List<T> getTagContents(ResourceLocation location) {
        List<T> base = hardcoded.get(location);
        List<T> json = built.get(location);
        if (base == null && json == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<T> merged = new LinkedHashSet<>();
        if (base != null) {
            merged.addAll(base);
        }
        if (json != null) {
            merged.addAll(json);
        }
        return Collections.unmodifiableList(new ArrayList<>(merged));
    }

    /** Contents of a tag, or an empty list. */
    public List<T> getTagContents(String namespace, String name) {
        return getTagContents(new ResourceLocation(namespace, name));
    }

    /** @return true when the object belongs to the tag. */
    public boolean is(T object, ResourceLocation location) {
        List<T> base = hardcoded.get(location);
        if (base != null && base.contains(object)) {
            return true;
        }
        List<T> json = built.get(location);
        return json != null && json.contains(object);
    }

    /** @return true when the object belongs to the tag. */
    public boolean is(T object, String namespace, String name) {
        return is(object, new ResourceLocation(namespace, name));
    }

    /** @return true when the object belongs to the tag. */
    public boolean is(T object, TagKey<T> tag) {
        return tag.isFor(registry) && is(object, tag.location());
    }

    /** @return true when the tag exists (JSON-backed or hard-coded). */
    public boolean exists(ResourceLocation location) {
        return built.containsKey(location) || hardcoded.containsKey(location);
    }

    /** @return true when the tag exists. */
    public boolean exists(String namespace, String name) {
        return exists(new ResourceLocation(namespace, name));
    }

    /** All known tag ids (JSON-backed and hard-coded). */
    public Set<ResourceLocation> getAllTagNames() {
        Set<ResourceLocation> names = new LinkedHashSet<>(built.keySet());
        names.addAll(hardcoded.keySet());
        return Collections.unmodifiableSet(names);
    }

    /**
     * Returns the mutable hard-coded contents of a tag, creating it when absent.
     * Changes take effect immediately and survive reloads.
     */
    public List<T> getOrCreateTagContents(ResourceLocation location) {
        List<T> contents = hardcoded.get(location);
        if (contents == null) {
            contents = new ArrayList<>();
            hardcoded.put(location, contents);
        }
        return contents;
    }

    /** Returns the mutable hard-coded contents of a tag, creating it when absent. */
    public List<T> getOrCreateTagContents(String namespace, String name) {
        return getOrCreateTagContents(new ResourceLocation(namespace, name));
    }

    /** Adds one element directly to a tag's hard-coded contents. */
    public void add(ResourceLocation location, T value) {
        List<T> contents = getOrCreateTagContents(location);
        if (!contents.contains(value)) {
            contents.add(value);
        }
    }

    /** Clears JSON-backed state only; hard-coded contents survive. */
    public void clearRaw() {
        unload();
    }

    /** Clears everything, including hard-coded contents. */
    public void clear() {
        builders.clear();
        built.clear();
        hardcoded.clear();
    }

    // ==================== Element lookup ====================

    /**
     * Resolves an element id to the concrete object. Mirrors the modern
     * {@code TagLoader.ElementLookup<T>}.
     */
    public interface ElementLookup<T> {

        Optional<? extends T> get(ResourceLocation id, boolean required);

        static ElementLookup<Item> item() {
            return new ElementLookup<Item>() {
                @Override
                public Optional<? extends Item> get(ResourceLocation id, boolean required) {
                    Object value = Item.itemRegistry.getObject(id.toString());
                    return value instanceof Item ? Optional.of((Item) value) : Optional.empty();
                }
            };
        }

        static ElementLookup<Block> block() {
            return new ElementLookup<Block>() {
                @Override
                public Optional<? extends Block> get(ResourceLocation id, boolean required) {
                    Object value = Block.blockRegistry.getObject(id.toString());
                    return value instanceof Block ? Optional.of((Block) value) : Optional.empty();
                }
            };
        }
    }

    /** Item element lookup. */
    public static ElementLookup<Item> createItemLookup() {
        return ElementLookup.item();
    }

    /** Block element lookup. */
    public static ElementLookup<Block> createBlockLookup() {
        return ElementLookup.block();
    }

    // ==================== Support types ====================

    /** One raw entry together with the pack that contributed it. */
    public static final class EntryWithSource {

        private final TagEntry entry;
        private final String source;

        public EntryWithSource(TagEntry entry, String source) {
            this.entry = entry;
            this.source = source;
        }

        public TagEntry getEntry() {
            return entry;
        }

        public String getSource() {
            return source;
        }

        @Override
        public String toString() {
            return entry + " (from " + source + ')';
        }
    }

    private final class SortingEntry implements DependencySorter.Entry<ResourceLocation> {

        private final List<EntryWithSource> entries;

        private SortingEntry(List<EntryWithSource> entries) {
            this.entries = entries;
        }

        @Override
        public void visitRequiredDependencies(java.util.function.Consumer<ResourceLocation> output) {
            for (EntryWithSource entry : entries) {
                entry.getEntry().visitRequiredDependencies(output);
            }
        }

        @Override
        public void visitOptionalDependencies(java.util.function.Consumer<ResourceLocation> output) {
            for (EntryWithSource entry : entries) {
                entry.getEntry().visitOptionalDependencies(output);
            }
        }
    }

    /**
     * Consolidated problem sink for build-phase findings (missing references).
     * Problems are also available to the datapack report through this channel.
     */
    public interface TagProblemSink {

        void report(boolean error, String tagId, String message);
    }
}
