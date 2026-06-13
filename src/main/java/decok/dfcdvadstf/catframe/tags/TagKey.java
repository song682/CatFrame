package decok.dfcdvadstf.catframe.tags;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/**
 * Tag 键 - 标识一个特定的标签
 * 
 * 类似 26.1 的 TagKey<T>，用于唯一标识一个标签
 * 使用 Interner 模式确保相同标识的 TagKey 是同一个实例
 * 
 * 使用方式：
 * <pre>
 * // 创建 TagKey
 * TagKey<Item> woolTag = TagKey.createItem("my_mod:wool");
 * 
 * // 检查物品是否属于该标签
 * if (itemStack.getItem().is(woolTag)) {
 *     // 执行逻辑
 * }
 * </pre>
 * 
 * @param <T> 标签类型（Item 或 Block）
 */
public final class TagKey<T> {
    
    /** 对象池，确保相同标识的 TagKey 只有一份实例 */
    private static final java.util.Map<String, TagKey<?>> VALUES = new java.util.WeakHashMap<>();
    
    /** 注册表类型（"item" 或 "block"） */
    private final String registry;
    
    /** 标签位置标识符 */
    private final ResourceLocation location;
    
    private TagKey(String registry, ResourceLocation location) {
        this.registry = registry;
        this.location = location;
    }
    
    /**
     * 创建或获取一个物品 TagKey
     */
    @SuppressWarnings("unchecked")
    public static TagKey<Item> createItem(String namespace, String name) {
        return createItem(new ResourceLocation(namespace, name));
    }
    
    /**
     * 创建或获取一个物品 TagKey
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
     * 创建或获取一个方块 TagKey
     */
    @SuppressWarnings("unchecked")
    public static TagKey<Block> createBlock(String namespace, String name) {
        return createBlock(new ResourceLocation(namespace, name));
    }
    
    /**
     * 创建或获取一个方块 TagKey
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
     * 检查此 TagKey 是否属于指定的注册表类型
     */
    public boolean isFor(String registry) {
        return this.registry.equals(registry);
    }
    
    /**
     * 获取注册表类型
     */
    public String getRegistry() {
        return registry;
    }
    
    /**
     * 获取标签位置
     */
    public ResourceLocation getLocation() {
        return location;
    }
    
    /**
     * 获取完整的标签标识符（命名空间:名称）
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
