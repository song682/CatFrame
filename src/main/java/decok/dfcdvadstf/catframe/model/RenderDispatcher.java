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
        int metadata = world.getBlockMetadata(x, y, z);
        ResolvedModel rm = resolveBlockModel(world, x, y, z, block, metadata);
        if (rm == null) return false;
        UniformRenderPipeline.renderBlockQuads(rm.part, world, x, y, z, block, rm.rot,
                RenderPhase.BLOCK_WORLD, metadata, rm.blockstateProps);
        return true;
    }

    /**
     * Resolve block's world-rendering model (shared by {@link #renderBlock} and {@link #renderBlockDestroy}).
     * <p>
     * Preserves exact three-path semantics and order from pre-refactor renderBlock:
     * <ol>
     *   <li>CatStateDefinition path: hit is final — parse failure returns null directly,
     *       no further path attempts (matches pre-refactor {@code return renderStateWithCatBlockState(...)}
     *       direct return semantics);</li>
     *   <li>registeredBlockModels path: collectParts non-empty → part + rotation;</li>
     *   <li>IBlockStateProvider dynamic path: variant / multipart resolution.</li>
     * </ol>
     * Resolves the block's world-rendering model, shared by renderBlock and
     * renderBlockDestroy; keeps the exact three-path semantics of the old renderBlock.
     *
     * @return resolution result (part + Y-axis rotation), null if no model
     */
    private static ResolvedModel resolveBlockModel(IBlockAccess world, int x, int y, int z,
                                                   Block block, int metadata) {
        // --- Path 1: check CatStateDefinition first (v0.3.0) ---
        CatStateDefinition<?> stateDef = ModelRegistry.blockStateDefinitions.get(block);
        if (stateDef != null && block instanceof IBlockStateProvider) {
            IBlockStateProvider sp = (IBlockStateProvider) block;
            CatBlockState catState = sp.getBlockState(world, x, y, z, metadata);
            if (catState != null) {
                // Use CatBlockState.toVariantKey() for matching
                BlockstateJson bs = ModelManagerDataLoader.stateBlockData.get(block);
                if (bs != null) {
                    return renderStateWithCatBlockState(world, x, y, z, block, catState, bs);
                }
            }
        }

        // --- Path 2: check registered BlockStateModel ---
        BlockStateModel stateModel = ModelRegistry.registeredBlockModels.get(block);
        if (stateModel != null) {
            BlockStateModel.CollectedPart collected = stateModel.collectPartsWithProps(world, x, y, z, metadata);
            BlockStateModelPart part = collected != null ? collected.part : null;
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
                return new ResolvedModel(part, rot, collected.blockstateProps);
            }
        }

        // --- Path 3: dynamic state-provider ---
        if (block instanceof IBlockStateProvider && ModelManagerDataLoader.stateBlockData.containsKey(block)) {
            return renderStateProviderBlock(world, x, y, z, block, metadata);
        }

        return null;
    }

    /**
     * Renders the block's destroy decal overlay.
     * <p>
     * Reuses the exact model resolution of {@link #renderBlock}, but submits under
     * {@link RenderPhase#BLOCK_DESTROY} phase: directly calls
     * {@link FeatureRenderDispatcher#flushInline} (same inline semantics as BLOCK_WORLD,
     * writes vertices only), never goes through {@link RenderCommandBuffers#submit} —
     * that path for non-BLOCK_WORLD enters flushGroup (startDrawingQuads/draw/bindTexture),
     * corrupting the vanilla {@code RenderGlobal.drawBlockDamageTexture}'s established
     * startDrawingQuads batch context.
     * <p>
     * Destroy icon (destroy_stage_0~9) injected as IIcon into
     * {@link BlockDestroyExtension#setCurrentIcon}, overwritten to each quad by extension
     * chain during BLOCK_DESTROY phase; GL state (multiplicative blend 774/768, polygon
     * offset, alpha test) all managed by vanilla drawBlockDamageTexture, this method
     * only writes vertices.
     *
     * @param world       world
     * @param x           block X coordinate
     * @param y           block Y coordinate
     * @param z           block Z coordinate
     * @param block       block instance
     * @param destroyIcon current destroy phase IIcon (destroy_stage_0~9)
     */
    public static void renderBlockDestroy(IBlockAccess world, int x, int y, int z,
                                          Block block, IIcon destroyIcon) {
        int metadata = world.getBlockMetadata(x, y, z);
        ResolvedModel rm = resolveBlockModel(world, x, y, z, block, metadata);
        if (rm == null) return;
        // Destroy decal brightness handled entirely by BlockDestroyExtension (always full-bright 15728880, cracks visible in dark);
        // submit side passes no brightness (-1 semantics see RenderSubmit).
        RenderSubmit s = new RenderSubmit(RenderPhase.BLOCK_DESTROY, rm.part,
                RenderTypeRegistry.BLOCK_ATLAS_DESTROY, x, y, z, rm.rot,
                block, null, world, metadata, null, null, false, false,
                rm.blockstateProps, null);
        BlockDestroyExtension.setCurrentIcon(destroyIcon);
        try {
            FeatureRenderDispatcher.flushInline(s);
        } finally {
            BlockDestroyExtension.clearCurrentIcon();
        }
    }

    /**
     * Resolve a block part using CatBlockState's variant key for blockstate matching (v0.3.0).
     * <p>
     * Refactored from renderStateWithCatBlockState: only resolves model part, no rendering
     * (rendering submitted by caller per phase). Only resolves the model part; rendering is
     * left to the caller (renderBlock / renderBlockDestroy).
     *
     * @return resolution result (part + state properties); null if no renderable model
     */
    private static ResolvedModel renderStateWithCatBlockState(IBlockAccess world, int x, int y, int z,
                                                              Block block, CatBlockState catState,
                                                              BlockstateJson bs) {
        if (bs == null) return null;

        // One-shot variant key validation (identity-deduped inside): invalid
        // property=value keys fall back to builtin/missing (MissingNo).
        // One-shot variant key validation (identity-deduped inside): invalid property=value keys fall back to builtin/missing (MissingNo).
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

            // [C1+W3] Rotation already baked in bakeModel, pass 0 at runtime
            // Go through BakedModelCache (thread-safe + lazy bake)
            String cacheKey = BakedModelCache.buildKey(variant.model, variant.x, variant.y, variant.z);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part == null || part.isEmpty()) return null;

            // Extract typed properties (consistent with toVariantKey serialization semantics) carried with submission
            Map<String, String> propMap = propsFromCatState(catState);
            return new ResolvedModel(part, 0,
                    propMap.isEmpty() ? null : Collections.unmodifiableMap(propMap));

        } else if (bs.multipart != null) {
            // Convert CatBlockState to property map for multipart condition matching
            Map<String, String> propMap = propsFromCatState(catState);

            java.util.List<BakedQuad> allQuads = new java.util.ArrayList<>();
            int seed = x * 3129871 ^ z * 116129781 ^ y;

            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                boolean applies = (mpc.when == null) || mpc.when.matches(propMap);
                if (applies && mpc.apply != null) {
                    BlockstateJson.Variant v = mpc.apply.getVariant(seed);
                    if (v != null && v.model != null) {
                        // [C1] Go through BakedModelCache
                        String partKey = BakedModelCache.buildKey(v.model, v.x, v.y, v.z);
                        BlockStateModelPart bakedPart = BakedModelCache.INSTANCE.get(partKey);
                        if (bakedPart != null && !bakedPart.isEmpty()) {
                            allQuads.addAll(bakedPart.getAllQuads());
                        }
                    }
                }
            }

            if (allQuads.isEmpty()) return null;
            return new ResolvedModel(BlockStateModelPart.fromQuads(allQuads), 0,
                    propMap.isEmpty() ? null : Collections.unmodifiableMap(propMap));
        }

        return null;
    }

    /**
     * Extract all typed properties from CatBlockState as name→value string map
     * (consistent with {@link CatBlockState#toVariantKey()} serialization semantics).
     */
    private static Map<String, String> propsFromCatState(CatBlockState catState) {
        Map<String, String> propMap = new java.util.HashMap<>();
        CatStateDefinition<?> def = catState.getDefinition();
        if (def != null) {
            for (Property<?> p : def.getProperties()) {
                propMap.put(p.getName(), catState.getValue(p).toString());
            }
        }
        return propMap;
    }

    /**
     * Resolve a block part using IBlockStateProvider's dynamic variant resolution.
     * Matches current block properties to blockstate variants or multipart conditions.
     * <p>
     * Refactored from renderStateProviderBlock: only resolves model part, no rendering
     * (rendering submitted by caller per phase). Only resolves the model part; rendering is
     * left to the caller (renderBlock / renderBlockDestroy).
     *
     * @return resolution result (part + state properties); null if no renderable model
     */
    private static ResolvedModel renderStateProviderBlock(IBlockAccess world, int x, int y, int z, Block block, int metadata) {
        IBlockStateProvider provider = (IBlockStateProvider) block;
        BlockstateJson bs = ModelManagerDataLoader.stateBlockData.get(block);
        if (bs == null) return null;

        Map<String, String> properties = provider.getStateProperties(world, x, y, z, metadata);
        if (properties == null) properties = Collections.emptyMap();
        // After match, wrap read-only view carried with submission (prevent extension tampering); keep null when props empty
        Map<String, String> exposed = properties.isEmpty()
                ? null : Collections.unmodifiableMap(properties);

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

            // [C1+W3] Go through BakedModelCache (thread-safe + lazy bake)
            String cacheKey = BakedModelCache.buildKey(variant.model, variant.x, variant.y, variant.z);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part == null || part.isEmpty()) return null;

            return new ResolvedModel(part, 0, exposed);

        } else if (bs.multipart != null) {
            // Multipart: combine all matching parts
            List<BakedQuad> allQuads = new ArrayList<>();
            int seed = x * 3129871 ^ z * 116129781 ^ y;

            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                boolean applies = (mpc.when == null) || mpc.when.matches(properties);
                if (applies && mpc.apply != null) {
                    BlockstateJson.Variant v = mpc.apply.getVariant(seed);
                    if (v != null && v.model != null) {
                        // [C1] Go through BakedModelCache
                        String partKey = BakedModelCache.buildKey(v.model, v.x, v.y, v.z);
                        BlockStateModelPart bakedPart = BakedModelCache.INSTANCE.get(partKey);
                        if (bakedPart != null && !bakedPart.isEmpty()) {
                            allQuads.addAll(bakedPart.getAllQuads());
                        }
                    }
                }
            }

            if (allQuads.isEmpty()) return null;
            return new ResolvedModel(BlockStateModelPart.fromQuads(allQuads), 0, exposed);
        }

        return null;
    }

    /**
     * Model resolution result: render part + Y-axis rotation + per-position blockstate
     * properties computed during matching (nullable).
     */
    private static final class ResolvedModel {
        final BlockStateModelPart part;
        final float rot;
        /** Blockstate properties constructed during matching (immutable view), null when no properties. */
        final Map<String, String> blockstateProps;

        ResolvedModel(BlockStateModelPart part, float rot, Map<String, String> blockstateProps) {
            this.part = part;
            this.rot = rot;
            this.blockstateProps = blockstateProps;
        }
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

        // --- Query registered IItemState model (block items without items/{name}.json fall back to builtin/missing) ---
        IItemStateProvider itemModel = ModelRegistry.getRegisteredItemModel(item);
        if (itemModel != null) {
            // Open render scope: item's multiple sub-models (dual-model/composite/multi-layer) accumulate in scope,
            // batch flush at endScope sorted by registry key (solid→translucent, single texture bind).
            RenderCommandBuffers.beginScope();
            try {
                itemModel.render(stack, RenderPhase.ITEM_GUI);
            } finally {
                RenderCommandBuffers.endScope();
            }
        }
    }

    /**
     * Legacy interface compat thin wrapper: no NBT context, extensions only see item+damage.
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
     * @param stack        item stack
     * @param isFirstPerson true=first person, false=third person
     */
    public static void renderItemInHand(ItemStack stack, boolean isFirstPerson) {
        if (stack == null) return;
        Item item = stack.getItem();
        if (item == null) return;

        RenderPhase phase = isFirstPerson
                ? RenderPhase.ITEM_HAND_FIRST_PERSON
                : RenderPhase.ITEM_HAND_THIRD_PERSON;

        // --- Query registered IItemState model (block items without items/{name}.json fall back to builtin/missing) ---
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
     * Legacy interface compat — defaults to first person.
     */
    public static void renderItemInHand(ItemStack stack) {
        renderItemInHand(stack, true);
    }

    /**
     * Legacy interface compat thin wrapper.
     */
    public static void renderItemInHand(Item item, int damage) {
        if (item == null) return;
        renderItemInHand(new ItemStack(item, 1, damage), true);
    }

    /**
     * Render dropped item (ItemStack on ground).
     * Caller (e.g. Forge IItemRenderer ENTITY path or custom EntityItem renderer)
     * should have set up GL matrices (entity position, bob, etc.), this method only draws the model.
     *
     * @param stack item stack
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
     * Render dropped block (with world context, for biome tinting etc. needing position).
     *
     * @param stack item stack
     * @param world world
     * @param x     block X coordinate
     * @param y     block Y coordinate
     * @param z     block Z coordinate
     * @param block block instance
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
     * Render item in Item Frame.
     * <p>
     * Called by {@code RenderItemInFrameEvent} handler,
     * uses {@link RenderPhase#ITEM_FIXED} phase,
     * corresponds to JSON model's {@code display.fixed} transform.
     * <p>
     * GL context already set up by {@code RenderItemFrame.func_82402_b}
     * with frame facing rotation and item rotation, this method only draws the model.
     * <p>
     * Aligned with modern {@code ItemFrameRenderer.submit()}:
     * renderer-side extra {@code scale(0.5)}, combined with display.fixed's scale(0.5)
     * gives net scale 0.25, close to vanilla 1.7.10 RenderItem.renderInFrame path's
     * scale(1.25)×scale(0.25)=0.3125.
     *
     * @param stack item stack in frame
     */
    public static void renderItemInFrame(ItemStack stack) {
        if (stack == null) return;
        Item item = stack.getItem();
        if (item == null) return;

        // --- Renderer-side pre-transform, aligned with modern ItemFrameRenderer.submit() + 1.7.10 RenderItem.doRender renderInFrame offset ---
        // Vanilla RenderItem.doRender when renderInFrame=true:
        //   T(0, 0.05, 0) × RY(-90) × S(1.25) × S(0.25) × T(-0.5)
        // CatFrame display.fixed: S(0.5) × T(-0.5)
        // Delta: T(0, 0.05, 0) × S(0.5) [ignore RY(-90), func_82402_b handles facing]
        Matrix4d framePreTransform = new Matrix4d();
        framePreTransform.setIdentity();

        // (1) T(0, 0.05, 0) — vanilla renderInFrame Y-axis offset
        Matrix4d t = new Matrix4d();
        t.setIdentity();
        t.setTranslation(new Vector3d(0, 0.15, 0));
        framePreTransform.mul(t);

        // (2) S(0.5) — renderer-side scale
        Matrix4d s = new Matrix4d();
        s.setIdentity();
        s.m00 = 0.5; s.m11 = 0.5; s.m22 = 0.5;
        framePreTransform.mul(s);

        // --- Query registered IItemState model (block items without items/{name}.json fall back to builtin/missing) ---
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
