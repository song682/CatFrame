package decok.dfcdvadstf.catframe.model.render;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.mixin.middle.render.MixinRenderBlocks;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic ISBRH bridge handler for mod blocks.
 * <p>
 * Connects Forge's {@link ISimpleBlockRenderingHandler} mechanism to VMM's blockstate model pipeline:
 * after a mod block is registered via {@code register()}, rendering is delegated to
 * {@link RenderDispatcher} for actual drawing.
 * <p>
 * <b>Usage:</b>
 * <ol>
 *   <li>Block class implements {@link IBlockStateProvider}</li>
 *   <li>Block class returns {@link #register(Block)}'s return value from {@code getRenderType()}</li>
 *   <li>Call {@link #register(Block)} in preInit to register the ISBRH</li>
 * </ol>
 * <p>
 * Texture collection, blockstate JSON loading, and model baking are handled automatically by the VMM pipeline
 * ({@link ModelManagerDataLoader}, {@link decok.dfcdvadstf.catframe.model.VanillaTextureTracker}).
 */
@SideOnly(Side.CLIENT)
public class RenderJsonBlockModel implements ISimpleBlockRenderingHandler {

    /** Block → renderType ID mapping */
    private static final Map<Block, Integer> registeredBlocks = new HashMap<>();

    /** renderType ID → RenderJsonBlockModel instance mapping */
    private static final Map<Integer, RenderJsonBlockModel> handlers = new HashMap<>();

    /** Next available renderType ID */
    private static int nextId = 90000;

    private final int renderTypeId;

    private RenderJsonBlockModel(int renderTypeId) {
        this.renderTypeId = renderTypeId;
    }

    /**
     * Register ISBRH handler for a block.
     * <p>
     * Allocates a unique renderType ID and registers it via Forge {@link RenderingRegistry}.
     * The block's {@code getRenderType()} should return the value returned by this method.
     *
     * @param block block instance implementing IBlockStateProvider
     * @return allocated renderType ID
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
     * Check if a block is registered via {@link RenderJsonBlockModel}.
     * <p>
     * Used by {@link MixinRenderBlocks} to determine whether to skip Mixin interception
     * (blocks registered with ISBRH are dispatched directly to the ISBRH handler by Forge).
     *
     * @param block block instance
     * @return true if the block was registered via {@link #register(Block)}
     */
    public static boolean isRegistered(Block block) {
        return registeredBlocks.containsKey(block);
    }

    // ==================== ISBRH interface implementation ====================

    /**
     * Render block in world — delegates to {@link RenderDispatcher#renderBlock}.
     * <p>
     * VanillaRenderDispatcher fetches blockstate data from VMMDataLoader.stateBlockData and completes
     * drawing via BakedModelCache lazy baking + UniformRenderPipeline.
     */
    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z,
                                      Block block, int modelId, RenderBlocks renderer) {
        return RenderDispatcher.renderBlock(world, x, y, z, block, renderer);
    }

    /**
     * Block rendering in inventory/GUI — <b>deprecated/disabled</b>.
     * <p>
     * Inventory/hand/dropped rendering for block items is unified under the item pipeline
     * (Forge {@code IItemRenderer} + {@link RenderJsonItemModel}, driven by {@code items/{name}.json})
     * and no longer goes through ISBRH. This method is retained only to satisfy the interface and is a no-op.
     */
    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        // no-op: item context rendering is handled by the item pipeline (see ModelRegistry.getRegisteredItemModel).
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        // Inventory block rendering is disabled (unified under the item pipeline); return false so vanilla
        // does not attempt 3D inventory rendering.
        return false;
    }

    @Override
    public int getRenderId() {
        return this.renderTypeId;
    }
}
