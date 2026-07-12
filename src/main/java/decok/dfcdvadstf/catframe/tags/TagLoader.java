package decok.dfcdvadstf.catframe.tags;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 * Tag loader - responsible for loading and managing the tag system
 * 
 * Similar to 26.1's TagLoader<T>, but adapted for 1.7.10 Forge environment
 * 类似 26.1 的 TagLoader<T>，但适配 1.7.10 Forge 环境
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
 * @param <T> Tag type (Item or Block)
 */
public class TagLoader<T> {
    
    private static final Logger LOGGER = LogManager.getLogger(TagLoader.class);
    
    /** Registry type (e.g. "item" or "block") */
    private final String registryType;
    
    /** Default namespace for tags loaded from directory */
    private String defaultNamespace = "catframe";
    
    /** Tag registry: tag name -> set of objects in the tag */
    private final Map<ResourceLocation, Set<T>> tagRegistry = new HashMap<>();
    
    /** Raw entry registry (unresolved TagEntry) */
    private final Map<ResourceLocation, List<TagEntry>> rawEntries = new HashMap<>();
    
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
        buildAllTags();
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
     * Load tag file from single JSON
     */
    public void loadFromFile(File jsonFile, String namespace, String tagName) {
        try {
            if (!jsonFile.exists()) {
                return;
            }
            
            BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
            JsonParser parser = new JsonParser();
            JsonElement jsonElement = parser.parse(reader);
            reader.close();
            
            if (!jsonElement.isJsonObject()) {
                LOGGER.warn("Invalid JSON in tag file: {}", jsonFile.getPath());
                return;
            }
            
            JsonObject json = jsonElement.getAsJsonObject();
            TagFile tagFile = TagFile.fromJson(json);
            
            ResourceLocation tagLocation = new ResourceLocation(namespace, tagName);
            
            if (tagFile.isReplace()) {
                // If replace is true, clear existing content
                rawEntries.remove(tagLocation);
            }
            
            // Add entries
            rawEntries.computeIfAbsent(tagLocation, k -> new ArrayList<>())
                      .addAll(tagFile.getEntries());
            
            LOGGER.debug("Loaded tag file: {} -> {}", jsonFile.getName(), tagLocation);
            
        } catch (Exception e) { 
            LOGGER.error("Failed to load tag JSON from: {}", jsonFile.getPath(), e);
        }
    }
    
    /**
     * Build all tags (resolve all TagEntry)
     */
    private void buildAllTags() {
        // Simple implementation: build all tags directly
        // 26.1 uses DependencySorter to handle dependency relationships, which is simplified here
        for (Map.Entry<ResourceLocation, List<TagEntry>> entry : rawEntries.entrySet()) {
            ResourceLocation tagLocation = entry.getKey();
            List<TagEntry> entries = entry.getValue();
            
            Set<T> values = new LinkedHashSet<>();
            TagEntry.Lookup<T> lookup = createLookup();
            
            for (TagEntry tagEntry : entries) {
                tagEntry.build(lookup, values::add);
            }
            
            tagRegistry.put(tagLocation, values);
            LOGGER.debug("Built tag {} with {} entries", tagLocation, values.size());
        }
    }
    
    /**
     * Create lookup
     */
    private TagEntry.Lookup<T> createLookup() {
        return new TagEntry.Lookup<T>() {
            @Override
            public T element(ResourceLocation key, boolean required) {
                return elementLookup.get(key, required);
            }
            
            @Override
            public Collection<T> tag(ResourceLocation key) {
                return tagRegistry.get(key);
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
        return Collections.unmodifiableSet(
            tagRegistry.getOrDefault(location, Collections.emptySet())
        );
    }
    
    /**
     * Get or create tag content set (for hard-coded registration)
     * 如果标签不存在，会自动创建
     */
    public Set<T> getOrCreateTagContents(ResourceLocation location) {
        Set<T> contents = tagRegistry.get(location);
        if (contents == null) {
            contents = new LinkedHashSet<>();
            tagRegistry.put(location, contents);
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
        Set<T> tagContents = tagRegistry.get(location);
        return tagContents != null && tagContents.contains(object);
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
        return tagRegistry.containsKey(location);
    }
    
    /**
     * Get all registered tag names
     */
    public Set<ResourceLocation> getAllTagNames() {
        return Collections.unmodifiableSet(tagRegistry.keySet());
    }
    
    /**
     * Clear all tags
     */
    public void clear() {
        tagRegistry.clear();
        rawEntries.clear();
    }
    
    /**
     * Element lookup interface
     */
    public interface ElementLookup<T> {
        /**
         * 查找元素
         * 
         * @param id 元素标识符
         * @param required 是否必须存在
         * @return 找到的元素，找不到时返回 null
         */
        T get(ResourceLocation id, boolean required);
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
