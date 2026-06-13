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
 * CatFrame 配方管理器
 * 
 * 提供统一的 API 来添加和管理 Tag 兼容的合成配方
 * 支持：有序合成、无序合成、熔炼
 * 同时提供配方删除功能（原版没有的功能）
 * 
 * 使用示例：
 * <pre>
 * // 添加有序配方（使用 Tag）
 * CatFrameRecipeManager.addShaped(
 *     new ItemStack(Blocks.chest),
 *     "###", "# #", "###",
 *     '#', "catframe:planks"
 * );
 * 
 * // 添加无序配方
 * CatFrameRecipeManager.addShapeless(
 *     new ItemStack(Items.iron_ingot, 9),
 *     "catframe:blockIron"
 * );
 * 
 * // 添加熔炼配方
 * CatFrameRecipeManager.addSmelting(Items.iron_ore, new ItemStack(Items.iron_ingot), 0.7F);
 * 
 * // 删除配方
 * CatFrameRecipeManager.removeRecipesByOutput(Blocks.crafting_table);
 * </pre>
 */
public final class CatFrameRecipeManager {
    
    private static final Logger LOGGER = LogManager.getLogger(CatFrameRecipeManager.class);
    
    private CatFrameRecipeManager() {
        // 工具类，禁止实例化
    }
    
    // ==================== 添加有序配方 ====================
    
    /**
     * 添加有序合成配方
     * 
     * @param result 输出物品
     * @param recipe 配方定义（形状 + 材料映射）
     * @return 创建的配方对象
     */
    public static IRecipe addShaped(ItemStack result, Object... recipe) {
        ShapedTagRecipe shapedRecipe = new ShapedTagRecipe(result, recipe);
        CraftingManager.getInstance().getRecipeList().add(shapedRecipe);
        LOGGER.info("Added shaped recipe for {}", result);
        return shapedRecipe;
    }
    
    /**
     * 添加有序合成配方（使用 Item 作为输出）
     */
    public static IRecipe addShaped(Item result, Object... recipe) {
        return addShaped(new ItemStack(result), recipe);
    }
    
    /**
     * 添加有序合成配方（使用 Block 作为输出）
     */
    public static IRecipe addShaped(Block result, Object... recipe) {
        return addShaped(new ItemStack(result), recipe);
    }
    
    // ==================== 添加无序配方 ====================
    
    /**
     * 添加无序合成配方
     * 
     * @param result 输出物品
     * @param recipe 配方材料（不需要形状）
     * @return 创建的配方对象
     */
    public static IRecipe addShapeless(ItemStack result, Object... recipe) {
        ShapelessTagRecipe shapelessRecipe = new ShapelessTagRecipe(result, recipe);
        CraftingManager.getInstance().getRecipeList().add(shapelessRecipe);
        LOGGER.info("Added shapeless recipe for {}", result);
        return shapelessRecipe;
    }
    
    /**
     * 添加无序合成配方（使用 Item 作为输出）
     */
    public static IRecipe addShapeless(Item result, Object... recipe) {
        return addShapeless(new ItemStack(result), recipe);
    }
    
    /**
     * 添加无序合成配方（使用 Block 作为输出）
     */
    public static IRecipe addShapeless(Block result, Object... recipe) {
        return addShapeless(new ItemStack(result), recipe);
    }
    
    // ==================== 添加熔炼配方 ====================
    
    /**
     * 添加熔炼配方
     * 
     * @param input 输入物品
     * @param result 熔炼结果
     * @param xp 经验值
     */
    public static void addSmelting(Item input, ItemStack result, float xp) {
        TagFurnaceRecipe.addSmelting(input, result, xp);
        LOGGER.info("Added smelting recipe for {} -> {}", input, result);
    }
    
    /**
     * 添加熔炼配方（方块输入）
     */
    public static void addSmelting(Block input, ItemStack result, float xp) {
        TagFurnaceRecipe.addSmelting(input, result, xp);
        LOGGER.info("Added smelting recipe for {} -> {}", input, result);
    }
    
    /**
     * 添加熔炼配方（ItemStack 输入）
     */
    public static void addSmelting(ItemStack input, ItemStack result, float xp) {
        TagFurnaceRecipe.addSmelting(input, result, xp);
        LOGGER.info("Added smelting recipe for {} -> {}", input, result);
    }
    
    /**
     * 为 Tag 中的所有物品添加熔炼配方
     * 
     * @param tagName Tag 名称（如 "forge:ores"）
     * @param result 熔炼结果
     * @param xp 经验值
     */
    public static void addSmeltingForTag(String tagName, ItemStack result, float xp) {
        TagFurnaceRecipe.addSmeltingForTag(tagName, result, xp);
    }
    
