package decok.dfcdvadstf.catframe.compact.vanilla;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.VMMDataLoader;
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
 * <p>Blocks without a registered mapper can use {@code "meta=N"} variant keys
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

    /** Wood plank/variant names for planks (0-5). */
    private static final String[] WOOD_TYPES = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

    // ==================== Registration ====================

    @SideOnly(Side.CLIENT)
    public static void registerVanillaMetadataMappings() {

        // ---- 原木 (log) ----
        // meta low 2 bits = wood type, high 2 bits = axis (0=y, 1=x, 2=z, 3=bark→y)
        final String[] woods = {"oak", "spruce", "birch", "jungle"};
        final String[] axes = {"y", "x", "z"};
        VMMDataLoader.registerMetadataMapping(Blocks.log, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods[meta & 3]);
            props.put("axis", axes[(meta >> 2) % 3]);
            return props;
        });

        // ---- 金合欢/深色橡木原木 (log2) ----
        // meta low 1 bit = wood type, high 2 bits = axis (same scheme)
        final String[] woods2 = {"acacia", "dark_oak"};
        VMMDataLoader.registerMetadataMapping(Blocks.log2, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("wood", woods2[meta & 1]);
            props.put("axis", axes[(meta >> 2) % 3]);
            return props;
        });

        // ---- 树叶 (leaves) ----
        // meta & 3 = variant (oak/spruce/birch/jungle), bit 4+ = decay/player-placed
        VMMDataLoader.registerMetadataMapping(Blocks.leaves, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", woods[meta & 3]);
            return props;
        });

        // ---- 金合欢/深色橡木树叶 (leaves2) ----
        VMMDataLoader.registerMetadataMapping(Blocks.leaves2, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", woods2[meta & 1]);
            return props;
        });

        // ==================== 16-Color Blocks ====================

        // ---- 羊毛 (wool) ----
        VMMDataLoader.registerMetadataMapping(Blocks.wool, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("color", COLORS[meta & 15]);
            return props;
        });

        // ---- 染色玻璃 (stained_glass) ----
        VMMDataLoader.registerMetadataMapping(Blocks.stained_glass, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("color", COLORS[meta & 15]);
            return props;
        });

        // ---- 染色玻璃板 (stained_glass_pane) - meta=颜色索引(0-15) ----> per-color blockstate ---
        // 连接状态由世界邻居决定，不在 metadata 中编码；property mapper 来自 metadata_map.json
        VMMDataLoader.registerBlockstateRedirect(Blocks.stained_glass_pane, meta -> COLORS[meta & 15] + "_stained_glass_pane");

        // ---- 染色硬化粘土 (stained_hardened_clay) ----
        VMMDataLoader.registerMetadataMapping(Blocks.stained_hardened_clay, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("color", COLORS[meta & 15]);
            return props;
        });

        // ==================== Wood Variants ====================

        // ---- 木板 (planks) ----
        VMMDataLoader.registerMetadataMapping(Blocks.planks, meta -> {
            Map<String, String> props = new HashMap<>();
            int idx = Math.min(meta, WOOD_TYPES.length - 1);
            props.put("variant", WOOD_TYPES[idx]);
            return props;
        });

        // ==================== Stone & Mineral Variants ====================

        // ---- 石头 (stone) ----
        // meta: 0=stone, 1=granite, 2=polished_granite, 3=diorite, 4=polished_diorite, 5=andesite, 6=polished_andesite
        VMMDataLoader.registerMetadataMapping(Blocks.stone, meta -> {
            Map<String, String> props = new HashMap<>();
            String[] variants = {"stone", "granite", "polished_granite", "diorite",
                "polished_diorite", "andesite", "polished_andesite"};
            int idx = meta < variants.length ? meta : 0;
            props.put("variant", variants[idx]);
            return props;
        });

        // ---- 石砖 (stonebrick) ----
        VMMDataLoader.registerMetadataMapping(Blocks.stonebrick, meta -> {
            Map<String, String> props = new HashMap<>();
            String[] variants = {"stonebrick", "mossy_stonebrick", "cracked_stonebrick", "chiseled_stonebrick"};
            int idx = meta < variants.length ? meta : 0;
            props.put("variant", variants[idx]);
            return props;
        });

        // ---- 石英块 (quartz_block) ----
        // meta: 0=block, 1=chiseled, 2=pillar_y, 3=pillar_x, 4=pillar_z
        VMMDataLoader.registerMetadataMapping(Blocks.quartz_block, meta -> {
            Map<String, String> props = new HashMap<>();
            switch (meta) {
                case 0: props.put("variant", "quartz_block"); break;
                case 1: props.put("variant", "chiseled_quartz_block"); break;
                case 2: props.put("variant", "quartz_pillar"); props.put("axis", "y"); break;
                case 3: props.put("variant", "quartz_pillar"); props.put("axis", "x"); break;
                case 4: props.put("variant", "quartz_pillar"); props.put("axis", "z"); break;
                default: props.put("variant", "quartz_block"); break;
            }
            return props;
        });

        // ==================== Sand & Sandstone ====================

        // ---- 沙子 (sand) ----
        VMMDataLoader.registerMetadataMapping(Blocks.sand, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", meta == 0 ? "sand" : "red_sand");
            return props;
        });

        // ---- 砂岩 (sandstone) ----
        VMMDataLoader.registerMetadataMapping(Blocks.sandstone, meta -> {
            Map<String, String> props = new HashMap<>();
            String[] variants = {"sandstone", "chiseled_sandstone", "smooth_sandstone"};
            int idx = meta < variants.length ? meta : 0;
            props.put("variant", variants[idx]);
            return props;
        });

        // ==================== Dirt & Grass ====================

        // ---- 泥土 (dirt) ----
        // meta: 0=dirt, 1=coarse_dirt, 2=podzol
        VMMDataLoader.registerMetadataMapping(Blocks.dirt, meta -> {
            Map<String, String> props = new HashMap<>();
            String[] variants = {"dirt", "coarse_dirt", "podzol"};
            int idx = meta < variants.length ? meta : 0;
            props.put("variant", variants[idx]);
            return props;
        });

        // ==================== Walls ====================

        // ---- 圆石墙 (cobblestone_wall) ----
        VMMDataLoader.registerMetadataMapping(Blocks.cobblestone_wall, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("variant", meta == 0 ? "cobblestone" : "mossy_cobblestone");
            return props;
        });

        // ==================== Multipart (Connection-based) ====================

        // ---- 玻璃板 (glass_pane) ----
        // metadata: bitmask of {north=1, east=2, south=4, west=8}
        VMMDataLoader.registerMetadataMapping(Blocks.glass_pane, meta -> {
            Map<String, String> props = new HashMap<>();
            props.put("north", (meta & 1) != 0 ? "true" : "false");
            props.put("east", (meta & 2) != 0 ? "true" : "false");
            props.put("south", (meta & 4) != 0 ? "true" : "false");
            props.put("west", (meta & 8) != 0 ? "true" : "false");
            return props;
        });
    }
}
