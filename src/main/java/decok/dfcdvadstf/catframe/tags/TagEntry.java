package decok.dfcdvadstf.catframe.tags;

import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Tag entry - represents an element or reference within a tag
 * 
 * Similar to 26.1's TagEntry, supporting two types:
 * 1. Direct element reference (e.g. "minecraft:wool")
 * 2. Tag reference (e.g. "#catframe:wool_like")
 * 
 * Support required mark, control whether to error when not found
 */
public class TagEntry {
    
    private static final Logger LOGGER = LogManager.getLogger(TagEntry.class);
    
    /** Identifier of the entry */
    private final ResourceLocation id;
    
    /** Whether it is a tag reference (true=reference to another tag, false=direct element) */
    private final boolean tag;
    
    /** Whether it is required to exist (true=not found will error, false=not found will be ignored) */
    private final boolean required;
    
    private TagEntry(ResourceLocation id, boolean tag, boolean required) {
        this.id = id;
        this.tag = tag;
        this.required = required;
    }
    
    /**
     * Create a direct element entry (required to exist)
     */
    public static TagEntry element(ResourceLocation id) {
        return new TagEntry(id, false, true);
    }
    
    /**
     * Create a direct element entry (optional)
     */
    public static TagEntry optionalElement(ResourceLocation id) {
        return new TagEntry(id, false, false);
    }
    
    /**
     * Create a tag reference entry (required to exist)
     */
    public static TagEntry tag(ResourceLocation id) {
        return new TagEntry(id, true, true);
    }
    
    /**
     * Create a tag reference entry (optional)
     */
    public static TagEntry optionalTag(ResourceLocation id) {
        return new TagEntry(id, true, false);
    }
    
    /**
     * Parse TagEntry from string
     * Supported formats:
     * - "minecraft:wool" -> direct element
     * - "#catframe:wool" -> tag reference
     * - "minecraft:wool?" -> optional element (? suffix)
     */
    public static TagEntry parse(String value) {
        boolean optional = value.endsWith("?");
        if (optional) {
            value = value.substring(0, value.length() - 1);
        }
        
        boolean isTag = value.startsWith("#");
        if (isTag) {
            value = value.substring(1);
        }
        
        ResourceLocation location;
        try {
            location = new ResourceLocation(value);
        } catch (Exception e) {
            LOGGER.warn("Invalid tag entry format: {}", value);
            return null;
        }
        
        if (isTag) {
            return optional ? optionalTag(location) : tag(location);
        } else {
            return optional ? optionalElement(location) : element(location);
        }
    }
    
    /**
     * Build (resolve) the actual object of this entry
     * 
     * @param lookup the lookup resolver
     * @param output the output collector
     * @return whether the build succeeded
     */
    public <T> boolean build(Lookup<T> lookup, Consumer<T> output) {
        if (this.tag) {
            // Reference to another tag
            Collection<T> result = lookup.tag(this.id);
            if (result == null) {
                if (this.required) {
                    LOGGER.warn("Missing required tag: {}", this.id);
                }
                return !this.required;
            }
            
            result.forEach(output);
        } else {
            // Direct element
            T result = lookup.element(this.id, this.required);
            if (result == null) {
                if (this.required) {
                    LOGGER.warn("Missing required element: {}", this.id);
                }
                return !this.required;
            }
            
            output.accept(result);
        }
        
        return true;
    }
    
    /**
     * Visit required dependency tags
     */
    public void visitRequiredDependencies(Consumer<ResourceLocation> output) {
        if (this.tag && this.required) {
            output.accept(this.id);
        }
    }
    
    /**
     * Visit optional dependency tags
     */
    public void visitOptionalDependencies(Consumer<ResourceLocation> output) {
        if (this.tag && !this.required) {
            output.accept(this.id);
        }
    }
    
    /**
     * Get the referenced identifier
     */
    public ResourceLocation getId() {
        return id;
    }
    
    /**
     * Whether it is a tag reference
     */
    public boolean isTag() {
        return tag;
    }
    
    /**
     * Whether it is required to exist  
     */
    public boolean isRequired() {
        return required;
    }
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (this.tag) {
            result.append('#');
        }
        
        result.append(this.id);
        if (!this.required) {
            result.append('?');
        }
        
        return result.toString();
    }
    
    /**
     * Lookup interface - resolves a TagEntry to concrete objects
     */
    public interface Lookup<T> {
        /**
         * Look up a direct element
         * 
         * @param key the element identifier
         * @param required whether the element must exist
         * @return the resolved element, or null when not found and required=false
         */
        T element(ResourceLocation key, boolean required);
        
        /**
         * Look up the contents of a tag
         * 
         * @param key the tag identifier
         * @return all elements of the tag, or null when the tag is not found
         */
        Collection<T> tag(ResourceLocation key);
    }
}
