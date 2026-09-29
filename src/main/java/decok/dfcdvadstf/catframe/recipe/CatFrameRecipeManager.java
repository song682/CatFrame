package decok.dfcdvadstf.catframe.recipe;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * CatFrame Recipe Manager
 * 
 * Provides a unified API for adding and managing Tag-compatible recipes
 * Supports: shaped, shapeless, smelting
 * Also provides recipe removal (not available in vanilla)
 * 
 * Usage examples:
 * <pre>
 * // Add shaped recipe (using Tag)
 * CatFrameRecipeManager.addShaped(
 *     new ItemStack(Blocks.chest),
 *     "###", "# #", "###",
 *     '#', "catframe:planks"
 * );
 * 
 * // Add shapeless recipe
 * CatFrameRecipeManager.addShapeless(
 *     new ItemStack(Items.iron_ingot, 9),
 *     "catframe:blockIron"
 * );
 * 
 * // Add smelting recipe
 * CatFrameRecipeManager.addSmelting(Items.iron_ore, new ItemStack(Items.iron_ingot), 0.7F);
 * 
 * // Remove recipes
 * CatFrameRecipeManager.removeRecipesByOutput(Blocks.crafting_table);
 * </pre>
 */
public final class CatFrameRecipeManager {
    
    private static final Logger LOGGER = LogManager.getLogger(CatFrameRecipeManager.class);
    
    private CatFrameRecipeManager() {
        // Utility class, no instantiation
    }
    
    /**
     * Get vanilla crafting recipe list (wrapped as generic list to eliminate unchecked warning).
     */
    @SuppressWarnings("unchecked")
    private static List<IRecipe> getCraftingRecipeList() {
        return CraftingManager.getInstance().getRecipeList();
    }
    
    // ==================== Add Shaped Recipes ====================
    
    /**
     * Add a shaped recipe
     * 
     * @param result output item
     * @param recipe recipe definition (shape + ingredient mapping)
     * @return created recipe object
     */
    public static IRecipe addShaped(ItemStack result, Object... recipe) {
        ShapedTagRecipe shapedRecipe = new ShapedTagRecipe(result, recipe);
        getCraftingRecipeList().add(shapedRecipe);
        LOGGER.info("Added shaped recipe for {}", result);
        return shapedRecipe;
    }
    
    /**
     * Add a shaped recipe (using Item as output)
     */
    public static IRecipe addShaped(Item result, Object... recipe) {
        return addShaped(new ItemStack(result), recipe);
    }
    
    /**
     * Add a shaped recipe (using Block as output)
     */
    public static IRecipe addShaped(Block result, Object... recipe) {
        return addShaped(new ItemStack(result), recipe);
    }
    
    // ==================== Add Shapeless Recipes ====================
    
    /**
     * Add a shapeless recipe
     * 
     * @param result output item
     * @param recipe recipe ingredients (no shape required)
     * @return created recipe object
     */
    public static IRecipe addShapeless(ItemStack result, Object... recipe) {
        ShapelessTagRecipe shapelessRecipe = new ShapelessTagRecipe(result, recipe);
        getCraftingRecipeList().add(shapelessRecipe);
        LOGGER.info("Added shapeless recipe for {}", result);
        return shapelessRecipe;
    }
    
    /**
     * Add a shapeless recipe (using Item as output)
     */
    public static IRecipe addShapeless(Item result, Object... recipe) {
        return addShapeless(new ItemStack(result), recipe);
    }
    
    /**
     * Add a shapeless recipe (using Block as output)
     */
    public static IRecipe addShapeless(Block result, Object... recipe) {
        return addShapeless(new ItemStack(result), recipe);
    }
    
    // ==================== Add Smelting Recipes ====================
    
    /**
     * Add a smelting recipe
     * 
     * @param input input item
     * @param result smelting result
     * @param xp experience value
     */
    public static void addSmelting(Item input, ItemStack result, float xp) {
        TagFurnaceRecipe.addSmelting(input, result, xp);
        LOGGER.info("Added smelting recipe for {} -> {}", input, result);
    }
    
    /**
     * Add a smelting recipe (block input)
     */
    public static void addSmelting(Block input, ItemStack result, float xp) {
        TagFurnaceRecipe.addSmelting(input, result, xp);
        LOGGER.info("Added smelting recipe for {} -> {}", input, result);
    }
    
    /**
     * Add a smelting recipe (ItemStack input)
     */
    public static void addSmelting(ItemStack input, ItemStack result, float xp) {
        TagFurnaceRecipe.addSmelting(input, result, xp);
        LOGGER.info("Added smelting recipe for {} -> {}", input, result);
    }
    
    /**
     * Add smelting recipes for all items in a Tag
     * 
     * @param tagName Tag name (e.g. "forge:ores")
     * @param result smelting result
     * @param xp experience value
     */
    public static void addSmeltingForTag(String tagName, ItemStack result, float xp) {
        TagFurnaceRecipe.addSmeltingForTag(tagName, result, xp);
    }
    
