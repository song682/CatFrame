package decok.dfcdvadstf.catframe.model.render;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
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
 * 渲染时委托给 {@link RenderDispatcher} 完成实际绘制。
 * <p>
 * <b>使用方式：</b>
 * <ol>
 *   <li>方块类实现 {@link IBlockStateProvider}</li>
 *   <li>在方块类中 {@code getRenderType()} 返回 {@link #register(Block)} 的返回值</li>
 *   <li>在 preInit 中调用 {@link #register(Block)} 注册 ISBRH</li>
 * </ol>
 * <p>
 * 纹理收集、blockstate JSON 加载、模型烘焙由 VMM 管线
 * （{@link ModelManagerDataLoader}、
 * {@link decok.dfcdvadstf.catframe.model.VanillaTextureTracker}）自动处理。
 */
@SideOnly(Side.CLIENT)
public class RenderJsonBlockModel implements ISimpleBlockRenderingHandler {

    /** Block → renderType ID 映射 */
    private static final Map<Block, Integer> registeredBlocks = new HashMap<>();

    /** renderType ID → RenderJsonBlockModel 实例映射 */
    private static final Map<Integer, RenderJsonBlockModel> handlers = new HashMap<>();

    /** 下一个可用的 renderType ID */
    private static int nextId = 90000;

    private final int renderTypeId;

    private RenderJsonBlockModel(int renderTypeId) {
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
        RenderJsonBlockModel handler = new RenderJsonBlockModel(id);
        registeredBlocks.put(block, id);
        handlers.put(id, handler);

        RenderingRegistry.registerBlockHandler(handler);
        return id;
    }

    /**
     * 检查方块是否通过 {@link RenderJsonBlockModel} 注册。
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
     * 世界中方块渲染 — 委托给 {@link RenderDispatcher#renderBlock}。
     * <p>
     * VanillaRenderDispatcher 内部从 VMMDataLoader.stateBlockData 取出
     * blockstate 数据，通过 BakedModelCache 懒烘焙 + UniformRenderPipeline 完成绘制。
     */
    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z,
                                     Block block, int modelId, RenderBlocks renderer) {
        return RenderDispatcher.renderBlock(world, x, y, z, block, renderer);
    }

    /**
     * 物品栏 / GUI 中的方块渲染 — <b>已停用</b>。
     * <p>
     * 方块物品的物品栏 / 手持 / 掉落物渲染统一走物品管线（Forge {@code IItemRenderer}
     * + {@link RenderJsonItemModel}，由 {@code items/{name}.json} 驱动），不再经由 ISBRH。
     * 此方法保留仅为满足接口，恒为空操作。
     */
    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        // no-op: 物品上下文渲染由物品管线负责，见 ModelRegistry.getRegisteredItemModel。
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        // 物品栏方块渲染已停用（统一走物品管线），返回 false 让原版不再尝试 3D 物品栏渲染。
        return false;
    }

    @Override
    public int getRenderId() {
        return this.renderTypeId;
    }
}
