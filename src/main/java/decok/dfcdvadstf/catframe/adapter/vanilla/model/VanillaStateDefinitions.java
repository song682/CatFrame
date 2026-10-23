package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.Models;
import decok.dfcdvadstf.catframe.model.state.BlockStateDefinition;
import decok.dfcdvadstf.catframe.model.state.property.BooleanFlagProperty;
import decok.dfcdvadstf.catframe.model.state.property.EnumMetaProperty;
import decok.dfcdvadstf.catframe.model.state.property.MappedMetaProperty;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import decok.dfcdvadstf.catframe.model.state.property.StateDefinitions;
import decok.dfcdvadstf.catframe.model.state.property.TableMetaProperty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.init.Blocks;

import java.util.Arrays;

/**
 * Registration entry point for typed state definitions ({@link BlockStateDefinition}) of vanilla blocks.
 * <p>
 * Replaces the old {@code VanillaMetadataMapper} ({@code IMetadataMapper} lambda) and the redundant
 * {@code metadata_map.json}: the "metadata → property value → variant key" decoding layer is unified into
 * {@link Property} value domains + {@link BlockStateDefinition.MetaCodec}, aligning with the wiki / modern
 * {@code BlockStateModelDispatcher} "property + value-domain driven dispatch" model.
 *
 * <h3>Behavior preservation</h3>
 * Each def's {@code getStateFromMeta(meta)} reproduces the same {@code {property:value}} mapping emitted by the
 * old lambdas; variant matching uniformly goes through {@code RenderDispatcher.buildVariantKey} (alphabetical +
 * {@code toString()}),
 * so the final variant key is byte-identical to the old path. All properties use
 * {@link StateDefinitions#stringProp}
 * (the value is the string itself, so {@code toString()} aligns exactly with the variant key values in the
 * blockstate JSON).
 *
 * <h3>Dynamic properties</h3>
 * Stair {@code shape} and pane {@code north/east/south/west} are marked {@code .dynamic(...)}
 * (excluded from meta decoding) and are computed from the world and overridden at runtime by
 * {@link VanillaBlockResolvers}.
 */
@SideOnly(Side.CLIENT)
public final class VanillaStateDefinitions {

        private VanillaStateDefinitions() {
        }

        // ==================== Shared / per-block property constants ====================

        // Logs / leaves
        public static final Property<String> LOG_WOOD = StateDefinitions.stringProp("wood", "oak", "spruce", "birch",
                        "jungle");
        public static final Property<String> LOG2_WOOD = StateDefinitions.stringProp("wood", "acacia", "dark_oak");

        // Single variant / wood (clamp decode)
        public static final Property<String> SAPLING_VARIANT = StateDefinitions.stringProp("variant", "oak", "spruce",
                        "birch", "jungle", "acacia", "dark_oak");
        public static final Property<String> EGG_VARIANT = StateDefinitions.stringProp("variant", "stone",
                        "cobblestone", "stone_brick",
                        "mossy_stone_brick", "cracked_stone_brick", "chiseled_stone_brick");
        public static final Property<String> PLANK_WOOD = StateDefinitions.stringProp("wood", "oak", "spruce", "birch",
                        "jungle", "acacia", "dark_oak");
        public static final Property<String> STONEBRICK_VARIANT = StateDefinitions.stringProp("variant", "stonebrick",
                        "mossy_stonebrick",
                        "cracked_stonebrick", "chiseled_stonebrick");
        public static final Property<String> SANDSTONE_VARIANT = StateDefinitions.stringProp("variant", "sandstone",
                        "chiseled_sandstone", "smooth_sandstone");
        public static final Property<String> SAND_VARIANT = StateDefinitions.stringProp("variant", "sand", "red_sand");
        public static final Property<String> DIRT_VARIANT = StateDefinitions.stringProp("variant", "dirt", "podzol");
        public static final Property<String> WALL_VARIANT = StateDefinitions.stringProp("variant", "cobblestone",
                        "mossy_cobblestone");

        // Flowers
        public static final Property<String> RED_FLOWER_VARIANT = StateDefinitions.stringProp("variant", "poppy",
                        "blue_orchid", "allium", "houstonia",
                        "tulip_red", "tulip_orange", "tulip_white", "tulip_pink", "oxeye_daisy");
        public static final Property<String> YELLOW_FLOWER_VARIANT = StateDefinitions.stringProp("variant",
                        "dandelion");

        // Tall grass (single block)
        public static final Property<String> TALLGRASS_VARIANT = StateDefinitions.stringProp("variant", "grass",
                        "fern");

        // Grass block snow overlay (dynamic; computed at runtime by the SNOWY resolver from the block above)
        public static final Property<String> SNOWY = StateDefinitions.stringProp("snowy", "false", "true");

        // Double plants (2 blocks tall)
        public static final Property<String> DOUBLE_PLANT_VARIANT = StateDefinitions.stringProp("variant", "sunflower",
                        "lilac",
                        "double_grass", "double_fern", "rose_bush", "peony");
        // Vanilla double_plant half uses lower/upper — NOT the slab's bottom/top
        // vocabulary
        // Vanilla double_plant's half is lower/upper - not the slab's bottom/top vocabulary, so SLAB_HALF cannot be reused
        public static final Property<String> PLANT_HALF = StateDefinitions.stringProp("half", "lower", "upper");

        // Stone slab
        public static final Property<String> SLAB_VARIANT = StateDefinitions.stringProp("variant", "stone",
                        "sandstone", "wood", "cobblestone",
                        "brick", "stone_brick", "nether_brick", "quartz");

        // Anvil
        public static final Property<String> ANVIL_FACING = StateDefinitions.stringProp("facing", "north", "east",
                        "south", "west");
        public static final Property<String> ANVIL_DAMAGE = StateDefinitions.stringProp("damage", "0", "1", "2");

        // Crop age
        public static final Property<String> AGE = StateDefinitions.stringProp("age", "0", "1", "2", "3", "4", "5",
                        "6", "7");

        // Hopper
        public static final Property<String> HOPPER_FACING = StateDefinitions.stringProp("facing", "down", "north",
                        "south", "west", "east");
        public static final Property<String> POWERED = StateDefinitions.stringProp("powered", "false", "true");

