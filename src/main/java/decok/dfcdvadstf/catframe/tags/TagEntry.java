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
     * 支持格式：
     * - "minecraft:wool" -> 直接元素
     * - "#catframe:wool" -> 标签引用
     * - "minecraft:wool?" -> 可选元素（? 后缀）
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
     * @param lookup 查找器
     * @param output 输出收集器
     * @return 是否成功
     */
    public <T> boolean build(Lookup<T> lookup, Consumer<T> output) {
        if (this.tag) {
            // 引用其他标签
            Collection<T> result = lookup.tag(this.id);
            if (result == null) {
                if (this.required) {
                    LOGGER.warn("Missing required tag: {}", this.id);
                }
                return !this.required;
            }
            
            result.forEach(output);
        } else {
            // 直接元素
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
     * 查找器接口 - 用于解析 TagEntry 到实际对象
     */
    public interface Lookup<T> {
        /**
         * 查找直接元素
         * 
         * @param key 元素标识符
         * @param required 是否必须存在
         * @return 找到的元素，找不到且 required=false 时返回 null
         */
        T element(ResourceLocation key, boolean required);
        
        /**
         * 查找标签内容
         * 
         * @param key 标签标识符
         * @return 标签中的所有元素，找不到时返回 null
         */
        Collection<T> tag(ResourceLocation key);
    }
}
