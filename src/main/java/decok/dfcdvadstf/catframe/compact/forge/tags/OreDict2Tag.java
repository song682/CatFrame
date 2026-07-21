package decok.dfcdvadstf.catframe.compact.forge.tags;

import decok.dfcdvadstf.catframe.tags.TagLoader;
import decok.dfcdvadstf.catframe.tags.impl.CatFrameTags;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

///
/// OreDict ↔ Tag 双向转换器<br>
/// 实现 Forge OreDictionary 和 CatFrame Tag 系统之间的互通：
/// - OreDict → Tag：将 OreDict 条目转换为 Tag
/// - Tag → OreDict：将 Tag 注册到 OreDict
///
/// 命名空间统一使用 "forge"。<br>
/// 使用场景：
/// 1. 兼容旧版使用 OreDict 的 Mod
/// 2. 逐步迁移到新的 Tag 系统
/// 3. 同时支持两种系统
/// 使用示例：
/// <pre>
/// // OreDict → Tag
/// OreDict2Tag.convertOreDictToTags();
///
/// // Tag → OreDict
/// OreDict2Tag.convertTagToOreDict("catframe:wool", "wool");
/// </pre>
///
public final class OreDict2Tag {
    
    private static final Logger LOGGER = LogManager.getLogger(OreDict2Tag.class);
    
    /** Forge 命名空间 */
    public static final String FORGE_NAMESPACE = "forge";
    
    private OreDict2Tag() {
        // 工具类，禁止实例化
    }
    
    // ==================== 命名转换 ====================
    
    /// 智能转换 OreDict 名称为 Tag 名称
    /// 转换规则（驼峰拆分 + 斜杠）：
    /// - oreIron → ore/iron
    /// - ingotIron → ingot/iron
    /// - logWood → log（忽略通用修饰词 Wood）
    /// - dustRedstone → dust/redstone
    /// - gemDiamond → gem/diamond
    /// - blockGold → block/gold
    /// - treeSapling → tree/sapling
    ///
    /// @param oreName OreDict 名称
    /// @return Tag 名称（不含命名空间）
    public static String convertOreDictNameToTagName(String oreName) {
        if (oreName == null || oreName.isEmpty()) {
            return oreName;
        }
        
        // 特殊处理：某些常见的后缀可以直接去掉
        String[] genericSuffixes = {"Wood", "Stone"};
        for (String suffix : genericSuffixes) {
            if (oreName.endsWith(suffix) && oreName.length() > suffix.length()) {
                // logWood → log, cobbleStone → cobble
                return oreName.substring(0, oreName.length() - suffix.length()).toLowerCase();
            }
        }
        
        // 驼峰拆分：找到第一个大写字母的位置
        int firstUpperCase = -1;
        for (int i = 1; i < oreName.length(); i++) {
            if (Character.isUpperCase(oreName.charAt(i))) {
                firstUpperCase = i;
                break;
            }
        }
        
        // 如果没有大写字母，直接转小写
        if (firstUpperCase == -1) {
            return oreName.toLowerCase();
        }
        
        // 拆分：类型 + 材料
        String type = oreName.substring(0, firstUpperCase).toLowerCase();
        String material = oreName.substring(firstUpperCase).toLowerCase();
        
        // 如果材料部分是通用词（Wood、Stone），则只保留类型
        if (isGenericMaterial(material)) {
            return type;
        }
        
        // 否则用斜杠分隔：type/material
        return type + "/" + material;
    }
    
    /**
     * 判断是否是通用材料词（可以忽略）
     */
    private static boolean isGenericMaterial(String material) {
        return material.equals("wood") || 
               material.equals("stone") ||
               material.equals("metal");
    }
    
    // ==================== OreDict → Tag ====================
    
    /// 将所有 OreDict 条目转换为 Tag<br>
    /// 转换规则（驼峰拆分 + 斜杠）：
    /// - OreDict "logWood" → Tag "forge:log"
    /// - OreDict "oreIron" → Tag "forge:ore/iron"
    /// - OreDict "ingotGold" → Tag "forge:ingot/gold"
    /// - 所有注册的 ItemStack 自动添加到对应 Tag
    public static void convertAllOreDictToTags() {
        LOGGER.info("Converting all OreDictionary entries to Tags...");
        
        String[] oreNames = OreDictionary.getOreNames();
        int converted = 0;
        
        for (String oreName : oreNames) {
            if (convertOreDictToTag(oreName)) {
                converted++;
            }
        }
        
        LOGGER.info("Converted {}/{} OreDict entries to Tags", converted, oreNames.length);
    }
    
