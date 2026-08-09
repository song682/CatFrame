package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.render.extension.BlockDestroyExtension;
import decok.dfcdvadstf.catframe.model.render.pipeline.FeatureRenderDispatcher;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderCommandBuffers;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderSubmit;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderTypeRegistry;
import decok.dfcdvadstf.catframe.model.state.*;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
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
public class RenderDispatcher {

    public static boolean renderBlock(IBlockAccess world, int x, int y, int z, Block block, RenderBlocks renderer) {
        int[] rotation = new int[1];
        BlockStateModelPart part = resolveBlockPart(world, x, y, z, block, rotation);
        if (part == null) return false;
        UniformRenderPipeline.renderBlockQuads(part, world, x, y, z, block, rotation[0]);
        return true;
    }

    /**
     * 解析方块的世界渲染模型部件，供 {@link #renderBlock} 与 {@link #renderBlockDestroy} 共用
     * （保证破坏贴图与方块世界形状完全一致）。三条解析路径按优先级依次尝试：
     * <ol>
     *   <li>CatStateDefinition + IBlockStateProvider → variants/multipart
     *       （旋转已在 bakeModel 中烘焙，rot=0）；</li>
     *   <li>已注册 BlockStateModel → {@code collectParts} + rot
     *       （randomRotation / registeredBlockRotations）；</li>
     *   <li>IBlockStateProvider + stateBlockData → variants/multipart 动态解析（rot=0）。</li>
     * </ol>
     *
     * @param world       世界
     * @param x           X 坐标
     * @param y           Y 坐标
     * @param z           Z 坐标
     * @param block       方块
     * @param rotationOut 输出 Y 轴旋转角度（0/90/180/270），无旋转时为 0
     * @return 解析出的模型部件；无可用模型时返回 null
     */
    private static BlockStateModelPart resolveBlockPart(IBlockAccess world, int x, int y, int z,
                                                        Block block, int[] rotationOut) {
        int metadata = world.getBlockMetadata(x, y, z);
        rotationOut[0] = 0;

        // --- New path: check CatStateDefinition first (v0.3.0) ---
        CatStateDefinition<?> stateDef = ModelRegistry.blockStateDefinitions.get(block);
        if (stateDef != null && block instanceof IBlockStateProvider) {
            IBlockStateProvider sp = (IBlockStateProvider) block;
            CatBlockState catState = sp.getBlockState(world, x, y, z, metadata);
            if (catState != null) {
                // Use CatBlockState.toVariantKey() for matching
                BlockstateJson bs = ModelManagerDataLoader.stateBlockData.get(block);
                if (bs != null) {
                    BlockStateModelPart part = resolveCatBlockStatePart(world, x, y, z, block, catState, bs);
                    if (part != null && !part.isEmpty()) return part;
                }
            }
        }

        // --- New path: check registered BlockStateModel first ---
        BlockStateModel stateModel = ModelRegistry.registeredBlockModels.get(block);
        if (stateModel != null) {
            BlockStateModelPart part = stateModel.collectParts(world, x, y, z, metadata);
            if (part != null && !part.isEmpty()) {
                // Compute rotation
                int rot = 0;
                if (ModelRegistry.randomRotationBlocks.contains(block)) {
                    rot = 90 * (Math.abs(x + y + z) % 4);
                } else {
                    Map<Integer, Integer> rotMap = ModelRegistry.registeredBlockRotations.get(block);
                    if (rotMap != null) {
                        Integer r = rotMap.get(metadata);
                        if (r == null) r = rotMap.get(0);
                        if (r != null) rot = r;
                    }

                }
                rotationOut[0] = rot;
                return part;
            }
        }

        // Dynamic state-provider path: resolve variant at render time
        if (block instanceof IBlockStateProvider && ModelManagerDataLoader.stateBlockData.containsKey(block)) {
            return resolveStateProviderPart(world, x, y, z, block, metadata);
        }

        return null;
    }