        // Quartz block
        public static final Property<String> QUARTZ_TYPE = StateDefinitions.stringProp("type", "quartz_block",
                        "chiseled_quartz_block", "quartz_pillar");

        // Stairs
        public static final Property<String> STAIR_FACING = StateDefinitions.stringProp("facing", "east", "west",
                        "south", "north");
        public static final Property<String> STAIR_SHAPE = StateDefinitions.stringProp("shape", "straight",
                        "inner_left", "inner_right", "outer_left", "outer_right");

        // Pane connections
        public static final Property<String> PANE_NORTH = StateDefinitions.stringProp("north", "false", "true");
        public static final Property<String> PANE_EAST = StateDefinitions.stringProp("east", "false", "true");
        public static final Property<String> PANE_SOUTH = StateDefinitions.stringProp("south", "false", "true");
        public static final Property<String> PANE_WEST = StateDefinitions.stringProp("west", "false", "true");

        // Wooden slab variants (double_wooden_slab / wooden_slab: 6 in total via BlockWoodSlab.field_150005_b)
        public static final Property<String> WOOD_SLAB_VARIANT = StateDefinitions.stringProp("variant", "oak",
                        "spruce", "birch", "jungle", "acacia", "big_oak");

        // Torch facing (east/west/south/north wall-mounted, standing on the ground)
        public static final Property<String> TORCH_FACING = StateDefinitions.stringProp("facing", "east", "west",
                        "south", "north", "standing");

        // Trapdoor
        public static final Property<String> TRAPDOOR_FACING = StateDefinitions.stringProp("facing", "north", "south",
                        "east", "west");
        public static final Property<String> OPEN = StateDefinitions.stringProp("open", "false", "true");

        // Doors (iron + wooden): the facing values align with the DOOR resolver's 1.8 mapping table (east/south/west/north)
        public static final Property<String> DOOR_FACING = StateDefinitions.stringProp("facing", "east", "south",
                        "west", "north");
        public static final Property<String> DOOR_HALF = StateDefinitions.stringProp("half", "lower", "upper");
        public static final Property<String> DOOR_HINGE = StateDefinitions.stringProp("hinge", "left", "right");

        // Cauldron water level
        public static final Property<String> LEVEL = StateDefinitions.stringProp("level", "0", "1", "2", "3");

        // Redstone wire signal strength
        public static final Property<String> POWER = StateDefinitions.stringProp("power", "0", "1", "2", "3", "4", "5",
                        "6", "7",
                        "8", "9", "10", "11", "12", "13", "14", "15");

        // Redstone wire climbing (1.7.10 renderBlockRedstoneWire's four independent ramp faces)
        public static final Property<String> REDSTONE_UP_NORTH = StateDefinitions.stringProp("up_north", "false",
                        "true");
        public static final Property<String> REDSTONE_UP_EAST = StateDefinitions.stringProp("up_east", "false",
                        "true");
        public static final Property<String> REDSTONE_UP_SOUTH = StateDefinitions.stringProp("up_south", "false",
                        "true");
        public static final Property<String> REDSTONE_UP_WEST = StateDefinitions.stringProp("up_west", "false",
                        "true");

        // Repeater / comparator facing (output direction)
        public static final Property<String> DIODE_FACING = StateDefinitions.stringProp("facing", "north", "east",
                        "south", "west");
        public static final Property<String> DELAY = StateDefinitions.stringProp("delay", "1", "2", "3", "4");
        public static final Property<String> COMPARATOR_MODE = StateDefinitions.stringProp("mode", "compare",
                        "subtract");

        // Piston facing (down/up/north/south/west/east - EnumFacing order) plus the extended/sticky flags
        // BlockPistonBase meta[0:2]=facing, bit3=extended; BlockPistonExtension has the same layout but
        // bit3=sticky
        public static final Property<String> PISTON_FACING = StateDefinitions.stringProp("facing", "down", "up",
                        "north", "south", "west", "east");
        public static final Property<String> EXTENDED = StateDefinitions.stringProp("extended", "false", "true");
        public static final Property<String> STICKY = StateDefinitions.stringProp("sticky", "false", "true");

        // ==================== Group four: ladder / lever / fence gate, etc. ====================

        // Ladder facing (north/south/west/east)
        public static final Property<String> LADDER_FACING = StateDefinitions.stringProp("facing", "north", "south",
                        "west", "east");

        // Lever facing (east/west/south/north/up/down)
        public static final Property<String> LEVER_FACING = StateDefinitions.stringProp("facing", "east", "west",
                        "south", "north", "up", "down");

        // Fence gate facing (south/west/north/east - BlockDirectional order)
        public static final Property<String> FENCE_GATE_FACING = StateDefinitions.stringProp("facing", "south", "west",
                        "north", "east");

        // Pumpkin facing (south/east/north/west - special order)
        public static final Property<String> PUMPKIN_FACING = StateDefinitions.stringProp("facing", "south", "east",
                        "north", "west");

        // Dispenser/dropper facing (down/up/north/south/west/east - EnumFacing order)
        public static final Property<String> DISPENSER_FACING = StateDefinitions.stringProp("facing", "down", "up",
                        "north", "south", "west", "east");

        // Button facing (east/west/south/north - wall-mounted only)
        public static final Property<String> BUTTON_FACING = StateDefinitions.stringProp("facing", "east", "west",
                        "south", "north");

        // Furnace facing (north/south/west/east)
        public static final Property<String> FURNACE_FACING = StateDefinitions.stringProp("facing", "north", "south",
                        "west", "east");

        // ==================== Property-level metadata byte mapping (per-property decode) ====================
        //
        // These replace the former whole-definition positional MetaCodec lambdas for the linear /
        // bit-field / clamp cases: each property owns the metadata bit field it decodes, so
        // BlockStateDefinition resolves meta by calling each static property's decode() directly
        // (see BlockStateDefinition per-property decoding). The remaining non-linear switch blocks
        // (torch/lever/ladder/hopper/dispenser/button/furnace/quartz and the constant-value ones)
        // still use a positional MetaCodec, which takes precedence when present.

