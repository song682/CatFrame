package decok.dfcdvadstf.catframe.datagen.model;

import decok.dfcdvadstf.catframe.model.core.ModelJson;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Preset model template library (the {@code TexturedModel} counterpart of the
 * modern datagen pipeline).
 * <p>
 * Each template knows the parent path and the texture slots its instances
 * must bind. {@link #create(Template, Map)} produces a {@link ModelJson} whose
 * {@code parent} and {@code textures} are filled in; the optional fixed
 * elements (glass pane panes, etc.) are defined by the parent template model
 * itself, so instances only ever need textures.
 * <p>
 * The template list covers every parent category found in the hand-written
 * {@code models/} tree (909 files) — the migration provider routes each
 * parented model through its matching template and falls back to the
 * extracted data table for the few irregular ones.
 * <p>
 * 预设模型模板库（高版本 {@code TexturedModel} 的对应物）。
 * 每个模板记录 parent 路径与实例必须绑定的纹理槽；
 * {@link #create(Template, Map)} 生成填充好 {@code parent}/{@code textures}
 * 的 {@link ModelJson}。固定元素（如玻璃板的面）由模板模型自身定义，
 * 实例只需提供纹理。模板清单覆盖手写 {@code models/} 树中全部 parent 类别，
 * 迁移 Provider 将每个有 parent 的模型路由到匹配模板，少量不规则模型
 * 回退到提取的数据表。
 */
public final class ModelTemplates {

    private ModelTemplates() {
    }

    /**
     * A model template: parent path + texture slot names.
     */
    public enum Template {
        // --- items ---
        ITEM_GENERATED("item/generated", "layer0"),
        ITEM_GENERATED_MC("minecraft:item/generated", "layer0"),
        ITEM_HANDHELD("item/handheld", "layer0"),
        ITEM_BOW("minecraft:item/bow", "layer0"),
        BUILTIN_GENERATED("builtin/generated"),

        // --- full cubes ---
        CUBE_ALL("block/cube_all", "all"),
        CUBE_ALL_MC("minecraft:block/cube_all", "all"),
        CUBE("block/cube", "down", "up", "north", "south", "east", "west", "particle"),
        CUBE_BOTTOM_TOP("block/cube_bottom_top", "bottom", "side", "top"),
        CUBE_BOTTOM_TOP_MC("minecraft:block/cube_bottom_top", "bottom", "side", "top"),
        CUBE_COLUMN("block/cube_column", "end", "side"),
        LEAVES("block/leaves", "all"),

        // --- base model with display transforms ---
        BLOCK("block/block", "particle"),
        BLOCK_MC("minecraft:block/block", "particle"),

        // --- cross / plants ---
        CROSS("minecraft:block/cross", "cross"),
        CROSS_LEGACY("block/cross", "cross"),
        TINTED_CROSS("minecraft:block/tinted_cross", "cross"),

        // --- slabs / stairs ---
        SLAB("block/slab", "bottom", "side", "top"),
        SLAB_TOP("block/slab_top", "bottom", "side", "top"),
        SLAB_TOP_MC("minecraft:block/slab_top", "bottom", "side", "top"),
        STAIRS("minecraft:block/stairs", "bottom", "side", "top"),
        STAIRS_LEGACY("block/stairs", "bottom", "side", "top"),
        STAIRS_INNER("block/stairs_inner", "bottom", "side", "top"),
        INNER_STAIRS("block/inner_stairs", "bottom", "side", "top"),
        OUTER_STAIRS("block/outer_stairs", "bottom", "side", "top"),

        // --- rails ---
        RAIL_FLAT("minecraft:block/rail_flat", "rail"),
        RAIL_CURVED("minecraft:block/rail_curved", "rail"),
        RAIL_RAISED_NE("minecraft:block/template_rail_raised_ne", "rail"),
        RAIL_RAISED_SW("minecraft:block/template_rail_raised_sw", "rail"),

        // --- mechanism parts ---
        PRESSURE_PLATE_UP("minecraft:block/pressure_plate_up", "texture"),
        PRESSURE_PLATE_DOWN("minecraft:block/pressure_plate_down", "texture"),
        BUTTON("minecraft:block/button", "texture"),
        BUTTON_INVENTORY("minecraft:block/button_inventory", "texture"),
        BUTTON_PRESSED("minecraft:block/button_pressed", "texture"),
        TORCH("minecraft:block/template_torch", "torch"),
        TORCH_WALL("minecraft:block/template_torch_wall", "torch"),
        TORCH_WALL_LEGACY("block/template_torch_wall", "torch"),
        FENCE_GATE("minecraft:block/template_fence_gate", "texture"),
        FENCE_GATE_OPEN("minecraft:block/template_fence_gate_open", "texture"),
        FENCE_INVENTORY("minecraft:block/fence_inventory", "texture"),
        FENCE_POST("minecraft:block/fence_post", "texture"),
        FENCE_SIDE("minecraft:block/fence_side", "texture"),
        PISTON_HEAD("minecraft:block/template_piston_head", "platform", "side", "unsticky"),
        PISTON_HEAD_SHORT("minecraft:block/template_piston_head_short", "platform", "side", "unsticky"),
        COMPARATOR("block/template_comparator", "back_torch", "front_torch", "side", "top"),
        COMPARATOR_SUBTRACT("block/template_comparator_subtract", "back_torch", "front_torch", "side", "top"),
        DAYLIGHT_DETECTOR("minecraft:block/template_daylight_detector", "side", "top"),

        // --- glass panes ---
        GLASS_PANE_POST("minecraft:block/template_glass_pane_post", "edge", "pane"),
        GLASS_PANE_SIDE("minecraft:block/template_glass_pane_side", "edge", "pane"),
        GLASS_PANE_SIDE_ALT("minecraft:block/template_glass_pane_side_alt", "edge", "pane"),
        GLASS_PANE_NOSIDE("minecraft:block/template_glass_pane_noside", "pane"),
        GLASS_PANE_NOSIDE_ALT("minecraft:block/template_glass_pane_noside_alt", "pane"),

        // --- walls / bars ---
        WALL_POST("minecraft:block/template_wall_post", "wall"),
        WALL_SIDE("minecraft:block/template_wall_side", "wall"),
        WALL_INVENTORY("minecraft:block/wall_inventory", "wall"),
        WALL_POST_LEGACY("block/template_wall_post", "wall"),
        WALL_SIDE_LEGACY("block/template_wall_side", "wall"),
        BARS_CAP("minecraft:block/template_bars_cap", "bars", "edge"),
        BARS_CAP_ALT("minecraft:block/template_bars_cap_alt", "bars", "edge"),
        BARS_POST("minecraft:block/template_bars_post", "bars", "edge"),
        BARS_POST_ENDS("minecraft:block/template_bars_post_ends", "bars", "edge"),
        BARS_SIDE("minecraft:block/template_bars_side", "bars", "edge"),
        BARS_SIDE_ALT("minecraft:block/template_bars_side_alt", "bars", "edge"),

        // --- crops / stems ---
        CROP("minecraft:block/crop", "crop"),
        STEM_GROWTH0("minecraft:block/stem_growth0", "stem"),
        STEM_GROWTH1("minecraft:block/stem_growth1", "stem"),
        STEM_GROWTH2("minecraft:block/stem_growth2", "stem"),
        STEM_GROWTH3("minecraft:block/stem_growth3", "stem"),
        STEM_GROWTH4("minecraft:block/stem_growth4", "stem"),
        STEM_GROWTH5("minecraft:block/stem_growth5", "stem"),
        STEM_GROWTH6("minecraft:block/stem_growth6", "stem"),
        STEM_GROWTH7("minecraft:block/stem_growth7", "stem"),

        // --- doors / trapdoors ---
        DOOR_BOTTOM_LEFT("minecraft:block/door_bottom_left", "bottom", "top"),
        DOOR_BOTTOM_LEFT_OPEN("minecraft:block/door_bottom_left_open", "bottom", "top"),
        DOOR_BOTTOM_RIGHT("minecraft:block/door_bottom_right", "bottom", "top"),
        DOOR_BOTTOM_RIGHT_OPEN("minecraft:block/door_bottom_right_open", "bottom", "top"),
        DOOR_TOP_LEFT("minecraft:block/door_top_left", "bottom", "top"),
        DOOR_TOP_LEFT_OPEN("minecraft:block/door_top_left_open", "bottom", "top"),
        DOOR_TOP_RIGHT("minecraft:block/door_top_right", "bottom", "top"),
        DOOR_TOP_RIGHT_OPEN("minecraft:block/door_top_right_open", "bottom", "top"),
        TRAPDOOR_BOTTOM("block/template_trapdoor_bottom", "texture"),
        TRAPDOOR_OPEN("block/template_trapdoor_open", "texture"),
        TRAPDOOR_TOP("block/template_trapdoor_top", "texture"),

        // --- misc ---
        ANVIL("block/template_anvil", "body", "top"),
        CARPET("minecraft:block/carpet", "wool"),
        THIN_BLOCK("block/thin_block", "particle"),
        ORIENTABLE("minecraft:block/orientable", "front", "side", "top"),
        ORIENTABLE_VERTICAL("minecraft:block/orientable_vertical", "front", "side"),
        ORIENTABLE_WITH_BOTTOM("block/orientable_with_bottom", "bottom"),
        CAULDRON("block/cauldron"),
        TEMPLATE_CAULDRON_FULL("block/template_cauldron_full", "content"),
        TEMPLATE_CAULDRON_LEVEL1("block/template_cauldron_level1", "content"),
        TEMPLATE_CAULDRON_LEVEL2("block/template_cauldron_level2", "content"),
        ITEM_FRAME("minecraft:block/template_item_frame", "back", "particle", "wood"),
        ITEM_FRAME_MAP("minecraft:block/template_item_frame_map", "back", "particle", "wood");

        private final String parent;
        private final String[] slots;

        Template(String parent, String... slots) {
            this.parent = parent;
            this.slots = slots;
        }

        /**
         * @return parent model path (e.g. {@code "block/cube_all"})
         */
        public String parent() {
            return parent;
        }

        /**
         * @return texture slot names this template binds
         */
        public String[] slots() {
            return slots;
        }
    }

    /**
     * Resolves a texture location for one slot of a named model instance.
     */
    @FunctionalInterface
    public interface TextureResolver {
        /**
         * @param slot texture slot (e.g. {@code "all"}, {@code "layer0"})
         * @return texture location (e.g. {@code "blocks/stone"})
         */
        String resolve(String slot);
    }

    /**
     * Create a model instance from a template and explicit textures.
     *
     * @param template the template to instantiate
     * @param textures slot → texture location bindings
     * @return a {@link ModelJson} with parent and textures set
     */
    public static ModelJson create(Template template, Map<String, String> textures) {
        ModelJson model = new ModelJson();
        model.parent = template.parent();
        if (textures != null && !textures.isEmpty()) {
            model.textures = new LinkedHashMap<>(textures);
        }
        return model;
    }

    /**
     * Create a model instance from a template and a slot resolver.
     * <p>
     * Every declared slot of the template is resolved; extra slots produced by
     * the resolver are kept as-is.
     *
     * @param template the template to instantiate
     * @param name     model/block name passed to the resolver
     * @param resolver slot → texture location resolver
     * @return a {@link ModelJson} with parent and textures set
     */
    public static ModelJson create(Template template, String name, TextureResolver resolver) {
        Map<String, String> textures = new LinkedHashMap<>();
        for (String slot : template.slots()) {
            textures.put(slot, resolver.resolve(slot));
        }
        return create(template, textures);
    }
}
