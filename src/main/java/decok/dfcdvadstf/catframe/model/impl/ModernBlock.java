package decok.dfcdvadstf.catframe.model.impl;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.CatModels;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.RenderJsonBlockModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.CatStateDefinition;
import decok.dfcdvadstf.catframe.model.state.CatStateInheritance;
import decok.dfcdvadstf.catframe.model.state.block.ResidentStateModel;
import decok.dfcdvadstf.catframe.model.state.block.StateProviderBlockModel;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.world.IBlockAccess;

import java.util.Collections;
import java.util.Map;

/**
 * ModernBlock — CatFrame 模组方块基类。
 *
 * <p>类似 {@link ModernItem}，为方块提供一键接入 CatFrame blockstate JSON 模型管线的能力：
 * <ul>
 *   <li>通过 {@link #setBlockstate(String, String)} 或 {@link #setBlockstate(String)} 指定 blockstate JSON 路径</li>
 *   <li>通过 {@link #register(ModernBlock)} 完成 VMM 数据加载 + ISBRH 注册 + BlockStateModel 注册</li>
 *   <li>默认按 "normal" variant 渲染；子类可覆盖 {@link #getStateProperties} 实现动态属性匹配</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * public class MyBlock extends ModernBlock {
 *     public MyBlock() {
 *         super(Material.rock);
 *         setBlockName("my_block");
 *         setBlockTextureName("catframe:my_block");
 *         setBlockstate("catframe", "my_block");
 *     }
 * }
 *
 * // 在 preInit 中（确保在 VMMDataLoader.init() 之后）：
 * GameRegistry.registerBlock(myBlock, "my_block");
 * ModernBlock.register(myBlock);
 * }</pre>
 */
public class ModernBlock extends Block implements IBlockStateProvider {

    /** 默认 blockstate 命名空间，与原版资源包目录一致。 */
    public static final String DEFAULT_BLOCKSTATE_NAMESPACE = "minecraft";

    /** 显式指定的 blockstate 命名空间。空字符串表示未设置，注册时尝试从注册名推导。 */
    protected String blockstateNamespace = "";

    /** 显式指定的 blockstate 文件名（不含 .json）。空字符串表示未设置，注册时尝试从注册名推导。 */
    protected String blockstateName = "";

    /** ISBRH renderType ID，由 {@link #register(ModernBlock)} 在客户端分配。 */
    protected int modernRenderType = 0;

    /**
     * v0.3.0+ 自动继承解析得到的 typed 状态定义（{@link #register(ModernBlock)} 时填充）。
     * <p>
     * 子类无需手动赋值：未显式配置时沿继承链合并基类片段（见
     * {@link CatStateInheritance}），供渲染管线与 blockstate 键校验使用。
     */
    private CatStateDefinition<?> stateDefinition;

    protected ModernBlock(Material material) {
        super(material);
    }

    /**
     * 设置 blockstate JSON 的命名空间与文件名。
     *
     * @param namespace 资源包命名空间，如 "catframe"
     * @param name      blockstate 文件名（不含 .json），如 "my_block"
     * @return this
     */
    public ModernBlock setBlockstate(String namespace, String name) {
        this.blockstateNamespace = namespace == null ? "" : namespace;
        this.blockstateName = name == null ? "" : name;
        return this;
    }

    /**
     * 使用默认命名空间 {@link #DEFAULT_BLOCKSTATE_NAMESPACE} 设置 blockstate 文件名。
     *
     * @param name blockstate 文件名（不含 .json）
     * @return this
     */
    public ModernBlock setBlockstate(String name) {
        return setBlockstate(DEFAULT_BLOCKSTATE_NAMESPACE, name);
    }

    @Override
    public ModernBlock setBlockName(String name) {
        super.setBlockName(name);
        return this;
    }

    @Override
    public ModernBlock setBlockTextureName(String name) {
        super.setBlockTextureName(name);
        return this;
    }

    @Override
    public ModernBlock setCreativeTab(CreativeTabs tab) {
        super.setCreativeTab(tab);
        return this;
    }

    @Override
    public String getBlockstateNamespace() {
        return blockstateNamespace;
    }

    @Override
    public String getBlockstateName() {
        return blockstateName;
    }

    @Override
    public CatStateDefinition<?> getStateDefinition() {
        return stateDefinition;
    }

    /**
     * 动态属性映射。默认返回空 map（匹配 blockstate JSON 中的 "normal" variant）。
     * <p>
     * 子类可覆盖此方法，根据世界状态 / metadata 返回属性键值对，例如：
     * <pre>{@code
     * Map<String, String> props = new HashMap<>();
     * props.put("facing", EnumFacing.getFront(metadata).name());
     * return props;
     * }</pre>
     */
    @Override
    public Map<String, String> getStateProperties(IBlockAccess world, int x, int y, int z, int metadata) {
        return Collections.emptyMap();
    }

    @Override
    public int getRenderType() {
        return modernRenderType;
    }