        /** Convenience: build a String-valued linear/clamp meta property (mask>>shift indexes the value list). */
        private static MappedMetaProperty<String> smeta(String name, int mask, int shift, boolean wrap, String... values) {
                return MappedMetaProperty.of(name, Arrays.asList(values), mask, shift, wrap);
        }

        // Axis: raw = (meta & 0xC) >> 2, wraps into {y, x, z} (vanilla (meta>>2)%3).
        public static final EnumMetaProperty<Direction.Axis> AXIS_META = EnumMetaProperty.create("axis",
                        Direction.Axis.class,
                        Arrays.asList(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z), 0xC, 2, true);

        // Log / log2 wood type fields.
        public static final MappedMetaProperty<String> LOG_WOOD_META = smeta("wood", 3, 0, false,
                        "oak", "spruce", "birch", "jungle");
        public static final MappedMetaProperty<String> LOG2_WOOD_META = smeta("wood", 1, 0, false,
                        "acacia", "dark_oak");

        // Slab/stairs/plant half bit fields: half lives on bit 3 for most blocks, bit 2 for stairs.
        public static final MappedMetaProperty<String> HALF_BOTTOM_TOP_B3 = smeta("half", 8, 3, false, "bottom", "top");
        public static final MappedMetaProperty<String> HALF_BOTTOM_TOP_B2 = smeta("half", 4, 2, false, "bottom", "top");
        public static final MappedMetaProperty<String> HALF_LOWER_UPPER_B3 = smeta("half", 8, 3, false, "lower", "upper");

        // Stairs / trapdoor facing (linear meta & 3, block-specific value order).
        public static final MappedMetaProperty<String> STAIR_FACING_META = smeta("facing", 3, 0, false,
                        "east", "west", "south", "north");
        public static final MappedMetaProperty<String> TRAPDOOR_FACING_META = smeta("facing", 3, 0, false,
                        "north", "south", "east", "west");

