package decok.dfcdvadstf.catframe.datapack.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import decok.dfcdvadstf.catframe.datapack.DataContentLoader;
import decok.dfcdvadstf.catframe.datapack.DataPackErrors;
import decok.dfcdvadstf.catframe.datapack.DataPackJson;
import decok.dfcdvadstf.catframe.datapack.DataPackRepository;
import decok.dfcdvadstf.catframe.recipe.TagRecipeManager;
import decok.dfcdvadstf.catframe.tags.impl.CatFrameTags;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tag-recipe content loader (worklist C2): CatFrame's own recipe schema,
 * hand-written on top of {@link TagRecipeManager}.
 * <p>
 * Deliberately <em>not</em> the vanilla-shaped recipe JSON — 1.7.10's
 * {@code CraftingManager} is hardcoded around its own recipe classes, and the
 * vanilla format drags in modern ingredient machinery. The schema lives under
 * {@code data/<ns>/recipes/**.json} and looks like this:
 * </p>
 * <pre>{@code
 * // shaped
 * {
 *   "type": "catframe:shaped",
 *   "pattern": ["ABA", " B "],
 *   "key": {
 *     "A": "#catframe:wool",
 *     "B": { "item": "minecraft:stick" }
 *   },
 *   "result": { "item": "minecraft:wool", "count": 1, "data": 0 }
 * }
 *
 * // shapeless
 * {
 *   "type": "catframe:shapeless",
 *   "ingredients": ["minecraft:red_mushroom", "#catframe:flowers"],
 *   "result": "minecraft:dye"
 * }
 *
 * // smelting (input may be an item or a tag reference)
 * {
 *   "type": "catframe:smelting",
 *   "input": "#catframe:ores",
 *   "experience": 0.7,
 *   "result": "minecraft:iron_ingot"
 * }
 * }</pre>
 * <p>
 * Ingredients accept {@code "#tag"} strings, {@code "ns:item"} strings and
 * {@code {"item": ..., "data": n, "count": n}} / {@code {"tag": ...}} objects;
 * {@code "data": -1} means the wildcard metadata. Tag ingredients are resolved
 * against the freshly built tag contents (tags load before recipes). A tag
 * reference on the ingredient side intentionally resolves to a
 * {@code List<Item>}, which both tag recipe classes match as an "any of" set.
 * </p>
 * <p>
 * Override semantics: for the same pack-relative path, a higher-priority pack
 * replaces the lower-priority file. Unload removes exactly the recipe objects
 * and smelting inputs registered by the previous pass, so reloads are
 * idempotent.
 * </p>
 */
public class RecipesContentLoader implements DataContentLoader {

    private static final Logger LOGGER = LogManager.getLogger(RecipesContentLoader.class);

    public static final String ID = "recipes";

    private static final String TYPE_SHAPED = "shaped";
    private static final String TYPE_SHAPELESS = "shapeless";
    private static final String TYPE_SMELTING = "smelting";

    /** Crafting recipes registered by the previous pass (identity set), removed on unload. */
    private final Set<IRecipe> registeredCrafting = Collections.newSetFromMap(new IdentityHashMap<IRecipe, Boolean>());

    /** Smelting inputs registered by the previous pass, removed on unload. */
    private final List<ItemStack> registeredSmeltingInputs = new ArrayList<>();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void unload(DataPackErrors errors) {
        if (!registeredCrafting.isEmpty()) {
            TagRecipeManager.removeRecipes(registeredCrafting::contains);
            registeredCrafting.clear();
        }
        for (ItemStack input : registeredSmeltingInputs) {
            TagRecipeManager.removeSmelting(input);
        }
        registeredSmeltingInputs.clear();
    }

    @Override
    public void load(DataPackRepository repository, DataPackErrors errors) {
        // Override semantics: same pack-relative path, later (higher-priority)
        // pack replaces the earlier file.
        Map<String, DataPackRepository.PackResource> winners = new LinkedHashMap<>();
        for (String namespace : repository.listNamespaces()) {
            String prefix = "data/" + namespace + "/recipes/";
            for (DataPackRepository.PackResource resource : repository.listResources(prefix)) {
                winners.put(resource.getPath(), resource);
            }
        }

        int loaded = 0;
        for (DataPackRepository.PackResource resource : winners.values()) {
            if (parseRecipe(resource, errors)) {
                loaded++;
            }
        }
        LOGGER.info("Loaded {} tag recipe(s) from data packs ({} file(s) considered)", loaded, winners.size());
    }

    // ==================== Parsing ====================

    private boolean parseRecipe(DataPackRepository.PackResource resource, DataPackErrors errors) {
        String packId = resource.getPack().getId();
        String path = resource.getPath();
        JsonObject json = DataPackJson.parseObject(resource.read(), packId, path, errors);
        if (json == null) {
            return false;
        }
        try {
            String type = normalizeType(DataPackJson.getString(json, "type", TYPE_SHAPED));
            ItemStack result = parseResult(json.get("result"), packId, path, errors);
            if (result == null) {
                errors.error(packId, path, "missing or invalid 'result'");
                return false;
            }
            if (TYPE_SHAPED.equals(type)) {
                return parseShaped(json, result, packId, path, errors);
            }
            if (TYPE_SHAPELESS.equals(type)) {
                return parseShapeless(json, result, packId, path, errors);
            }
            if (TYPE_SMELTING.equals(type)) {
                return parseSmelting(json, result, packId, path, errors);
            }
            errors.error(packId, path, "unknown recipe type: " + type + " (use shaped, shapeless or smelting)");
            return false;
        } catch (RuntimeException e) {
            errors.error(packId, path, "recipe construction failed: " + e);
            return false;
        }
    }

    /** Accepts "catframe:shaped" and the bare "shaped" (local part, lower-cased). */
    private static String normalizeType(String raw) {
        String type = raw == null ? "" : raw.trim();
        int colon = type.indexOf(':');
        if (colon >= 0) {
            type = type.substring(colon + 1);
        }
        return type.toLowerCase(Locale.ROOT);
    }

    private boolean parseShaped(JsonObject json, ItemStack result, String packId, String path, DataPackErrors errors) {
        JsonArray patternArray = DataPackJson.getArray(json, "pattern");
        if (patternArray == null || patternArray.size() == 0) {
            errors.error(packId, path, "shaped recipe requires a non-empty 'pattern' array");
            return false;
        }
        String[] pattern = new String[patternArray.size()];
        for (int i = 0; i < pattern.length; i++) {
            JsonElement row = patternArray.get(i);
            if (row == null || !row.isJsonPrimitive()) {
                errors.error(packId, path, "pattern row " + i + " is not a string");
                return false;
            }
            pattern[i] = row.getAsString();
        }
        int width = pattern[0].length();
        for (String row : pattern) {
            if (row.length() != width) {
                errors.error(packId, path, "pattern rows must all have the same width");
                return false;
            }
        }

        JsonObject key = DataPackJson.getObject(json, "key");
        if (key == null) {
            errors.error(packId, path, "shaped recipe requires a 'key' object");
            return false;
        }

        List<Object> args = new ArrayList<>();
        args.add(pattern);
        Set<Character> declared = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            if (entry.getKey().length() != 1) {
                errors.error(packId, path, "key '" + entry.getKey() + "' must be a single character");
                return false;
            }
            char symbol = entry.getKey().charAt(0);
            Object ingredient = parseIngredient(entry.getValue(), packId, path, errors);
            if (ingredient == null) {
                return false;
            }
            declared.add(symbol);
            args.add(Character.valueOf(symbol));
            args.add(ingredient);
        }
        for (String row : pattern) {
            for (int i = 0; i < row.length(); i++) {
                char symbol = row.charAt(i);
                if (symbol != ' ' && !declared.contains(symbol)) {
                    errors.error(packId, path, "pattern uses '" + symbol + "' but the key object has no such entry");
                    return false;
                }
            }
        }

        IRecipe recipe = TagRecipeManager.addShaped(result, args.toArray());
        registeredCrafting.add(recipe);
        return true;
    }

    private boolean parseShapeless(JsonObject json, ItemStack result, String packId, String path, DataPackErrors errors) {
        JsonArray ingredients = DataPackJson.getArray(json, "ingredients");
        if (ingredients == null || ingredients.size() == 0) {
            errors.error(packId, path, "shapeless recipe requires a non-empty 'ingredients' array");
            return false;
        }
        List<Object> args = new ArrayList<>();
        for (JsonElement element : ingredients) {
            Object ingredient = parseIngredient(element, packId, path, errors);
            if (ingredient == null) {
                return false;
            }
            args.add(ingredient);
        }
        IRecipe recipe = TagRecipeManager.addShapeless(result, args.toArray());
        registeredCrafting.add(recipe);
        return true;
    }

    private boolean parseSmelting(JsonObject json, ItemStack result, String packId, String path, DataPackErrors errors) {
        JsonElement input = json.get("input");
        if (input == null || input.isJsonNull()) {
            errors.error(packId, path, "smelting recipe requires an 'input'");
            return false;
        }
        float experience = DataPackJson.getFloat(json, "experience", DataPackJson.getFloat(json, "xp", 0.0F));

        if (input.isJsonPrimitive() && input.getAsString().startsWith("#")) {
            List<Item> items = resolveTag(input.getAsString().substring(1), packId, path, errors);
            if (items == null) {
                return false;
            }
            for (Item item : items) {
                TagRecipeManager.addSmelting(item, result, experience);
                registeredSmeltingInputs.add(new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE));
            }
            return true;
        }

        ItemStack inputStack = parseResult(input, packId, path, errors);
        if (inputStack == null) {
            errors.error(packId, path, "invalid 'input'");
            return false;
        }
        TagRecipeManager.addSmelting(inputStack, result, experience);
        registeredSmeltingInputs.add(inputStack);
        return true;
    }

    /**
     * Parses a result / item value: {@code "ns:item"} or
     * {@code {"item": ..., "data": n, "count": n}} ({@code data -1} = wildcard).
     */
    private ItemStack parseResult(JsonElement element, String packId, String path, DataPackErrors errors) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return resolveItem(element.getAsString(), 0, 1, packId, path, errors);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String id = DataPackJson.getString(object, "item", null);
            if (id == null) {
                return null;
            }
            int data = DataPackJson.getInt(object, "data", 0);
            int count = DataPackJson.getInt(object, "count", 1);
            return resolveItem(id, data, count, packId, path, errors);
        }
        return null;
    }

    /**
     * Parses an ingredient.
     *
     * @return an {@link ItemStack}, a {@code List<Item>} (resolved tag), or
     *         {@code null} when invalid (already reported)
     */
    private Object parseIngredient(JsonElement element, String packId, String path, DataPackErrors errors) {
        if (element == null || element.isJsonNull()) {
            errors.error(packId, path, "empty ingredient");
            return null;
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (value.startsWith("#")) {
                return resolveTag(value.substring(1), packId, path, errors);
            }
            return resolveItem(value, 0, 1, packId, path, errors);
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String tagId = DataPackJson.getString(object, "tag", null);
            if (tagId != null) {
                return resolveTag(tagId, packId, path, errors);
            }
            String itemId = DataPackJson.getString(object, "item", null);
            if (itemId != null) {
                int data = DataPackJson.getInt(object, "data", 0);
                return resolveItem(itemId, data, 1, packId, path, errors);
            }
            errors.error(packId, path, "ingredient object needs 'item' or 'tag'");
            return null;
        }
        errors.error(packId, path, "unsupported ingredient form (use \"#tag\", \"ns:item\" or an item/tag object)");
        return null;
    }

    private List<Item> resolveTag(String tagId, String packId, String path, DataPackErrors errors) {
        try {
            ResourceLocation location = new ResourceLocation(tagId);
            Set<Item> items = CatFrameTags.itemLoader().getTagContents(location);
            if (items.isEmpty()) {
                errors.warning(packId, path, "tag '" + tagId + "' is empty; the recipe cannot match");
            }
            return new ArrayList<>(items);
        } catch (RuntimeException e) {
            errors.error(packId, path, "invalid tag id: " + tagId);
            return null;
        }
    }

    private ItemStack resolveItem(String itemId, int data, int count, String packId, String path, DataPackErrors errors) {
        Object registered = Item.itemRegistry.getObject(itemId);
        if (!(registered instanceof Item)) {
            errors.error(packId, path, "unknown item: " + itemId);
            return null;
        }
        int damage = data == -1 ? OreDictionary.WILDCARD_VALUE : data;
        return new ItemStack((Item) registered, Math.max(1, count), damage);
    }
}
