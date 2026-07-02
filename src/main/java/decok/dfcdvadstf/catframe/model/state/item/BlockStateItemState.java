package decok.dfcdvadstf.catframe.model.state.item;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.block.SingleBlockModel;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 方块状态物品模型包装器。
 * <p>
 * 将 {@link BlockStateModel} 的烘焙 quads 复用为物品渲染，
 * 类似于 1.21.5 的 CuboidItemModelWrapper。
 * <p>
 * 物品渲染时，通过 {@link UniformRenderPipeline#renderItemQuads} 完成
 * 扩展链处理和 Tessellator 提交。
 * <p>
 * 支持 JSON model 的 {@code display} transforms，根据 {@link RenderPhase}
 * 自动应用对应的变换（gui / thirdperson_righthand）。
 */
public class BlockStateItemState implements IItemStateProvider {

    /**
     * 在物品栏/GUI/手持中应渲染为扁平 2D item 的原版方块。
     * <p>
     * 这些方块在 1.7.10 原版中使用扁平 item 纹理，而非 3D 方块模型。
     * 模型路径由 Item 注册名自动推导（minecraft:sapling → minecraft:item/sapling）。
     */
    private static final Set<Block> VANILLA_FLAT_BLOCKS = new HashSet<>();

    static {
        // 树苗类
        VANILLA_FLAT_BLOCKS.add(Blocks.sapling);
        // 红石元件
        VANILLA_FLAT_BLOCKS.add(Blocks.redstone_wire);
        VANILLA_FLAT_BLOCKS.add(Blocks.redstone_torch);
        VANILLA_FLAT_BLOCKS.add(Blocks.unpowered_repeater);
        VANILLA_FLAT_BLOCKS.add(Blocks.powered_repeater);
        // 门
        VANILLA_FLAT_BLOCKS.add(Blocks.wooden_door);
        VANILLA_FLAT_BLOCKS.add(Blocks.iron_door);
        // 功能性方块
        VANILLA_FLAT_BLOCKS.add(Blocks.hopper);
        VANILLA_FLAT_BLOCKS.add(Blocks.cauldron);
        VANILLA_FLAT_BLOCKS.add(Blocks.brewing_stand);
        VANILLA_FLAT_BLOCKS.add(Blocks.cake);
        VANILLA_FLAT_BLOCKS.add(Blocks.flower_pot);
        // 触发/交互元件
        VANILLA_FLAT_BLOCKS.add(Blocks.lever);
        // 照明/梯子
        VANILLA_FLAT_BLOCKS.add(Blocks.torch);
        VANILLA_FLAT_BLOCKS.add(Blocks.ladder);
        // 铁轨
        VANILLA_FLAT_BLOCKS.add(Blocks.rail);
        VANILLA_FLAT_BLOCKS.add(Blocks.golden_rail);
        VANILLA_FLAT_BLOCKS.add(Blocks.detector_rail);
        VANILLA_FLAT_BLOCKS.add(Blocks.activator_rail);
        // 其他
        VANILLA_FLAT_BLOCKS.add(Blocks.tripwire_hook);
        VANILLA_FLAT_BLOCKS.add(Blocks.reeds);
        VANILLA_FLAT_BLOCKS.add(Blocks.glass_pane);
    }

    private final BlockStateModel blockModel;
    private final Map<String, ModelJson.DisplayTransform> display;

    public BlockStateItemState(BlockStateModel blockModel) {
        this(blockModel, null);
    }

    public BlockStateItemState(BlockStateModel blockModel, Map<String, ModelJson.DisplayTransform> display) {
        this.blockModel = blockModel;
        this.display = display;
    }

    public BlockStateItemState(BlockStateModelPart part) {
        this(part, null);
    }

    public BlockStateItemState(BlockStateModelPart part, Map<String, ModelJson.DisplayTransform> display) {
        this.blockModel = new SingleBlockModel(part);
        this.display = display;
    }

    /**
     * 从预烘焙的 quad 列表直接构造（无需经过 BlockStateModel 调度）。
     */
    public static BlockStateItemState fromQuads(List<BakedQuad> quads) {
        return new BlockStateItemState(BlockStateModelPart.fromQuads(quads));
    }

    public static BlockStateItemState fromQuads(List<BakedQuad> quads, Map<String, ModelJson.DisplayTransform> display) {
        return new BlockStateItemState(BlockStateModelPart.fromQuads(quads), display);
    }

    /**
     * 解析模型路径的 display transforms。
     */
    @javax.annotation.Nullable
    private static Map<String, ModelJson.DisplayTransform> resolveDisplay(String modelPath) {
        if (modelPath == null) return null;
        ModelJson resolved = ModelResolver.resolve(modelPath);
        return resolved != null ? resolved.display : null;
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase) {
        if (stack != null && stack.getItem() instanceof ItemBlock) {
            Block block = ((ItemBlock) stack.getItem()).field_150939_a;

            // 检测 IBlockStateProvider.inventoryFlatModel() → 模组方块扁平 2D
            if (block instanceof IBlockStateProvider) {
                String flatModel = ((IBlockStateProvider) block).inventoryFlatModel();
                if (flatModel != null) {
                    renderFlatModel(stack, phase, flatModel);
                    return;
                }
            }

            // 检测原版方块扁平渲染白名单
            if (VANILLA_FLAT_BLOCKS.contains(block)) {
                Item item = stack.getItem();
                String itemName = Item.itemRegistry.getNameForObject(item);
                if (itemName != null) {
                    // minecraft:sapling → minecraft:item/sapling
                    String flatModel = itemName.replace(":", ":item/");
                    renderFlatModel(stack, phase, flatModel);
                    return;
                }
            }
        }

        // 默认：使用 3D 方块模型渲染
        int damage = stack.getItemDamage();
        BlockStateModelPart part = blockModel.collectParts(null, 0, 0, 0, damage);
        if (part == null || part.isEmpty()) return;

        UniformRenderPipeline.renderItemQuads(part, stack, phase, display);
    }

    /**
     * 渲染扁平 item 模型。
     */
    private void renderFlatModel(ItemStack stack, RenderPhase phase, String modelPath) {
        String cacheKey = BakedModelCache.buildKey(modelPath, 0, 0);
        BlockStateModelPart flatPart = BakedModelCache.INSTANCE.get(cacheKey);
        if (flatPart != null && !flatPart.isEmpty()) {
            Map<String, ModelJson.DisplayTransform> flatDisplay = resolveDisplay(modelPath);
            UniformRenderPipeline.renderItemQuads(flatPart, stack, phase, flatDisplay);
        }
    }
}
