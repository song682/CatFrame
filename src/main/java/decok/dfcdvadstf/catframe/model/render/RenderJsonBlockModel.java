package decok.dfcdvadstf.catframe.model.render;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.VanillaModelRegistry;
import decok.dfcdvadstf.catframe.model.VanillaRenderDispatcher;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import java.util.HashMap;
import java.util.Map;

/**
 * 模组方块的通用 ISBRH 桥接处理器。
 * <p>
 * 将 Forge 的 {@link ISimpleBlockRenderingHandler} 机制与 VMM 的
 * blockstate 模型管线连接：mod 方块通过 {@code register()} 注册后，
 * 渲染时委托给 {@link VanillaRenderDispatcher} 完成实际绘制。
 * <p>
 * <b>使用方式：</b>
 * <ol>
 *   <li>方块类实现 {@link IBlockStateProvider}</li>
 *   <li>在方块类中 {@code getRenderType()} 返回 {@link #register()} 的返回值</li>
 *   <li>在 preInit 中调用 {@link #register()} 注册 ISBRH</li>
 * </ol>
 * <p>
 * 纹理收集、blockstate JSON 加载、模型烘焙由 VMM 管线
 * （{@link decok.dfcdvadstf.catframe.model.VMMDataLoader}、
 * {@link decok.dfcdvadstf.catframe.model.VanillaTextureTracker}）自动处理。
 */
@SideOnly(Side.CLIENT)
public class BlockStateISBRH implements ISimpleBlockRenderingHandler {

    /** Block → renderType ID 映射 */
    private static final Map<Block, Integer> registeredBlocks = new HashMap<>();

    /** renderType ID → BlockStateISBRH 实例映射 */
    private static final Map<Integer, BlockStateISBRH> handlers = new HashMap<>();

    /** 下一个可用的 renderType ID */
    private static int nextId = 90000;

    private final int renderTypeId;

    private BlockStateISBRH(int renderTypeId) {
        this.renderTypeId = renderTypeId;
    }

    /**
     * 为方块注册 ISBRH 处理器。
     * <p>
     * 分配唯一 renderType ID 并通过 Forge {@link RenderingRegistry} 注册。
     * 方块的 {@code getRenderType()} 应返回此方法的返回值。
     *
     * @param block 实现了 IBlockStateProvider 的方块实例
     * @return 分配的 renderType ID
     */
    public static int register(Block block) {
        int id = nextId++;
        BlockStateISBRH handler = new BlockStateISBRH(id);
        registeredBlocks.put(block, id);
        handlers.put(id, handler);
        RenderingRegistry.registerBlockHandler(handler);
        return id;
    }

    /**
     * 检查方块是否通过 {@link BlockStateISBRH} 注册。
     * <p>
     * 供 {@link decok.dfcdvadstf.catframe.mixin.middle.MixinRenderBlocks}
     * 判断是否跳过 Mixin 拦截（已注册 ISBRH 的方块由 Forge 直接分派到 ISBRH 处理器）。
     *
     * @param block 方块实例
     * @return true 如果该方块已通过 {@link #register(Block)} 注册
     */
    public static boolean isRegistered(Block block) {
        return registeredBlocks.containsKey(block);
    }

    // ==================== ISBRH 接口实现 ====================

    /**
     * 世界中方块渲染 — 委托给 {@link VanillaRenderDispatcher#renderBlock}。
     * <p>
     * VanillaRenderDispatcher 内部从 VMMDataLoader.stateBlockData 取出
     * blockstate 数据，通过 BakedModelCache 懒烘焙 + UniformRenderPipeline 完成绘制。
     */
    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z,
                                     Block block, int modelId, RenderBlocks renderer) {
        return VanillaRenderDispatcher.renderBlock(world, x, y, z, block, renderer);
    }

    /**
     * 物品栏 / GUI 中的方块渲染 — 从 VMM 取模型数据，走 BLOCK_GUI 阶段的 UniformRenderPipeline。
     */
    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        // 优先从 VMM 注册表取预烘焙模型
        BlockStateModel model = VanillaModelRegistry.getBlockModel(block);
        if (model != null) {
            BlockStateModelPart part = model.collectParts(null, 0, 0, 0, metadata);
            if (part != null && !part.isEmpty()) {
                UniformRenderPipeline.renderBlockQuadsGUI(part, block, null, metadata);
            }
            return;
        }

        // 兜底：registeredBlockModels 未命中时，尝试从 blockstate 的 "normal" variant 取模型
        BlockStateModelPart part = collectFromBlockstate(block, metadata);
        if (part != null && !part.isEmpty()) {
            UniformRenderPipeline.renderBlockQuadsGUI(part, block, null, metadata);
        }
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return this.renderTypeId;
    }

    // ==================== 内部辅助 ====================

    /**
     * 从 blockstate JSON 的 "normal" variant 收集默认模型部件（用于物品栏渲染兜底）。
     */
    private static BlockStateModelPart collectFromBlockstate(Block block, int metadata) {
        decok.dfcdvadstf.catframe.model.state.BlockstateJson bs =
                decok.dfcdvadstf.catframe.model.VMMDataLoader.getBlockstateData(block);
        if (bs == null || bs.variants == null) return null;

        decok.dfcdvadstf.catframe.model.state.BlockstateJson.VariantEntry entry = bs.variants.get("normal");
        if (entry == null) return null;

        decok.dfcdvadstf.catframe.model.state.BlockstateJson.Variant variant = entry.getVariant(0);
        if (variant == null || variant.model == null) return null;

        String cacheKey = BakedModelCache.buildKey(
                variant.model, variant.x, variant.y);
        BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
        return (part != null && !part.isEmpty()) ? part : null;
    }
}
