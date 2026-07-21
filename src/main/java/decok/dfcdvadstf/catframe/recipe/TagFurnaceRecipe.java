package decok.dfcdvadstf.catframe.recipe;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import decok.dfcdvadstf.catframe.tags.impl.CatFrameTags;

import java.util.Set;

/**
 * 熔炼 Tag 配方工具类
 * 
 * 为 Tag 中的所有物品批量添加熔炼配方
 * 不是真正的 IRecipe，而是用于简化熔炼配方注册的工具
 * 
 * 使用示例：
 * <pre>
 * // 为 Tag 中的所有矿石添加熔炼配方
 * TagFurnaceRecipe.addSmeltingForTag("forge:ores_iron", new ItemStack(Items.iron_ingot), 0.7F);
 * 
 * // 为单个物品添加熔炼配方
 * TagFurnaceRecipe.addSmelting(Items.iron_ore, new ItemStack(Items.iron_ingot), 0.7F);
 * </pre>
 */
public final class TagFurnaceRecipe {
    
    private static final Logger LOGGER = LogManager.getLogger(TagFurnaceRecipe.class);
    
    private TagFurnaceRecipe() {
        // 工具类，禁止实例化
    }
    
    /**
     * 为 Tag 中的所有物品添加熔炼配方
     * 
     * @param tagName Tag 名称（如 "forge:ores" 或 "catframe:my_ores"）
     * @param result 熔炼结果
     * @param xp 经验值
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
     * 为 Tag 中的所有方块添加熔炼配方
     * 
     * @param tagName Tag 名称
     * @param result 熔炼结果
     * @param xp 经验值
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
     * 为单个物品添加熔炼配方
     * 
     * @param input 输入物品
     * @param result 熔炼结果
     * @param xp 经验值
     */
    public static void addSmelting(Item input, ItemStack result, float xp) {
        FurnaceRecipes.smelting().func_151396_a(input, result, xp);
    }
    
    /**
     * 为单个方块添加熔炼配方
     * 
     * @param input 输入方块
     * @param result 熔炼结果
     * @param xp 经验值
     */
    public static void addSmelting(Block input, ItemStack result, float xp) {
        FurnaceRecipes.smelting().func_151393_a(input, result, xp);
    }
    
    /**
     * 为单个 ItemStack 添加熔炼配方
     * 
     * @param input 输入 ItemStack
     * @param result 熔炼结果
     * @param xp 经验值
     */
    public static void addSmelting(ItemStack input, ItemStack result, float xp) {
        FurnaceRecipes.smelting().func_151394_a(input, result, xp);
    }
    
    /**
     * 批量添加熔炼配方
     * 
     * @param inputs 输入物品数组
     * @param result 熔炼结果
     * @param xp 经验值
     */
    public static void addSmeltingAll(Item[] inputs, ItemStack result, float xp) {
        for (Item input : inputs) {
            addSmelting(input, result, xp);
        }
    }
    
    /**
     * 批量添加熔炼配方（方块）
     * 
     * @param inputs 输入方块数组
     * @param result 熔炼结果
     * @param xp 经验值
     */
    public static void addSmeltingAll(Block[] inputs, ItemStack result, float xp) {
        for (Block input : inputs) {
            addSmelting(input, result, xp);
        }
    }
}
