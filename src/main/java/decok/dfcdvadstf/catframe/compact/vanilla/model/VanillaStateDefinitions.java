package decok.dfcdvadstf.catframe.compact.vanilla.model;

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
 * 映射；variant 匹配统一走 {@code RenderDispatcher.buildVariantKey}（字母序 + {@code toString()}），
 * 故最终 variant key 与旧路径逐字节相同。所有属性一律用 {@link StateDefinitions#stringProp}
 * （值即字符串，{@code toString()} 与 blockstate JSON 的 variant key 值精确对齐）。
 *
 * <h3>动态属性</h3>
 * 楼梯 {@code shape}、玻璃板 {@code north/east/south/west} 标记为 {@code .dynamic(...)}
 * （不参与 meta 解码），运行时由 {@link VanillaBlockResolvers} 从世界计算并覆盖。
 */
@SideOnly(Side.CLIENT)
public final class VanillaStateDefinitions {

    private VanillaStateDefinitions() {}

    // ==================== 共享 / 每块属性常量 ====================

    // 原木 / 树叶
    private static final Property<String> LOG_WOOD =
            StateDefinitions.stringProp("wood", "oak", "spruce", "birch", "jungle");
    private static final Property<String> LOG2_WOOD =
            StateDefinitions.stringProp("wood", "acacia", "dark_oak");

    // 单 variant / wood（clamp 解码）
    private static final Property<String> SAPLING_VARIANT =
            StateDefinitions.stringProp("variant", "oak", "spruce", "birch", "jungle", "acacia", "dark_oak");
    private static final Property<String> EGG_VARIANT =
            StateDefinitions.stringProp("variant", "stone", "cobblestone", "stone_brick",
                    "mossy_stone_brick", "cracked_stone_brick", "chiseled_stone_brick");
    private static final Property<String> PLANK_WOOD =
            StateDefinitions.stringProp("wood", "oak", "spruce", "birch", "jungle", "acacia", "dark_oak");
    private static final Property<String> STONEBRICK_VARIANT =
            StateDefinitions.stringProp("variant", "stonebrick", "mossy_stonebrick",
                    "cracked_stonebrick", "chiseled_stonebrick");
    private static final Property<String> SANDSTONE_VARIANT =
            StateDefinitions.stringProp("variant", "sandstone", "chiseled_sandstone", "smooth_sandstone");
    private static final Property<String> SAND_VARIANT =
            StateDefinitions.stringProp("variant", "sand", "red_sand");
    private static final Property<String> DIRT_VARIANT =
            StateDefinitions.stringProp("variant", "dirt", "podzol");
    private static final Property<String> WALL_VARIANT =
            StateDefinitions.stringProp("variant", "cobblestone", "mossy_cobblestone");

    // 石台阶
    private static final Property<String> SLAB_VARIANT =
            StateDefinitions.stringProp("variant", "stone", "sandstone", "wood", "cobblestone",
                    "brick", "stone_brick", "nether_brick", "quartz");

    // 铁砧
    private static final Property<String> ANVIL_FACING =
            StateDefinitions.stringProp("facing", "north", "east", "south", "west");
    private static final Property<String> ANVIL_DAMAGE =
            StateDefinitions.stringProp("damage", "0", "1", "2");

    // 作物 age
    private static final Property<String> AGE =
            StateDefinitions.stringProp("age", "0", "1", "2", "3", "4", "5", "6", "7");

    // 漏斗
    private static final Property<String> HOPPER_FACING =
            StateDefinitions.stringProp("facing", "down", "north", "south", "west", "east");
    private static final Property<String> POWERED =
            StateDefinitions.stringProp("powered", "false", "true");

    // 石英块
    private static final Property<String> QUARTZ_TYPE =
            StateDefinitions.stringProp("type", "quartz_block", "chiseled_quartz_block", "quartz_pillar");

    // 楼梯
    private static final Property<String> STAIR_FACING =
            StateDefinitions.stringProp("facing", "east", "west", "south", "north");
    private static final Property<String> STAIR_SHAPE =
            StateDefinitions.stringProp("shape", "straight", "inner_left", "inner_right", "outer_left", "outer_right");

    // 玻璃板连接
    private static final Property<String> PANE_NORTH = StateDefinitions.stringProp("north", "false", "true");
    private static final Property<String> PANE_EAST = StateDefinitions.stringProp("east", "false", "true");
    private static final Property<String> PANE_SOUTH = StateDefinitions.stringProp("south", "false", "true");
    private static final Property<String> PANE_WEST = StateDefinitions.stringProp("west", "false", "true");

    // ==================== 登记入口 ====================

    /**
     * 登记全部原版方块的类型化状态定义。preInit 阶段调用（在
     * {@code ModelManagerDataLoader.init()} 之前）。
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
        registerPanes();
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
                .metaCodec(meta -> new Comparable<?>[]{
                        LOG_WOOD.getValues().get(meta & 3),
                        StateDefinitions.AXIS.getValues().get((meta >> 2) % 3)})
                .create();
        CatModels.register(Blocks.log).states(logDef).register();

        // log2: wood={acacia,dark_oak}[meta&1], axis 同上
        CatStateDefinition<Block> log2Def = new CatStateDefinition.Builder<Block>(Blocks.log2)
                .add(LOG2_WOOD, StateDefinitions.AXIS)
                .metaCodec(meta -> new Comparable<?>[]{
                        LOG2_WOOD.getValues().get(meta & 1),
                        StateDefinitions.AXIS.getValues().get((meta >> 2) % 3)})
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
                meta -> new Comparable<?>[]{SAND_VARIANT.getValues().get(meta == 0 ? 0 : 1)});
        // dirt: meta==2 ? podzol : dirt
        singleVariant(Blocks.dirt, DIRT_VARIANT,
                meta -> new Comparable<?>[]{DIRT_VARIANT.getValues().get(meta == 2 ? 1 : 0)});
        // cobblestone_wall: meta==0 ? cobblestone : mossy_cobblestone
        singleVariant(Blocks.cobblestone_wall, WALL_VARIANT,
                meta -> new Comparable<?>[]{WALL_VARIANT.getValues().get(meta == 0 ? 0 : 1)});
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
            if (idx > max) idx = max;
            if (idx < 0) idx = 0;
            return new Comparable<?>[]{prop.getValues().get(idx)};
        };
    }

    // ==================== 石台阶（half+variant）/ 铁砧（facing+damage） ====================

    private static void registerSlabAndAnvil() {
        // stone_slab: half=(meta&8)==0?bottom:top, variant=SLAB_TYPES[meta&7]
        CatStateDefinition<Block> slabDef = new CatStateDefinition.Builder<Block>(Blocks.stone_slab)
                .add(StateDefinitions.SLAB_HALF, SLAB_VARIANT)
                .metaCodec(meta -> new Comparable<?>[]{
                        StateDefinitions.SLAB_HALF.getValues().get((meta & 8) == 0 ? 0 : 1),
                        SLAB_VARIANT.getValues().get(meta & 7)})
                .create();
        CatModels.register(Blocks.stone_slab).states(slabDef).register();

        // anvil: facing={north,east,south,west}[meta&3], damage={0,1,2}[min((meta>>2)&3,2)]
        CatStateDefinition<Block> anvilDef = new CatStateDefinition.Builder<Block>(Blocks.anvil)
                .add(ANVIL_FACING, ANVIL_DAMAGE)
                .metaCodec(meta -> new Comparable<?>[]{
                        ANVIL_FACING.getValues().get(meta & 3),
                        ANVIL_DAMAGE.getValues().get(Math.min((meta >> 2) & 3, 2))})
                .create();
        CatModels.register(Blocks.anvil).states(anvilDef).register();
    }

    // ==================== 作物（单 age，meta&7；默认解码 meta%8 == meta&7） ====================

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
                        case 2: facing = "north"; break;
                        case 3: facing = "south"; break;
                        case 4: facing = "west"; break;
                        case 5: facing = "east"; break;
                        case 0:
                        default: facing = "down"; break;
                    }
                    return new Comparable<?>[]{facing, (meta & 8) != 0 ? "true" : "false"};
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
                        case 1: return new Comparable<?>[]{"chiseled_quartz_block", "y"};
                        case 2: return new Comparable<?>[]{"quartz_pillar", "y"};
                        case 3: return new Comparable<?>[]{"quartz_pillar", "x"};
                        case 4: return new Comparable<?>[]{"quartz_pillar", "z"};
                        case 0:
                        default: return new Comparable<?>[]{"quartz_block", "y"};
                    }
                })
                .create();
        CatModels.register(Blocks.quartz_block).states(def).register();
    }

    // ==================== 楼梯（facing+half 静态，shape 动态） ====================

    private static void registerStairs() {
        Block[] stairs = {
                Blocks.oak_stairs, Blocks.stone_stairs, Blocks.brick_stairs, Blocks.stone_brick_stairs,
                Blocks.nether_brick_stairs, Blocks.sandstone_stairs, Blocks.spruce_stairs, Blocks.birch_stairs,
                Blocks.jungle_stairs, Blocks.quartz_stairs, Blocks.acacia_stairs, Blocks.dark_oak_stairs
        };
        for (Block stair : stairs) {
            // facing={east,west,south,north}[meta&3], half=(meta&4)==0?bottom:top, shape=dynamic
            CatStateDefinition<Block> def = new CatStateDefinition.Builder<Block>(stair)
                    .add(STAIR_FACING, StateDefinitions.SLAB_HALF, STAIR_SHAPE)
                    .dynamic(STAIR_SHAPE)
                    .metaCodec(meta -> new Comparable<?>[]{
                            STAIR_FACING.getValues().get(meta & 3),
                            StateDefinitions.SLAB_HALF.getValues().get((meta & 4) == 0 ? 0 : 1)})
                    .create();
            CatModels.register(stair)
                    .states(def)
                    .dynamic(VanillaBlockResolvers.STAIRS)
                    .register();
        }
    }

    // ==================== 玻璃板（连接全动态） ====================

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
        CatStateDefinition<Block> stainedPaneDef = new CatStateDefinition.Builder<Block>(Blocks.stained_glass_pane)
                .add(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                .dynamic(PANE_NORTH, PANE_EAST, PANE_SOUTH, PANE_WEST)
                .create();
        CatModels.register(Blocks.stained_glass_pane)
                .states(stainedPaneDef)
                .dynamic(VanillaBlockResolvers.PANE)
                .connectionMultipart()
                .redirect(meta -> StateDefinitions.COLORS[meta & 15] + "_stained_glass_pane", "minecraft")
                .register();
    }
}
