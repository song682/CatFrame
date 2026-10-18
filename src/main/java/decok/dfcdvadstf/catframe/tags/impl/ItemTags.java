package decok.dfcdvadstf.catframe.tags.impl;

import decok.dfcdvadstf.catframe.tags.TagKey;
import decok.dfcdvadstf.catframe.tags.TagManager;
import decok.dfcdvadstf.catframe.tags.TagRegistry;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

/**
 * Built-in item tag constants, mirroring the modern {@code ItemTags}.
 * <p>
 * Tag files live under {@code data/minecraft/tags/item}.
 * </p>
 */
public final class ItemTags {

    private static final String NAMESPACE = "minecraft";

    // ==================== Tools ====================

    public static final TagKey<Item> SWORDS = create("swords");
    public static final TagKey<Item> AXES = create("axes");
    public static final TagKey<Item> PICKAXES = create("pickaxes");
    public static final TagKey<Item> SHOVELS = create("shovels");
    public static final TagKey<Item> HOES = create("hoes");
    public static final TagKey<Item> TOOLS = create("tools");

    // ==================== Materials ====================

    public static final TagKey<Item> PLANKS = create("planks");
    public static final TagKey<Item> LOGS = create("logs");
    public static final TagKey<Item> STONES = create("stones");
    public static final TagKey<Item> WOOL = create("wool");
    public static final TagKey<Item> DYES = create("dyes");

    // ==================== Functions ====================

    public static final TagKey<Item> FOODS = create("foods");
    public static final TagKey<Item> BREWING_ITEMS = create("brewing_items");
    public static final TagKey<Item> FUELS = create("fuels");

    private ItemTags() {
    }

    private static TagKey<Item> create(String name) {
        return TagKey.create(TagRegistry.ITEMS, new ResourceLocation(NAMESPACE, name));
    }

    /** @return true when the item belongs to the tag. */
    public static boolean is(Item item, TagKey<Item> tag) {
        return TagManager.is(item, tag);
    }
}
