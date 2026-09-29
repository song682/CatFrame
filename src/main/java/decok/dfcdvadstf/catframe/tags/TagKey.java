package decok.dfcdvadstf.catframe.tags;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/**
 * Tag key - identifies a specific tag
 * 
 * Similar to 26.1's TagKey<T>, used to uniquely identify a tag
 * Uses Interner pattern to ensure same identifier returns same instance
 * 
 * Usage:
 * <pre>
 * // Create TagKey
 * TagKey<Item> woolTag = TagKey.createItem("my_mod:wool");
 * 
 * // Check if item belongs to tag
 * if (itemStack.getItem().is(woolTag)) {
 *     // execute logic
 * }
 * </pre>
 * 
 * @param <T> Tag type (Item or Block)
 */
public final class TagKey<T> {
    
    /** Object pool, ensures only one instance per identifier */
    private static final java.util.Map<String, TagKey<?>> VALUES = new java.util.WeakHashMap<>();
    
    /** Registry type ("item" or "block") */
    private final String registry;
    
    /** Tag location identifier */
    private final ResourceLocation location;
    
    private TagKey(String registry, ResourceLocation location) {
        this.registry = registry;
        this.location = location;
    }
    
    /**
     * Creates or gets an item TagKey
     */
    @SuppressWarnings("unchecked")
    public static TagKey<Item> createItem(String namespace, String name) {
        return createItem(new ResourceLocation(namespace, name));
    }
    
    /**
     * Creates or gets an item TagKey
     */
    @SuppressWarnings("unchecked")
    public static TagKey<Item> createItem(ResourceLocation location) {
        String key = "item:" + location;
        TagKey<?> existing = VALUES.get(key);
        if (existing != null) {
            return (TagKey<Item>) existing;
        }
        
        TagKey<Item> newKey = new TagKey<>("item", location);
        VALUES.put(key, newKey);
        return newKey;
    }
    
    /**
     * Creates or gets a block TagKey
     */
    @SuppressWarnings("unchecked")
    public static TagKey<Block> createBlock(String namespace, String name) {
        return createBlock(new ResourceLocation(namespace, name));
    }
    
    /**
     * Creates or gets a block TagKey
     */
    @SuppressWarnings("unchecked")
    public static TagKey<Block> createBlock(ResourceLocation location) {
        String key = "block:" + location;
        TagKey<?> existing = VALUES.get(key);
        if (existing != null) {
            return (TagKey<Block>) existing;
        }
        
        TagKey<Block> newKey = new TagKey<>("block", location);
        VALUES.put(key, newKey);
        return newKey;
    }
    
    /**
     * Checks if this TagKey belongs to the specified registry type
     */
    public boolean isFor(String registry) {
        return this.registry.equals(registry);
    }
    
    /**
     * Gets the registry type
     */
    public String getRegistry() {
        return registry;
    }
    
    /**
     * Gets the tag location
     */
    public ResourceLocation getLocation() {
        return location;
    }
    
    /**
     * Gets the full tag identifier (namespace:name)
     */
    public String getFullIdentifier() {
        return location.toString();
    }
    
    @Override
    public String toString() {
        return "TagKey[" + registry + " / " + location + "]";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TagKey)) return false;
        TagKey<?> other = (TagKey<?>) obj;
        return registry.equals(other.registry) && location.equals(other.location);
    }
    
    @Override
    public int hashCode() {
        return registry.hashCode() * 31 + location.hashCode();
    }
}
