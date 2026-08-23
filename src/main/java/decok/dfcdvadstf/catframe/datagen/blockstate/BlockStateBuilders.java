package decok.dfcdvadstf.catframe.datagen.blockstate;

import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson.MultipartCase;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson.MultipartWhen;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson.Variant;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson.VariantEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blockstate preset builders (the {@code BlockModelGenerators} counterpart of
 * the modern datagen pipeline).
 * <p>
 * Every method returns a ready-to-serialize {@link BlockstateJson} POJO. The
 * presets mirror the structural families found in the hand-written
 * {@code blockstates/} tree (144 files): simple cubes, stairs (40 variants),
 * doors (32), trapdoors (16), rails, levers, pistons, multipart panes/fences/
 * walls/bars/redstone wire, and so on. Irregular states fall back to the
 * low-level {@link #variant}/{@link #part}/{@link #when} DSL or to the
 * extracted data table in the migration providers.
 * <p>
 * Variant keys follow the 1.7.10 convention: property pairs sorted by
 * property name, joined with commas (the loader matches them verbatim).
 * <p>
 * blockstate 预设构建器（高版本 {@code BlockModelGenerators} 的对应物）。
 * 每个方法返回可直接序列化的 {@link BlockstateJson} POJO，预设覆盖手写
 * {@code blockstates/} 树（144 个文件）的全部结构族；不规则状态回退到
 * 底层 {@link #variant}/{@link #part}/{@link #when} DSL 或数据表。
 */
public final class BlockStateBuilders {

    private BlockStateBuilders() {
    }

    // ==================== Variant DSL ====================

    /**
     * Create a variant entry with the given model and no rotation.
     */
    public static Variant variant(String model) {
        return variant(model, 0, 0, false);
    }

    /**
     * Create a variant entry with the given model and rotation.
     *
     * @param model  model path (e.g. {@code "minecraft:block/stone"})
     * @param y      Y rotation in degrees (0/90/180/270)
     * @param x      X rotation in degrees (0/90/180/270)
     * @param uvlock whether to lock UVs when rotating
     */
    public static Variant variant(String model, int y, int x, boolean uvlock) {
        Variant v = new Variant();
        v.model = model;
        v.y = y;
        v.x = x;
        v.uvlock = uvlock;
        return v;
    }

    /**
     * Start a variant-only blockstate; call {@link VariantBuilder#add} for
     * each key then {@link VariantBuilder#build()}.
     */
    public static VariantBuilder variants() {
        return new VariantBuilder();
    }

    /**
     * Build a single-variant blockstate ({@code "normal"} key).
     */
    public static BlockstateJson simple(String model) {
        BlockstateJson bs = new BlockstateJson();
        bs.variants = new LinkedHashMap<>();
        bs.variants.put("normal", entry(variant(model)));
        return bs;
    }

    /**
     * Build a blockstate from an explicit key → variant map.
     */
    public static BlockstateJson variants(Map<String, Variant> variants) {
        BlockstateJson bs = new BlockstateJson();
        bs.variants = new LinkedHashMap<>();
        for (Map.Entry<String, Variant> e : variants.entrySet()) {
            bs.variants.put(e.getKey(), entry(e.getValue()));
        }
        return bs;
    }

    private static VariantEntry entry(Variant v) {
        VariantEntry entry = new VariantEntry();
        entry.single = v;
        return entry;
    }

    /**
     * Build a blockstate from explicit key → variant-entry map (supports
     * weighted arrays via {@link #entryList(Variant...)}).
     */
    public static BlockstateJson variantEntries(Map<String, VariantEntry> variants) {
        BlockstateJson bs = new BlockstateJson();
        bs.variants = new LinkedHashMap<>(variants);
        return bs;
    }

    /**
     * Create a weighted variant entry from several variants.
     */
    public static VariantEntry entryList(Variant... variants) {
        VariantEntry entry = new VariantEntry();
        entry.list = new ArrayList<>(Arrays.asList(variants));
        return entry;
    }

    // ==================== Facing helpers ====================

    /** South baseline: south=0, west=90, north=180, east=270 (ladder,
     * pumpkin, jack_o_lantern, fence gates, anvils). */
    private static final String[] FACING_SOUTH = {"south", "west", "north", "east"};

    /** North baseline: north=0, east=90, south=180, west=270 (furnace,
     * dispenser, comparator). */
    private static final String[] FACING_NORTH = {"north", "east", "south", "west"};

    /** East baseline: east=0, west=180, south=90, north=270 (lever, button). */
    private static final String[] FACING_EAST = {"east", "west", "south", "north"};
    private static final int[] FACING_EAST_Y = {0, 180, 90, 270};

    /**
     * Four facing variants rotating Y by 0/90/180/270, starting at south
     * (ladder, pumpkin, jack_o_lantern, fence gates, anvils).
     */
    public static BlockstateJson facing4(String model) {
        return variants(facingYMap(FACING_SOUTH, model));
    }

    /**
     * Four facing variants rotating Y by 0/90/180/270, starting at north
     * (furnace, lit_furnace).
     */
    public static BlockstateJson facing4North(String model) {
        return variants(facingYMap(FACING_NORTH, model));
    }

    private static Map<String, Variant> facingYMap(String[] facings, String model) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            map.put("facing=" + facings[i], variant(model, i * 90, 0, false));
        }
        return map;
    }

    /**
     * Six facing variants with a vertical model for up/down (dispenser,
     * dropper); horizontals start at north.
     */
    public static BlockstateJson dispenser(String vertical, String horizontal) {
        Map<String, Variant> map = new LinkedHashMap<>();
        map.put("facing=down", variant(vertical, 0, 180, false));
        map.put("facing=up", variant(vertical));
        for (int i = 0; i < 4; i++) {
            map.put("facing=" + FACING_NORTH[i], variant(horizontal, i * 90, 0, false));
        }
        return variants(map);
    }

    // ==================== Pillars / logs ====================

    /**
     * Log pillar blockstate: three axis variants per wood type.
     *
     * @param modelByWood wood variant name → base model path (e.g.
     *                    {@code "oak" → "minecraft:block/oak_log"})
     */
    public static BlockstateJson logs(Map<String, String> modelByWood) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : modelByWood.entrySet()) {
            String wood = e.getKey();
            String model = e.getValue();
            map.put("axis=y,wood=" + wood, variant(model));
            map.put("axis=x,wood=" + wood, variant(model, 90, 90, false));
            map.put("axis=z,wood=" + wood, variant(model, 0, 90, false));
        }
        return variants(map);
    }

    /**
     * Quartz block: axis variants, only the pillar type rotates.
     *
     * @param modelByType type variant name → model path (e.g.
     *                    {@code "quartz_block" → "minecraft:block/quartz_block"})
     */
    public static BlockstateJson quartz(Map<String, String> modelByType) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : modelByType.entrySet()) {
            String type = e.getKey();
            String model = e.getValue();
            map.put("axis=y,type=" + type, variant(model));
            map.put("axis=x,type=" + type, variant(model, 90, 90, false));
            map.put("axis=z,type=" + type, variant(model, 0, 90, false));
        }
        return variants(map);
    }

    // ==================== Stairs / slabs ====================

    /**
     * Stairs blockstate: 40 variants (facing × half × shape), matching the
     * hand-written acacia_stairs.json matrix.
     *
     * @param model  straight stairs model (e.g. {@code "minecraft:block/acacia_stairs"})
     * @param inner  inner stairs model (e.g. {@code "minecraft:block/acacia_inner_stairs"})
     * @param outer  outer stairs model (e.g. {@code "minecraft:block/acacia_outer_stairs"})
     */
    public static BlockstateJson stairs(String model, String inner, String outer) {
        Map<String, Variant> map = new LinkedHashMap<>();
        String[] facings = {"east", "west", "south", "north"};
        int[] yByFacing = {0, 180, 90, 270};
        String[] halves = {"bottom", "top"};
        // shape → (model, y offset for "left" shapes)
        String[][] shapes = {
                {"straight", model},
                {"outer_right", outer},
                {"outer_left", outer},
                {"inner_right", inner},
                {"inner_left", inner},
        };
        int[] shapeYOffset = {0, 0, 270, 0, 270};
        for (String half : halves) {
            int x = "top".equals(half) ? 180 : 0;
            for (int f = 0; f < 4; f++) {
                for (int s = 0; s < shapes.length; s++) {
                    int y = (yByFacing[f] + shapeYOffset[s]) % 360;
                    // Only facing=east, half=bottom, shape=straight omits
                    // uvlock in the hand-written matrix; all 39 others set it.
                    boolean uvlock = !(f == 0 && "bottom".equals(half) && s == 0);
                    Variant v = variant(shapes[s][1], y, x, uvlock);
                    // left-hand shapes spell the zero rotation out ("y": 0)
                    v.forceY = (s == 2 || s == 4) && y == 0;
                    map.put("facing=" + facings[f] + ",half=" + half + ",shape=" + shapes[s][0],
                            v);
                }
            }
        }
        return variants(map);
    }

    /**
     * Slab blockstate: half=bottom/top pair.
     */
    public static BlockstateJson slab(String model, String modelTop) {
        Map<String, Variant> map = new LinkedHashMap<>();
        map.put("half=bottom", variant(model));
        map.put("half=top", variant(modelTop));
        return variants(map);
    }

    /**
     * Slab blockstate with several material variants, each with a bottom/top
     * model pair.
     *
     * @param variantModels material variant name → {bottom model, top model}
     */
    public static BlockstateJson slabByVariant(Map<String, String[]> variantModels) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : variantModels.entrySet()) {
            map.put("half=bottom,variant=" + e.getKey(), variant(e.getValue()[0]));
            map.put("half=top,variant=" + e.getKey(), variant(e.getValue()[1]));
        }
        return variants(map);
    }

    /**
     * Double slab blockstate: one model per material variant.
     */
    public static BlockstateJson doubleSlabByVariant(Map<String, String> variantModels) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : variantModels.entrySet()) {
            map.put("variant=" + e.getKey(), variant(e.getValue()));
        }
        return variants(map);
    }

    // ==================== Crops / stems ====================

    /**
     * Crop blockstate: age=0..7 mapping to {@code <prefix>_stage0..7}.
     */
    public static BlockstateJson crop(String stagePrefix) {
        String[] stages = new String[8];
        for (int i = 0; i < 8; i++) {
            stages[i] = stagePrefix + "_stage" + i;
        }
        return cropStages(stages);
    }

    /**
     * Crop blockstate with an explicit stage model list (carrots/potatoes
     * reuse stage models in pairs).
     */
    public static BlockstateJson cropStages(String[] stageModels) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (int i = 0; i < stageModels.length; i++) {
            map.put("age=" + i, variant(stageModels[i]));
        }
        return variants(map);
    }

    /**
     * Melon/pumpkin stem: age=0..7 mapping to {@code <prefix>_stage0..7}.
     */
    public static BlockstateJson stem(String stagePrefix) {
        return crop(stagePrefix);
    }

    /**
     * Reeds: age=0..15 all mapping to one model.
     */
    public static BlockstateJson reeds(String model) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (int i = 0; i < 16; i++) {
            map.put("age=" + i, variant(model));
        }
        return variants(map);
    }

    // ==================== Mechanism blocks ====================

    /**
     * Lever: 12 variants (facing 4 × powered 2, plus up/down). Wall variants
     * rotate around the east baseline; up/down use the standing model with a
     * 180° X flip for down.
     *
     * @param wallModel    wall-mounted lever model (horizontal facings)
     * @param wallModelOn  powered wall-mounted lever model
     * @param model        standing lever model (facing=up/down)
     * @param modelOn      powered standing lever model
     */
    public static BlockstateJson lever(String wallModel, String wallModelOn, String model, String modelOn) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            map.put("facing=" + FACING_EAST[i] + ",powered=false", variant(wallModel, FACING_EAST_Y[i], 0, false));
            map.put("facing=" + FACING_EAST[i] + ",powered=true", variant(wallModelOn, FACING_EAST_Y[i], 0, false));
        }
        map.put("facing=up,powered=false", variant(model));
        map.put("facing=up,powered=true", variant(modelOn));
        map.put("facing=down,powered=false", variant(model, 0, 180, false));
        map.put("facing=down,powered=true", variant(modelOn, 0, 180, false));
        return variants(map);
    }

    /**
     * Button: 8 variants (facing 4 × powered 2), rotating around the east
     * baseline (east=0, west=180, south=90, north=270).
     */
    public static BlockstateJson button(String model, String modelPressed) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            map.put("facing=" + FACING_EAST[i] + ",powered=false", variant(model, FACING_EAST_Y[i], 0, false));
            map.put("facing=" + FACING_EAST[i] + ",powered=true", variant(modelPressed, FACING_EAST_Y[i], 0, false));
        }
        return variants(map);
    }

    /**
     * Piston: 12 variants (extended × facing 6).
     */
    public static BlockstateJson piston(String model, String modelExtended) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (String extended : new String[]{"false", "true"}) {
            String m = extended.equals("false") ? model : modelExtended;
            map.put("extended=" + extended + ",facing=down", variant(m, 0, 270, false));
            map.put("extended=" + extended + ",facing=up", variant(m, 0, 90, false));
            map.put("extended=" + extended + ",facing=north", variant(m));
            map.put("extended=" + extended + ",facing=south", variant(m, 180, 0, false));
            map.put("extended=" + extended + ",facing=west", variant(m, 270, 0, false));
            map.put("extended=" + extended + ",facing=east", variant(m, 90, 0, false));
        }
        return variants(map);
    }

    /**
     * Piston head: 12 variants (facing 6 × sticky 2).
     */
    public static BlockstateJson pistonHead(String model, String modelSticky) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (String sticky : new String[]{"false", "true"}) {
            String m = sticky.equals("false") ? model : modelSticky;
            map.put("facing=down,sticky=" + sticky, variant(m, 0, 270, false));
            map.put("facing=up,sticky=" + sticky, variant(m, 0, 90, false));
            map.put("facing=north,sticky=" + sticky, variant(m));
            map.put("facing=south,sticky=" + sticky, variant(m, 180, 0, false));
            map.put("facing=west,sticky=" + sticky, variant(m, 270, 0, false));
            map.put("facing=east,sticky=" + sticky, variant(m, 90, 0, false));
        }
        return variants(map);
    }

    /**
     * Anvil: 12 variants (damage 3 × facing 4).
     *
     * @param models three damage models: intact, slightly damaged, very damaged
     */
    public static BlockstateJson anvil(String[] models) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (int d = 0; d < 3; d++) {
            for (int i = 0; i < 4; i++) {
                map.put("damage=" + d + ",facing=" + FACING_SOUTH[i], variant(models[d], i * 90, 0, false));
            }
        }
        return variants(map);
    }

    /**
     * Hopper: 10 variants (facing 5 × powered 2).
     */
    public static BlockstateJson hopper(String model, String modelSide) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (String powered : new String[]{"false", "true"}) {
            map.put("facing=down,powered=" + powered, variant(model));
            map.put("facing=north,powered=" + powered, variant(modelSide));
            map.put("facing=south,powered=" + powered, variant(modelSide, 180, 0, false));
            map.put("facing=west,powered=" + powered, variant(modelSide, 270, 0, false));
            map.put("facing=east,powered=" + powered, variant(modelSide, 90, 0, false));
        }
        return variants(map);
    }

    /**
     * Torch: 5 variants (facing 4 wall + standing).
     */
    public static BlockstateJson torch(String model, String modelWall) {
        Map<String, Variant> map = new LinkedHashMap<>();
        map.put("facing=east", variant(modelWall));
        map.put("facing=west", variant(modelWall, 180, 0, false));
        map.put("facing=south", variant(modelWall, 90, 0, false));
        map.put("facing=north", variant(modelWall, 270, 0, false));
        map.put("facing=standing", variant(model));
        return variants(map);
    }

    /**
     * Pressure plate: 2 variants (powered=false/true).
     */
    public static BlockstateJson pressurePlate(String model, String modelDown) {
        Map<String, Variant> map = new LinkedHashMap<>();
        map.put("powered=false", variant(model));
        map.put("powered=true", variant(modelDown));
        return variants(map);
    }

    /**
     * Rail: 12 variants (shape 6 × no powered property in 1.7.10 model keys).
     *
     * @param modelFlat   flat rail model
     * @param modelRaisedNe ascending north-east rail model
     * @param modelRaisedSw ascending south-west rail model
     */
    public static BlockstateJson rail(String modelFlat, String modelRaisedNe, String modelRaisedSw) {
        Map<String, Variant> map = new LinkedHashMap<>();
        map.put("shape=north_south", variant(modelFlat));
        map.put("shape=east_west", variant(modelFlat, 90, 0, false));
        map.put("shape=ascending_east", variant(modelRaisedNe, 90, 0, false));
        map.put("shape=ascending_west", variant(modelRaisedSw, 90, 0, false));
        map.put("shape=ascending_north", variant(modelRaisedNe));
        map.put("shape=ascending_south", variant(modelRaisedSw));
        return variants(map);
    }

    /**
     * Fence gate: 8 variants (facing 4 × open 2).
     */
    public static BlockstateJson gate(String model, String modelOpen) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            map.put("facing=" + FACING_SOUTH[i] + ",open=false", variant(model, i * 90, 0, false));
            map.put("facing=" + FACING_SOUTH[i] + ",open=true", variant(modelOpen, i * 90, 0, false));
        }
        return variants(map);
    }

    /**
     * Door: 32 variants (facing 4 × half 2 × hinge 2 × open 2). Model names
     * use the bottom/top half wording while variant keys keep the vanilla
     * lower/upper property values.
     *
     * @param modelPrefix model name prefix (e.g. {@code "minecraft:block/wooden_door"})
     */
    public static BlockstateJson door(String modelPrefix) {
        Map<String, Variant> map = new LinkedHashMap<>();
        String[] facings = {"east", "north", "south", "west"};
        String[] hinges = {"left", "right"};
        for (String facing : facings) {
            for (String half : new String[]{"lower", "upper"}) {
                String modelHalf = "lower".equals(half) ? "bottom" : "top";
                for (String hinge : hinges) {
                    String base = modelPrefix + "_" + modelHalf + "_" + hinge;
                    map.put("facing=" + facing + ",half=" + half + ",hinge=" + hinge + ",open=false",
                            variant(base, doorY(facing, false, hinge), 0, false));
                    map.put("facing=" + facing + ",half=" + half + ",hinge=" + hinge + ",open=true",
                            variant(base + "_open", doorY(facing, true, hinge), 0, false));
                }
            }
        }
        return variants(map);
    }

    private static int doorY(String facing, boolean open, String hinge) {
        // Base rotation: east=0, north=270, south=90, west=180 (1.7.10 door matrix)
        int base;
        switch (facing) {
            case "east": base = 0; break;
            case "north": base = 270; break;
            case "south": base = 90; break;
            default: base = 180; break;
        }
        if (!open) {
            return base;
        }
        // Open offsets: left hinge +90, right hinge +270 (matching hand-written files)
        int offset = hinge.equals("left") ? 90 : 270;
        return (base + offset) % 360;
    }

    /**
     * Trapdoor: 16 variants (facing 4 × half 2 × open 2).
     */
    public static BlockstateJson trapdoor(String modelBottom, String modelTop, String modelOpen) {
        Map<String, Variant> map = new LinkedHashMap<>();
        String[] facings = {"north", "south", "east", "west"};
        int[] y = {0, 180, 90, 270};
        for (int f = 0; f < 4; f++) {
            for (String half : new String[]{"bottom", "top"}) {
                String closed = half.equals("bottom") ? modelBottom : modelTop;
                map.put("facing=" + facings[f] + ",half=" + half + ",open=false",
                        variant(closed, y[f], 0, false));
                map.put("facing=" + facings[f] + ",half=" + half + ",open=true",
                        variant(modelOpen, y[f], 0, false));
            }
        }
        return variants(map);
    }

    /**
     * Cake: 7 variants (bites=0..6).
     *
     * @param modelPrefix model name prefix (e.g. {@code "minecraft:block/cake"})
     */
    public static BlockstateJson cake(String modelPrefix) {
        Map<String, Variant> map = new LinkedHashMap<>();
        map.put("bites=0", variant(modelPrefix));
        for (int i = 1; i <= 6; i++) {
            map.put("bites=" + i, variant(modelPrefix + "_slice" + i));
        }
        return variants(map);
    }

    /**
     * Cauldron: 4 variants (level=0..3).
     *
     * @param models four models for level 0..3 (empty, level1, level2, full)
     */
    public static BlockstateJson cauldron(String[] models) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (int i = 0; i < models.length; i++) {
            map.put("level=" + i, variant(models[i]));
        }
        return variants(map);
    }

    /**
     * Repeater: 16 variants (delay 4 × facing 4), model names follow
     * {@code <base>_<delay>tick<suffix>} (suffix {@code "_on"} for the
     * powered variant).
     */
    public static BlockstateJson repeater(String modelBase, String modelSuffix) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (int delay = 1; delay <= 4; delay++) {
            String model = modelBase + "_" + delay + "tick" + modelSuffix;
            map.put("delay=" + delay + ",facing=north", variant(model, 180, 0, false));
            map.put("delay=" + delay + ",facing=east", variant(model, 270, 0, false));
            map.put("delay=" + delay + ",facing=south", variant(model));
            map.put("delay=" + delay + ",facing=west", variant(model, 90, 0, false));
        }
        return variants(map);
    }

    /**
     * Comparator: 16 variants (facing 4 × mode 2 × powered 2).
     */
    public static BlockstateJson comparator(String modelPrefix) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            String facing = FACING_NORTH[i];
            int y = i * 90;
            map.put("facing=" + facing + ",mode=compare,powered=false", variant(modelPrefix + "_off", y, 0, false));
            map.put("facing=" + facing + ",mode=compare,powered=true", variant(modelPrefix + "_on", y, 0, false));
            map.put("facing=" + facing + ",mode=subtract,powered=false", variant(modelPrefix + "_off_subtract", y, 0, false));
            map.put("facing=" + facing + ",mode=subtract,powered=true", variant(modelPrefix + "_on_subtract", y, 0, false));
        }
        return variants(map);
    }

    // ==================== Enum variant blocks ====================

    /**
     * Enum-dispatch blockstate: variant name → model, no rotation
     * (planks, saplings, flowers, monster eggs, sandstone...).
     */
    public static BlockstateJson variantMap(Map<String, String> variantModels) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : variantModels.entrySet()) {
            map.put("variant=" + e.getKey(), variant(e.getValue()));
        }
        return variants(map);
    }

    /**
     * Sixteen-color blockstate: color=white..black → model per color.
     */
    public static BlockstateJson colorMap(Map<String, String> colorModels) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : colorModels.entrySet()) {
            map.put("color=" + e.getKey(), variant(e.getValue()));
        }
        return variants(map);
    }

    /**
     * Double plant: 12 variants (half 2 × plant 6).
     */
    public static BlockstateJson doublePlant(Map<String, String[]> plantModels) {
        Map<String, Variant> map = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : plantModels.entrySet()) {
            map.put("half=lower,variant=" + e.getKey(), variant(e.getValue()[0]));
            map.put("half=upper,variant=" + e.getKey(), variant(e.getValue()[1]));
        }
        return variants(map);
    }

    // ==================== Multipart DSL ====================

    /**
     * Start a multipart blockstate; call {@link MultipartBuilder#part} /
     * {@link MultipartBuilder#partWhen} then {@link MultipartBuilder#build()}.
     */
    public static MultipartBuilder multipart() {
        return new MultipartBuilder();
    }

    /**
     * Create a condition for one property.
     */
    public static MultipartWhen when(String property, String value) {
        MultipartWhen w = new MultipartWhen();
        w.conditions = new LinkedHashMap<>();
        w.conditions.put(property, value);
        return w;
    }

    /**
     * Create an OR condition from several property maps.
     */
    public static MultipartWhen or(Map<String, String>... groups) {
        MultipartWhen w = new MultipartWhen();
        w.or = new ArrayList<>(Arrays.asList(groups));
        return w;
    }

    /**
     * Create an OR condition with a single group of property maps.
     */
    public static MultipartWhen or(List<Map<String, String>> groups) {
        MultipartWhen w = new MultipartWhen();
        w.or = new ArrayList<>(groups);
        return w;
    }

    private static MultipartCase caseOf(Variant apply) {
        MultipartCase c = new MultipartCase();
        c.apply = apply;
        return c;
    }

    // ==================== Multipart presets ====================

    /**
     * Glass pane: 9 multipart parts (post + 4 sides + 4 nosides), matching
     * the hand-written glass_pane.json.
     */
    public static BlockstateJson pane(String modelPrefix) {
        MultipartBuilder mb = multipart();
        mb.part(variant(modelPrefix + "_post"));
        mb.partWhen(when("north", "true"), variant(modelPrefix + "_side"));
        mb.partWhen(when("east", "true"), variant(modelPrefix + "_side", 90, 0, false));
        mb.partWhen(when("south", "true"), variant(modelPrefix + "_side_alt"));
        mb.partWhen(when("west", "true"), variant(modelPrefix + "_side_alt", 90, 0, false));
        mb.partWhen(when("north", "false"), variant(modelPrefix + "_noside"));
        mb.partWhen(when("east", "false"), variant(modelPrefix + "_noside_alt"));
        mb.partWhen(when("south", "false"), variant(modelPrefix + "_noside_alt", 90, 0, false));
        mb.partWhen(when("west", "false"), variant(modelPrefix + "_noside", 270, 0, false));
        return mb.build();
    }

    /**
     * Fence: 5 multipart parts (post + 4 sides), matching the hand-written
     * fence.json.
     */
    public static BlockstateJson fence(String modelPrefix) {
        MultipartBuilder mb = multipart();
        mb.part(variant(modelPrefix + "_post"));
        mb.partWhen(when("north", "true"), variant(modelPrefix + "_side"));
        mb.partWhen(when("east", "true"), variant(modelPrefix + "_side", 90, 0, false));
        mb.partWhen(when("south", "true"), variant(modelPrefix + "_side", 180, 0, false));
        mb.partWhen(when("west", "true"), variant(modelPrefix + "_side", 270, 0, false));
        return mb.build();
    }

    /**
     * Iron bars: 10 multipart parts (isolated post + 4 sides + 4 caps + full
     * connection), matching the hand-written iron_bars.json.
     */
    public static BlockstateJson bars(String modelPrefix) {
        MultipartBuilder mb = multipart();
        Map<String, String> none = new LinkedHashMap<>();
        none.put("north", "false");
        none.put("east", "false");
        none.put("south", "false");
        none.put("west", "false");
        mb.partWhen(when("north", "false").conditions == null ? when("north", "false") : when("north", "false"), variant(modelPrefix + "_post"));
        mb.part(variant(modelPrefix + "_post"));
        mb.partWhen(when("north", "true"), variant(modelPrefix + "_side"));
        mb.partWhen(when("east", "true"), variant(modelPrefix + "_side", 90, 0, false));
        mb.partWhen(when("south", "true"), variant(modelPrefix + "_side_alt"));
        mb.partWhen(when("west", "true"), variant(modelPrefix + "_side_alt", 90, 0, false));
        // caps: exactly one side connected
        mb.partWhen(or(capGroup("north")), variant(modelPrefix + "_cap"));
        mb.partWhen(or(capGroup("east")), variant(modelPrefix + "_cap", 90, 0, false));
        mb.partWhen(or(capGroup("south")), variant(modelPrefix + "_cap_alt"));
        mb.partWhen(or(capGroup("west")), variant(modelPrefix + "_cap_alt", 90, 0, false));
        return mb.build();
    }

    private static Map<String, String> capGroup(String connected) {
        Map<String, String> group = new LinkedHashMap<>();
        String[] dirs = {"north", "east", "south", "west"};
        for (String d : dirs) {
            group.put(d, d.equals(connected) ? "true" : "false");
        }
        return group;
    }

    /**
     * Cobblestone wall: multipart with one post+4 sides per variant, matching
     * the hand-written cobblestone_wall.json.
     *
     * @param modelByVariant variant name → model prefix (e.g.
     *                       {@code "cobblestone" → "minecraft:block/cobblestone_wall"})
     */
    public static BlockstateJson wall(Map<String, String> modelByVariant) {
        MultipartBuilder mb = multipart();
        for (Map.Entry<String, String> e : modelByVariant.entrySet()) {
            String variant = e.getKey();
            String prefix = e.getValue();
            MultipartWhen v = when("variant", variant);
            mb.partWhen(v, variant(prefix + "_post"));
            mb.partWhen(and(v, "north", "true"), variant(prefix + "_side"));
            mb.partWhen(and(v, "east", "true"), variant(prefix + "_side", 90, 0, false));
            mb.partWhen(and(v, "south", "true"), variant(prefix + "_side", 180, 0, false));
            mb.partWhen(and(v, "west", "true"), variant(prefix + "_side", 270, 0, false));
        }
        return mb.build();
    }

    private static MultipartWhen and(MultipartWhen base, String property, String value) {
        MultipartWhen w = new MultipartWhen();
        w.conditions = new LinkedHashMap<>();
        w.conditions.putAll(base.conditions);
        w.conditions.put(property, value);
        return w;
    }

    /**
     * Redstone wire: 11 multipart parts (dot OR-groups, side/up/line parts),
     * matching the hand-written redstone_wire.json. The OR groups enumerate
     * exactly the connection combinations the loader must disambiguate.
     */
    public static BlockstateJson redstoneWire(String modelPrefix) {
        MultipartBuilder mb = multipart();
        // dot: any combination that is not a straight line or corner bend
        mb.partWhen(or(dotGroups()), variant(modelPrefix + "_dot"));
        mb.partWhen(or(sideGroup("north")), variant(modelPrefix + "_side_north"));
        mb.partWhen(or(sideGroup("south")), variant(modelPrefix + "_side_south"));
        mb.partWhen(or(sideGroup("east")), variant(modelPrefix + "_side_east"));
        mb.partWhen(or(sideGroup("west")), variant(modelPrefix + "_side_west"));
        mb.partWhen(or(upGroup("north")), variant(modelPrefix + "_up_north"));
        mb.partWhen(or(upGroup("south")), variant(modelPrefix + "_up_south"));
        mb.partWhen(or(upGroup("east")), variant(modelPrefix + "_up_east"));
        mb.partWhen(or(upGroup("west")), variant(modelPrefix + "_up_west"));
        mb.partWhen(or(lineGroup("north_south")), variant(modelPrefix + "_line_ns"));
        mb.partWhen(or(lineGroup("east_west")), variant(modelPrefix + "_line"));
        return mb.build();
    }

    private static List<Map<String, String>> dotGroups() {
        List<Map<String, String>> groups = new ArrayList<>();
        groups.add(boolMap("false", "false", "false", "false"));
        groups.add(boolMap("true", "false", "false", "false"));
        groups.add(boolMap("false", "true", "false", "false"));
        groups.add(boolMap("false", "false", "true", "false"));
        groups.add(boolMap("false", "false", "false", "true"));
        groups.add(boolMap("true", "true", "true", "false"));
        groups.add(boolMap("true", "true", "false", "true"));
        groups.add(boolMap("true", "true", "true", "true"));
        groups.add(boolMap("true", "false", "true", "false"));
        groups.add(boolMap("true", "false", "false", "true"));
        groups.add(boolMap("true", "false", "true", "true"));
        groups.add(boolMap("false", "true", "true", "false"));
        groups.add(boolMap("false", "true", "false", "true"));
        groups.add(boolMap("false", "true", "true", "true"));
        return groups;
    }

    private static List<Map<String, String>> sideGroup(String connected) {
        List<Map<String, String>> groups = new ArrayList<>();
        // straight line through the connected direction
        switch (connected) {
            case "north":
                groups.add(boolMap("true", "false", "false", "false"));
                groups.add(boolMap("true", "true", "true", "false"));
                groups.add(boolMap("true", "true", "false", "true"));
                groups.add(boolMap("true", "true", "true", "true"));
                groups.add(boolMap("true", "false", "true", "false"));
                groups.add(boolMap("true", "false", "false", "true"));
                groups.add(boolMap("true", "false", "true", "true"));
                break;
            case "south":
                groups.add(boolMap("false", "true", "false", "false"));
                groups.add(boolMap("true", "true", "true", "false"));
                groups.add(boolMap("true", "true", "false", "true"));
                groups.add(boolMap("true", "true", "true", "true"));
                groups.add(boolMap("false", "true", "true", "false"));
                groups.add(boolMap("false", "true", "false", "true"));
                groups.add(boolMap("false", "true", "true", "true"));
                break;
            case "east":
                groups.add(boolMap("false", "false", "true", "false"));
                groups.add(boolMap("true", "true", "true", "false"));
                groups.add(boolMap("true", "true", "true", "true"));
                groups.add(boolMap("true", "false", "true", "false"));
                groups.add(boolMap("false", "true", "true", "false"));
                groups.add(boolMap("true", "false", "true", "true"));
                groups.add(boolMap("false", "true", "true", "true"));
                break;
            case "west":
                groups.add(boolMap("false", "false", "false", "true"));
                groups.add(boolMap("true", "true", "false", "true"));
                groups.add(boolMap("true", "true", "true", "true"));
                groups.add(boolMap("true", "false", "false", "true"));
                groups.add(boolMap("false", "true", "false", "true"));
                groups.add(boolMap("true", "false", "true", "true"));
                groups.add(boolMap("false", "true", "true", "true"));
                break;
            default:
                break;
        }
        return groups;
    }

    private static List<Map<String, String>> upGroup(String direction) {
        // up_* models apply when the wire goes up in that direction (north/east
        // connections in 1.7.10 semantics, matching hand-written OR groups)
        List<Map<String, String>> groups = new ArrayList<>();
        groups.add(boolMap("true", "false", "false", "false"));
        groups.add(boolMap("true", "true", "true", "false"));
        groups.add(boolMap("true", "true", "false", "true"));
        groups.add(boolMap("true", "true", "true", "true"));
        groups.add(boolMap("true", "false", "true", "false"));
        groups.add(boolMap("true", "false", "false", "true"));
        groups.add(boolMap("true", "false", "true", "true"));
        return groups;
    }

    private static List<Map<String, String>> lineGroup(String orientation) {
        List<Map<String, String>> groups = new ArrayList<>();
        if (orientation.equals("north_south")) {
            groups.add(boolMap("true", "false", "false", "false"));
            groups.add(boolMap("false", "true", "false", "false"));
            groups.add(boolMap("true", "true", "false", "false"));
        } else {
            groups.add(boolMap("false", "false", "true", "false"));
            groups.add(boolMap("false", "false", "false", "true"));
            groups.add(boolMap("false", "false", "true", "true"));
        }
        return groups;
    }

    private static Map<String, String> boolMap(String north, String south, String east, String west) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("north", north);
        m.put("south", south);
        m.put("east", east);
        m.put("west", west);
        return m;
    }

    // ==================== Builders ====================

    /**
     * Fluent builder for variant blockstates.
     */
    public static final class VariantBuilder {
        private final Map<String, VariantEntry> variants = new LinkedHashMap<>();

        private VariantBuilder() {
        }

        /**
         * Add one variant key → model mapping.
         */
        public VariantBuilder add(String key, String model) {
            variants.put(key, entry(variant(model)));
            return this;
        }

        /**
         * Add one variant key with rotation.
         */
        public VariantBuilder add(String key, String model, int y, int x, boolean uvlock) {
            variants.put(key, entry(variant(model, y, x, uvlock)));
            return this;
        }

        /**
         * Add a pre-built variant entry (supports weighted arrays).
         */
        public VariantBuilder add(String key, VariantEntry entry) {
            variants.put(key, entry);
            return this;
        }

        /**
         * Finish and return the blockstate POJO.
         */
        public BlockstateJson build() {
            BlockstateJson bs = new BlockstateJson();
            bs.variants = new LinkedHashMap<>(variants);
            return bs;
        }
    }

    /**
     * Fluent builder for multipart blockstates.
     */
    public static final class MultipartBuilder {
        private final List<MultipartCase> parts = new ArrayList<>();

        private MultipartBuilder() {
        }

        /**
         * Add an unconditional part.
         */
        public MultipartBuilder part(String model) {
            parts.add(caseOf(variant(model)));
            return this;
        }

        /**
         * Add an unconditional part with rotation.
         */
        public MultipartBuilder part(String model, int y, int x, boolean uvlock) {
            parts.add(caseOf(variant(model, y, x, uvlock)));
            return this;
        }

        /**
         * Add a pre-built apply variant.
         */
        public MultipartBuilder part(Variant apply) {
            parts.add(caseOf(apply));
            return this;
        }

        /**
         * Add a conditional part.
         */
        public MultipartBuilder partWhen(MultipartWhen when, String model) {
            return partWhen(when, variant(model));
        }

        /**
         * Add a conditional part with rotation.
         */
        public MultipartBuilder partWhen(MultipartWhen when, String model, int y, int x, boolean uvlock) {
            return partWhen(when, variant(model, y, x, uvlock));
        }

        /**
         * Add a conditional part with a pre-built apply variant.
         */
        public MultipartBuilder partWhen(MultipartWhen when, Variant apply) {
            MultipartCase c = caseOf(apply);
            c.when = when;
            parts.add(c);
            return this;
        }

        /**
         * Finish and return the blockstate POJO.
         */
        public BlockstateJson build() {
            BlockstateJson bs = new BlockstateJson();
            bs.multipart = new ArrayList<>(parts);
            return bs;
        }
    }
}