    /**
     * 将单个 OreDict 条目转换为 Tag
     * 
     * 转换规则（驼峰拆分 + 斜杠）：
     * - oreIron → forge:ore/iron
     * - ingotIron → forge:ingot/iron
     * - logWood → forge:log
     * - dustRedstone → forge:dust/redstone
     * - gemDiamond → forge:gem/diamond
     * 
     * @param oreName OreDict 名称（如 "logWood"）
     * @return 是否成功转换
     */
    public static boolean convertOreDictToTag(String oreName) {
        ArrayList<ItemStack> ores = OreDictionary.getOres(oreName);
        if (ores == null || ores.isEmpty()) {
            LOGGER.debug("OreDict entry '{}' is empty, skipping", oreName);
            return false;
        }
        
        // 智能转换 OreDict 名称为 Tag 名称
        String tagName = convertOreDictNameToTagName(oreName);
        TagLoader<Item> itemLoader = CatFrameTags.itemLoader();
        TagLoader<Block> blockLoader = CatFrameTags.blockLoader();
        
        ResourceLocation tagLocation = new ResourceLocation(FORGE_NAMESPACE, tagName);
        
        // 获取或创建 Tag 内容集合（可修改）
        Set<Item> items = itemLoader.getOrCreateTagContents(tagLocation);
        Set<Block> blocks = blockLoader.getOrCreateTagContents(tagLocation);
        
        // 将所有 ItemStack 转换为 Item/Block 并添加到 Tag
        for (ItemStack stack : ores) {
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            
            Item item = stack.getItem();
            
            // 尝试作为 Block 添加
            try {
                Block block = Block.getBlockFromItem(item);
                if (block != null && block != Blocks.air) {
                    blocks.add(block);
                }
            } catch (Exception e) {
                // 不是方块，正常
            }
            
            // 作为 Item 添加
            items.add(item);
        }
        
        LOGGER.debug("Converted OreDict '{}' to Tag 'forge:{}' with {} items/blocks", 
            oreName, tagName, items.size() + blocks.size());
        
        return true;
    }
    
    /**
     * 批量转换指定的 OreDict 条目
     * 
     * @param oreNames OreDict 名称列表
     */
    public static void convertOreDictToTags(String... oreNames) {
        LOGGER.info("Converting {} OreDict entries to Tags...", oreNames.length);
        
        int converted = 0;
        for (String oreName : oreNames) {
            if (convertOreDictToTag(oreName)) {
                converted++;
            }
        }
        
        LOGGER.info("Converted {}/{} OreDict entries to Tags", converted, oreNames.length);
    }
    
    // ==================== Tag → OreDict ====================
    
    /**
     * 将 Tag 转换为 OreDict 条目
     * 
     * 转换规则：
     * - Tag "catframe:wool" → OreDict "wool"
     * - 自动将 Tag 中的所有 Item/Block 注册到 OreDict
     * 
     * @param tagFullName Tag 完整名称（如 "catframe:wool"）
     * @param oreName OreDict 名称（如 "wool"）
     */
    public static void convertTagToOreDict(String tagFullName, String oreName) {
        ResourceLocation tagLocation;
        try {
            tagLocation = new ResourceLocation(tagFullName);
        } catch (Exception e) {
            LOGGER.error("Invalid tag name: {}", tagFullName, e);
            return;
        }
        
        LOGGER.info("Converting Tag '{}' to OreDict '{}'", tagFullName, oreName);
        
        TagLoader<Item> itemLoader = CatFrameTags.itemLoader();
        TagLoader<Block> blockLoader = CatFrameTags.blockLoader();
        
        // 获取 Tag 内容
        Set<Item> items = itemLoader.getTagContents(tagLocation);
        Set<Block> blocks = blockLoader.getTagContents(tagLocation);
        
        int registered = 0;
        
        // 注册所有 Item
        for (Item item : items) {
            try {
                OreDictionary.registerOre(oreName, item);
                registered++;
            } catch (Exception e) {
                LOGGER.warn("Failed to register Item {} to OreDict '{}'", item, oreName, e);
            }
        }
        
        // 注册所有 Block
        for (Block block : blocks) {
            try {
                OreDictionary.registerOre(oreName, block);
                registered++;
            } catch (Exception e) {
                LOGGER.warn("Failed to register Block {} to OreDict '{}'", block, oreName, e);
            }
        }
        
        LOGGER.info("Registered {} items/blocks from Tag '{}' to OreDict '{}'", 
            registered, tagFullName, oreName);
    }
    