    /**
     * 为 Tag 中的所有方块添加熔炼配方
     */
    public static void addSmeltingBlocksForTag(String tagName, ItemStack result, float xp) {
        TagFurnaceRecipe.addSmeltingBlocksForTag(tagName, result, xp);
    }
    
    // ==================== 删除合成配方 ====================
    
    /**
     * 删除指定输出物品的所有合成配方
     * 
     * @param outputItem 输出物品
     * @return 删除的配方数量
     */
    public static int removeRecipesByOutput(Item outputItem) {
        return removeRecipes(recipe -> {
            ItemStack output = recipe.getRecipeOutput();
            return output != null && output.getItem() == outputItem;
        });
    }
    
    /**
     * 删除指定输出物品的所有合成配方（包括 metadata 匹配）
     * 
     * @param outputStack 输出物品（包含 metadata）
     * @return 删除的配方数量
     */
    public static int removeRecipesByOutput(ItemStack outputStack) {
        return removeRecipes(recipe -> {
            ItemStack output = recipe.getRecipeOutput();
            if (output == null) return false;
            if (output.getItem() != outputStack.getItem()) return false;
            
            // 如果 outputStack 的 damage 是 32767（通配符），匹配所有
            if (outputStack.getItemDamage() == 32767) return true;
            
            return output.getItemDamage() == outputStack.getItemDamage();
        });
    }
    
    /**
     * 删除匹配特定条件的合成配方
     * 
     * @param condition 条件谓词
     * @return 删除的配方数量
     */
    public static int removeRecipes(RecipePredicate condition) {
        List<IRecipe> recipes = CraftingManager.getInstance().getRecipeList();
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
     * 删除所有合成配方
     * 
     * @return 删除的配方数量
     */
    public static int removeAllRecipes() {
        List<IRecipe> recipes = CraftingManager.getInstance().getRecipeList();
        int count = recipes.size();
        recipes.clear();
        LOGGER.info("Removed all {} crafting recipes", count);
        return count;
    }
    
    // ==================== 删除熔炼配方 ====================
    
    /**
     * 删除指定输入物品的熔炼配方
     * 
     * @param input 输入物品
     * @return 删除的配方数量
     */
    public static int removeSmelting(Item input) {
        return removeSmelting(new ItemStack(input, 1, 32767));
    }
    
    /**
     * 删除指定输入方块的熔炼配方
     * 
     * @param input 输入方块
     * @return 删除的配方数量
     */
    public static int removeSmelting(Block input) {
        return removeSmelting(new ItemStack(input, 1, 32767));
    }
    
    /**
     * 删除指定输入 ItemStack 的熔炼配方
     * 
     * @param inputStack 输入 ItemStack
     * @return 删除的配方数量
     */
    @SuppressWarnings("unchecked")
    public static int removeSmelting(ItemStack inputStack) {
        Map<ItemStack, ItemStack> smeltingList = FurnaceRecipes.smelting().getSmeltingList();
        Iterator<Map.Entry<ItemStack, ItemStack>> iterator = smeltingList.entrySet().iterator();
        int removed = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<ItemStack, ItemStack> entry = iterator.next();
            ItemStack key = entry.getKey();
            
            // 检查是否匹配
            if (key.getItem() == inputStack.getItem()) {
                // 如果是通配符或者 damage 匹配
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
     * 删除所有熔炼配方
     * 
     * @return 删除的配方数量
     */
    @SuppressWarnings("unchecked")
    public static int removeAllSmelting() {
        Map<ItemStack, ItemStack> smeltingList = FurnaceRecipes.smelting().getSmeltingList();
        int count = smeltingList.size();
        smeltingList.clear();
        LOGGER.info("Removed all {} smelting recipes", count);
        return count;
    }
    
    // ==================== 查询配方 ====================
    
    /**
     * 获取所有合成配方列表
     */
    public static List<IRecipe> getAllRecipes() {
        return CraftingManager.getInstance().getRecipeList();
    }
    
    /**
     * 统计合成配方数量
     */
    public static int getRecipeCount() {
        return CraftingManager.getInstance().getRecipeList().size();
    }
    
    /**
     * 统计熔炼配方数量
     */
    @SuppressWarnings("unchecked")
    public static int getSmeltingCount() {
        return FurnaceRecipes.smelting().getSmeltingList().size();
    }
    
    // ==================== 内部接口 ====================
    
    /**
     * 配方条件谓词接口
     * 用于过滤要删除的配方
     */
    public interface RecipePredicate {
        boolean test(IRecipe recipe);
    }
}
