package decok.dfcdvadstf.catframe.compact.vanilla.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import net.minecraft.init.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers IMetadataMapper for every vanilla Minecraft block that uses metadata
 * for visual differentiation.
 *
 * <p>Each mapper converts metadata (0-15) into a property map that is then used
 * to match the corresponding variant key in the blockstate JSON, e.g.
 * {@code meta=0 → {color: "white"} → "color=white"}.
 *
 * <p>Blocks without a registered mapper can use numeric key variants
 * in their blockstate JSON as a direct fallback.
 */
public class VanillaMetadataMapper {

    // ==================== Shared Color Constants ====================

    /** Minecraft 1.7.10 dye/wool color order for blocks with 16 color variants. */
    private static final String[] COLORS = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black"
    };

    // ==================== Registration ====================

    @SideOnly(Side.CLIENT)
    public static void registerVanillaMetadataMappings() {

        // ---- 原木 (log) ----
        // meta low 2 bits = wood type, high 2 bits = axis (0=y, 1=x, 2=z, 3=bark→y)
        final String[] woods = {"oak", "spruce", "birch", "jungle"};
        final String[] axes = {"y", "x", "z"};
        ModelManagerDataLoader.registerMetadataMapping(Blocks.log, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods[meta & 3]);
            props.put("axis", axes[(meta >> 2) % 3]);
            return props;
        });

        // ---- 金合欢/深色橡木原木 (log2) ----
        final String[] woods2 = {"acacia", "dark_oak"};
        ModelManagerDataLoader.registerMetadataMapping(Blocks.log2, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods2[meta & 1]);
            props.put("axis", axes[(meta >> 2) % 3]);
            return props;
        });

        // ---- 树叶 (leaves) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.leaves, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods[meta & 3]);
            return props;
        });

        // ---- 金合欢/深色橡木树叶 (leaves2) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.leaves2, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods2[meta & 1]);
            return props;
        });

        // ---- 染色玻璃板 (stained_glass_pane) - meta=颜色索引(0-15) ----> per-color blockstate ---
        ModelManagerDataLoader.registerBlockstateRedirect(Blocks.stained_glass_pane, meta -> COLORS[meta & 15] + "_stained_glass_pane");

        // ==================== Block Variants ====================

        // ---- 树苗 (sapling) ----
        // meta 0-5: oak, spruce, birch, jungle, acacia, dark_oak
        final String[] SAPLING_TYPES = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        ModelManagerDataLoader.registerMetadataMapping(Blocks.sapling, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", SAPLING_TYPES[Math.min(meta & 7, SAPLING_TYPES.length - 1)]);
            return props;
        });

        // ---- 铁砧 (anvil) ----
        // meta: facing=bits 0-1, damage=bits 2-3 (0=intact,1=slightly,2=very)
        // 1.7.10 onBlockPlacedBy: player_facing +1 (mod 4) → meta, which is equivalent to
        // high-version FACING = player_direction.getClockWise()
        // meta&3=0→north, 1→east, 2→south, 3→west
        final String[] FACINGS = {"north", "east", "south", "west"};
        final String[] ANVIL_DAMAGES = {"0", "1", "2"};
        ModelManagerDataLoader.registerMetadataMapping(Blocks.anvil, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("facing", FACINGS[meta & 3]);
            props.put("damage", ANVIL_DAMAGES[Math.min((meta >> 2) & 3, 2)]);
            return props;
        });

        // ---- 石台阶 (stone_slab) ----
        // meta low 3 bits = type, bit 3 = half (0=bottom, 1=top)
        final String[] SLAB_TYPES = {"stone", "sandstone", "wood", "cobblestone",
            "brick", "stone_brick", "nether_brick", "quartz"};
        ModelManagerDataLoader.registerMetadataMapping(Blocks.stone_slab, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("half", (meta & 8) == 0 ? "bottom" : "top");
            props.put("variant", SLAB_TYPES[meta & 7]);
            return props;
        });

        // ---- 地毯 (carpet) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.carpet, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("color", COLORS[meta & 15]);
            return props;
        });

        // ---- 怪物蛋 (monster_egg) ----
        // meta 0-5: stone, cobblestone, stone_brick, mossy_stone_brick, cracked_stone_brick, chiseled_stone_brick
        final String[] EGG_VARIANTS = {"stone", "cobblestone", "stone_brick",
            "mossy_stone_brick", "cracked_stone_brick", "chiseled_stone_brick"};
        ModelManagerDataLoader.registerMetadataMapping(Blocks.monster_egg, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", EGG_VARIANTS[Math.min(meta & 7, EGG_VARIANTS.length - 1)]);
            return props;
        });

        // ---- 羊毛 (wool) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.wool, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("color", COLORS[meta & 15]);
            return props;
        });

        // ---- 染色玻璃 (stained_glass) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.stained_glass, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("color", COLORS[meta & 15]);
            return props;
        });

        // ---- 染色硬化粘土 (stained_hardened_clay) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.stained_hardened_clay, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("color", COLORS[meta & 15]);
            return props;
        });

        // ---- 木板 (planks) ----
        final String[] WOOD_TYPES = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        ModelManagerDataLoader.registerMetadataMapping(Blocks.planks, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", WOOD_TYPES[Math.min(meta, WOOD_TYPES.length - 1)]);
            return props;
        });

        // ---- 石砖 (stonebrick) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.stonebrick, meta -> {
            Map<String, String> props = new HashMap<>();
            String[] variants = {"stonebrick", "mossy_stonebrick", "cracked_stonebrick", "chiseled_stonebrick"};
            props.put("variant", variants[Math.min(meta, variants.length - 1)]);
            return props;
        });

        // ---- 石英块 (quartz_block) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.quartz_block, meta -> {
            Map<String, String> props = new HashMap<>();
            switch (meta) {
                case 0: props.put("type", "quartz_block"); break;
                case 1: props.put("type", "chiseled_quartz_block"); break;
                case 2: props.put("type", "quartz_pillar"); props.put("axis", "y"); break;
                case 3: props.put("type", "quartz_pillar"); props.put("axis", "x"); break;
                case 4: props.put("type", "quartz_pillar"); props.put("axis", "z"); break;
                default: props.put("type", "quartz_block"); break;
            }
            return props;
        });

        /** Crops (wheat, carrots, potatoes, reeds) */
        // Key definition: age=0~7

        // ---- 小麦 (wheat) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.wheat, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("age", String.valueOf(meta & 7));
            return props;
        });

        // ---- 胡萝卜 (carrots) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.carrots, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("age", String.valueOf(meta & 7));
            return props;
        });

        // ---- 土豆 (potatoes) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.potatoes, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("age", String.valueOf(meta & 7));
            return props;
        });

        // ---- 西瓜苗 (melon_stem) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.melon_stem, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("age", String.valueOf(meta & 7));
            return props;
        });

        // ---- 南瓜苗 (pumpkin_stem) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.pumpkin_stem, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("age", String.valueOf(meta & 7));
            return props;
        });
        
        // ---- 甘蔗 (reeds) ----
        // meta 0-15: 生长阶段，blockstate 只定义 age=0~7，8-15 映射到 age=7
        ModelManagerDataLoader.registerMetadataMapping(Blocks.reeds, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("age", String.valueOf(meta & 7));
            return props;
        });

        // ---- 沙子 (sand) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.sand, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", meta == 0 ? "sand" : "red_sand");
            return props;
        });

        // ---- 砂岩 (sandstone) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.sandstone, meta -> {
            Map<String, String> props = new HashMap<>();
            String[] variants = {"sandstone", "chiseled_sandstone", "smooth_sandstone"};
            props.put("variant", variants[Math.min(meta, variants.length - 1)]);
            return props;
        });

        // ---- 泥土 (dirt) ----
        // meta: 0=dirt, 2=podzol（1.7.10 无 coarse_dirt）
        ModelManagerDataLoader.registerMetadataMapping(Blocks.dirt, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", meta == 2 ? "podzol" : "dirt");
            return props;
        });

        // ---- 圆石墙 (cobblestone_wall) ----
        ModelManagerDataLoader.registerMetadataMapping(Blocks.cobblestone_wall, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", meta == 0 ? "cobblestone" : "mossy_cobblestone");
            return props;
        });

        // ==================== Multipart (Connection-based) ====================

        // ---- 玻璃板 (glass_pane) ----
        // metadata: bitmask of {north=1, east=2, south=4, west=8}
        ModelManagerDataLoader.registerMetadataMapping(Blocks.glass_pane, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("north", (meta & 1) != 0 ? "true" : "false");
            props.put("east", (meta & 2) != 0 ? "true" : "false");
            props.put("south", (meta & 4) != 0 ? "true" : "false");
            props.put("west", (meta & 8) != 0 ? "true" : "false");
            return props;
        });
    }
}
