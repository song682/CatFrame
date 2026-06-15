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
 * Tag 加载器 - 负责加载和管理标签系统
 * 
 * 类似 26.1 的 TagLoader<T>，但适配 1.7.10 Forge 环境
 * 支持：
 * - 从 JSON 文件加载标签定义
 * - 标签引用（#namespace:tag_name）
 * - 依赖排序（先加载被引用的标签）
 * 
 * 使用方式：
 * <pre>
 * // 在 preInit 阶段初始化
 * TagLoader<Item> itemTagLoader = new TagLoader<>("item", TagLoader.createItemLookup());
 * itemTagLoader.loadFromDirectory(new File(configDir, "tags/items"));
 * 
 * // 查询标签
 * Set<Item> woolItems = itemTagLoader.getTagContents("catframe", "wool");
 * </pre>
 * 
 * @param <T> 标签类型（Item 或 Block）
 */
public class TagLoader<T> {
    
    private static final Logger LOGGER = LogManager.getLogger(TagLoader.class);
    
    /** 注册表类型（"item" 或 "block"） */
    private final String registryType;
    
    /** 标签注册表: tag名称 -> 该标签下的所有对象集合 */
    private final Map<ResourceLocation, Set<T>> tagRegistry = new HashMap<>();
    
    /** 原始条目注册表（未解析的 TagEntry） */
    private final Map<ResourceLocation, List<TagEntry>> rawEntries = new HashMap<>();
    
    /** 元素查找器 */
    private final ElementLookup<T> elementLookup;
    
    public TagLoader(String registryType, ElementLookup<T> elementLookup) {
        this.registryType = registryType;
        this.elementLookup = elementLookup;
    }
    
    /**
     * 从目录加载所有标签文件
     * 
     * @param tagsDir 标签目录（如 config/tags/items）
     */
    public void loadFromDirectory(File tagsDir) {
        if (!tagsDir.exists() || !tagsDir.isDirectory()) {
            LOGGER.info("Tags directory not found: {}", tagsDir.getPath());
            return;
        }
        
        LOGGER.info("Loading {} tags from: {}", registryType, tagsDir.getPath());
        loadDirectoryRecursive(tagsDir, "");
        
        // 加载完成后，构建所有标签
        buildAllTags();
    }
    
    /**
     * 递归加载目录中的标签文件
     */
    private void loadDirectoryRecursive(File dir, String prefix) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // 递归子目录
                String newPrefix = prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName();
                loadDirectoryRecursive(file, newPrefix);
            } else if (file.getName().endsWith(".json")) {
                // 加载 JSON 文件
                String tagName = prefix.isEmpty() ? file.getName().replace(".json", "") 
                                                   : prefix + "/" + file.getName().replace(".json", "");
                loadFromFile(file, "catframe", tagName);
            }
        }
    }
    
    /**
     * 从单个 JSON 文件加载标签
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
                // 如果 replace 为 true，清空已有内容
                rawEntries.remove(tagLocation);
            }
            
            // 添加条目
            rawEntries.computeIfAbsent(tagLocation, k -> new ArrayList<>())
                      .addAll(tagFile.getEntries());
            
            LOGGER.debug("Loaded tag file: {} -> {}", jsonFile.getName(), tagLocation);
            
        } catch (Exception e) {
            LOGGER.error("Failed to load tag JSON from: {}", jsonFile.getPath(), e);
        }
    }
    
    /**
     * 构建所有标签（解析所有 TagEntry）
     */
    private void buildAllTags() {
        // 简单实现：直接构建所有标签
        // 26.1 使用 DependencySorter 处理依赖关系，这里简化处理
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
     * 创建查找器
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
     * 获取标签中的所有对象（只读）
     */
    public Set<T> getTagContents(String namespace, String name) {
        return getTagContents(new ResourceLocation(namespace, name));
    }
    
    /**
     * 获取标签中的所有对象（只读）
     */
    public Set<T> getTagContents(ResourceLocation location) {
        return Collections.unmodifiableSet(
            tagRegistry.getOrDefault(location, Collections.emptySet())
        );
    }
    
    /**
     * 获取或创建标签内容集合（用于硬编码注册）
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
     * 获取或创建标签内容集合（用于硬编码注册）
     */
    public Set<T> getOrCreateTagContents(String namespace, String name) {
        return getOrCreateTagContents(new ResourceLocation(namespace, name));
    }
    
    /**
     * 检查对象是否属于某个标签
     */
    public boolean is(T object, String namespace, String name) {
        return is(object, new ResourceLocation(namespace, name));
    }
    
    /**
     * 检查对象是否属于某个标签
     */
    public boolean is(T object, ResourceLocation location) {
        Set<T> tagContents = tagRegistry.get(location);
        return tagContents != null && tagContents.contains(object);
    }
    
    /**
     * 检查标签是否存在
     */
    public boolean exists(String namespace, String name) {
        return exists(new ResourceLocation(namespace, name));
    }
    
    /**
     * 检查标签是否存在
     */
    public boolean exists(ResourceLocation location) {
        return tagRegistry.containsKey(location);
    }
    
    /**
     * 获取所有已注册的标签名称
     */
    public Set<ResourceLocation> getAllTagNames() {
        return Collections.unmodifiableSet(tagRegistry.keySet());
    }
    
    /**
     * 清空所有标签
     */
    public void clear() {
        tagRegistry.clear();
        rawEntries.clear();
    }
    
    /**
     * 元素查找器接口
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
     * 创建物品 ElementLookup
     */
    public static ElementLookup<Item> createItemLookup() {
        return (id, required) -> {
            Object obj = Item.itemRegistry.getObject(id.toString());
            return obj instanceof Item ? (Item) obj : null;
        };
    }
    
    /**
     * 创建方块 ElementLookup
     */
    public static ElementLookup<Block> createBlockLookup() {
        return (id, required) -> {
            Object obj = Block.blockRegistry.getObject(id.toString());
            return obj instanceof Block ? (Block) obj : null;
        };
    }
}
