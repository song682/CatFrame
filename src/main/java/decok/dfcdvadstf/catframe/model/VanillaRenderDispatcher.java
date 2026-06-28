package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.*;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Rendering dispatch extracted from {@link VanillaModelManager}.
 * <p>
 * Responsible for rendering blocks and items using the appropriate model
 * (blockstate dispatch, registered BlockStateModel, baked quads, etc.)
 */
@SideOnly(Side.CLIENT)
public class VanillaRenderDispatcher {

    public static boolean renderBlock(IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer) {
        int metadata = world.getBlockMetadata(x, y, z);

        // --- New path: check CatStateDefinition first (v0.3.0) ---
        CatStateDefinition<?> stateDef = VanillaModelManager.blockStateDefinitions.get(block);
        if (stateDef != null && block instanceof IBlockStateProvider) {
            IBlockStateProvider sp = (IBlockStateProvider) block;
            CatBlockState catState = sp.getBlockState(world, x, y, z, metadata);
            if (catState != null) {
                // Use CatBlockState.toVariantKey() for matching
                BlockstateJson bs = VanillaModelManager.stateBlockData.get(block);
                if (bs != null) {
                    return renderStateWithCatBlockState(world, x, y, z, block, catState, bs);
                }
            }
        }

        // --- New path: check registered BlockStateModel first ---
        BlockStateModel stateModel = VanillaModelManager.registeredBlockModels.get(block);
        if (stateModel != null) {
            BlockStateModelPart part = stateModel.collectParts(world, x, y, z, metadata);
            if (part != null && !part.isEmpty()) {
                // Compute rotation
                int rot = 0;
                if (VanillaModelManager.randomRotationBlocks.contains(block)) {
                    rot = 90 * (Math.abs(x + y + z) % 4);
                } else {
                    Map<Integer, Integer> rotMap = VanillaModelManager.registeredBlockRotations.get(block);
                    if (rotMap != null) {
                        Integer r = rotMap.get(metadata);
                        if (r == null) r = rotMap.get(0);
                        if (r != null) rot = r;
                    }

                }
                UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, rot);
                return true;
            }
        }

        // Dynamic state-provider path: resolve variant at render time
        if (block instanceof IBlockStateProvider && VanillaModelManager.stateBlockData.containsKey(block)) {
            return renderStateProviderBlock(world, x, y, z, block, metadata);
        }