    /**
     * Add smelting recipes for all blocks in a Tag
     */
    public static void addSmeltingBlocksForTag(String tagName, ItemStack result, float xp) {
        TagFurnaceRecipe.addSmeltingBlocksForTag(tagName, result, xp);
    }
    
    // ==================== Remove Crafting Recipes ====================
    
    /**
     * Remove all crafting recipes with the given output item
     * 
     * @param outputItem output item
     * @return number of recipes removed
     */
    public static int removeRecipesByOutput(Item outputItem) {
        return removeRecipes(recipe -> {
            ItemStack output = recipe.getRecipeOutput();
            return output != null && output.getItem() == outputItem;
        });
    }
    
    /**
     * Remove all crafting recipes with the given output item (including metadata matching)
     * 
     * @param outputStack output item (with metadata)
     * @return number of recipes removed
     */
    public static int removeRecipesByOutput(ItemStack outputStack) {
        return removeRecipes(recipe -> {
            ItemStack output = recipe.getRecipeOutput();
            if (output == null) return false;
            if (output.getItem() != outputStack.getItem()) return false;
            
            // If outputStack's damage is 32767 (wildcard), match all
            if (outputStack.getItemDamage() == 32767) return true;
            
            return output.getItemDamage() == outputStack.getItemDamage();
        });
    }
    
    /**
     * Remove crafting recipes matching a specific condition
     * 
     * @param condition condition predicate
     * @return number of recipes removed
     */
    public static int removeRecipes(RecipePredicate condition) {
        List<IRecipe> recipes = getCraftingRecipeList();
        Iterator<IRecipe> iterator = recipes.iterator();
        int removed = 0;
        
        while (iterator.hasNext()) {
            IRecipe recipe = iterator.next();
            if (condition.test(recipe)) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            LOGGER.info("Removed {} crafting recipes", removed);
        }
        
        return removed;
    }
    
    /**
     * Remove all crafting recipes
     * 
     * @return number of recipes removed
     */
    public static int removeAllRecipes() {
        List<IRecipe> recipes = getCraftingRecipeList();
        int count = recipes.size();
        recipes.clear();
        LOGGER.info("Removed all {} crafting recipes", count);
        return count;
    }
    
    // ==================== Remove Smelting Recipes ====================
    
    /**
     * Remove smelting recipe for the given input item
     * 
     * @param input input item
     * @return number of recipes removed
     */
    public static int removeSmelting(Item input) {
        return removeSmelting(new ItemStack(input, 1, 32767));
    }
    
    /**
     * Remove smelting recipe for the given input block
     * 
     * @param input input block
     * @return number of recipes removed
     */
    public static int removeSmelting(Block input) {
        return removeSmelting(new ItemStack(input, 1, 32767));
    }
    
    /**
     * Remove smelting recipe for the given input ItemStack
     * 
     * @param inputStack input ItemStack
     * @return number of recipes removed
     */
    @SuppressWarnings("unchecked")
    public static int removeSmelting(ItemStack inputStack) {
        Map<ItemStack, ItemStack> smeltingList = FurnaceRecipes.smelting().getSmeltingList();
        Iterator<Map.Entry<ItemStack, ItemStack>> iterator = smeltingList.entrySet().iterator();
        int removed = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<ItemStack, ItemStack> entry = iterator.next();
            ItemStack key = entry.getKey();
            
            // Check if matches
            if (key.getItem() == inputStack.getItem()) {
                // If wildcard or damage matches
                if (inputStack.getItemDamage() == 32767 || 
                    key.getItemDamage() == inputStack.getItemDamage() ||
                    key.getItemDamage() == 32767) {
                    iterator.remove();
                    removed++;
                }
            }
        }
        
        if (removed > 0) {
            LOGGER.info("Removed {} smelting recipes for {}", removed, inputStack);
        }
        
        return removed;
    }
    
    /**
     * Remove all smelting recipes
     * 
     * @return number of recipes removed
     */
    @SuppressWarnings("unchecked")
    public static int removeAllSmelting() {
        Map<ItemStack, ItemStack> smeltingList = FurnaceRecipes.smelting().getSmeltingList();
        int count = smeltingList.size();
        smeltingList.clear();
        LOGGER.info("Removed all {} smelting recipes", count);
        return count;
    }
    
    // ==================== Query Recipes ====================
    
    /**
     * Get all crafting recipes
     */
    public static List<IRecipe> getAllRecipes() {
        return getCraftingRecipeList();
    }
    
    /**
     * Count crafting recipes
     */
    public static int getRecipeCount() {
        return getCraftingRecipeList().size();
    }
    
    /**
     * Count smelting recipes
     */
    @SuppressWarnings("unchecked")
    public static int getSmeltingCount() {
        return FurnaceRecipes.smelting().getSmeltingList().size();
    }
    
    // ==================== Internal Interfaces ====================
    
    /**
     * Recipe condition predicate interface
     * Used to filter recipes for removal
     */
    public interface RecipePredicate {
        boolean test(IRecipe recipe);
    }
}
