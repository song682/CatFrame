package decok.dfcdvadstf.catframe.tags;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import decok.dfcdvadstf.catframe.Tags;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Tag loader - responsible for loading and managing the tag system
 * 
 * Similar to 26.1's TagLoader<T>, but adapted for 1.7.10 Forge environment
 * Support:
 * - Loading tag definitions from JSON files
 * - Tag references (e.g. #namespace:tag_name)
 * - Dependency sorting (load tags that are referenced first)
 * 
 * Usage:
 * <pre>
 * // Initialize in preInit phase
 * TagLoader<Item> itemTagLoader = new TagLoader<>("item", TagLoader.createItemLookup());
 * itemTagLoader.loadFromDirectory(new File(configDir, "tags/items"));
 * 
 * // Query tag contents
 * Set<Item> woolItems = itemTagLoader.getTagContents("catframe", "wool");
 * </pre>
 * 
 * <p>
 * Contents come from two layers that are queried as a union: hard-coded
 * contents (registered through {@link #getOrCreateTagContents}) and
 * JSON-backed contents (added through {@link #addTagFile}, rebuilt by
 * {@link #rebuild()}). Rebuilds only replace the JSON layer, so hard-coded
 * registrations always survive reload passes.
 * </p>
 * 
 * @param <T> Tag type (Item or Block)
 */
public class TagLoader<T> {
    
    private static final Logger LOGGER = LogManager.getLogger(TagLoader.class);
    
    /** UTF-8 charset (explicit, never the platform default). */
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    
    /** Registry type (e.g. "item" or "block") */
    private final String registryType;
    
    /** Default namespace for tags loaded from directory */
    private String defaultNamespace = Tags.MODID;
    
    /** Hard-coded tag contents (registered through getOrCreateTagContents); never touched by rebuilds. */
    private final Map<ResourceLocation, Set<T>> baseContents = new HashMap<>();
    
    /** JSON-backed tag contents; replaced wholesale by every rebuild. */
    private Map<ResourceLocation, Set<T>> jsonContents = new HashMap<>();
    
    /** Raw entry registry (unresolved TagEntry), in insertion order for deterministic builds. */
    private Map<ResourceLocation, List<TagEntry>> rawEntries = new LinkedHashMap<>();
    
    /** Optional consolidated problem sink; when unset, problems only go to the log. */
    private TagProblemSink problemSink;
    
    /** Element lookup function */
    private final ElementLookup<T> elementLookup;
    
    public TagLoader(String registryType, ElementLookup<T> elementLookup) {
        this.registryType = registryType;
        this.elementLookup = elementLookup;
    }
    
    /**
     * Set the default namespace used when loading tags from directory
     * 
     * @param namespace default namespace (e.g. "catframe", "forge", "my_mod")
     * @return this loader for chaining
     */
    public TagLoader<T> setDefaultNamespace(String namespace) {
        this.defaultNamespace = namespace;
        return this;
    }
    
    /**
     * Get the current default namespace
     */
    public String getDefaultNamespace() {
        return defaultNamespace;
    }
    
    /**
     * Load all tag files from the directory
     * 
     * @param tagsDir Tag directory (e.g. config/tags/items)
     */
    public void loadFromDirectory(File tagsDir) {
        if (!tagsDir.exists() || !tagsDir.isDirectory()) {
            LOGGER.info("Tags directory not found: {}", tagsDir.getPath());
            return;
        }
        
        LOGGER.info("Loading {} tags from: {}", registryType, tagsDir.getPath());
        loadDirectoryRecursive(tagsDir, "");
        
        // Load all tags after processing
        rebuild();
    }
    
    /**
     * Recursively load tag files from the directory
     */
    private void loadDirectoryRecursive(File dir, String prefix) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // Recurse the folder
                String newPrefix = prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName();
                loadDirectoryRecursive(file, newPrefix);
            } else if (file.getName().endsWith(".json")) {
                // Load JSON file
                String tagName = prefix.isEmpty() ? file.getName().replace(".json", "") 
                                                   : prefix + "/" + file.getName().replace(".json", "");
                loadFromFile(file, defaultNamespace, tagName);
            }
        }
    }
    
    /**
     * Load tag file from single JSON (UTF-8, never the platform default)
     */
    public void loadFromFile(File jsonFile, String namespace, String tagName) {
        try {
            if (!jsonFile.exists()) {
                return;
            }
            
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(jsonFile), UTF_8));
            JsonParser parser = new JsonParser();
            JsonElement jsonElement = parser.parse(reader);
            reader.close();
            
            if (!jsonElement.isJsonObject()) {
                LOGGER.warn("Invalid JSON in tag file: {}", jsonFile.getPath());
                return;
            }
            
            JsonObject json = jsonElement.getAsJsonObject();
            TagFile tagFile = TagFile.fromJson(json);
            
            addTagFile(new ResourceLocation(namespace, tagName), tagFile);
            
            LOGGER.debug("Loaded tag file: {} -> {}:{}", jsonFile.getName(), namespace, tagName);
            
        } catch (Exception e) { 
            LOGGER.error("Failed to load tag JSON from: {}", jsonFile.getPath(), e);
        }
    }
    
    /**
     * Adds a parsed tag file to the raw entry set (JSON layer).
     * When {@code tagFile.isReplace()} is set, previously accumulated entries
     * for this tag are cleared first; because packs are visited from lowest to
     * highest priority, a replace file overrides lower-priority contributions
     * while later contributions still append.
     *
     * @param location tag id
     * @param tagFile parsed tag file
     */
    public void addTagFile(ResourceLocation location, TagFile tagFile) {
        if (tagFile.isReplace()) {
            rawEntries.remove(location);
        }
        rawEntries.computeIfAbsent(location, k -> new ArrayList<>()).addAll(tagFile.getEntries());
    }
    
    /** Clears all JSON-derived raw entries (never hard-coded contents). */
    public void clearRawEntries() {
        rawEntries = new LinkedHashMap<>();
    }
    
    /**
     * Sets the consolidated problem sink (may be {@code null} to disable).
     * Problems are still written to the log; the sink adds a second channel.
     */
    public void setProblemSink(TagProblemSink sink) {
        this.problemSink = sink;
    }
    
    /**
     * Rebuilds all JSON-backed tags from the raw entry set, replacing the
     * previous JSON results. Hard-coded contents (registered through
     * {@link #getOrCreateTagContents}) are preserved untouched.
     * 
     * Tags whose entries reference other tags are built in dependency order:
     * a fixpoint loop keeps building every tag whose required tag references
     * are already built until no progress is possible. When no progress is
     * possible but entries remain (a reference cycle), the remainder is built
     * in insertion order and the cycle is reported.
     */
    public void rebuild() {
        Map<ResourceLocation, Set<T>> fresh = new HashMap<>();
        List<ResourceLocation> pending = new ArrayList<>(rawEntries.keySet());
        Set<ResourceLocation> built = new HashSet<>();
        Set<ResourceLocation> rawTags = rawEntries.keySet();
        
        while (!pending.isEmpty()) {
            boolean progressed = false;
            Iterator<ResourceLocation> iterator = pending.iterator();
            while (iterator.hasNext()) {
                ResourceLocation location = iterator.next();
                if (requiredDependenciesReady(location, rawTags, built)) {
                    fresh.put(location, buildTag(location, fresh));
                    built.add(location);
                    iterator.remove();
                    progressed = true;
                }
            }
            if (!progressed) {
                // Dependency cycle (or self reference): build the remainder in
                // insertion order so the tag still resolves whatever it can.
                report(true, pending.toString(), "tag dependency cycle detected; building in insertion order");
                for (ResourceLocation location : new ArrayList<>(pending)) {
                    fresh.put(location, buildTag(location, fresh));
                    built.add(location);
                }
                pending.clear();
            }
        }
        
        jsonContents = fresh;
        LOGGER.info("Rebuilt {} JSON-backed {} tag(s)", fresh.size(), registryType);
    }
    
    /** @return true when every required tag reference of this tag is already built. */
    private boolean requiredDependenciesReady(ResourceLocation location, Set<ResourceLocation> rawTags, Set<ResourceLocation> built) {
        List<TagEntry> entries = rawEntries.get(location);
        if (entries == null) {
            return true;
        }
        final boolean[] ready = {true};
        for (TagEntry entry : entries) {
            entry.visitRequiredDependencies(dependency -> {
                // Only JSON-backed dependencies can hold up the build; missing
                // tags are reported during the build itself, not here.
                if (rawTags.contains(dependency) && !built.contains(dependency)) {
                    ready[0] = false;
                }
            });
        }
        return ready[0];
    }
    
    /** Resolves one tag's entries against already-built JSON tags and hard-coded contents. */
    private Set<T> buildTag(ResourceLocation location, Map<ResourceLocation, Set<T>> resolved) {
        Set<T> values = new LinkedHashSet<>();
        TagEntry.Lookup<T> lookup = createLookup(resolved);
        
        boolean complete = true;
        for (TagEntry tagEntry : rawEntries.get(location)) {
            if (!tagEntry.build(lookup, values::add)) {
                complete = false;
            }
        }
        if (!complete) {
            report(false, location.toString(), "some required entries are missing; the tag was built without them");
        }
        return values;
    }
    
    private void report(boolean error, String tagId, String message) {
        TagProblemSink sink = this.problemSink;
        if (sink != null) {
            sink.report(error, tagId, message);
        }
    }
    
    /**
     * Create lookup for one build pass; resolves tag references against
     * already-built JSON tags first, then hard-coded contents.
     */
    private TagEntry.Lookup<T> createLookup(final Map<ResourceLocation, Set<T>> resolved) {
        return new TagEntry.Lookup<T>() {
            @Override
            public T element(ResourceLocation key, boolean required) {
                return elementLookup.get(key, required);
            }
            
            @Override
            public Collection<T> tag(ResourceLocation key) {
                Collection<T> resolvedTag = resolved.get(key);
                if (resolvedTag != null) {
                    return resolvedTag;
                }
                return baseContents.get(key);
            }
        };
    }
    
    /**
     * Get all objects in the tag (read-only)
     */
    public Set<T> getTagContents(String namespace, String name) {
        return getTagContents(new ResourceLocation(namespace, name));
    }
    
    /**
     * Get all objects in the tag (read-only)
     */
    public Set<T> getTagContents(ResourceLocation location) {
        Set<T> base = baseContents.get(location);
        Set<T> json = jsonContents.get(location);
        if (base == null && json == null) {
            return Collections.emptySet();
        }
        Set<T> merged = new LinkedHashSet<>();
        if (base != null) {
            merged.addAll(base);
        }
        if (json != null) {
            merged.addAll(json);
        }
        return Collections.unmodifiableSet(merged);
    }
    
    /**
     * Get or create tag content set (for hard-coded registration)
     * If the tag does not exist, it will be created automatically
     */
    public Set<T> getOrCreateTagContents(ResourceLocation location) {
        Set<T> contents = baseContents.get(location);
        if (contents == null) {
            contents = new LinkedHashSet<>();
            baseContents.put(location, contents);
        }
        return contents;
    }
    
    /**
     * Get or create tag content set (for hard-coded registration)
     */
    public Set<T> getOrCreateTagContents(String namespace, String name) {
        return getOrCreateTagContents(new ResourceLocation(namespace, name));
    }
    
    /**
     * Check if object belongs to a tag
     */
    public boolean is(T object, String namespace, String name) {
        return is(object, new ResourceLocation(namespace, name));
    }
    
    /**
     * Check if object belongs to a tag
     */
    public boolean is(T object, ResourceLocation location) {
        Set<T> base = baseContents.get(location);
        if (base != null && base.contains(object)) {
            return true;
        }
        Set<T> json = jsonContents.get(location);
        return json != null && json.contains(object);
    }
    
    /**
     * Check if tag exists
     */
    public boolean exists(String namespace, String name) {
        return exists(new ResourceLocation(namespace, name));
    }
    
    /**
     * Check if tag exists
     */
    public boolean exists(ResourceLocation location) {
        return baseContents.containsKey(location) || jsonContents.containsKey(location);
    }
    
    /**
     * Get all registered tag names
     */
    public Set<ResourceLocation> getAllTagNames() {
        Set<ResourceLocation> names = new LinkedHashSet<>(baseContents.keySet());
        names.addAll(jsonContents.keySet());
        return Collections.unmodifiableSet(names);
    }
    
    /**
     * Clear all tags (hard-coded, JSON-backed and raw entries)
     */
    public void clear() {
        baseContents.clear();
        jsonContents = new HashMap<>();
        rawEntries = new LinkedHashMap<>();
    }
    
    /**
     * Element lookup interface
     */
    public interface ElementLookup<T> {
        /**
         * Lookup an element
         * 
         * @param id the element identifier
         * @param required whether the element must exist
         * @return the found element, or null if not found
         */
        T get(ResourceLocation id, boolean required);
    }
    
    /**
     * Consolidated problem sink for build-phase findings (dependency cycles,
     * missing required entries). Problems are also written to the log; this is
     * an additional channel for the datapack report.
     */
    public interface TagProblemSink {
        /**
         * @param error whether the problem is an error (vs a warning)
         * @param tagId affected tag id, or a descriptive context
         * @param message human-readable message
         */
        void report(boolean error, String tagId, String message);
    }
    
    /**
     * Create item ElementLookup
     */
    public static ElementLookup<Item> createItemLookup() {
        return (id, required) -> {
            Object obj = Item.itemRegistry.getObject(id.toString());
            return obj instanceof Item ? (Item) obj : null;
        };
    }
    
    /**
     * Create block ElementLookup
     */
    public static ElementLookup<Block> createBlockLookup() {
        return (id, required) -> {
            Object obj = Block.blockRegistry.getObject(id.toString());
            return obj instanceof Block ? (Block) obj : null;
        };
    }
}
