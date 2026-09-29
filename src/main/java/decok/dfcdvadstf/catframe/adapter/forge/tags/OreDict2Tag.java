package decok.dfcdvadstf.catframe.adapter.forge.tags;

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

/**
 * OreDict ↔ Tag bidirectional converter
 * Implements interoperability between Forge OreDictionary and CatFrame Tag systems:
 * - OreDict → Tag: converts OreDict entries to Tags
 * - Tag → OreDict: registers Tags to OreDict
 *
 * Unified namespace uses "forge".
 * Use cases:
 * 1. Compatibility with legacy mods using OreDict
 * 2. Gradual migration to the new Tag system
 * 3. Support both systems simultaneously
 * Usage examples:
 * <pre>
 * // OreDict → Tag
 * OreDict2Tag.convertOreDictToTags();
 *
 * // Tag → OreDict
 * OreDict2Tag.convertTagToOreDict("catframe:wool", "wool");
 * </pre>
 */
public final class OreDict2Tag {
    
    private static final Logger LOGGER = LogManager.getLogger(OreDict2Tag.class);
    
    /** Forge namespace */
    public static final String FORGE_NAMESPACE = "forge";
    
    private OreDict2Tag() {
        // Utility class, no instantiation
    }
    
// ==================== Name conversion ====================

/// Smart OreDict name to Tag name conversion
/// Conversion rules (camelCase split + slash):
/// - oreIron → ore/iron
/// - ingotIron → ingot/iron
/// - logWood → log (ignores generic suffix Wood)
/// - dustRedstone → dust/redstone
/// - gemDiamond → gem/diamond
/// - blockGold → block/gold
/// - treeSapling → tree/sapling
///
/// @param oreName OreDict name
/// @return Tag name (without namespace)
    public static String convertOreDictNameToTagName(String oreName) {
        if (oreName == null || oreName.isEmpty()) {
            return oreName;
        }
        
        // Special handling: some common suffixes can be dropped
        String[] genericSuffixes = {"Wood", "Stone"};
        for (String suffix : genericSuffixes) {
            if (oreName.endsWith(suffix) && oreName.length() > suffix.length()) {
                // logWood → log, cobbleStone → cobble
                return oreName.substring(0, oreName.length() - suffix.length()).toLowerCase();
            }
        }
        
        // CamelCase split: find first uppercase letter
        int firstUpperCase = -1;
        for (int i = 1; i < oreName.length(); i++) {
            if (Character.isUpperCase(oreName.charAt(i))) {
                firstUpperCase = i;
                break;
            }
        }
        
        // If no uppercase letter, just lowercase
        if (firstUpperCase == -1) {
            return oreName.toLowerCase();
        }
        
        // Split: type + material
        String type = oreName.substring(0, firstUpperCase).toLowerCase();
        String material = oreName.substring(firstUpperCase).toLowerCase();
        
        // If material part is generic (Wood, Stone), keep only type
        if (isGenericMaterial(material)) {
            return type;
        }
        
        // Otherwise use slash separator: type/material
        return type + "/" + material;
    }
    
    /**
     * Check if material is a generic word (can be ignored)
     */
    private static boolean isGenericMaterial(String material) {
        return material.equals("wood") || 
               material.equals("stone") ||
               material.equals("metal");
    }
    
    // ==================== OreDict → Tag ====================
    
    /// Convert all OreDict entries to Tags
    /// Conversion rules (camelCase split + slash):
    /// - OreDict "logWood" → Tag "forge:log"
    /// - OreDict "oreIron" → Tag "forge:ore/iron"
    /// - OreDict "ingotGold" → Tag "forge:ingot/gold"
    /// - All registered ItemStacks automatically added to corresponding Tags
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
     * Convert a single OreDict entry to Tag
     * 
     * Conversion rules (camelCase split + slash):
     * - oreIron → forge:ore/iron
     * - ingotIron → forge:ingot/iron
     * - logWood → forge:log
     * - dustRedstone → forge:dust/redstone
     * - gemDiamond → forge:gem/diamond
     * 
     * @param oreName OreDict name (e.g. "logWood")
     * @return whether conversion succeeded
     */
    public static boolean convertOreDictToTag(String oreName) {
        ArrayList<ItemStack> ores = OreDictionary.getOres(oreName);
        if (ores == null || ores.isEmpty()) {
            LOGGER.debug("OreDict entry '{}' is empty, skipping", oreName);
            return false;
        }
        
        // Smart OreDict name to Tag name conversion
        String tagName = convertOreDictNameToTagName(oreName);
        TagLoader<Item> itemLoader = CatFrameTags.itemLoader();
        TagLoader<Block> blockLoader = CatFrameTags.blockLoader();
        
        ResourceLocation tagLocation = new ResourceLocation(FORGE_NAMESPACE, tagName);
        
        // Get or create Tag content set (modifiable)
        Set<Item> items = itemLoader.getOrCreateTagContents(tagLocation);
        Set<Block> blocks = blockLoader.getOrCreateTagContents(tagLocation);
        
        // Convert all ItemStacks to Item/Block and add to Tag
        for (ItemStack stack : ores) {
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            
            Item item = stack.getItem();
            
            // Try to add as Block
            try {
                Block block = Block.getBlockFromItem(item);
                if (block != null && block != Blocks.air) {
                    blocks.add(block);
                }
            } catch (Exception e) {
                // Not a block, normal
            }
            
            // Add as Item
            items.add(item);
        }
        
        LOGGER.debug("Converted OreDict '{}' to Tag 'forge:{}' with {} items/blocks", 
            oreName, tagName, items.size() + blocks.size());
        
        return true;
    }
    