    /**
     * 渲染方块破坏贴花（破坏动画覆盖层，destroy_stage_0~9）。
     * <p>
     * 由 Mixin 在 {@code RenderBlocks.renderBlockUsingTexture} 中接管调用，运行于原版
     * {@code RenderGlobal.drawBlockDamageTexture} 的破坏批次 GL 上下文内（乘法混合、
     * polygon offset、blocks atlas 绑定、alpha test 均由原版完成），本方法只向当前
     * Tessellator 写入顶点：
     * <ul>
     *   <li>模型解析与 {@link #renderBlock} 完全一致（破坏贴图钉在方块自身模型上）；</li>
     *   <li>{@link RenderPhase#BLOCK_DESTROY} 阶段 + {@link BlockDestroyExtension}
     *       覆盖破坏图标并强制全亮 + 白色顶点（26.1.2 语义）；</li>
     *   <li>{@link FeatureRenderDispatcher#flushInline} 内联写出，
     *       不触碰 Tessellator / GL 状态。</li>
     * </ul>
     *
     * @param world       世界
     * @param x           X 坐标
     * @param y           Y 坐标
     * @param z           Z 坐标
     * @param block       方块
     * @param destroyIcon 当前破坏阶段图标（原版 {@code destroyBlockIcons[progress]}，0~9）
     * @return 方块是否有可用模型并已提交渲染
     */
    public static boolean renderBlockDestroy(IBlockAccess world, int x, int y, int z,
                                             Block block, IIcon destroyIcon) {
        int[] rotation = new int[1];
        BlockStateModelPart part = resolveBlockPart(world, x, y, z, block, rotation);
        if (part == null) return false;

        BlockDestroyExtension.setDestroyIcon(destroyIcon);
        RenderSubmit s = new RenderSubmit(RenderPhase.BLOCK_DESTROY, part,
                RenderTypeRegistry.BLOCK_ATLAS_SOLID, x, y, z, rotation[0],
                block, null, world, world.getBlockMetadata(x, y, z),
                null, null, false, false);
        FeatureRenderDispatcher.flushInline(s);
        return true;
    }

    /**
     * 用 CatBlockState 的 variant key 解析 blockstate 模型部件（v0.3.0），
     * 供世界渲染与破坏贴花共用；无匹配模型时返回 null。
     */
    private static BlockStateModelPart resolveCatBlockStatePart(IBlockAccess world, int x, int y, int z,
                                                                Block block, CatBlockState catState,
                                                                BlockstateJson bs) {
        if (bs == null) return null;

        // One-shot variant key validation (identity-deduped inside): invalid
        // property=value keys fall back to builtin/missing (MissingNo).
        // 一次性校验 variant 键（内部按引用去重）：无效的 属性=属性值 键回退 builtin/missing（MissingNo）。
        BlockstateKeyValidator.validate(bs, catState.getDefinition(),
                "blockstate of " + Block.blockRegistry.getNameForObject(block));

        if (bs.variants != null) {
            String variantKey = catState.toVariantKey();
            BlockstateJson.VariantEntry entry = bs.variants.get(variantKey);
            if (entry == null) entry = bs.variants.get("normal");
            if (entry == null) return null;

            int seed = x * 3129871 ^ z * 116129781 ^ y;
            BlockstateJson.Variant variant = entry.getVariant(seed);
            if (variant == null || variant.model == null) return null;

            // [C1+W3] 旋转已在 bakeModel 中烘焙，运行时传 0
            // 走 BakedModelCache 缓存（线程安全 + 懒烘焙）
            String cacheKey = BakedModelCache.buildKey(variant.model, variant.x, variant.y);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            return (part == null || part.isEmpty()) ? null : part;

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

            if (allQuads.isEmpty()) return null;
            return BlockStateModelPart.fromQuads(allQuads);
        }

        return null;
    }

    /**
     * 用 IBlockStateProvider 的动态 variant 解析模型部件（供世界渲染与破坏贴花共用）；
     * 无匹配模型时返回 null。
     */
    private static BlockStateModelPart resolveStateProviderPart(IBlockAccess world, int x, int y, int z,
                                                                Block block, int metadata) {
        IBlockStateProvider provider = (IBlockStateProvider) block;
        BlockstateJson bs = ModelManagerDataLoader.stateBlockData.get(block);
        if (bs == null) return null;

        Map<String, String> properties = provider.getStateProperties(world, x, y, z, metadata);
        if (properties == null) properties = Collections.emptyMap();

        if (bs.variants != null) {
            // Build variant key from properties: "key1=val1,key2=val2" (sorted)
            String variantKey = buildVariantKey(properties);
            BlockstateJson.VariantEntry entry = bs.variants.get(variantKey);

            // Fallback: try "normal" if exact match fails
            if (entry == null) entry = bs.variants.get("normal");
            if (entry == null) return null;

            // Use position-based seed for weighted random
            int seed = x * 3129871 ^ z * 116129781 ^ y;
            BlockstateJson.Variant variant = entry.getVariant(seed);
            if (variant == null || variant.model == null) return null;

            // [C1+W3] 走 BakedModelCache 缓存（线程安全 + 懒烘焙）
            String cacheKey = BakedModelCache.buildKey(variant.model, variant.x, variant.y);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            return (part == null || part.isEmpty()) ? null : part;

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

            if (allQuads.isEmpty()) return null;
            return BlockStateModelPart.fromQuads(allQuads);
        }

        return null;
    }

