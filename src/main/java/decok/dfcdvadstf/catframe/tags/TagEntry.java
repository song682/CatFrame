package decok.dfcdvadstf.catframe.tags;

import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Tag 条目 - 表示标签中的一个元素或引用
 * 
 * 类似 26.1 的 TagEntry，支持两种类型：
 * 1. 直接引用元素（如 "minecraft:wool"）
 * 2. 引用其他标签（如 "#catframe:wool_like"）
 * 
 * 支持 required 标记，控制找不到时是否报错
 */
public class TagEntry {
    
    private static final Logger LOGGER = LogManager.getLogger(TagEntry.class);
    
    /** 引用的标识符 */
    private final ResourceLocation id;
    
    /** 是否为标签引用（true=引用其他标签，false=直接元素） */
    private final boolean tag;
    
    /** 是否必须存在（true=找不到报错，false=找不到则忽略） */
    private final boolean required;
    
    private TagEntry(ResourceLocation id, boolean tag, boolean required) {
        this.id = id;
        this.tag = tag;
        this.required = required;
    }
    
    /**
     * 创建一个直接元素条目（必须存在）
     */
    public static TagEntry element(ResourceLocation id) {
        return new TagEntry(id, false, true);
    }
    
    /**
     * 创建一个直接元素条目（可选）
     */
    public static TagEntry optionalElement(ResourceLocation id) {
        return new TagEntry(id, false, false);
    }
    
    /**
     * 创建一个标签引用条目（必须存在）
     */
    public static TagEntry tag(ResourceLocation id) {
        return new TagEntry(id, true, true);
    }
    
    /**
     * 创建一个标签引用条目（可选）
     */
    public static TagEntry optionalTag(ResourceLocation id) {
        return new TagEntry(id, true, false);
    }
    
    /**
     * 从字符串解析 TagEntry
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
     * 构建（解析）此条目的实际对象
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
     * 访问所需的依赖标签
     */
    public void visitRequiredDependencies(Consumer<ResourceLocation> output) {
        if (this.tag && this.required) {
            output.accept(this.id);
        }
    }
    
    /**
     * 访问可选的依赖标签
     */
    public void visitOptionalDependencies(Consumer<ResourceLocation> output) {
        if (this.tag && !this.required) {
            output.accept(this.id);
        }
    }
    
    /**
     * 获取引用的标识符
     */
    public ResourceLocation getId() {
        return id;
    }
    
    /**
     * 是否为标签引用
     */
    public boolean isTag() {
        return tag;
    }
    
    /**
     * 是否必须存在
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