        return false;
    }

    /**
     * Render a block using CatBlockState's variant key for blockstate matching (v0.3.0).
     */
    private static boolean renderStateWithCatBlockState(IBlockAccess world, int x, int y, int z,
                                                         Block block, CatBlockState catState,
                                                         BlockstateJson bs) {
        if (bs == null) return false;

        if (bs.variants != null) {
            String variantKey = catState.toVariantKey();
            BlockstateJson.VariantEntry entry = bs.variants.get(variantKey);
            if (entry == null) entry = bs.variants.get("normal");
            if (entry == null) return false;

            int seed = x * 3129871 ^ z * 116129781 ^ y;
            BlockstateJson.Variant variant = entry.getVariant(seed);
            if (variant == null || variant.model == null) return false;

            // [C1+W3] 旋转已在 bakeModel 中烘焙，运行时传 0
            // 走 BakedModelCache 缓存（线程安全 + 懒烘焙）
            String cacheKey = BakedModelCache.buildKey(variant.model, variant.x, variant.y);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part == null || part.isEmpty()) return false;

            UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, 0);
            return true;

        } else if (bs.multipart != null) {
            // Convert CatBlockState to property map for multipart condition matching
            java.util.Map<String, String> propMap = new java.util.HashMap<>();
            CatStateDefinition<?> def = catState.getDefinition();
            if (def != null) {
                for (Property<?> p : def.getProperties()) {
                    propMap.put(p.getName(), catState.getValue(p).toString());
                }
            }

            java.util.List<BakedQuad> allQuads = new java.util.ArrayList<>();

            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                boolean applies = (mpc.when == null) || mpc.when.matches(propMap);
                if (applies && mpc.apply != null) {
                    // [C1] 走 BakedModelCache 缓存
                    String partKey = BakedModelCache.buildKey(mpc.apply.model, mpc.apply.x, mpc.apply.y);
                    BlockStateModelPart bakedPart = BakedModelCache.INSTANCE.get(partKey);
                    if (bakedPart != null && !bakedPart.isEmpty()) {
                        allQuads.addAll(bakedPart.getAllQuads());
                    }
                }
            }

            if (allQuads.isEmpty()) return false;
            BlockStateModelPart part = BlockStateModelPart.fromQuads(allQuads);
            UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, 0);
            return true;
        }

        return false;
    }

    /**
     * Render a block using IBlockStateProvider's dynamic variant resolution.
     * Matches current block properties to blockstate variants or multipart conditions.
     */
    private static boolean renderStateProviderBlock(IBlockAccess world, int x, int y, int z, Block block, int metadata) {
        IBlockStateProvider provider = (IBlockStateProvider) block;
        BlockstateJson bs = VanillaModelManager.stateBlockData.get(block);
        if (bs == null) return false;

        Map<String, String> properties = provider.getStateProperties(world, x, y, z, metadata);
        if (properties == null) properties = Collections.emptyMap();

        if (bs.variants != null) {
            // Build variant key from properties: "key1=val1,key2=val2" (sorted)
            String variantKey = buildVariantKey(properties);
            BlockstateJson.VariantEntry entry = bs.variants.get(variantKey);

            // Fallback: try "normal" if exact match fails
            if (entry == null) entry = bs.variants.get("normal");
            if (entry == null) return false;

            // Use position-based seed for weighted random
            int seed = x * 3129871 ^ z * 116129781 ^ y;
            BlockstateJson.Variant variant = entry.getVariant(seed);
            if (variant == null || variant.model == null) return false;

            // [C1+W3] 走 BakedModelCache 缓存（线程安全 + 懒烘焙）
            String cacheKey = BakedModelCache.buildKey(variant.model, variant.x, variant.y);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part == null || part.isEmpty()) return false;

            UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, 0);
            return true;

        } else if (bs.multipart != null) {
            // Multipart: combine all matching parts
            List<BakedQuad> allQuads = new ArrayList<>();

            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                boolean applies = (mpc.when == null) || mpc.when.matches(properties);
                if (applies && mpc.apply != null) {
                    // [C1] 走 BakedModelCache 缓存
                    String partKey = BakedModelCache.buildKey(mpc.apply.model, mpc.apply.x, mpc.apply.y);
                    BlockStateModelPart bakedPart = BakedModelCache.INSTANCE.get(partKey);
                    if (bakedPart != null && !bakedPart.isEmpty()) {
                        allQuads.addAll(bakedPart.getAllQuads());
                    }
                }
            }

            if (allQuads.isEmpty()) return false;
            BlockStateModelPart part = BlockStateModelPart.fromQuads(allQuads);
            UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, 0);
            return true;
        }

        return false;
    }

    /**
     * Build a variant key string from properties map.
     * Properties are sorted alphabetically and joined as "key1=val1,key2=val2".
     */
    static String buildVariantKey(Map<String, String> properties) {
        if (properties.isEmpty()) return "normal";
        List<String> keys = new ArrayList<>(properties.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(keys.get(i)).append('=').append(properties.get(keys.get(i)));
        }
        return sb.toString();
    }

    /**
     * Render an item using its JSON model (GUI / inventory context).
     * 接收 ItemStack 以便扩展能读取 NBT / damage / 附魔等完整上下文。
     */
    public static void renderItem(ItemStack stack) {
        if (stack == null) return;
        Item item = stack.getItem();
        if (item == null) return;

        // --- Check registered ItemModel (GTNHLib-style: ItemBlock falls back to block model) ---
        ItemModel itemModel = VanillaModelRegistry.getRegisteredItemModel(item);
        if (itemModel != null) {
            itemModel.render(stack, RenderPhase.ITEM_GUI);
        }
    }

    /**
     * 旧接口兼容薄包装：无 NBT 上下文，扩展仅能看到 item+damage。
     */
    public static void renderItem(Item item, int damage) {
        if (item == null) return;
        renderItem(new ItemStack(item, 1, damage));
    }

    /**
     * Render an item's JSON model as a 3D model for in-hand rendering.
     * Unlike {@link #renderItem(ItemStack)} which is designed for 2D GUI
     * context, this method renders the model at the current GL origin with
     * proper 3D centering — the caller ({@code ItemRenderer#renderItem})
     * has already set up the hand position / rotation transforms.
     *
     * @param stack        物品栈
     * @param isFirstPerson true=第一人称, false=第三人称
     */
    public static void renderItemInHand(ItemStack stack, boolean isFirstPerson) {
        if (stack == null) return;
        Item item = stack.getItem();
        if (item == null) return;

        RenderPhase phase = isFirstPerson
                ? RenderPhase.ITEM_HAND_FIRST_PERSON
                : RenderPhase.ITEM_HAND_THIRD_PERSON;

        // --- Check registered ItemModel (GTNHLib-style: ItemBlock falls back to block model) ---
        ItemModel itemModel = VanillaModelRegistry.getRegisteredItemModel(item);
        if (itemModel != null) {
            itemModel.render(stack, phase);
        }
    }

    /**
     * 旧接口兼容 — 默认第一人称。
     */
    public static void renderItemInHand(ItemStack stack) {
        renderItemInHand(stack, true);
    }

    /**
     * 旧接口兼容薄包装。
     */
    public static void renderItemInHand(Item item, int damage) {
        if (item == null) return;
        renderItemInHand(new ItemStack(item, 1, damage), true);
    }

    /**
     * 渲染掉落物（地面上的 ItemStack）。
     * 调用方（如 Forge IItemRenderer ENTITY 路径或自定义 EntityItem 渲染器）
     * 应已设置好 GL 矩阵（entity 位置、bob 浮动等），本方法仅执行模型绘制。
     *
     * @param stack 物品栈
     */
    public static void renderDroppedItem(ItemStack stack) {
        if (stack == null) return;
        Item item = stack.getItem();
        if (item == null) return;

        RenderPhase phase = (item instanceof net.minecraft.item.ItemBlock)
                ? RenderPhase.DROPPED_BLOCK_GROUND
                : RenderPhase.DROPPED_ITEM_GROUND;

        // --- Check registered ItemModel ---
        ItemModel itemModel = VanillaModelRegistry.getRegisteredItemModel(item);
        if (itemModel != null) {
            itemModel.render(stack, phase);
        }
    }

    /**
     * 渲染落地方块（带世界上下文，用于生物群系染色等需要位置的场景）。
     *
     * @param stack 物品栈
     * @param world 世界
     * @param x     方块 X 坐标
     * @param y     方块 Y 坐标
     * @param z     方块 Z 坐标
     * @param block 方块实例
     */
    public static void renderDroppedBlock(ItemStack stack,
                                          IBlockAccess world, int x, int y, int z,
                                          Block block) {
        if (stack == null || world == null || block == null) return;
        Item item = stack.getItem();
        if (item == null) return;

        RenderPhase phase = RenderPhase.DROPPED_BLOCK_GROUND;

        // --- Check registered ItemModel ---
        ItemModel itemModel = VanillaModelRegistry.getRegisteredItemModel(item);
        if (itemModel != null) {
            itemModel.render(stack, phase);
        }
    }
}
