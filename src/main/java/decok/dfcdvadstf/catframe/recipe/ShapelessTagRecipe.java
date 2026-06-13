package decok.dfcdvadstf.catframe.recipe;

import decok.dfcdvadstf.catframe.tags.CatFrameTags;
import decok.dfcdvadstf.catframe.tags.TagKey;
import net.minecraft.block.Block;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 无序 Tag 配方
 * 
 * 类似 Forge 的 ShapelessOreRecipe，但使用 CatFrame Tag 系统
 * 支持 Tag 名称作为配方材料，不要求材料在特定位置
 * 
 * 使用示例：
 * <pre>
 * // 使用 Tag 名称
 * new ShapelessTagRecipe(new ItemStack(Items.iron_ingot, 9),
 *     "catframe:blockIron"
 * );
 * 
 * // 混合使用
 * new ShapelessTagRecipe(new ItemStack(Items.dye, 2, 1),
 *     Items.red_mushroom, Items.brown_mushroom, "catframe:flowers"
 * );
 * </pre>
 */
public class ShapelessTagRecipe implements IRecipe {
    
    private ItemStack output = null;
    private ArrayList<Object> input = new ArrayList<Object>();
    
    /**
     * 使用 Block 作为输出
     */
    public ShapelessTagRecipe(Block result, Object... recipe) {
        this(new ItemStack(result), recipe);
    }
    
    /**
     * 使用 Item 作为输出
     */
    public ShapelessTagRecipe(Item result, Object... recipe) {
        this(new ItemStack(result), recipe);
    }
    
    /**
     * 使用 ItemStack 作为输出
     */
    public ShapelessTagRecipe(ItemStack result, Object... recipe) {
        output = result.copy();
        
        for (Object in : recipe) {
            input.add(parseIngredient(in));
        }
    }
    
    /**
     * 从原版 ShapelessRecipes 转换，并应用替换
     */
    @SuppressWarnings("unchecked")
    ShapelessTagRecipe(ShapelessRecipes recipe, Map<ItemStack, String> replacements) {
        output = recipe.getRecipeOutput();
        
        for (ItemStack ingred : ((List<ItemStack>) recipe.recipeItems)) {
            Object finalObj = ingred;
            for (Map.Entry<ItemStack, String> replace : replacements.entrySet()) {
                if (itemMatches(replace.getKey(), ingred, false)) {
                    finalObj = parseIngredient(replace.getValue());
                    break;
                }
            }
            input.add(finalObj);
        }
    }
    
    /**
     * 解析材料参数
     * 支持：ItemStack、Item、Block、String（Tag/OreDict）、TagKey
     */
    private Object parseIngredient(Object ingredient) {
        if (ingredient instanceof ItemStack) {
            return ((ItemStack) ingredient).copy();
        }
        
        if (ingredient instanceof Item) {
            return new ItemStack((Item) ingredient);
        }
        
        if (ingredient instanceof Block) {
            return new ItemStack((Block) ingredient, 1, OreDictionary.WILDCARD_VALUE);
        }
        
        if (ingredient instanceof TagKey) {
            @SuppressWarnings("unchecked")
            TagKey<Item> tag = (TagKey<Item>) ingredient;
            Set<Item> tagItems = CatFrameTags.itemLoader().getTagContents(tag.getLocation());
            return new ArrayList<Item>(tagItems);
        }
        
        if (ingredient instanceof String) {
            String str = (String) ingredient;
            
            // 判断是否是 Tag 名称
            if (str.contains(":")) {
                try {
                    ResourceLocation tagLocation = new ResourceLocation(str);
                    Set<Item> tagItems = CatFrameTags.itemLoader().getTagContents(tagLocation);
                    if (!tagItems.isEmpty()) {
                        return new ArrayList<Item>(tagItems);
                    }
                } catch (Exception e) {
                    // 不是有效的 ResourceLocation，尝试作为 OreDict
                }
            }
            
            // 作为 OreDict 名称（向后兼容）
            ArrayList<ItemStack> ores = OreDictionary.getOres(str);
            if (!ores.isEmpty()) {
                return ores;
            }
            
            // 如果 OreDict 也没有，再尝试作为 Tag
            try {
                ResourceLocation tagLocation = new ResourceLocation("catframe", str);
                Set<Item> tagItems = CatFrameTags.itemLoader().getTagContents(tagLocation);
                if (!tagItems.isEmpty()) {
                    return new ArrayList<Item>(tagItems);
                }
            } catch (Exception e) {
                // 忽略
            }
        }
        
        return ingredient;
    }
    
    @Override
    public int getRecipeSize() {
        return input.size();
    }
    
    @Override
    public ItemStack getRecipeOutput() {
        return output;
    }
    
    @Override
    public ItemStack getCraftingResult(InventoryCrafting var1) {
        return output.copy();
    }
    
    @Override
    public boolean matches(InventoryCrafting var1, World world) {
        ArrayList<Object> required = new ArrayList<Object>(input);
        
        for (int x = 0; x < var1.getSizeInventory(); x++) {
            ItemStack slot = var1.getStackInSlot(x);
            
            if (slot != null) {
                boolean inRecipe = false;
                Iterator<Object> req = required.iterator();
                
                while (req.hasNext()) {
                    boolean match = false;
                    
                    Object next = req.next();
                    
                    if (next instanceof ItemStack) {
                        // 普通 ItemStack 匹配
                        match = itemMatches((ItemStack) next, slot, false);
                    } else if (next instanceof List) {
                        // Tag 或 OreDict 列表匹配
                        Iterator<?> itr = ((List<?>) next).iterator();
                        while (itr.hasNext() && !match) {
                            Object listItem = itr.next();
                            
                            if (listItem instanceof Item) {
                                // Tag 内容（Item 列表）
                                if (slot.getItem() == listItem) {
                                    match = true;
                                }
                            } else if (listItem instanceof ItemStack) {
                                // OreDict 内容（ItemStack 列表）
                                if (itemMatches((ItemStack) listItem, slot, false)) {
                                    match = true;
                                }
                            }
                        }
                    }
                    
                    if (match) {
                        inRecipe = true;
                        required.remove(next);
                        break;
                    }
                }
                
                if (!inRecipe) {
                    return false;
                }
            }
        }
        
        return required.isEmpty();
    }
    
    /**
     * 检查两个 ItemStack 是否匹配
     */
    private boolean itemMatches(ItemStack target, ItemStack input, boolean strict) {
        if (input == null && target != null || input != null && target == null) {
            return false;
        }
        return (target.getItem() == input.getItem() && 
                ((target.getItemDamage() == OreDictionary.WILDCARD_VALUE && !strict) || 
                 target.getItemDamage() == input.getItemDamage()));
    }
    
    /**
     * 获取配方的输入材料
     * 警告：不要修改返回的列表，这会影响配方本身
     */
    public ArrayList<Object> getInput() {
        return this.input;
    }
}