    /**
     * Build a variant key string from properties map.
     * Properties are sorted alphabetically and joined as "key1=val1,key2=val2".
     */
    public static String buildVariantKey(Map<String, String> properties) {
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
     */
    public static void renderItem(ItemStack stack) {
        if (stack == null) return;
        Item item = stack.getItem();
        if (item == null) return;

        // --- 查询已注册 IItemState 模型（方块物品若缺 items/{name}.json 则回退 builtin/missing）---
        IItemStateProvider itemModel = ModelRegistry.getRegisteredItemModel(item);
        if (itemModel != null) {
            // 开渲染作用域：物品的多个子模型（双模型/composite/多层）在作用域内累积，
            // endScope 时按注册表分组排序批量 flush（solid→translucent、单次纹理绑定）。
            RenderCommandBuffers.beginScope();
            try {
                itemModel.render(stack, RenderPhase.ITEM_GUI);
            } finally {
                RenderCommandBuffers.endScope();
            }
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

        // --- 查询已注册 IItemState 模型（方块物品若缺 items/{name}.json 则回退 builtin/missing）---
        IItemStateProvider itemModel = ModelRegistry.getRegisteredItemModel(item);
        if (itemModel != null) {
            RenderCommandBuffers.beginScope();
            try {
                itemModel.render(stack, phase);
            } finally {
                RenderCommandBuffers.endScope();
            }
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

        // --- Check registered IItemState model ---
        IItemStateProvider itemModel = ModelRegistry.getRegisteredItemModel(item);
        if (itemModel != null) {
            RenderCommandBuffers.beginScope();
            try {
                itemModel.render(stack, phase);
            } finally {
                RenderCommandBuffers.endScope();
            }
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

        // --- Check registered IItemState model ---
        IItemStateProvider itemModel = ModelRegistry.getRegisteredItemModel(item);
        if (itemModel != null) {
            RenderCommandBuffers.beginScope();
            try {
                itemModel.render(stack, phase);
            } finally {
                RenderCommandBuffers.endScope();
            }
        }
    }

    /**
     * 在物品展示框（Item Frame）中渲染物品。
     * <p>
     * 由 {@code RenderItemInFrameEvent} handler 调用，
     * 使用 {@link RenderPhase#ITEM_FIXED} 阶段，
     * 对应 JSON model 的 {@code display.fixed} transform。
     * <p>
     * GL 上下文中已由 {@code RenderItemFrame.func_82402_b} 设置好
     * 展示框朝向旋转和物品旋转，本方法仅负责模型绘制。
     * <p>
     * 对齐高版本 {@code ItemFrameRenderer.submit()}：
     * 渲染器侧额外 {@code scale(0.5)}，与 display.fixed 的 scale(0.5) 叠加后
     * 净缩放 0.25，与原版 1.7.10 RenderItem.renderInFrame 路径的
     * scale(1.25)×scale(0.25)=0.3125 接近。
     *
     * @param stack 展示框内的物品栈
     */
    public static void renderItemInFrame(ItemStack stack) {
        if (stack == null) return;
        Item item = stack.getItem();
        if (item == null) return;

        // --- 渲染器侧预变换，对齐高版本 ItemFrameRenderer.submit() + 1.7.10 RenderItem.doRender renderInFrame 偏移 ---
        // 原版 RenderItem.doRender 在 renderInFrame=true 时:
        //   T(0, 0.05, 0) × RY(-90) × S(1.25) × S(0.25) × T(-0.5)
        // CatFrame display.fixed: S(0.5) × T(-0.5)
        // 差值: T(0, 0.05, 0) × S(0.5) [忽略 RY(-90)，func_82402_b 已处理朝向]
        Matrix4d framePreTransform = new Matrix4d();
        framePreTransform.setIdentity();

        // ① T(0, 0.05, 0) — 原版 renderInFrame 的 Y 轴偏移
        Matrix4d t = new Matrix4d();
        t.setIdentity();
        t.setTranslation(new Vector3d(0, 0.15, 0));
        framePreTransform.mul(t);

        // ② S(0.5) — 渲染器侧缩放
        Matrix4d s = new Matrix4d();
        s.setIdentity();
        s.m00 = 0.5; s.m11 = 0.5; s.m22 = 0.5;
        framePreTransform.mul(s);

        // --- 查询已注册 IItemState 模型（方块物品若缺 items/{name}.json 则回退 builtin/missing）---
        IItemStateProvider itemModel = ModelRegistry.getRegisteredItemModel(item);
        if (itemModel != null) {
            RenderCommandBuffers.beginScope();
            try {
                itemModel.render(stack, RenderPhase.ITEM_FIXED, framePreTransform);
            } finally {
                RenderCommandBuffers.endScope();
            }
        }
    }
}