    /**
     * 注册 ModernBlock 到 CatFrame 管线。
     * <p>
     * 本方法会：
     * <ol>
     *   <li>若未显式设置 blockstate 路径，尝试从方块注册名推导</li>
     *   <li>通过 {@link ModelManagerDataLoader#registerBlock(Block)} 加载 blockstate JSON</li>
     *   <li>注册 {@link BlockStateModel}，使 ItemBlock 在物品栏/手持中也能使用同一模型</li>
     *   <li>在客户端注册 ISBRH，获取并设置 renderType ID</li>
     * </ol>
     *
     * <p>注册时机已放开：发现流程由纹理缝合驱动（每次 {@code TextureStitchEvent.Pre} 增量执行），
     * 实现了 IBlockStateProvider 的注册表方块也会被自动发现；blockstate 至迟在下一次缝合时加载
     * （首轮发现完成后调用则立即加载）。renderType 只能在客户端获取。
     *
     * @param block 要注册的 ModernBlock 实例
     * @return 分配的 ISBRH renderType ID；服务端返回 -1
     */
    public static int register(ModernBlock block) {
        resolveBlockstateFromRegistry(block);

        if (block.blockstateName.isEmpty()) {
            CatFrame.logger.warn(
                    "[ModernBlock] blockstate not configured and could not be derived from registry name for {}. " +
                    "Call setBlockstate() before register(), or register the block with GameRegistry before ModernBlock.register().",
                    block.getClass().getName());
        }

        ModelManagerDataLoader.registerBlock(block);

        // 自动 BlockState 继承：未显式 typed def 时沿继承链合并基类片段（如
        // PillarBlock 的 axis），子类无需手动覆盖 getStateProperties 或声明 .states(...)。
        // Auto inheritance: merge base fragments (e.g. PillarBlock's axis) along the
        // class hierarchy when no explicit typed def exists.
        if (block.stateDefinition == null && !ModelRegistry.hasStateDefinition(block)) {
            CatStateInheritance.Inherited inherited = CatStateInheritance.resolve(block);
            if (inherited != null) {
                block.stateDefinition = inherited.def;
                ModelRegistry.registerStateDefinition(block, block.stateDefinition);
                CatFrame.logger.info("[ModernBlock] inherited state definition for {} from base class chain",
                        block.getClass().getName());
            }
        }

        // 注册 BlockStateModel，供 ItemBlock 渲染 fallback 使用
        ModelRegistry.registerBlockModel(block, new LazyStateProviderBlockModel(block));

        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            int id = RenderJsonBlockModel.register(block);
            block.modernRenderType = id;
            return id;
        }
        return -1;
    }

    /**
     * 任意 Block 的便捷注册入口：自动 BlockState 继承 + CatModels 声明式管线。
     * <p>
     * 非 {@link ModernBlock} 的方块（如直接继承原版 {@code BlockRotatedPillar} 的
     * PillarBlock 扩展）无需实现 {@link IBlockStateProvider}：本方法从注册名推导并登记
     * blockstate 命名空间，然后走 {@link CatModels#register(Block)} 管线（含继承解析），
     * blockstate JSON 至迟在下次纹理缝合时加载、物化。
     *
     * @param block 任意方块实例
     * @return 客户端分配的 ISBRH renderType ID；非 {@link ModernBlock} 返回 -1
     */
    public static int register(Block block) {
        if (block instanceof ModernBlock) {
            return register((ModernBlock) block);
        }
        // 推导并登记 blockstate 命名空间（CatModels.materialize 依赖已加载的 blockstates）
        String registryName = Block.blockRegistry.getNameForObject(block);
        if (registryName != null) {
            int colon = registryName.indexOf(':');
            String ns = colon > 0 ? registryName.substring(0, colon) : DEFAULT_BLOCKSTATE_NAMESPACE;
            ModelManagerDataLoader.registerNamespace(ns);
        }
        CatModels.register(block).register();
        return -1;
    }

    /**
     * 若未显式设置 blockstate 路径，从方块注册名推导命名空间和名称。
     * 注册名格式为 "namespace:name"，无命名空间时回退到 {@link #DEFAULT_BLOCKSTATE_NAMESPACE}。
     */
    private static void resolveBlockstateFromRegistry(ModernBlock block) {
        if (block.blockstateName.isEmpty()) {
            String registryName = Block.blockRegistry.getNameForObject(block);
            if (registryName != null && !registryName.isEmpty()) {
                int colon = registryName.indexOf(':');
                if (colon >= 0) {
                    block.blockstateNamespace = registryName.substring(0, colon);
                    block.blockstateName = registryName.substring(colon + 1);
                } else {
                    block.blockstateNamespace = DEFAULT_BLOCKSTATE_NAMESPACE;
                    block.blockstateName = registryName;
                }
            }
        }
    }

    /**
     * 延迟解析 blockstate 的 BlockStateModel。
     * <p>
     * 允许 {@link #register(ModernBlock)} 在 blockstate JSON 尚未加载时也能预先注册模型；
     * 首次渲染时从 {@link ModelManagerDataLoader#getBlockstateData(Block)} 懒加载。
     */
    private static class LazyStateProviderBlockModel implements BlockStateModel {
        private final IBlockStateProvider provider;
        private BlockStateModel delegate;

        LazyStateProviderBlockModel(IBlockStateProvider provider) {
            this.provider = provider;
        }

        private BlockStateModel getDelegate() {
            if (delegate == null) {
                Block block = (Block) provider;
                BlockstateJson bs = ModelManagerDataLoader.getBlockstateData(block);
                if (bs != null) {
                    // typed 状态定义存在时（显式 .states(...) 或继承解析得到），
                    // 走 ResidentStateModel 属性驱动匹配；否则回退到 provider 动态属性。
                    CatStateDefinition<?> def = ModelRegistry.getStateDefinition(block);
                    if (def != null) {
                        delegate = ResidentStateModel.builder(block)
                                        .blockstate(bs)
                                        .stateDefinition(def)
                                        .build();
                    } else {
                        delegate = new StateProviderBlockModel(provider, bs, null);
                    }
                }
            }
            return delegate;
        }

        @Override
        public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
            BlockStateModel model = getDelegate();
            return model != null
                    ? model.collectParts(world, x, y, z, metadata)
                    : BlockStateModelPart.empty();
        }
    }
}
