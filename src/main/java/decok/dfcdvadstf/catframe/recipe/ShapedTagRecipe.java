package decok.dfcdvadstf.catframe.recipe;

import decok.dfcdvadstf.catframe.tags.CatFrameTags;
import decok.dfcdvadstf.catframe.tags.TagKey;
import net.minecraft.block.Block;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 有序 Tag 配方
 * 
 * 类似 Forge 的 ShapedOreRecipe，但使用 CatFrame Tag 系统而不是 OreDictionary
 * 支持 Tag 名称作为配方材料，实现更灵活的配方定义
 * 
 * 使用示例：
 * <pre>
 * // 使用 Tag 名称
 * new ShapedTagRecipe(new ItemStack(Blocks.chest),
 *     "###", "# #", "###",
 *     '#', "catframe:planks"
 * );
 * 
 * // 使用 TagKey
 * new ShapedTagRecipe(new ItemStack(Blocks.chest),
 *     "###", "# #", "###",
 *     '#', CatFrameItemTags.PLANKS
 * );
 * </pre>
 */
public class ShapedTagRecipe implements IRecipe {
    
    private static final int MAX_CRAFT_GRID_WIDTH = 3;
    private static final int MAX_CRAFT_GRID_HEIGHT = 3;
    
    private ItemStack output = null;
    private Object[] input = null;
    private int width = 0;
    private int height = 0;
    private boolean mirrored = true;
    
    /**
     * 使用 Block 作为输出
     */
    public ShapedTagRecipe(Block result, Object... recipe) {
        this(new ItemStack(result), recipe);
    }
    
    /**
     * 使用 Item 作为输出
     */
    public ShapedTagRecipe(Item result, Object... recipe) {
        this(new ItemStack(result), recipe);
    }
    
    /**
     * 使用 ItemStack 作为输出
     */
    public ShapedTagRecipe(ItemStack result, Object... recipe) {
        output = result.copy();
        
        String shape = "";
        int idx = 0;
        
        // 检查是否有 mirrored 参数
        if (recipe[idx] instanceof Boolean) {
            mirrored = (Boolean) recipe[idx];
            if (recipe[idx + 1] instanceof Object[]) {
                recipe = (Object[]) recipe[idx + 1];
            } else {
                idx = 1;
            }
        }
        
        // 解析配方形状
        if (recipe[idx] instanceof String[]) {
            String[] parts = ((String[]) recipe[idx++]);
            
            for (String s : parts) {
                width = s.length();
                shape += s;
            }
            
            height = parts.length;
        } else {
            while (recipe[idx] instanceof String) {
                String s = (String) recipe[idx++];
                shape += s;
                width = s.length();
                height++;
            }
        }
        
        // 验证形状
        if (width * height != shape.length()) {
            String ret = "Invalid shaped tag recipe: ";
            for (Object tmp : recipe) {
                ret += tmp + ", ";
            }
            ret += output;
            throw new RuntimeException(ret);
        }
        
        // 解析材料映射
        HashMap<Character, Object> itemMap = new HashMap<Character, Object>();
        
        for (; idx < recipe.length; idx += 2) {
            Character chr = (Character) recipe[idx];
            Object in = recipe[idx + 1];
            
            itemMap.put(chr, parseIngredient(in));
        }
        
        // 构建输入数组
        input = new Object[width * height];
        int x = 0;
        for (char chr : shape.toCharArray()) {
            input[x++] = itemMap.get(chr);
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
            
            // 判断是否是 Tag 名称（包含 : 或者是已知的 Tag）
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
            
            // 如果 OreDict 也没有，再尝试作为 Tag（不含命名空间）
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
    public ItemStack getCraftingResult(InventoryCrafting var1) {
        return output.copy();
    }
    
    @Override
    public int getRecipeSize() {
        return input.length;
    }
    
    @Override
    public ItemStack getRecipeOutput() {
        return output;
    }
    
    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        for (int x = 0; x <= MAX_CRAFT_GRID_WIDTH - width; x++) {
            for (int y = 0; y <= MAX_CRAFT_GRID_HEIGHT - height; ++y) {
                if (checkMatch(inv, x, y, false)) {
                    return true;
                }
                
                if (mirrored && checkMatch(inv, x, y, true)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    @SuppressWarnings("unchecked")
    private boolean checkMatch(InventoryCrafting inv, int startX, int startY, boolean mirror) {
        for (int x = 0; x < MAX_CRAFT_GRID_WIDTH; x++) {
            for (int y = 0; y < MAX_CRAFT_GRID_HEIGHT; y++) {
                int subX = x - startX;
                int subY = y - startY;
                Object target = null;
                
                if (subX >= 0 && subY >= 0 && subX < width && subY < height) {
                    if (mirror) {
                        target = input[width - subX - 1 + subY * width];
                    } else {
                        target = input[subX + subY * width];
                    }
                }
                
                ItemStack slot = inv.getStackInRowAndColumn(x, y);
                
                if (target instanceof ItemStack) {
                    // 普通 ItemStack 匹配
                    if (!itemMatches((ItemStack) target, slot, false)) {
                        return false;
                    }
                } else if (target instanceof List) {
                    // Tag 或 OreDict 列表匹配
                    boolean matched = false;
                    
                    if (((List<?>) target).isEmpty()) {
                        return false;
                    }
                    
                    // 检查第一个元素类型
                    Object first = ((List<?>) target).get(0);
                    
                    if (first instanceof Item) {
                        // Tag 内容（Item 列表）
                        Iterator<Item> itr = ((List<Item>) target).iterator();
                        while (itr.hasNext() && !matched) {
                            Item tagItem = itr.next();
                            if (slot != null && slot.getItem() == tagItem) {
                                matched = true;
                            }
                        }
                    } else if (first instanceof ItemStack) {
                        // OreDict 内容（ItemStack 列表）
                        Iterator<ItemStack> itr = ((List<ItemStack>) target).iterator();
                        while (itr.hasNext() && !matched) {
                            ItemStack oreStack = itr.next();
                            if (itemMatches(oreStack, slot, false)) {
                                matched = true;
                            }
                        }
                    }
                    
                    if (!matched) {
                        return false;
                    }
                } else if (target == null && slot != null) {
                    // 配方要求空位，但实际有物品
                    return false;
                }
            }
        }
        
        return true;
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
    
    public ShapedTagRecipe setMirrored(boolean mirror) {
        mirrored = mirror;
        return this;
    }
    
    /**
     * 获取配方的输入材料
     * 警告：不要修改返回的数组，这会影响配方本身
     */
    public Object[] getInput() {
        return this.input;
    }
}
