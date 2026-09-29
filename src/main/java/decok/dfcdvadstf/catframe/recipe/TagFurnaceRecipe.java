package decok.dfcdvadstf.catframe.recipe;

import decok.dfcdvadstf.catframe.tags.impl.CatFrameTags;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

/**
 * Smelting Tag Recipe Utility
 * 
 * Batch adds smelting recipes for all items in a Tag
 * Not a real IRecipe, just a utility to simplify smelting recipe registration
 * 
 * Usage examples:
 * <pre>
 * // Add smelting recipes for all ores in a Tag
 * TagFurnaceRecipe.addSmeltingForTag("forge:ores_iron", new ItemStack(Items.iron_ingot), 0.7F);
 * 
 * // Add smelting recipe for a single item
 * TagFurnaceRecipe.addSmelting(Items.iron_ore, new ItemStack(Items.iron_ingot), 0.7F);
 * </pre>
 */
public final class TagFurnaceRecipe {
    
    private static final Logger LOGGER = LogManager.getLogger(TagFurnaceRecipe.class);
    
    private TagFurnaceRecipe() {
        // Utility class, no instantiation
    }
    
    /**
     * Add smelting recipes for all items in a Tag
     * 
     * @param tagName Tag name (e.g. "forge:ores" or "catframe:my_ores")
     * @param result smelting result
     * @param xp experience value
     */
    public static void addSmeltingForTag(String tagName, ItemStack result, float xp) {
        ResourceLocation tagLocation;
        try {
            tagLocation = new ResourceLocation(tagName);
        } catch (Exception e) {
            LOGGER.error("Invalid tag name: {}", tagName, e);
            return;
        }
        
        Set<Item> items = CatFrameTags.itemLoader().getTagContents(tagLocation);
        
        if (items.isEmpty()) {
            LOGGER.warn("Tag '{}' is empty, no smelting recipes added", tagName);
            return;
        }
        
        int added = 0;
        for (Item item : items) {
            try {
                addSmelting(item, result, xp);
                added++;
            } catch (Exception e) {
                LOGGER.warn("Failed to add smelting recipe for {}", item, e);
            }
        }
        
        LOGGER.info("Added {} smelting recipes for tag '{}'", added, tagName);
    }
    
    /**
     * Add smelting recipes for all blocks in a Tag
     * 
     * @param tagName Tag name
     * @param result smelting result
     * @param xp experience value
     */
    public static void addSmeltingBlocksForTag(String tagName, ItemStack result, float xp) {
        ResourceLocation tagLocation;
        try {
            tagLocation = new ResourceLocation(tagName);
        } catch (Exception e) {
            LOGGER.error("Invalid tag name: {}", tagName, e);
            return;
        }
        
        Set<Block> blocks = CatFrameTags.blockLoader().getTagContents(tagLocation);
        
        if (blocks.isEmpty()) {
            LOGGER.warn("Block tag '{}' is empty, no smelting recipes added", tagName);
            return;
        }
        
        int added = 0;
        for (Block block : blocks) {
            try {
                addSmelting(block, result, xp);
                added++;
            } catch (Exception e) {
                LOGGER.warn("Failed to add smelting recipe for {}", block, e);
            }
        }
        
        LOGGER.info("Added {} block smelting recipes for tag '{}'", added, tagName);
    }
    
    /**
     * Add smelting recipe for a single item
     * 
     * @param input input item
     * @param result smelting result
     * @param xp experience value
     */
    public static void addSmelting(Item input, ItemStack result, float xp) {
        FurnaceRecipes.smelting().func_151396_a(input, result, xp);
    }
    
    /**
     * Add smelting recipe for a single block
     * 
     * @param input input block
     * @param result smelting result
     * @param xp experience value
     */
    public static void addSmelting(Block input, ItemStack result, float xp) {
        FurnaceRecipes.smelting().func_151393_a(input, result, xp);
    }
    
    /**
     * Add smelting recipe for a single ItemStack
     * 
     * @param input input ItemStack
     * @param result smelting result
     * @param xp experience value
     */
    public static void addSmelting(ItemStack input, ItemStack result, float xp) {
        FurnaceRecipes.smelting().func_151394_a(input, result, xp);
    }
    
    /**
     * Batch add smelting recipes
     * 
     * @param inputs input items array
     * @param result smelting result
     * @param xp experience value
     */
    public static void addSmeltingAll(Item[] inputs, ItemStack result, float xp) {
        for (Item input : inputs) {
            addSmelting(input, result, xp);
        }
    }
    
    /**
     * Batch add smelting recipes (blocks)
     * 
     * @param inputs input blocks array
     * @param result smelting result
     * @param xp experience value
     */
    public static void addSmeltingAll(Block[] inputs, ItemStack result, float xp) {
        for (Block input : inputs) {
            addSmelting(input, result, xp);
        }
    }
}