        // Single-variant clamp fields (mask 0x7 or full nibble, clamped to the last value).
        public static final MappedMetaProperty<String> SAPLING_VARIANT_META = smeta("variant", 7, 0, false,
                        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak");
        public static final MappedMetaProperty<String> EGG_VARIANT_META = smeta("variant", 7, 0, false,
                        "stone", "cobblestone", "stone_brick", "mossy_stone_brick", "cracked_stone_brick",
                        "chiseled_stone_brick");
        public static final MappedMetaProperty<String> PLANK_WOOD_META = smeta("wood", 0xF, 0, false,
                        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak");
        public static final MappedMetaProperty<String> STONEBRICK_VARIANT_META = smeta("variant", 0xF, 0, false,
                        "stonebrick", "mossy_stonebrick", "cracked_stonebrick", "chiseled_stonebrick");
        public static final MappedMetaProperty<String> SANDSTONE_VARIANT_META = smeta("variant", 0xF, 0, false,
                        "sandstone", "chiseled_sandstone", "smooth_sandstone");
        public static final MappedMetaProperty<String> RED_FLOWER_VARIANT_META = smeta("variant", 0xF, 0, false,
                        "poppy", "blue_orchid", "allium", "houstonia", "tulip_red", "tulip_orange", "tulip_white",
                        "tulip_pink", "oxeye_daisy");

        // Slab / double-slab variant fields.
        public static final MappedMetaProperty<String> SLAB_VARIANT_META = smeta("variant", 7, 0, false,
                        "stone", "sandstone", "wood", "cobblestone", "brick", "stone_brick", "nether_brick", "quartz");
        public static final MappedMetaProperty<String> WOOD_SLAB_VARIANT_META = smeta("variant", 7, 0, false,
                        "oak", "spruce", "birch", "jungle", "acacia", "big_oak");
        public static final MappedMetaProperty<String> DOUBLE_PLANT_VARIANT_META = smeta("variant", 7, 0, false,
                        "sunflower", "lilac", "double_grass", "double_fern", "rose_bush", "peony");

        // Anvil facing + damage.
        public static final MappedMetaProperty<String> ANVIL_FACING_META = smeta("facing", 3, 0, false,
                        "north", "east", "south", "west");
        public static final MappedMetaProperty<String> ANVIL_DAMAGE_META = smeta("damage", 0xC, 2, false, "0", "1", "2");

        // Cauldron level / redstone power / repeater delay + facing.
        public static final MappedMetaProperty<String> LEVEL_META = smeta("level", 3, 0, false, "0", "1", "2", "3");
        public static final MappedMetaProperty<String> POWER_META = smeta("power", 0xF, 0, false,
                        "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15");
        public static final MappedMetaProperty<String> DIODE_FACING_META = smeta("facing", 3, 0, false,
                        "north", "east", "south", "west");
        public static final MappedMetaProperty<String> DELAY_META = smeta("delay", 0xC, 2, false, "1", "2", "3", "4");

        // Piston facing (meta & 7 clamped to the six EnumFacing values) + extended / sticky flags (bit 3).
        public static final MappedMetaProperty<String> PISTON_FACING_META = smeta("facing", 7, 0, false,
                        "down", "up", "north", "south", "west", "east");
        public static final BooleanFlagProperty EXTENDED_FLAG = BooleanFlagProperty.flag("extended", 8);
        public static final BooleanFlagProperty STICKY_FLAG = BooleanFlagProperty.flag("sticky", 8);

        // Trapdoor open flag (bit 2).
        public static final BooleanFlagProperty OPEN_B2 = BooleanFlagProperty.flag("open", 4);

        // Lever / button / hopper powered flag (bit 3).
        public static final BooleanFlagProperty POWERED_B3 = BooleanFlagProperty.flag("powered", 8);

        // Non-linear facing tables (raw = meta & 7): EnumMetaProperty table mode replaces the former
        // switch lambdas. Each entry maps a raw meta field value to a Direction / TorchFacing whose
        // serialized name matches the blockstate variant-key value; the fallback covers out-of-range raws.
        public static final EnumMetaProperty<Direction> SWITCH_NSWE = EnumMetaProperty.withTable("facing", 7, 0,
                        Direction.NORTH,
                        Direction.NORTH, Direction.NORTH, Direction.NORTH, Direction.SOUTH, Direction.WEST,
                        Direction.EAST, Direction.NORTH, Direction.NORTH);
        public static final EnumMetaProperty<Direction> LEVER_FACING_TABLE = EnumMetaProperty.withTable("facing", 7, 0,
                        Direction.DOWN,
                        Direction.DOWN, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH,
                        Direction.UP, Direction.UP, Direction.DOWN);
        public static final EnumMetaProperty<Direction> DISPENSER_FACING_TABLE = EnumMetaProperty.withTable("facing", 7, 0,
                        Direction.NORTH,
                        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST,
                        Direction.EAST, Direction.NORTH, Direction.NORTH);
        public static final EnumMetaProperty<Direction> BUTTON_FACING_TABLE = EnumMetaProperty.withTable("facing", 7, 0,
                        Direction.EAST,
                        Direction.EAST, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH,
                        Direction.EAST, Direction.EAST, Direction.EAST);
        public static final EnumMetaProperty<Direction> HOPPER_FACING_TABLE = EnumMetaProperty.withTable("facing", 7, 0,
                        Direction.DOWN,
                        Direction.DOWN, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST,
                        Direction.EAST, Direction.DOWN, Direction.DOWN);
        public static final EnumMetaProperty<TorchFacing> TORCH_FACING_TABLE = EnumMetaProperty.withTable("facing", 7, 0,
                        TorchFacing.STANDING,
                        TorchFacing.STANDING, TorchFacing.EAST, TorchFacing.WEST, TorchFacing.SOUTH, TorchFacing.NORTH,
                        TorchFacing.STANDING, TorchFacing.STANDING, TorchFacing.STANDING);

        // Non-linear single-variant String tables (raw = full meta & 0xF). These replace the former
        // positional MetaCodec for sand/dirt/tallgrass/quartz, where the meta field is not a contiguous
        // ordinal range (e.g. dirt only becomes podzol at raw 2, tallgrass becomes grass only at raw 1).
        public static final MappedMetaProperty<String> SAND_VARIANT_META = smeta("variant", 0xF, 0, false,
                        "sand", "red_sand");
        public static final MappedMetaProperty<String> WALL_VARIANT_META = smeta("variant", 0xF, 0, false,
                        "cobblestone", "mossy_cobblestone");
        // yellow_flower: dandelion only (constant). Mask 0 -> raw always 0 -> the single value.
        public static final MappedMetaProperty<String> YELLOW_FLOWER_VARIANT_META = smeta("variant", 0, 0, false,
                        "dandelion");
        public static final TableMetaProperty<String> DIRT_VARIANT_META = TableMetaProperty.of("variant",
                        Arrays.asList("dirt", "podzol"), 0xF, 0, "dirt",
                        null, null, "podzol");
        public static final TableMetaProperty<String> TALLGRASS_VARIANT_META = TableMetaProperty.of("variant",
                        Arrays.asList("grass", "fern"), 0xF, 0, "fern",
                        null, "grass");
        // quartz_block: type and axis are each a pure function of the full meta (0-4), so the former
        // joint switch decomposes into two independent raw tables.
        public static final TableMetaProperty<String> QUARTZ_TYPE_META = TableMetaProperty.of("type",
                        Arrays.asList("quartz_block", "chiseled_quartz_block", "quartz_pillar"), 0xF, 0, "quartz_block",
                        "quartz_block", "chiseled_quartz_block", "quartz_pillar", "quartz_pillar", "quartz_pillar");
        public static final TableMetaProperty<String> QUARTZ_AXIS_META = TableMetaProperty.of("axis",
                        Arrays.asList("y", "x", "z"), 0xF, 0, "y",
                        null, null, null, "x", "z");

        // Comparator mode / powered bit fields. The lit comparator always reports powered=true, so its
        // powered property is a constant one-value field (mask 0 -> raw 0 regardless of meta).
        public static final MappedMetaProperty<String> COMPARATOR_MODE_META = smeta("mode", 4, 2, false,
                        "compare", "subtract");
        public static final MappedMetaProperty<String> COMPARATOR_POWERED_LIT = smeta("powered", 0, 0, false, "true");
        public static final MappedMetaProperty<String> COMPARATOR_POWERED_B3 = smeta("powered", 8, 3, false,
                        "false", "true");

        // ==================== Registration entry points ====================

        /**
         * Registers the typed state definitions of all vanilla blocks. Called during preInit (earlier than the
         * texture-stitching-time {@code ModelManagerDataLoader.init()} triggered by {@code TexturesStitch}).
         */
        @SideOnly(Side.CLIENT)
        public static void registerVanillaStateDefinitions() {
                registerVanillaBaseClasses();
                registerColorBlocks();
                registerLogsAndLeaves();
                registerSingleVariantBlocks();
                registerSlabAndAnvil();
                registerCrops();
                registerHopper();
                registerQuartzBlock();
                registerStairs();
                registerWall();
                registerPanes();
                registerGrass();
                registerFlowersAndGrass();
                registerDoubleSlabs();
                registerWoodenSlab();
                registerTorches();
                registerTrapdoor();
                registerDoors();
                registerCauldron();
                registerRedstoneWire();
                registerRepeaters();
                registerComparators();
                registerLadder();
                registerLever();
                registerFenceGate();
                registerPumpkins();
                registerDispenserAndDropper();
                registerButtons();
                registerFurnaces();
                registerPistons();
        }

        // ==================== Vanilla base-class state definition fragments (automatic BlockState
        // inheritance) ====================

        /**
         * Registers state definition fragments of vanilla block base classes so that custom subclasses
         * automatically inherit properties and meta decoding along the inheritance chain.
         * <p>
         * Subclasses (such as a mod's {@code PillarBlock} extension) need not declare properties manually: calling
         * {@code Models.register(block).register()} or {@code ModernBlock.register(block)} during preInit
         * automatically merges the parent fragments (see {@code CatStateInheritance}).
         * <p>
         * Note: the codec must be safe for all 16 meta values (Builder.create() pre-fills
         * resolvedByMeta[0..15]), so indices are always clamped into the value domain.
         */
        private static void registerVanillaBaseClasses() {
                // BlockRotatedPillar (1.7.10's PillarBlock): axis is encoded in meta bit2-3
                // (0=y,1=x,2=z), with the low 2 bits reserved for subclasses (e.g. a log's wood type) -
                // aligned with the j1|b0 bit-field layout of onBlockPlaced. The AXIS_META property owns
                // that bit field, so no whole-definition codec is needed.
                BlockStateDefinition<Class<?>> pillarDef = new BlockStateDefinition.Builder<Class<?>>(
                                BlockRotatedPillar.class)
                                .add(AXIS_META)
                                .create();
                Models.registerBase(BlockRotatedPillar.class).states(pillarDef).register();

                // BlockStairs: facing[meta&3: 0=east,1=west,2=south,3=north] +
                // half[bit2: 0=bottom,1=top] + shape dynamic (corner computed at runtime by the STAIRS resolver).
                BlockStateDefinition<Class<?>> stairsDef = new BlockStateDefinition.Builder<Class<?>>(
                                BlockStairs.class)
                                .add(STAIR_FACING_META, HALF_BOTTOM_TOP_B2, STAIR_SHAPE)
                                .dynamic(STAIR_SHAPE)
                                .create();
                Models.registerBase(BlockStairs.class)
                                .states(stairsDef)
                                .dynamic(VanillaBlockResolvers.STAIRS)
                                .register();

                // BlockSlab: half is encoded in meta bit3 (0=bottom,1=top), with the low 3 bits reserved for subclass variants.
                BlockStateDefinition<Class<?>> slabDef = new BlockStateDefinition.Builder<Class<?>>(BlockSlab.class)
                                .add(HALF_BOTTOM_TOP_B3)
                                .create();
                Models.registerBase(BlockSlab.class).states(slabDef).register();
        }

        // ==================== Pure 16-color blocks (single COLOR, default cartesian decode of meta&15) ====================

        private static void registerColorBlocks() {
                colorBlock(Blocks.wool);
                colorBlock(Blocks.carpet);
                colorBlock(Blocks.stained_glass);
                colorBlock(Blocks.stained_hardened_clay);
        }

        private static void colorBlock(Block block) {
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<>(block)
                                .add(StateDefinitions.COLOR)
                                .create();
                Models.register(block).states(def).register();
        }

        // ==================== Logs (wood+axis) / leaves (wood) ====================

        private static void registerLogsAndLeaves() {
                // log: wood={oak,spruce,birch,jungle}[meta&3], axis={y,x,z}[(meta>>2)%3] - each property owns its bit field
                BlockStateDefinition<Block> logDef = new BlockStateDefinition.Builder<Block>(Blocks.log)
                                .add(LOG_WOOD_META, AXIS_META)
                                .create();
                Models.register(Blocks.log).states(logDef).register();

                // log2: wood={acacia,dark_oak}[meta&1], axis as above
                BlockStateDefinition<Block> log2Def = new BlockStateDefinition.Builder<Block>(Blocks.log2)
                                .add(LOG2_WOOD_META, AXIS_META)
                                .create();
                Models.register(Blocks.log2).states(log2Def).register();

                // leaves: wood[meta&3] (single property, default decode meta%4 == meta&3)
                BlockStateDefinition<Block> leavesDef = new BlockStateDefinition.Builder<Block>(Blocks.leaves)
                                .add(LOG_WOOD)
                                .create();
                Models.register(Blocks.leaves).states(leavesDef).register();

                // leaves2: wood[meta&1] (single property, default decode meta%2 == meta&1)
                BlockStateDefinition<Block> leaves2Def = new BlockStateDefinition.Builder<Block>(Blocks.leaves2)
                                .add(LOG2_WOOD)
                                .create();
                Models.register(Blocks.leaves2).states(leaves2Def).register();
        }

        // ==================== Single variant/wood (clamp / conditional decode) ====================

        private static void registerSingleVariantBlocks() {
                // clamp-style single variants: the property itself carries the mask/clamp byte mapping
                singleMeta(Blocks.sapling, SAPLING_VARIANT_META);
                singleMeta(Blocks.monster_egg, EGG_VARIANT_META);
                singleMeta(Blocks.planks, PLANK_WOOD_META);
                singleMeta(Blocks.stonebrick, STONEBRICK_VARIANT_META);
                singleMeta(Blocks.sandstone, SANDSTONE_VARIANT_META);

                // sand: meta==0 ? sand : red_sand (clamped: any non-zero meta resolves to red_sand)
                singleMeta(Blocks.sand, SAND_VARIANT_META);
                // dirt: podzol only at meta==2, everything else dirt (non-linear table)
                singleMeta(Blocks.dirt, DIRT_VARIANT_META);
        }

        /** Registers a block whose only property is a meta-backed property that decodes itself (no positional codec). */
        private static void singleMeta(Block block, Property<?> prop) {
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<>(block)
                                .add(prop)
                                .create();
                Models.register(block).states(def).register();
        }

        private static void singleVariant(Block block, Property<String> prop, BlockStateDefinition.MetaCodec codec) {
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<>(block)
                                .add(prop)
                                .metaCodec(codec)
                                .create();
                Models.register(block).states(def).register();
        }

        /**
         * Single-property clamp decoder: index = {@code min(mask>0 ? meta&mask : meta, count-1)}.
         */
        private static BlockStateDefinition.MetaCodec clampCodec(final Property<String> prop, final int mask) {
                final int max = prop.getValueCount() - 1;
                return meta -> {
                        int idx = mask > 0 ? (meta & mask) : meta;
                        if (idx > max)
                                idx = max;
                        if (idx < 0)
                                idx = 0;
                        return new Comparable<?>[] { prop.getValues().get(idx) };
                };
        }

        // ==================== Stone slab (half+variant) / anvil (facing+damage)
        // ====================

        private static void registerSlabAndAnvil() {
                // stone_slab: half=(meta&8)==0?bottom:top, variant=SLAB_TYPES[meta&7]
                BlockStateDefinition<Block> slabDef = new BlockStateDefinition.Builder<Block>(Blocks.stone_slab)
                                .add(HALF_BOTTOM_TOP_B3, SLAB_VARIANT_META)
                                .create();
                Models.register(Blocks.stone_slab).states(slabDef).register();

                // anvil: facing={north,east,south,west}[meta&3],
                // damage={0,1,2}[min((meta>>2)&3,2)]
                BlockStateDefinition<Block> anvilDef = new BlockStateDefinition.Builder<Block>(Blocks.anvil)
                                .add(ANVIL_FACING_META, ANVIL_DAMAGE_META)
                                .create();
                Models.register(Blocks.anvil).states(anvilDef).register();
        }

        // ==================== Crops (single age, meta&7; default decode meta%8 == meta&7)
        // ====================

        private static void registerCrops() {
                cropAge(Blocks.wheat);
                cropAge(Blocks.carrots);
                cropAge(Blocks.potatoes);
                cropAge(Blocks.melon_stem);
                cropAge(Blocks.pumpkin_stem);
                cropAge(Blocks.reeds);
        }

        private static void cropAge(Block block) {
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<>(block)
                                .add(AGE)
                                .create();
                Models.register(block).states(def).register();
        }

        // ==================== Hopper (facing+powered, custom codec) ====================

        private static void registerHopper() {
                // hopper: facing non-linear raw = meta & 7 (2=north,3=south,4=west,5=east, else down) + powered (bit 3)
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<Block>(Blocks.hopper)
                                .add(HOPPER_FACING_TABLE, POWERED_B3)
                                .create();
                Models.register(Blocks.hopper).states(def).register();
        }

        // ==================== Quartz block (type[+axis], custom codec) ====================

        private static void registerQuartzBlock() {
                // quartz_block: type + axis are both decoded from the full meta nibble; each is an
                // independent raw table, so the former joint switch is expressed per-property.
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<Block>(Blocks.quartz_block)
                                .add(QUARTZ_TYPE_META, QUARTZ_AXIS_META)
                                .create();
                Models.register(Blocks.quartz_block).states(def).register();
        }

        // ==================== Stairs (facing+half static, shape dynamic) ====================

        private static void registerStairs() {
                Block[] stairs = {
                                Blocks.oak_stairs, Blocks.stone_stairs, Blocks.brick_stairs, Blocks.stone_brick_stairs,
                                Blocks.nether_brick_stairs, Blocks.sandstone_stairs, Blocks.spruce_stairs,
                                Blocks.birch_stairs,
                                Blocks.jungle_stairs, Blocks.quartz_stairs, Blocks.acacia_stairs, Blocks.dark_oak_stairs
                };
                for (Block stair : stairs) {
                        // facing={east,west,south,north}[meta&3], half=(meta&4)==0?bottom:top,
                        // shape=dynamic
                        BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<Block>(stair)
                                        .add(STAIR_FACING_META, HALF_BOTTOM_TOP_B2, STAIR_SHAPE)
                                        .dynamic(STAIR_SHAPE)
                                        .create();
                        Models.register(stair)
                                        .states(def)
                                        .dynamic(VanillaBlockResolvers.STAIRS)
                                        .register();
                }
        }

        // ==================== Cobblestone wall (variant static + connections fully dynamic) ====================

        private static void registerWall() {
                // cobblestone_wall: meta 0/1 statically decodes the material variant; connection states are computed
                // by the PANE resolver.
                // 1.7.10 BlockWall is not a BlockPane, so it relies on the resolver's canConnectWallTo special case;
                // the blockstate is a 10-case multipart (2 materials × post + 4-way sides, with when matching variant+direction).
                BlockStateDefinition<Block> wallDef = new BlockStateDefinition.Builder<Block>(Blocks.cobblestone_wall)
                                .add(WALL_VARIANT_META, PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                Models.register(Blocks.cobblestone_wall)
                                .states(wallDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .register();
        }

        // ==================== Pane / fence (connections fully dynamic) ====================

        private static void registerPanes() {
                // glass_pane: north/east/south/west fully dynamic (meta ignored, computed at runtime by the PANE resolver)
                BlockStateDefinition<Block> paneDef = new BlockStateDefinition.Builder<Block>(Blocks.glass_pane)
                                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                Models.register(Blocks.glass_pane)
                                .states(paneDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .register();

                // stained_glass_pane: same connections as above + color handled via per-color blockstate redirect
                BlockStateDefinition<Block> stainedPaneDef = new BlockStateDefinition.Builder<Block>(
                                Blocks.stained_glass_pane)
                                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                Models.register(Blocks.stained_glass_pane)
                                .states(stainedPaneDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .redirect(meta -> StateDefinitions.COLORS[meta & 15] + "_stained_glass_pane",
                                                "minecraft")
                                .register();

                // fence: same connections as above (1.7.10 BlockFence is not a BlockPane, so it relies on the resolver's canConnectFenceTo special case)
                BlockStateDefinition<Block> fenceDef = new BlockStateDefinition.Builder<Block>(Blocks.fence)
                                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                Models.register(Blocks.fence)
                                .states(fenceDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .register();

                // iron_bars: a BlockPane instance (decided by canPaneConnectTo); the blockstate is a 10-case multipart
                // (post + 4-way sides + 4-way caps + fully connected post_ends)
                BlockStateDefinition<Block> barsDef = new BlockStateDefinition.Builder<Block>(Blocks.iron_bars)
                                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                Models.register(Blocks.iron_bars)
                                .states(barsDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .register();

                // nether_brick_fence: a BlockFence instance (decided by canConnectFenceTo); the blockstate is a 5-case
                // multipart
                BlockStateDefinition<Block> nbfDef = new BlockStateDefinition.Builder<Block>(Blocks.nether_brick_fence)
                                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                Models.register(Blocks.nether_brick_fence)
                                .states(nbfDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .register();
        }

        // ==================== Grass block (snowy dynamic) ====================

        private static void registerGrass() {
                // grass: no meta (always 0 in vanilla); snowy is a dynamic property - when the block above uses a
                // snow / snow-block material it switches to the grass_block_snow model (matching 1.7.10 BlockGrass.getIcon's side-texture switch)
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<Block>(Blocks.grass)
                                .add(SNOWY)
                                .dynamic(SNOWY)
                                .create();
                Models.register(Blocks.grass)
                                .states(def)
                                .dynamic(VanillaBlockResolvers.SNOWY)
                                .register();
        }

        // ==================== Flowers / tall grass / double plants ====================

        private static void registerFlowersAndGrass() {
                // red_flower: meta%9 (9 flower kinds) - clamped single property, decode lives in the property
                singleMeta(Blocks.red_flower, RED_FLOWER_VARIANT_META);
                // yellow_flower: dandelion only (constant single-value property)
                singleMeta(Blocks.yellow_flower, YELLOW_FLOWER_VARIANT_META);
                // tallgrass: grass only at meta==1, everything else fern (non-linear table)
                singleMeta(Blocks.tallgrass, TALLGRASS_VARIANT_META);

                // double_plant: variant[min(meta&7, 5)] + half[(meta&8)==0?lower:upper]
                // Clamp to 5: only 6 variants exist but meta&7 ranges 0~7 (vanilla BlockDoublePlant clamps too)
                // The low 3 bits of the upper half are a facing residue written by onBlockPlacedBy rather than the variant;
                // the real variant is read from the block below by the DOUBLE_PLANT dynamic resolver (matching vanilla func_149885_e)
                BlockStateDefinition<Block> doublePlantDef = new BlockStateDefinition.Builder<Block>(Blocks.double_plant)
                                .add(DOUBLE_PLANT_VARIANT_META, HALF_LOWER_UPPER_B3)
                                .create();
                Models.register(Blocks.double_plant).states(doublePlantDef)
                                .dynamic(VanillaBlockResolvers.DOUBLE_PLANT).register();
        }

        // ==================== Double slabs (double_stone_slab / double_wooden_slab)
        // ====================

        private static void registerDoubleSlabs() {
                // double_stone_slab: variant[meta&7]
                singleMeta(Blocks.double_stone_slab, SLAB_VARIANT_META);

                // double_wooden_slab: variant[meta&7 clamped to 5] (BlockWoodSlab supports 6 kinds)
                singleMeta(Blocks.double_wooden_slab, WOOD_SLAB_VARIANT_META);
        }

        // ==================== Wooden slab (wooden_slab, half+variant) ====================

        private static void registerWoodenSlab() {
                // wooden_slab: variant[meta&7→clamp 5] + half[(meta&8)==0?bottom:top]
                // BlockWoodSlab supports 6 plank kinds (oak/spruce/birch/jungle/acacia/big_oak)
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<Block>(Blocks.wooden_slab)
                                .add(WOOD_SLAB_VARIANT_META, HALF_BOTTOM_TOP_B3)
                                .create();
                Models.register(Blocks.wooden_slab).states(def).register();
        }

        // ==================== Torches (torch / redstone_torch / unlit_redstone_torch)
        // ====================

        private static void registerTorches() {
                // torch: facing non-linear raw = meta & 7 (1=east,2=west,3=south,4=north, else standing)
                singleMeta(Blocks.torch, TORCH_FACING_TABLE);
                singleMeta(Blocks.redstone_torch, TORCH_FACING_TABLE);
                singleMeta(Blocks.unlit_redstone_torch, TORCH_FACING_TABLE);
        }

        // ==================== Trapdoor (facing+half+open) ====================

        private static void registerTrapdoor() {
                // trapdoor: facing[meta&3: 0=north,1=south,2=east,3=west]
                // open[(meta&4)!=0], half[(meta&8)!=0?top:bottom]
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<Block>(Blocks.trapdoor)
                                .add(TRAPDOOR_FACING_META, HALF_BOTTOM_TOP_B3, OPEN_B2)
                                .create();
                Models.register(Blocks.trapdoor).states(def).register();
        }

        // ==================== Doors (facing+hinge+open dynamic, half static) ====================

        private static void registerDoors() {
                // door: half[(meta&8)!=0?upper:lower] is statically decoded from its own meta;
                // facing/open live in the lower half's meta and hinge in the upper half's,
                // requiring a cross-block read of the other half → delegated to the runtime VanillaBlockResolvers.DOOR
                Block[] doors = { Blocks.wooden_door, Blocks.iron_door };
                for (Block door : doors) {
                        BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<Block>(door)
                                        .add(DOOR_FACING, HALF_LOWER_UPPER_B3, DOOR_HINGE, OPEN)
                                        .dynamic(DOOR_FACING, DOOR_HINGE, OPEN)
                                        .create();
                        Models.register(door)
                                        .states(def)
                                        .dynamic(VanillaBlockResolvers.DOOR)
                                        .register();
                }
        }

        // ==================== Cauldron (level) ====================

        private static void registerCauldron() {
                // cauldron: level[meta&3] 0-3
                singleMeta(Blocks.cauldron, LEVEL_META);
        }

        // ==================== Redstone wire (power + connection directions) ====================

        private static void registerRedstoneWire() {
                // redstone_wire: power[meta] 0-15 static; north/east/south/west connections + up_* climbing are
                // fully dynamic, computed at runtime by the REDSTONE_WIRE resolver following 1.7.10
                // isPowerProviderOrWire and renderBlockRedstoneWire. metaCodec only decodes power.
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<Block>(Blocks.redstone_wire)
                                .add(POWER_META, PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST,
                                                REDSTONE_UP_NORTH, REDSTONE_UP_EAST, REDSTONE_UP_SOUTH,
                                                REDSTONE_UP_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST,
                                                REDSTONE_UP_NORTH, REDSTONE_UP_EAST, REDSTONE_UP_SOUTH,
                                                REDSTONE_UP_WEST)
                                .create();
                Models.register(Blocks.redstone_wire)
                                .states(def)
                                .dynamic(VanillaBlockResolvers.REDSTONE_WIRE)
                                .connectionMultipart()
                                .register();
        }

        // ==================== Repeaters (facing+delay) ====================

        private static void registerRepeaters() {
                // repeater: facing={north,east,south,west}+delay={1,2,3,4}
                // meta&3=facing, (meta>>2)&3=delay - each property owns its bit field
                BlockStateDefinition<Block> poweredDef = new BlockStateDefinition.Builder<Block>(Blocks.powered_repeater)
                                .add(DIODE_FACING_META, DELAY_META)
                                .create();
                Models.register(Blocks.powered_repeater).states(poweredDef).register();

                BlockStateDefinition<Block> unpoweredDef = new BlockStateDefinition.Builder<Block>(
                                Blocks.unpowered_repeater)
                                .add(DIODE_FACING_META, DELAY_META)
                                .create();
                Models.register(Blocks.unpowered_repeater).states(unpoweredDef).register();
        }

        // ==================== Comparators (facing+mode+powered) ====================

        private static void registerComparators() {
                // comparator: facing={north,east,south,west}+mode={compare,subtract}+powered
                // meta&3=facing, (meta&4)!=0→subtract, (meta&8)!=0→powered(lit)
                // The lit state lives in meta bit 8 while the block stays
                // unpowered_comparator; the powered_comparator instance always
                // renders lit (isRepeaterPowered), so its powered field is a constant "true".
                BlockStateDefinition<Block> poweredDef = new BlockStateDefinition.Builder<Block>(Blocks.powered_comparator)
                                .add(DIODE_FACING_META, COMPARATOR_MODE_META, COMPARATOR_POWERED_LIT)
                                .create();
                Models.register(Blocks.powered_comparator).states(poweredDef).register();

                BlockStateDefinition<Block> unpoweredDef = new BlockStateDefinition.Builder<Block>(
                                Blocks.unpowered_comparator)
                                .add(DIODE_FACING_META, COMPARATOR_MODE_META, COMPARATOR_POWERED_B3)
                                .create();
                Models.register(Blocks.unpowered_comparator).states(unpoweredDef).register();
        }

        // ==================== Pistons (facing+extended / piston head facing+sticky)
        // ====================

        private static void registerPistons() {
                // piston / sticky_piston (BlockPistonBase): facing[meta&7] + extended[bit3]
                // 1.7.10 meta layout: meta[0:2] =
                // EnumFacing (0=down,1=up,2=north,3=south,4=west,5=east),
                // bit3 = extended (the extended state; the body retracts 1/4 block and exposes the piston_inner face).
                // meta 6/7 are invalid facings (EnumFacing is only 0-5), so the facing field clamps to the
                // six values; extended/sticky occupy bit 3 (owning properties decode their own bits).
                BlockStateDefinition<Block> pistonDef = new BlockStateDefinition.Builder<Block>(Blocks.piston)
                                .add(PISTON_FACING_META, EXTENDED_FLAG)
                                .create();
                Models.register(Blocks.piston).states(pistonDef).register();

                BlockStateDefinition<Block> stickyDef = new BlockStateDefinition.Builder<Block>(Blocks.sticky_piston)
                                .add(PISTON_FACING_META, EXTENDED_FLAG)
                                .create();
                Models.register(Blocks.sticky_piston).states(stickyDef).register();

                // piston_head (BlockPistonExtension): facing[meta&7] + sticky[bit3]
                // the stationary piston head block (piston_extension / BlockPistonMoving during movement is rendered via TileEntity and is not registered)
                BlockStateDefinition<Block> headDef = new BlockStateDefinition.Builder<Block>(Blocks.piston_head)
                                .add(PISTON_FACING_META, STICKY_FLAG)
                                .create();
                Models.register(Blocks.piston_head).states(headDef).register();
        }

        // ==================== Ladder (single facing, custom decode) ====================

        private static void registerLadder() {
                // ladder: facing non-linear raw = meta & 7 (2=north,3=south,4=west,5=east, else north)
                singleMeta(Blocks.ladder, SWITCH_NSWE);
        }

        // ==================== Lever (facing+powered) ====================

        private static void registerLever() {
                // lever: facing non-linear raw = meta & 7 (1=east,2=west,3=south,4=north,5/6=up,0/7=down) + powered (bit 3)
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<Block>(Blocks.lever)
                                .add(LEVER_FACING_TABLE, POWERED_B3)
                                .create();
                Models.register(Blocks.lever).states(def).register();
        }

        // ==================== Fence gate (facing+open) ====================

        private static void registerFenceGate() {
                // fence_gate: facing={south,west,north,east}[meta&3] + open[bit2] - both decode their own bit fields
                BlockStateDefinition<Block> def = new BlockStateDefinition.Builder<Block>(Blocks.fence_gate)
                                .add(smeta("facing", 3, 0, false, "south", "west", "north", "east"),
                                                BooleanFlagProperty.flag("open", 4))
                                .create();
                Models.register(Blocks.fence_gate).states(def).register();
        }

        // ==================== Pumpkins / jack o'lanterns (single facing, special decode) ====================

        private static void registerPumpkins() {
                // pumpkin/lit_pumpkin: facing={south,east,north,west}[meta&3] - linear field, decode lives in the property
                Property<String> pumpkinFacing = smeta("facing", 3, 0, false, "south", "east", "north", "west");
                singleMeta(Blocks.pumpkin, pumpkinFacing);
                singleMeta(Blocks.lit_pumpkin, pumpkinFacing);
        }

        // ==================== Dispensers / droppers (single facing, EnumFacing decode) ====================

        private static void registerDispenserAndDropper() {
                // dispenser/dropper: facing non-linear raw = meta & 7 (0=down,1=up,2=north,3=south,4=west,5=east, else north)
                singleMeta(Blocks.dispenser, DISPENSER_FACING_TABLE);
                singleMeta(Blocks.dropper, DISPENSER_FACING_TABLE);
        }

        // ==================== Buttons (facing+powered, wall-mounted only) ====================

        private static void registerButtons() {
                // stone_button/wooden_button: facing non-linear raw = meta & 7 (1=east,2=west,3=south,4=north, else east) + powered (bit 3)
                BlockStateDefinition<Block> stoneDef = new BlockStateDefinition.Builder<Block>(Blocks.stone_button)
                                .add(BUTTON_FACING_TABLE, POWERED_B3)
                                .create();
                Models.register(Blocks.stone_button).states(stoneDef).register();

                BlockStateDefinition<Block> woodDef = new BlockStateDefinition.Builder<Block>(Blocks.wooden_button)
                                .add(BUTTON_FACING_TABLE, POWERED_B3)
                                .create();
                Models.register(Blocks.wooden_button).states(woodDef).register();
        }

        // ==================== Furnaces / lit furnaces (single facing, custom decode) ====================

        private static void registerFurnaces() {
                // furnace/lit_furnace: facing non-linear raw = meta & 7 (2=north,3=south,4=west,5=east, else north)
                singleMeta(Blocks.furnace, SWITCH_NSWE);
                singleMeta(Blocks.lit_furnace, SWITCH_NSWE);
        }
}