    /**
     * Batch convert specified OreDict entries
     * 
     * @param oreNames list of OreDict names
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
     * Convert Tag to OreDict entry
     * 
     * Conversion rules:
     * - Tag "catframe:wool" → OreDict "wool"
     * - Automatically registers all Items/Blocks in Tag to OreDict
     * 
     * @param tagFullName full Tag name (e.g. "catframe:wool")
     * @param oreName OreDict name (e.g. "wool")
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
        
        // Get Tag contents
        Set<Item> items = itemLoader.getTagContents(tagLocation);
        Set<Block> blocks = blockLoader.getTagContents(tagLocation);
        
        int registered = 0;
        
        // Register all Items
        for (Item item : items) {
            try {
                OreDictionary.registerOre(oreName, item);
                registered++;
            } catch (Exception e) {
                LOGGER.warn("Failed to register Item {} to OreDict '{}'", item, oreName, e);
            }
        }
        
        // Register all Blocks
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
     * Batch convert Tags to OreDicts
     * 
     * @param tagToOreMap Tag name → OreDict name mapping
     */
    public static void convertTagsToOreDicts(Map<String, String> tagToOreMap) {
        LOGGER.info("Converting {} Tags to OreDict entries...", tagToOreMap.size());
        
        for (Map.Entry<String, String> entry : tagToOreMap.entrySet()) {
            convertTagToOreDict(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Automatically convert all forge namespace Tags to OreDict
     * 
     * Rule: Tag "forge:xxx" → OreDict "xxx"
     */
    public static void convertAllForgeTagsToOreDict() {
        LOGGER.info("Converting all forge: Tags to OreDictionary...");
        
        TagLoader<Item> itemLoader = CatFrameTags.itemLoader();
        TagLoader<Block> blockLoader = CatFrameTags.blockLoader();
        
        Set<ResourceLocation> itemTags = itemLoader.getAllTagNames();
        Set<ResourceLocation> blockTags = blockLoader.getAllTagNames();
        
        int converted = 0;
        
        // Convert item tags
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
        
        // Convert block tags
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
    
    // ==================== Bidirectional sync ====================
    
    /**
     * Check if OreDict and Tag are in sync
     * 
     * @param oreName OreDict name
     * @param tagName Tag name
     * @return whether in sync
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
        
        // Simple check: are counts equal?
        return oreStacks.size() == tagItems.size();
    }
    
    /**
     * Sync OreDict and Tag (bidirectional)
     * 
     * @param oreName OreDict name
     * @param tagName Tag name
     */
    public static void syncOreDictAndTag(String oreName, String tagName) {
        LOGGER.info("Syncing OreDict '{}' with Tag '{}'", oreName, tagName);
        
        // OreDict → Tag
        convertOreDictToTag(oreName);
        
        // Tag → OreDict
        convertTagToOreDict(tagName, oreName);
    }
    
    // ==================== Convenience methods ====================
    
    /**
     * Check if item belongs to an OreDict category
     * 
     * @param item item
     * @param oreName OreDict name
     * @return whether it belongs
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
     * Check if item belongs to a Tag category
     * 
     * @param item item
     * @param tagName Tag name
     * @return whether it belongs
     */
    public static boolean isItemInTag(Item item, String tagName) {
        return CatFrameTags.is(item, tagName);
    }
    
    /**
     * Get all items in OreDict (as Item set)
     * 
     * @param oreName OreDict name
     * @return item set
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