    /**
     * 批量转换 Tag 到 OreDict
     * 
     * @param tagToOreMap Tag 名称 → OreDict 名称的映射
     */
    public static void convertTagsToOreDicts(Map<String, String> tagToOreMap) {
        LOGGER.info("Converting {} Tags to OreDict entries...", tagToOreMap.size());
        
        for (Map.Entry<String, String> entry : tagToOreMap.entrySet()) {
            convertTagToOreDict(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * 自动将 forge 命名空间的所有 Tag 转换为 OreDict
     * 
     * 规则：Tag "forge:xxx" → OreDict "xxx"
     */
    public static void convertAllForgeTagsToOreDict() {
        LOGGER.info("Converting all forge: Tags to OreDictionary...");
        
        TagLoader<Item> itemLoader = CatFrameTags.itemLoader();
        TagLoader<Block> blockLoader = CatFrameTags.blockLoader();
        
        Set<ResourceLocation> itemTags = itemLoader.getAllTagNames();
        Set<ResourceLocation> blockTags = blockLoader.getAllTagNames();
        
        int converted = 0;
        
        // 转换物品标签
        for (ResourceLocation tagLocation : itemTags) {
            if (FORGE_NAMESPACE.equals(tagLocation.getResourceDomain())) {
                String oreName = tagLocation.getResourcePath();
                Set<Item> items = itemLoader.getTagContents(tagLocation);
                
                for (Item item : items) {
                    try {
                        OreDictionary.registerOre(oreName, item);
                        converted++;
                    } catch (Exception e) {
                        LOGGER.debug("Failed to register Item {} to OreDict '{}'", item, oreName);
                    }
                }
            }
        }
        
        // 转换方块标签
        for (ResourceLocation tagLocation : blockTags) {
            if (FORGE_NAMESPACE.equals(tagLocation.getResourceDomain())) {
                String oreName = tagLocation.getResourcePath();
                Set<Block> blocks = blockLoader.getTagContents(tagLocation);
                
                for (Block block : blocks) {
                    try {
                        OreDictionary.registerOre(oreName, block);
                        converted++;
                    } catch (Exception e) {
                        LOGGER.debug("Failed to register Block {} to OreDict '{}'", block, oreName);
                    }
                }
            }
        }
        
        LOGGER.info("Converted {} items/blocks from forge: Tags to OreDict", converted);
    }
    
    // ==================== 双向同步 ====================
    
    /**
     * 检查 OreDict 和 Tag 是否同步
     * 
     * @param oreName OreDict 名称
     * @param tagName Tag 名称
     * @return 是否同步
     */
    public static boolean isSynced(String oreName, String tagName) {
        ArrayList<ItemStack> oreStacks = OreDictionary.getOres(oreName);
        ResourceLocation tagLocation;
        
        try {
            tagLocation = new ResourceLocation(tagName);
        } catch (Exception e) {
            return false;
        }
        
        TagLoader<Item> itemLoader = CatFrameTags.itemLoader();
        Set<Item> tagItems = itemLoader.getTagContents(tagLocation);
        
        // 简单检查：数量是否一致
        return oreStacks.size() == tagItems.size();
    }
    
    /**
     * 同步 OreDict 和 Tag（双向）
     * 
     * @param oreName OreDict 名称
     * @param tagName Tag 名称
     */
    public static void syncOreDictAndTag(String oreName, String tagName) {
        LOGGER.info("Syncing OreDict '{}' with Tag '{}'", oreName, tagName);
        
        // OreDict → Tag
        convertOreDictToTag(oreName);
        
        // Tag → OreDict
        convertTagToOreDict(tagName, oreName);
    }
    
    // ==================== 便捷方法 ====================
    
    /**
     * 通过 OreDict 名称检查物品是否属于某个分类
     * 
     * @param item 物品
     * @param oreName OreDict 名称
     * @return 是否属于
     */
    public static boolean isItemInOreDict(Item item, String oreName) {
        ArrayList<ItemStack> ores = OreDictionary.getOres(oreName);
        
        for (ItemStack oreStack : ores) {
            if (oreStack != null && oreStack.getItem() == item) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 通过 Tag 名称检查物品是否属于某个分类
     * 
     * @param item 物品
     * @param tagName Tag 名称
     * @return 是否属于
     */
    public static boolean isItemInTag(Item item, String tagName) {
        return CatFrameTags.is(item, tagName);
    }
    
    /**
     * 获取 OreDict 中的所有物品（转换为 Item 集合）
     * 
     * @param oreName OreDict 名称
     * @return 物品集合
     */
    public static Set<Item> getOreDictItems(String oreName) {
        Set<Item> items = new HashSet<>();
        ArrayList<ItemStack> stacks = OreDictionary.getOres(oreName);
        
        for (ItemStack stack : stacks) {
            if (stack != null && stack.getItem() != null) {
                items.add(stack.getItem());
            }
        }
        
        return items;
    }
}
