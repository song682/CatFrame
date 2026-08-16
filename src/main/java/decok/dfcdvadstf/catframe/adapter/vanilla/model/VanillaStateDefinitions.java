package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.CatModels;
import decok.dfcdvadstf.catframe.model.state.CatStateDefinition;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import decok.dfcdvadstf.catframe.model.state.property.StateDefinitions;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * 原版方块的类型化状态定义（{@link CatStateDefinition}）登记入口。
 * <p>
 * 取代旧的 {@code VanillaMetadataMapper}（{@code IMetadataMapper} lambda）与冗余的
 * {@code metadata_map.json}：把「metadata → 属性值 → variant key」的解码层统一收口到
 * {@link Property} 值域 + {@link CatStateDefinition.MetaCodec}，对齐 wiki / 高版本
 * {@code BlockStateModelDispatcher} 的「属性 + 值域驱动调度」模型。
 *
 * <h3>行为保持</h3>
 * 每个 def 的 {@code getStateFromMeta(meta)} 复现旧 lambda 输出的相同 {@code {属性名:值}}
 * 映射；variant 匹配统一走 {@code RenderDispatcher.buildVariantKey}（字母序 +
 * {@code toString()}），
 * 故最终 variant key 与旧路径逐字节相同。所有属性一律用 {@link StateDefinitions#stringProp}
 * （值即字符串，{@code toString()} 与 blockstate JSON 的 variant key 值精确对齐）。
 *
 * <h3>动态属性</h3>
 * 楼梯 {@code shape}、玻璃板 {@code north/east/south/west} 标记为 {@code .dynamic(...)}
 * （不参与 meta 解码），运行时由 {@link VanillaBlockResolvers} 从世界计算并覆盖。
 */
@SideOnly(Side.CLIENT)
public final class VanillaStateDefinitions {

        private VanillaStateDefinitions() {
        }

        // ==================== 共享 / 每块属性常量 ====================

        // 原木 / 树叶
        private static final Property<String> LOG_WOOD = StateDefinitions.stringProp("wood", "oak", "spruce", "birch",
                        "jungle");
        private static final Property<String> LOG2_WOOD = StateDefinitions.stringProp("wood", "acacia", "dark_oak");

        // 单 variant / wood（clamp 解码）
        private static final Property<String> SAPLING_VARIANT = StateDefinitions.stringProp("variant", "oak", "spruce",
                        "birch", "jungle", "acacia", "dark_oak");
        private static final Property<String> EGG_VARIANT = StateDefinitions.stringProp("variant", "stone",
                        "cobblestone", "stone_brick",
                        "mossy_stone_brick", "cracked_stone_brick", "chiseled_stone_brick");
        private static final Property<String> PLANK_WOOD = StateDefinitions.stringProp("wood", "oak", "spruce", "birch",
                        "jungle", "acacia", "dark_oak");
        private static final Property<String> STONEBRICK_VARIANT = StateDefinitions.stringProp("variant", "stonebrick",
                        "mossy_stonebrick",
                        "cracked_stonebrick", "chiseled_stonebrick");
        private static final Property<String> SANDSTONE_VARIANT = StateDefinitions.stringProp("variant", "sandstone",
                        "chiseled_sandstone", "smooth_sandstone");
        private static final Property<String> SAND_VARIANT = StateDefinitions.stringProp("variant", "sand", "red_sand");
        private static final Property<String> DIRT_VARIANT = StateDefinitions.stringProp("variant", "dirt", "podzol");
        private static final Property<String> WALL_VARIANT = StateDefinitions.stringProp("variant", "cobblestone",
                        "mossy_cobblestone");

        // 花
        private static final Property<String> RED_FLOWER_VARIANT = StateDefinitions.stringProp("variant", "poppy",
                        "blue_orchid", "allium", "houstonia",
                        "tulip_red", "tulip_orange", "tulip_white", "tulip_pink", "oxeye_daisy");
        private static final Property<String> YELLOW_FLOWER_VARIANT = StateDefinitions.stringProp("variant",
                        "dandelion");

        // 高草丛（单格）
        private static final Property<String> TALLGRASS_VARIANT = StateDefinitions.stringProp("variant", "grass",
                        "fern");

        // 双草丛（2 格高）
        private static final Property<String> DOUBLE_PLANT_VARIANT = StateDefinitions.stringProp("variant", "sunflower",
                        "lilac",
                        "double_grass", "double_fern", "rose_bush", "peony");
        // Vanilla double_plant half uses lower/upper — NOT the slab's bottom/top
        // vocabulary
        // 原版 double_plant 的 half 是 lower/upper —— 不是台阶的 bottom/top 词汇，不可复用 SLAB_HALF
        private static final Property<String> PLANT_HALF = StateDefinitions.stringProp("half", "lower", "upper");

        // 石台阶
        private static final Property<String> SLAB_VARIANT = StateDefinitions.stringProp("variant", "stone",
                        "sandstone", "wood", "cobblestone",
                        "brick", "stone_brick", "nether_brick", "quartz");

        // 铁砧
        private static final Property<String> ANVIL_FACING = StateDefinitions.stringProp("facing", "north", "east",
                        "south", "west");
        private static final Property<String> ANVIL_DAMAGE = StateDefinitions.stringProp("damage", "0", "1", "2");

        // 作物 age
        private static final Property<String> AGE = StateDefinitions.stringProp("age", "0", "1", "2", "3", "4", "5",
                        "6", "7");

        // 漏斗
        private static final Property<String> HOPPER_FACING = StateDefinitions.stringProp("facing", "down", "north",
                        "south", "west", "east");
        private static final Property<String> POWERED = StateDefinitions.stringProp("powered", "false", "true");

        // 石英块
        private static final Property<String> QUARTZ_TYPE = StateDefinitions.stringProp("type", "quartz_block",
                        "chiseled_quartz_block", "quartz_pillar");

        // 楼梯
        private static final Property<String> STAIR_FACING = StateDefinitions.stringProp("facing", "east", "west",
                        "south", "north");
        private static final Property<String> STAIR_SHAPE = StateDefinitions.stringProp("shape", "straight",
                        "inner_left", "inner_right", "outer_left", "outer_right");

        // 玻璃板连接
        private static final Property<String> PANE_NORTH = StateDefinitions.stringProp("north", "false", "true");
        private static final Property<String> PANE_EAST = StateDefinitions.stringProp("east", "false", "true");
        private static final Property<String> PANE_SOUTH = StateDefinitions.stringProp("south", "false", "true");
        private static final Property<String> PANE_WEST = StateDefinitions.stringProp("west", "false", "true");

        // 木台阶变种（double_wooden_slab / wooden_slab：BlockWoodSlab.field_150005_b 共 6 种）
        private static final Property<String> WOOD_SLAB_VARIANT = StateDefinitions.stringProp("variant", "oak",
                        "spruce", "birch", "jungle", "acacia", "big_oak");

        // 火把 facing（east/west/south/north 壁挂, standing 地面）
        private static final Property<String> TORCH_FACING = StateDefinitions.stringProp("facing", "east", "west",
                        "south", "north", "standing");

        // 活板门
        private static final Property<String> TRAPDOOR_FACING = StateDefinitions.stringProp("facing", "north", "south",
                        "east", "west");
        private static final Property<String> OPEN = StateDefinitions.stringProp("open", "false", "true");

        // 门（铁门+木门）：facing 取值对齐 DOOR 解析器的 1.8 映射表（east/south/west/north）
        private static final Property<String> DOOR_FACING = StateDefinitions.stringProp("facing", "east", "south",
                        "west", "north");
        private static final Property<String> DOOR_HALF = StateDefinitions.stringProp("half", "lower", "upper");
        private static final Property<String> DOOR_HINGE = StateDefinitions.stringProp("hinge", "left", "right");

        // 炼药锅水位
        private static final Property<String> LEVEL = StateDefinitions.stringProp("level", "0", "1", "2", "3");

        // 红石线信号强度
        private static final Property<String> POWER = StateDefinitions.stringProp("power", "0", "1", "2", "3", "4", "5",
                        "6", "7",
                        "8", "9", "10", "11", "12", "13", "14", "15");

        // 红石线爬线（1.7.10 renderBlockRedstoneWire 四方向独立上坡面）
        private static final Property<String> REDSTONE_UP_NORTH = StateDefinitions.stringProp("up_north", "false",
                        "true");
        private static final Property<String> REDSTONE_UP_EAST = StateDefinitions.stringProp("up_east", "false",
                        "true");
        private static final Property<String> REDSTONE_UP_SOUTH = StateDefinitions.stringProp("up_south", "false",
                        "true");
        private static final Property<String> REDSTONE_UP_WEST = StateDefinitions.stringProp("up_west", "false",
                        "true");

        // 中继器 / 比较器的 facing（输出方向）
        private static final Property<String> DIODE_FACING = StateDefinitions.stringProp("facing", "north", "east",
                        "south", "west");
        private static final Property<String> DELAY = StateDefinitions.stringProp("delay", "1", "2", "3", "4");
        private static final Property<String> COMPARATOR_MODE = StateDefinitions.stringProp("mode", "compare",
                        "subtract");

        // 活塞 facing（down/up/north/south/west/east——EnumFacing 顺序）与伸出/粘性标志
        // BlockPistonBase meta[0:2]=facing, bit3=extended；BlockPistonExtension 同布局但
        // bit3=sticky
        private static final Property<String> PISTON_FACING = StateDefinitions.stringProp("facing", "down", "up",
                        "north", "south", "west", "east");
        private static final Property<String> EXTENDED = StateDefinitions.stringProp("extended", "false", "true");
        private static final Property<String> STICKY = StateDefinitions.stringProp("sticky", "false", "true");

        // ==================== 第四组：梯子/拉杆/栅栏门等 ====================

        // 梯子 facing（north/south/west/east）
        private static final Property<String> LADDER_FACING = StateDefinitions.stringProp("facing", "north", "south",
                        "west", "east");

        // 拉杆 facing（east/west/south/north/up/down）
        private static final Property<String> LEVER_FACING = StateDefinitions.stringProp("facing", "east", "west",
                        "south", "north", "up", "down");

        // 栅栏门 facing（south/west/north/east——BlockDirectional 顺序）
        private static final Property<String> FENCE_GATE_FACING = StateDefinitions.stringProp("facing", "south", "west",
                        "north", "east");

        // 南瓜 facing（south/east/north/west——特殊顺序）
        private static final Property<String> PUMPKIN_FACING = StateDefinitions.stringProp("facing", "south", "east",
                        "north", "west");

        // 发射器/投掷器 facing（down/up/north/south/west/east——EnumFacing 顺序）
        private static final Property<String> DISPENSER_FACING = StateDefinitions.stringProp("facing", "down", "up",
                        "north", "south", "west", "east");

        // 按钮 facing（east/west/south/north——仅壁挂）
        private static final Property<String> BUTTON_FACING = StateDefinitions.stringProp("facing", "east", "west",
                        "south", "north");

        // 熔炉 facing（north/south/west/east）
        private static final Property<String> FURNACE_FACING = StateDefinitions.stringProp("facing", "north", "south",
                        "west", "east");

        // ==================== 登记入口 ====================

        /**
         * 登记全部原版方块的类型化状态定义。preInit 阶段调用（早于纹理缝合期
         * 由 {@code TexturesStitch} 触发的 {@code ModelManagerDataLoader.init()}）。
         */
        @SideOnly(Side.CLIENT)
        public static void registerVanillaStateDefinitions() {
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

        // ==================== 纯 16 色（单 COLOR，默认笛卡尔解码 meta&15） ====================

        private static void registerColorBlocks() {
                colorBlock(Blocks.wool);
                colorBlock(Blocks.carpet);
                colorBlock(Blocks.stained_glass);
                colorBlock(Blocks.stained_hardened_clay);
        }

        private static void colorBlock(Block block) {
                CatStateDefinition<Block> def = new CatStateDefinition.Builder<>(block)
                                .add(StateDefinitions.COLOR)
                                .create();
                CatModels.register(block).states(def).register();
        }

        // ==================== 原木（wood+axis）/ 树叶（wood） ====================

        private static void registerLogsAndLeaves() {
                // log: wood={oak,spruce,birch,jungle}[meta&3], axis={y,x,z}[(meta>>2)%3]
                CatStateDefinition<Block> logDef = new CatStateDefinition.Builder<Block>(Blocks.log)
                                .add(LOG_WOOD, StateDefinitions.AXIS)
                                .metaCodec(meta -> new Comparable<?>[] {
                                                LOG_WOOD.getValues().get(meta & 3),
                                                StateDefinitions.AXIS.getValues().get((meta >> 2) % 3) })
                                .create();
                CatModels.register(Blocks.log).states(logDef).register();

                // log2: wood={acacia,dark_oak}[meta&1], axis 同上
                CatStateDefinition<Block> log2Def = new CatStateDefinition.Builder<Block>(Blocks.log2)
                                .add(LOG2_WOOD, StateDefinitions.AXIS)
                                .metaCodec(meta -> new Comparable<?>[] {
                                                LOG2_WOOD.getValues().get(meta & 1),
                                                StateDefinitions.AXIS.getValues().get((meta >> 2) % 3) })
                                .create();
                CatModels.register(Blocks.log2).states(log2Def).register();

                // leaves: wood[meta&3]（单属性，默认解码 meta%4 == meta&3）
                CatStateDefinition<Block> leavesDef = new CatStateDefinition.Builder<Block>(Blocks.leaves)
                                .add(LOG_WOOD)
                                .create();
                CatModels.register(Blocks.leaves).states(leavesDef).register();

                // leaves2: wood[meta&1]（单属性，默认解码 meta%2 == meta&1）
                CatStateDefinition<Block> leaves2Def = new CatStateDefinition.Builder<Block>(Blocks.leaves2)
                                .add(LOG2_WOOD)
                                .create();
                CatModels.register(Blocks.leaves2).states(leaves2Def).register();
        }

        // ==================== 单 variant/wood（clamp / 条件解码） ====================

        private static void registerSingleVariantBlocks() {
                // sapling: min(meta&7, 5)
                singleVariant(Blocks.sapling, SAPLING_VARIANT, clampCodec(SAPLING_VARIANT, 7));
                // monster_egg: min(meta&7, 5)
                singleVariant(Blocks.monster_egg, EGG_VARIANT, clampCodec(EGG_VARIANT, 7));
                // planks: min(meta, 5)
                singleVariant(Blocks.planks, PLANK_WOOD, clampCodec(PLANK_WOOD, 0));
                // stonebrick: min(meta, 3)
                singleVariant(Blocks.stonebrick, STONEBRICK_VARIANT, clampCodec(STONEBRICK_VARIANT, 0));
                // sandstone: min(meta, 2)
                singleVariant(Blocks.sandstone, SANDSTONE_VARIANT, clampCodec(SANDSTONE_VARIANT, 0));

                // sand: meta==0 ? sand : red_sand
                singleVariant(Blocks.sand, SAND_VARIANT,
                                meta -> new Comparable<?>[] { SAND_VARIANT.getValues().get(meta == 0 ? 0 : 1) });
                // dirt: meta==2 ? podzol : dirt
                singleVariant(Blocks.dirt, DIRT_VARIANT,
                                meta -> new Comparable<?>[] { DIRT_VARIANT.getValues().get(meta == 2 ? 1 : 0) });
        }

        private static void singleVariant(Block block, Property<String> prop, CatStateDefinition.MetaCodec codec) {
                CatStateDefinition<Block> def = new CatStateDefinition.Builder<>(block)
                                .add(prop)
                                .metaCodec(codec)
                                .create();
                CatModels.register(block).states(def).register();
        }

        /**
         * 单属性 clamp 解码器：索引 = {@code min(mask>0 ? meta&mask : meta, count-1)}。
         */
        private static CatStateDefinition.MetaCodec clampCodec(final Property<String> prop, final int mask) {
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

        // ==================== 石台阶（half+variant）/ 铁砧（facing+damage）
        // ====================

        private static void registerSlabAndAnvil() {
                // stone_slab: half=(meta&8)==0?bottom:top, variant=SLAB_TYPES[meta&7]
                CatStateDefinition<Block> slabDef = new CatStateDefinition.Builder<Block>(Blocks.stone_slab)
                                .add(StateDefinitions.SLAB_HALF, SLAB_VARIANT)
                                .metaCodec(meta -> new Comparable<?>[] {
                                                StateDefinitions.SLAB_HALF.getValues().get((meta & 8) == 0 ? 0 : 1),
                                                SLAB_VARIANT.getValues().get(meta & 7) })
                                .create();
                CatModels.register(Blocks.stone_slab).states(slabDef).register();

                // anvil: facing={north,east,south,west}[meta&3],
                // damage={0,1,2}[min((meta>>2)&3,2)]
                CatStateDefinition<Block> anvilDef = new CatStateDefinition.Builder<Block>(Blocks.anvil)
                                .add(ANVIL_FACING, ANVIL_DAMAGE)
                                .metaCodec(meta -> new Comparable<?>[] {
                                                ANVIL_FACING.getValues().get(meta & 3),
                                                ANVIL_DAMAGE.getValues().get(Math.min((meta >> 2) & 3, 2)) })
                                .create();
                CatModels.register(Blocks.anvil).states(anvilDef).register();
        }

        // ==================== 作物（单 age，meta&7；默认解码 meta%8 == meta&7）
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
                CatStateDefinition<Block> def = new CatStateDefinition.Builder<>(block)
                                .add(AGE)
                                .create();
                CatModels.register(block).states(def).register();
        }

        // ==================== 漏斗（facing+powered，自定义 codec） ====================

        private static void registerHopper() {
                CatStateDefinition<Block> def = new CatStateDefinition.Builder<Block>(Blocks.hopper)
                                .add(HOPPER_FACING, POWERED)
                                .metaCodec(meta -> {
                                        String facing;
                                        switch (meta & 7) {
                                                case 2:
                                                        facing = "north";
                                                        break;
                                                case 3:
                                                        facing = "south";
                                                        break;
                                                case 4:
                                                        facing = "west";
                                                        break;
                                                case 5:
                                                        facing = "east";
                                                        break;
                                                case 0:
                                                default:
                                                        facing = "down";
                                                        break;
                                        }
                                        return new Comparable<?>[] { facing, (meta & 8) != 0 ? "true" : "false" };
                                })
                                .create();
                CatModels.register(Blocks.hopper).states(def).register();
        }

        // ==================== 石英块（type[+axis]，自定义 codec） ====================

        private static void registerQuartzBlock() {
                CatStateDefinition<Block> def = new CatStateDefinition.Builder<Block>(Blocks.quartz_block)
                                .add(QUARTZ_TYPE, StateDefinitions.AXIS)
                                .metaCodec(meta -> {
                                        switch (meta) {
                                                case 1:
                                                        return new Comparable<?>[] { "chiseled_quartz_block", "y" };
                                                case 2:
                                                        return new Comparable<?>[] { "quartz_pillar", "y" };
                                                case 3:
                                                        return new Comparable<?>[] { "quartz_pillar", "x" };
                                                case 4:
                                                        return new Comparable<?>[] { "quartz_pillar", "z" };
                                                case 0:
                                                default:
                                                        return new Comparable<?>[] { "quartz_block", "y" };
                                        }
                                })
                                .create();
                CatModels.register(Blocks.quartz_block).states(def).register();
        }

        // ==================== 楼梯（facing+half 静态，shape 动态） ====================

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
                        CatStateDefinition<Block> def = new CatStateDefinition.Builder<Block>(stair)
                                        .add(STAIR_FACING, StateDefinitions.SLAB_HALF, STAIR_SHAPE)
                                        .dynamic(STAIR_SHAPE)
                                        .metaCodec(meta -> new Comparable<?>[] {
                                                        STAIR_FACING.getValues().get(meta & 3),
                                                        StateDefinitions.SLAB_HALF.getValues()
                                                                        .get((meta & 4) == 0 ? 0 : 1) })
                                        .create();
                        CatModels.register(stair)
                                        .states(def)
                                        .dynamic(VanillaBlockResolvers.STAIRS)
                                        .register();
                }
        }

        // ==================== 石墙（variant 静态 + 连接全动态） ====================

        private static void registerWall() {
                // cobblestone_wall: meta 0/1 静态解码材质 variant，连接状态由 PANE resolver 计算。
                // 1.7.10 BlockWall 非 BlockPane，靠 resolver 的 canConnectWallTo 特判；
                // blockstate 为 10-case multipart（2 材质 × post + 4 向 side，when 匹配 variant+方向）。
                CatStateDefinition<Block> wallDef = new CatStateDefinition.Builder<Block>(Blocks.cobblestone_wall)
                                .add(WALL_VARIANT, PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .metaCodec(meta -> new Comparable<?>[] {
                                                WALL_VARIANT.getValues().get(meta == 0 ? 0 : 1) })
                                .create();
                CatModels.register(Blocks.cobblestone_wall)
                                .states(wallDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .register();
        }

        // ==================== 玻璃板 / 栅栏（连接全动态） ====================

        private static void registerPanes() {
                // glass_pane: north/east/south/west 全动态（meta 忽略，运行时由 PANE resolver 计算）
                CatStateDefinition<Block> paneDef = new CatStateDefinition.Builder<Block>(Blocks.glass_pane)
                                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                CatModels.register(Blocks.glass_pane)
                                .states(paneDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .register();

                // stained_glass_pane: 连接同上 + 颜色走 per-color blockstate redirect
                CatStateDefinition<Block> stainedPaneDef = new CatStateDefinition.Builder<Block>(
                                Blocks.stained_glass_pane)
                                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                CatModels.register(Blocks.stained_glass_pane)
                                .states(stainedPaneDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .redirect(meta -> StateDefinitions.COLORS[meta & 15] + "_stained_glass_pane",
                                                "minecraft")
                                .register();

                // fence: 连接同上（1.7.10 BlockFence 非 BlockPane，靠 resolver 的 canConnectFenceTo 特判）
                CatStateDefinition<Block> fenceDef = new CatStateDefinition.Builder<Block>(Blocks.fence)
                                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                CatModels.register(Blocks.fence)
                                .states(fenceDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .register();

                // iron_bars: BlockPane 实例（canPaneConnectTo 判定），blockstate 为 10-case multipart
                // （post + 4 向 side + 4 向 cap + 全连接 post_ends）
                CatStateDefinition<Block> barsDef = new CatStateDefinition.Builder<Block>(Blocks.iron_bars)
                                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                CatModels.register(Blocks.iron_bars)
                                .states(barsDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .register();

                // nether_brick_fence: BlockFence 实例（canConnectFenceTo 判定），blockstate 为 5-case
                // multipart
                CatStateDefinition<Block> nbfDef = new CatStateDefinition.Builder<Block>(Blocks.nether_brick_fence)
                                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                                .create();
                CatModels.register(Blocks.nether_brick_fence)
                                .states(nbfDef)
                                .dynamic(VanillaBlockResolvers.PANE)
                                .connectionMultipart()
                                .register();
        }

        // ==================== 花/高草丛/双草丛 ====================

        private static void registerFlowersAndGrass() {
                // red_flower: meta%9（9 种花）
                singleVariant(Blocks.red_flower, RED_FLOWER_VARIANT, clampCodec(RED_FLOWER_VARIANT, 0));
                // yellow_flower: 仅 dandelion（meta 恒 0）
                singleVariant(Blocks.yellow_flower, YELLOW_FLOWER_VARIANT, meta -> new Comparable<?>[] { "dandelion" });
                // tallgrass: meta==1→grass, meta==2→fern
                singleVariant(Blocks.tallgrass, TALLGRASS_VARIANT,
                                meta -> new Comparable<?>[] { TALLGRASS_VARIANT.getValues().get(meta == 1 ? 0 : 1) });

                // double_plant: variant[min(meta&7, 5)] + half[(meta&8)==0?lower:upper]
                // Clamp to 5: only 6 variants exist but meta&7 ranges 0~7 (vanilla
                // BlockDoublePlant clamps too)
                // 钳制到 5：变体只有 6 种，但 meta&7 范围是 0~7（原版 BlockDoublePlant 同样做了钳制）
                // 上半块的低 3 位是 onBlockPlacedBy 写入的朝向残值而非变体，真实变体由
                // DOUBLE_PLANT 动态解析器从下方方块读取（对齐原版 func_149885_e）
                CatStateDefinition<Block> doublePlantDef = new CatStateDefinition.Builder<Block>(Blocks.double_plant)
                                .add(DOUBLE_PLANT_VARIANT, PLANT_HALF)
                                .metaCodec(meta -> new Comparable<?>[] {
                                                DOUBLE_PLANT_VARIANT.getValues().get(Math.min(meta & 7, 5)),
                                                PLANT_HALF.getValues().get((meta & 8) == 0 ? 0 : 1) })
                                .create();
                CatModels.register(Blocks.double_plant).states(doublePlantDef)
                                .dynamic(VanillaBlockResolvers.DOUBLE_PLANT).register();
        }

        // ==================== 双台阶（double_stone_slab / double_wooden_slab）
        // ====================

        private static void registerDoubleSlabs() {
                // double_stone_slab: variant[meta&7]
                singleVariant(Blocks.double_stone_slab, SLAB_VARIANT,
                                meta -> new Comparable<?>[] { SLAB_VARIANT.getValues().get(meta & 7) });

                // double_wooden_slab: variant[meta&7 再 clamp 到 5]（BlockWoodSlab 支持 6 种）
                CatStateDefinition<Block> woodDef = new CatStateDefinition.Builder<Block>(Blocks.double_wooden_slab)
                                .add(WOOD_SLAB_VARIANT)
                                .metaCodec(meta -> new Comparable<?>[] {
                                                WOOD_SLAB_VARIANT.getValues().get(Math.min(meta & 7, 5)) })
                                .create();
                CatModels.register(Blocks.double_wooden_slab).states(woodDef).register();
        }

        // ==================== 木台阶（wooden_slab，half+variant） ====================

        private static void registerWoodenSlab() {
                // wooden_slab: variant[meta&7→clamp 5] + half[(meta&8)==0?bottom:top]
                // BlockWoodSlab 支持 6 种木板（oak/spruce/birch/jungle/acacia/big_oak）
                CatStateDefinition<Block> def = new CatStateDefinition.Builder<Block>(Blocks.wooden_slab)
                                .add(WOOD_SLAB_VARIANT, StateDefinitions.SLAB_HALF)
                                .metaCodec(meta -> new Comparable<?>[] {
                                                WOOD_SLAB_VARIANT.getValues().get(Math.min(meta & 7, 5)),
                                                StateDefinitions.SLAB_HALF.getValues().get((meta & 8) == 0 ? 0 : 1) })
                                .create();
                CatModels.register(Blocks.wooden_slab).states(def).register();
        }

        // ==================== 火把（torch / redstone_torch / unlit_redstone_torch）
        // ====================

        private static void registerTorches() {
                // torch: facing[meta&7] 1=east,2=west,3=south,4=north,5=standing
                CatStateDefinition.MetaCodec torchCodec = meta -> {
                        switch (meta & 7) {
                                case 1:
                                        return new Comparable<?>[] { "east" };
                                case 2:
                                        return new Comparable<?>[] { "west" };
                                case 3:
                                        return new Comparable<?>[] { "south" };
                                case 4:
                                        return new Comparable<?>[] { "north" };
                                case 5:
                                default:
                                        return new Comparable<?>[] { "standing" };
                        }
                };
                singleVariant(Blocks.torch, TORCH_FACING, torchCodec);
                singleVariant(Blocks.redstone_torch, TORCH_FACING, torchCodec);
                singleVariant(Blocks.unlit_redstone_torch, TORCH_FACING, torchCodec);
        }

        // ==================== 活板门（facing+half+open） ====================

        private static void registerTrapdoor() {
                // trapdoor: facing[meta&3: 0=north,1=south,2=east,3=west]
                // open[(meta&4)!=0], half[(meta&8)!=0?top:bottom]
                CatStateDefinition<Block> def = new CatStateDefinition.Builder<Block>(Blocks.trapdoor)
                                .add(TRAPDOOR_FACING, StateDefinitions.SLAB_HALF, OPEN)
                                .metaCodec(meta -> new Comparable<?>[] {
                                                TRAPDOOR_FACING.getValues().get(meta & 3),
                                                StateDefinitions.SLAB_HALF.getValues().get((meta & 8) == 0 ? 0 : 1),
                                                (meta & 4) != 0 ? "true" : "false" })
                                .create();
                CatModels.register(Blocks.trapdoor).states(def).register();
        }

        // ==================== 门（facing+hinge+open 动态，half 静态） ====================

        private static void registerDoors() {
                // door: half[(meta&8)!=0?upper:lower] 由自身 meta 静态解码；
                // facing/open 存在下半 meta、hinge 存在上半 meta，
                // 需跨方块读取另一半 → 交给 VanillaBlockResolvers.DOOR 运行时解析
                Block[] doors = { Blocks.wooden_door, Blocks.iron_door };
                for (Block door : doors) {
                        CatStateDefinition<Block> def = new CatStateDefinition.Builder<Block>(door)
                                        .add(DOOR_FACING, DOOR_HALF, DOOR_HINGE, OPEN)
                                        .dynamic(DOOR_FACING, DOOR_HINGE, OPEN)
                                        .metaCodec(meta -> new Comparable<?>[] {
                                                        DOOR_HALF.getValues().get((meta & 8) == 0 ? 0 : 1) })
                                        .create();
                        CatModels.register(door)
                                        .states(def)
                                        .dynamic(VanillaBlockResolvers.DOOR)
                                        .register();
                }
        }

        // ==================== 炼药锅（level） ====================

        private static void registerCauldron() {
                // cauldron: level[meta&3] 0-3
                singleVariant(Blocks.cauldron, LEVEL,
                                meta -> new Comparable<?>[] { LEVEL.getValues().get(meta & 3) });
        }

        // ==================== 红石线（power + 连接方向） ====================

        private static void registerRedstoneWire() {
                // redstone_wire: power[meta] 0-15 静态；north/east/south/west 连接 + up_* 爬线
                // 全动态，运行时由 REDSTONE_WIRE resolver 按 1.7.10 isPowerProviderOrWire
                // 及 renderBlockRedstoneWire 判定计算。metaCodec 只解码 power。
                CatStateDefinition<Block> def = new CatStateDefinition.Builder<Block>(Blocks.redstone_wire)
                                .add(POWER, PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST,
                                                REDSTONE_UP_NORTH, REDSTONE_UP_EAST, REDSTONE_UP_SOUTH,
                                                REDSTONE_UP_WEST)
                                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST,
                                                REDSTONE_UP_NORTH, REDSTONE_UP_EAST, REDSTONE_UP_SOUTH,
                                                REDSTONE_UP_WEST)
                                .metaCodec(meta -> new Comparable<?>[] { POWER.getValues().get(meta & 15) })
                                .create();
                CatModels.register(Blocks.redstone_wire)
                                .states(def)
                                .dynamic(VanillaBlockResolvers.REDSTONE_WIRE)
                                .connectionMultipart()
                                .register();
        }

        // ==================== 中继器（facing+delay） ====================

        private static void registerRepeaters() {
                // repeater: facing={north,east,south,west}+delay={1,2,3,4}
                // meta&3=facing, (meta>>2)&3=delay
                CatStateDefinition.MetaCodec repeaterCodec = meta -> new Comparable<?>[] {
                                DIODE_FACING.getValues().get(meta & 3),
                                DELAY.getValues().get((meta >> 2) & 3) };

                CatStateDefinition<Block> poweredDef = new CatStateDefinition.Builder<Block>(Blocks.powered_repeater)
                                .add(DIODE_FACING, DELAY)
                                .metaCodec(repeaterCodec)
                                .create();
                CatModels.register(Blocks.powered_repeater).states(poweredDef).register();

                CatStateDefinition<Block> unpoweredDef = new CatStateDefinition.Builder<Block>(
                                Blocks.unpowered_repeater)
                                .add(DIODE_FACING, DELAY)
                                .metaCodec(repeaterCodec)
                                .create();
                CatModels.register(Blocks.unpowered_repeater).states(unpoweredDef).register();
        }

        // ==================== 比较器（facing+mode+powered） ====================

        private static void registerComparators() {
                // comparator: facing={north,east,south,west}+mode={compare,subtract}+powered
                // meta&3=facing, (meta&4)!=0→subtract, (meta&8)!=0→powered(lit)
                // 1.7.10 点亮状态存于 meta bit 8 且方块保持 unpowered_comparator；
                // powered_comparator 实例恒按点亮渲染（isRepeaterPowered）。
                // The lit state lives in meta bit 8 while the block stays
                // unpowered_comparator; the powered_comparator instance always
                // renders lit (isRepeaterPowered).
                CatStateDefinition.MetaCodec unpoweredCodec = meta -> new Comparable<?>[] {
                                DIODE_FACING.getValues().get(meta & 3),
                                (meta & 4) != 0 ? "subtract" : "compare",
                                (meta & 8) != 0 ? "true" : "false" };
                CatStateDefinition.MetaCodec poweredCodec = meta -> new Comparable<?>[] {
                                DIODE_FACING.getValues().get(meta & 3),
                                (meta & 4) != 0 ? "subtract" : "compare",
                                "true" };

                CatStateDefinition<Block> poweredDef = new CatStateDefinition.Builder<Block>(Blocks.powered_comparator)
                                .add(DIODE_FACING, COMPARATOR_MODE, POWERED)
                                .metaCodec(poweredCodec)
                                .create();
                CatModels.register(Blocks.powered_comparator).states(poweredDef).register();

                CatStateDefinition<Block> unpoweredDef = new CatStateDefinition.Builder<Block>(
                                Blocks.unpowered_comparator)
                                .add(DIODE_FACING, COMPARATOR_MODE, POWERED)
                                .metaCodec(unpoweredCodec)
                                .create();
                CatModels.register(Blocks.unpowered_comparator).states(unpoweredDef).register();
        }

        // ==================== 活塞（facing+extended / 活塞头 facing+sticky）
        // ====================

        private static void registerPistons() {
                // piston / sticky_piston (BlockPistonBase): facing[meta&7] + extended[bit3]
                // 1.7.10 meta 布局：meta[0:2] =
                // EnumFacing（0=down,1=up,2=north,3=south,4=west,5=east），
                // bit3 = extended（伸出状态，本体缩回 1/4 格并露出 piston_inner 面）。
                // meta 6/7 是无效 facing（EnumFacing 仅 0-5），但 create() 预填
                // resolvedByMeta[0..15] 会对全部 meta 调用本 codec，必须钳制索引
                CatStateDefinition.MetaCodec pistonCodec = meta -> new Comparable<?>[] {
                                PISTON_FACING.getValues().get(Math.min(meta & 7, 5)),
                                (meta & 8) != 0 ? "true" : "false" };

                CatStateDefinition<Block> pistonDef = new CatStateDefinition.Builder<Block>(Blocks.piston)
                                .add(PISTON_FACING, EXTENDED)
                                .metaCodec(pistonCodec)
                                .create();
                CatModels.register(Blocks.piston).states(pistonDef).register();

                CatStateDefinition<Block> stickyDef = new CatStateDefinition.Builder<Block>(Blocks.sticky_piston)
                                .add(PISTON_FACING, EXTENDED)
                                .metaCodec(pistonCodec)
                                .create();
                CatModels.register(Blocks.sticky_piston).states(stickyDef).register();

                // piston_head (BlockPistonExtension): facing[meta&7] + sticky[bit3]
                // 静止活塞头方块（piston_extension / BlockPistonMoving 移动中走 TileEntity 渲染，不注册）
                CatStateDefinition<Block> headDef = new CatStateDefinition.Builder<Block>(Blocks.piston_head)
                                .add(PISTON_FACING, STICKY)
                                .metaCodec(pistonCodec)
                                .create();
                CatModels.register(Blocks.piston_head).states(headDef).register();
        }

        // ==================== 梯子（单 facing，自定义解码） ====================

        private static void registerLadder() {
                // ladder: facing={north,south,west,east} meta 2=north,3=south,4=west,5=east
                CatStateDefinition.MetaCodec codec = meta -> {
                        switch (meta & 7) {
                                case 2:
                                        return new Comparable<?>[] { "north" };
                                case 3:
                                        return new Comparable<?>[] { "south" };
                                case 4:
                                        return new Comparable<?>[] { "west" };
                                case 5:
                                        return new Comparable<?>[] { "east" };
                                default:
                                        return new Comparable<?>[] { "north" };
                        }
                };
                singleVariant(Blocks.ladder, LADDER_FACING, codec);
        }

        // ==================== 拉杆（facing+powered） ====================

        private static void registerLever() {
                // lever: facing={east,west,south,north,up,down} + powered
                // meta[0:2]: 1=east,2=west,3=south,4=north,5/6=up,0/7=down
                // bit3: powered
                CatStateDefinition.MetaCodec codec = meta -> {
                        String facing;
                        switch (meta & 7) {
                                case 1:
                                        facing = "east";
                                        break;
                                case 2:
                                        facing = "west";
                                        break;
                                case 3:
                                        facing = "south";
                                        break;
                                case 4:
                                        facing = "north";
                                        break;
                                case 5:
                                case 6:
                                        facing = "up";
                                        break;
                                case 0:
                                case 7:
                                default:
                                        facing = "down";
                                        break;
                        }
                        return new Comparable<?>[] { facing, (meta & 8) != 0 ? "true" : "false" };
                };
                CatStateDefinition<Block> def = new CatStateDefinition.Builder<Block>(Blocks.lever)
                                .add(LEVER_FACING, POWERED)
                                .metaCodec(codec)
                                .create();
                CatModels.register(Blocks.lever).states(def).register();
        }

        // ==================== 栅栏门（facing+open） ====================

        private static void registerFenceGate() {
                // fence_gate: facing={south,west,north,east} + open
                // meta[0:1] BlockDirectional: 0=south,1=west,2=north,3=east
                // bit2: open
                CatStateDefinition.MetaCodec codec = meta -> new Comparable<?>[] {
                                FENCE_GATE_FACING.getValues().get(meta & 3),
                                (meta & 4) != 0 ? "true" : "false" };
                CatStateDefinition<Block> def = new CatStateDefinition.Builder<Block>(Blocks.fence_gate)
                                .add(FENCE_GATE_FACING, OPEN)
                                .metaCodec(codec)
                                .create();
                CatModels.register(Blocks.fence_gate).states(def).register();
        }

        // ==================== 南瓜 / 南瓜灯（单 facing，特殊解码） ====================

        private static void registerPumpkins() {
                // pumpkin/lit_pumpkin: facing={south,east,north,west}
                // meta 0=south,1=east,2=north,3=west
                CatStateDefinition.MetaCodec codec = meta -> new Comparable<?>[] {
                                PUMPKIN_FACING.getValues().get(meta & 3) };
                singleVariant(Blocks.pumpkin, PUMPKIN_FACING, codec);
                singleVariant(Blocks.lit_pumpkin, PUMPKIN_FACING, codec);
        }

        // ==================== 发射器 / 投掷器（单 facing，EnumFacing 解码） ====================

        private static void registerDispenserAndDropper() {
                // dispenser/dropper: facing={down,up,north,south,west,east}
                // meta[0:2] EnumFacing: 0=down,1=up,2=north,3=south,4=west,5=east
                CatStateDefinition.MetaCodec codec = meta -> {
                        switch (meta & 7) {
                                case 0:
                                        return new Comparable<?>[] { "down" };
                                case 1:
                                        return new Comparable<?>[] { "up" };
                                case 2:
                                        return new Comparable<?>[] { "north" };
                                case 3:
                                        return new Comparable<?>[] { "south" };
                                case 4:
                                        return new Comparable<?>[] { "west" };
                                case 5:
                                        return new Comparable<?>[] { "east" };
                                default:
                                        return new Comparable<?>[] { "north" };
                        }
                };
                singleVariant(Blocks.dispenser, DISPENSER_FACING, codec);
                singleVariant(Blocks.dropper, DISPENSER_FACING, codec);
        }

        // ==================== 按钮（facing+powered，仅壁挂） ====================

        private static void registerButtons() {
                // stone_button/wooden_button: facing={east,west,south,north} + powered
                // meta[0:2]: 1=east,2=west,3=south,4=north
                // bit3: powered
                CatStateDefinition.MetaCodec codec = meta -> {
                        String facing;
                        switch (meta & 7) {
                                case 1:
                                        facing = "east";
                                        break;
                                case 2:
                                        facing = "west";
                                        break;
                                case 3:
                                        facing = "south";
                                        break;
                                case 4:
                                        facing = "north";
                                        break;
                                default:
                                        facing = "east";
                                        break;
                        }
                        return new Comparable<?>[] { facing, (meta & 8) != 0 ? "true" : "false" };
                };

                CatStateDefinition<Block> stoneDef = new CatStateDefinition.Builder<Block>(Blocks.stone_button)
                                .add(BUTTON_FACING, POWERED)
                                .metaCodec(codec)
                                .create();
                CatModels.register(Blocks.stone_button).states(stoneDef).register();

                CatStateDefinition<Block> woodDef = new CatStateDefinition.Builder<Block>(Blocks.wooden_button)
                                .add(BUTTON_FACING, POWERED)
                                .metaCodec(codec)
                                .create();
                CatModels.register(Blocks.wooden_button).states(woodDef).register();
        }

        // ==================== 熔炉 / 燃烧熔炉（单 facing，自定义解码） ====================

        private static void registerFurnaces() {
                // furnace/lit_furnace: facing={north,south,west,east}
                // meta 2=north,3=south,4=west,5=east
                CatStateDefinition.MetaCodec codec = meta -> {
                        switch (meta & 7) {
                                case 2:
                                        return new Comparable<?>[] { "north" };
                                case 3:
                                        return new Comparable<?>[] { "south" };
                                case 4:
                                        return new Comparable<?>[] { "west" };
                                case 5:
                                        return new Comparable<?>[] { "east" };
                                default:
                                        return new Comparable<?>[] { "north" };
                        }
                };
                singleVariant(Blocks.furnace, FURNACE_FACING, codec);
                singleVariant(Blocks.lit_furnace, FURNACE_FACING, codec);
        }
}
