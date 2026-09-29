package decok.dfcdvadstf.catframe.recipe;

import decok.dfcdvadstf.catframe.tags.TagKey;
import decok.dfcdvadstf.catframe.tags.impl.CatFrameTags;
import net.minecraft.block.Block;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import java.util.*;

/**
 * Shapeless Tag Recipe
 * 
 * Similar to Forge's ShapelessOreRecipe, but uses CatFrame Tag system
 * Supports Tag names as recipe ingredients, no specific position required
 * 
 * Usage examples:
 * <pre>
 * // Using Tag name
 * new ShapelessTagRecipe(new ItemStack(Items.iron_ingot, 9),
 *     "catframe:blockIron"
 * );
 * 
 * // Mixed usage
 * new ShapelessTagRecipe(new ItemStack(Items.dye, 2, 1),
 *     Items.red_mushroom, Items.brown_mushroom, "catframe:flowers"
 * );
 * </pre>
 */
public class ShapelessTagRecipe implements IRecipe {
    
    private ItemStack output = null;
    private ArrayList<Object> input = new ArrayList<Object>();
    
    /**
     * Using Block as output
     */
    public ShapelessTagRecipe(Block result, Object... recipe) {
        this(new ItemStack(result), recipe);
    }
    
    /**
     * Using Item as output
     */
    public ShapelessTagRecipe(Item result, Object... recipe) {
        this(new ItemStack(result), recipe);
    }
    
    /**
     * Using ItemStack as output
     */
    public ShapelessTagRecipe(ItemStack result, Object... recipe) {
        output = result.copy();
        
        for (Object in : recipe) {
            input.add(parseIngredient(in));
        }
    }
    
    /**
     * Convert from vanilla ShapelessRecipes and apply replacements
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
     * Parse ingredient argument
     * Supports: ItemStack, Item, Block, String (Tag/OreDict), TagKey
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
            
            // Check if it's a Tag name
            if (str.contains(":")) {
                try {
                    ResourceLocation tagLocation = new ResourceLocation(str);
                    Set<Item> tagItems = CatFrameTags.itemLoader().getTagContents(tagLocation);
                    if (!tagItems.isEmpty()) {
                        return new ArrayList<Item>(tagItems);
                    }
                } catch (Exception e) {
                    // Not a valid ResourceLocation, try as OreDict
                }
            }
            
            // As OreDict name (backwards compatible)
            ArrayList<ItemStack> ores = OreDictionary.getOres(str);
            if (!ores.isEmpty()) {
                return ores;
            }
            
            // If OreDict doesn't have it, try as Tag
            try {
                ResourceLocation tagLocation = new ResourceLocation("catframe", str);
                Set<Item> tagItems = CatFrameTags.itemLoader().getTagContents(tagLocation);
                if (!tagItems.isEmpty()) {
                    return new ArrayList<Item>(tagItems);
                }
            } catch (Exception e) {
                // Ignore
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
                        // Normal ItemStack match
                        match = itemMatches((ItemStack) next, slot, false);
                    } else if (next instanceof List) {
                        // Tag or OreDict list match
                        Iterator<?> itr = ((List<?>) next).iterator();
                        while (itr.hasNext() && !match) {
                            Object listItem = itr.next();
                            
                            if (listItem instanceof Item) {
                                // Tag contents (Item list)
                                if (slot.getItem() == listItem) {
                                    match = true;
                                }
                            } else if (listItem instanceof ItemStack) {
                                // OreDict contents (ItemStack list)
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
     * Check if two ItemStacks match
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
     * Get recipe input ingredients
     * Warning: do not modify the returned list, it affects the recipe itself
     */
    public ArrayList<Object> getInput() {
        return this.input;
    }
}
