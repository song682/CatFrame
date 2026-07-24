package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.CatStateDefinition;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一方块/物品模型登记 facade（链式 API）。
 * <p>
 * 把「typed 状态定义 → 常驻方块模型 + 物品决策树」的登记收口到一处，替代散落各处的
 * {@code registerBlockstateRedirect} / 手写 model 类实例化。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * CatModels.register(Blocks.wool)
 *     .states(StateDefinitions colorDef)   // typed 常驻表
 *     .itemFromBlockstate()                // 从 16 个静态状态导出物品 damage 决策树
 *     .register();
 *
 * CatModels.register(stairsBlock)
 *     .states(stairsDef)                   // facing/half + dynamic shape
 *     .dynamic(stairsShapeResolver)        // 运行时转角 shape
 *     .register();
 * }</pre>
 *
 * <h3>时序</h3>
 * {@link #register()} 只登记声明式 {@link CatModelSpec}；真正需要 blockstate JSON 的物化
 * 推迟到 {@link #materialize()}（由 {@code VanillaModelManager.Baking.registerAllModels}
 * 在 blockstate 加载完成后调用）。
 */
@SideOnly(Side.CLIENT)
public final class CatModels {

    private CatModels() {}

    /** preInit 登记的所有 spec（保序，供物化遍历）。 */
    public static final Map<Block, CatModelSpec> SPECS = new LinkedHashMap<>();

    /**
     * 开始登记一个方块的模型。
     *
     * @param block 目标方块
     * @return 链式配置器
     */
    public static Spec register(Block block) {
        return new Spec(block);
    }

    // ==================== 链式配置器 ====================

    public static final class Spec {
        private final CatModelSpec spec;

        private Spec(Block block) {
            this.spec = new CatModelSpec(block);
        }

        /** 设置 typed 常驻状态表（属性驱动 variant 匹配）。 */
        public Spec states(CatStateDefinition<?> def) {
            spec.def = def;
            return this;
        }

        /** 设置 per-meta blockstate 重定向（如按颜色拆分的多文件块）。 */
        public Spec redirect(IMetadataBlockstateRedirect redirect, String namespace) {
            spec.redirect = redirect;
            spec.redirectNamespace = namespace;
            return this;
        }

        /** 设置运行时动态属性解析器（stairs shape / pane 连接等）。 */
        public Spec dynamic(ResidentStateModel.DynamicPropertyResolver dynamic) {
            spec.dynamic = dynamic;
            return this;
        }

        /** 启用 pane 连接 multipart 模式（per-face 合并烘焙，isFullModel=false）。 */
        public Spec connectionMultipart() {
            spec.connectionMultipart = true;
            spec.fullModel = false;
            return this;
        }

        /** 覆盖 isFullModel（默认 true；multipart 装饰件可设为 false）。 */
        public Spec fullModel(boolean fullModel) {
            spec.fullModel = fullModel;
            return this;
        }

        /** 为指定 meta 登记固定 Y 旋转（度）。 */
        public Spec rotation(int meta, int degrees) {
            ModelRegistry.registerBlockRotation(spec.block, meta, degrees);
            return this;
        }

        /** 标记该方块使用基于位置的随机 Y 旋转。 */
        public Spec randomRotation() {
            ModelRegistry.markRandomRotation(spec.block);
            return this;
        }

        /** 从 blockstate 的 16 个静态状态导出物品 {@code damage} 决策树。 */
        public Spec itemFromBlockstate() {
            spec.itemFromBlockstate = true;
            return this;
        }

        /** 完成登记：存入 {@link #SPECS} 并注册 typed 状态定义。 */
        public void register() {
            SPECS.put(spec.block, spec);
            if (spec.def != null) {
                ModelRegistry.registerStateDefinition(spec.block, spec.def);
            }
        }
    }

    // ==================== 物化 ====================

    /**
     * 把所有已登记的 spec 物化为常驻方块模型与物品决策树。
     * <p>
     * 在 blockstate JSON 加载完成后（{@code registerAllModels}）调用。已注册模型的方块
     * 会被覆盖为常驻模型；启用 {@code itemFromBlockstate} 的方块导出物品决策树并标记 persistent，
     * 优先于 {@code items/{name}.json} 约定匹配。
     *
     * @return 成功物化的方块模型数量
     */
    public static int materialize() {
        int count = 0;
        for (CatModelSpec spec : SPECS.values()) {
            Block block = spec.block;
            String registryName = Block.blockRegistry.getNameForObject(block);
            String namespace = "minecraft";
            String name = registryName;
            if (registryName != null && registryName.contains(":")) {
                int c = registryName.indexOf(':');
                namespace = registryName.substring(0, c);
                name = registryName.substring(c + 1);
            }

            BlockstateJson bs = null;
            Map<String, BlockstateJson> nsMap = ModelManagerDataLoader.loadedBlockstates.get(namespace);
            if (nsMap != null && name != null) bs = nsMap.get(name);

            // 常驻方块模型（redirect 模式允许 bs 为 null）
            ResidentStateModel model = spec.buildBlockModel(bs);
            ModelRegistry.registerBlockModel(block, model);
            count++;

            // 物品决策树（可选）
            if (spec.itemFromBlockstate) {
                ItemStateNode node = spec.buildItemNode(bs);
                Item item = Item.getItemFromBlock(block);
                if (node != null && item != null) {
                    ModelRegistry.registeredItemModels.put(item, new ItemStateModel(node));
                    ModelRegistry.persistentItemModels.add(item);
                }
            }
        }
        if (count > 0) {
            CatFrame.logger.info("CatModels: materialized {} resident block models", count);
        }
        return count;
    }
}
