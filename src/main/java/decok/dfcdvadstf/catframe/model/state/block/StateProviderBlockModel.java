package decok.dfcdvadstf.catframe.model.state.block;

import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.ParticleIconResolver;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import decok.dfcdvadstf.catframe.model.core.baking.AtlasGuard;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake;
import decok.dfcdvadstf.catframe.model.core.baking.ModelBaker;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import java.util.Map;


/**
 * Dynamic model delegating to IBlockStateProvider. Each render calls
 * {@link IBlockStateProvider#getStateProperties} to get properties, then matches
 * the variant keys in the blockstate JSON.
 *
 * <p>Corresponds to the 1.21.5 VariantSelector + IBlockStateProvider combination.
 */
public class StateProviderBlockModel implements BlockStateModel {
    private final IBlockStateProvider provider;
    private final BlockstateJson blockstate;
    private final String fallbackModelPath;

    /**
     * @param provider          the block implementing IBlockStateProvider
     * @param blockstate        the loaded blockstate JSON
     * @param fallbackModelPath fallback model path (e.g. "block/stone")
     */
    public StateProviderBlockModel(IBlockStateProvider provider,
                                   BlockstateJson blockstate,
                                   String fallbackModelPath) {
        this.provider = provider;
        this.blockstate = blockstate;
        this.fallbackModelPath = fallbackModelPath;
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        return collectPartsWithProps(world, x, y, z, metadata).part;
    }

    @Override
    public BlockStateModel.CollectedPart collectPartsWithProps(IBlockAccess world, int x, int y, int z, int metadata) {
        if (blockstate == null) return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);

        Map<String, String> properties = provider.getStateProperties(world, x, y, z, metadata);
        if (properties == null) properties = java.util.Collections.emptyMap();

        BlockStateModelPart part;
        if (blockstate.variants != null) {
            String variantKey = RenderDispatcher.buildVariantKey(properties);
            BlockstateJson.VariantEntry entry = blockstate.variants.get(variantKey);
            if (entry == null) entry = blockstate.variants.get("normal");
            if (entry == null) return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);

            int seed = x * 3129871 ^ z * 116129781 ^ y;
            BlockstateJson.Variant variant = entry.getVariant(seed);
            if (variant == null || variant.model == null) return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);

            // Bake directly (VanillaModelManager has a cache)
            part = AtlasGuard.gate(ModelBaker.bake(variant.model), variant.model);
            if (part == null || part.isEmpty()) return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);

        } else if (blockstate.multipart != null) {
            // Multipart: merge all matching parts
            Map<Direction, java.util.List<JsonModelBake.BakedQuad>> mergedFace
                    = new java.util.EnumMap<>(Direction.class);
            java.util.List<JsonModelBake.BakedQuad> mergedGeneral = new java.util.ArrayList<>();

            for (BlockstateJson.MultipartCase mpc : blockstate.multipart) {
                boolean applies = (mpc.when == null) || mpc.when.matches(properties);
                if (applies && mpc.apply != null) {
                    BlockstateJson.Variant v = mpc.apply.getVariant(0);
                    if (v != null && v.model != null) {
                        BlockStateModelPart subPart = AtlasGuard.gate(ModelBaker.bake(v.model), v.model);
                        if (subPart != null) {
                            for (Direction dir : Direction.values()) {
                                mergedFace.computeIfAbsent(dir, k -> new java.util.ArrayList<>())
                                        .addAll(subPart.getQuads(dir));
                            }
                            mergedGeneral.addAll(subPart.getGeneralQuads());
                        }
                    }
                }
            }

            if (!mergedGeneral.isEmpty() || !mergedFace.isEmpty()) {
                part = BlockStateModelPart.fromFaceMap(mergedFace, mergedGeneral);
            } else {
                return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);
            }
        } else {
            return new BlockStateModel.CollectedPart(BlockStateModelPart.empty(), null);
        }

        // After matching, wrap a read-only view carried along with the submission (guards against extension tampering); stays null when properties are empty, at zero extra cost.
        Map<String, String> exposed = properties.isEmpty()
                ? null : java.util.Collections.unmodifiableMap(properties);
        return new BlockStateModel.CollectedPart(part, exposed);
    }

    // ==================== Particle icon resolution (model-driven) ====================

    /**
     * Particle icon resolution (model-driven). Provider properties are resolved with a
     * null world (the collectParts contract allows null for item rendering scenarios),
     * with the seed {@code metadata * 31}; multipart takes the first resolvable case
     * (26.1.2 first-selector semantics).
     */
    @Override
    public IIcon particleIcon(int metadata) {
        if (blockstate == null) return null;

        Map<String, String> properties = provider.getStateProperties(null, 0, 0, 0, metadata);
        if (properties == null) properties = java.util.Collections.emptyMap();

        if (blockstate.variants != null) {
            String variantKey = RenderDispatcher.buildVariantKey(properties);
            BlockstateJson.VariantEntry entry = blockstate.variants.get(variantKey);
            if (entry == null) entry = blockstate.variants.get("normal");
            if (entry == null) return null;

            BlockstateJson.Variant variant = entry.getVariant(metadata * 31);
            if (variant == null || variant.model == null) return null;

            BlockStateModelPart part = BakedModelCache.INSTANCE.get(
                    BakedModelCache.buildKey(variant.model, variant.x, variant.y, variant.z));
            return ParticleIconResolver.fromPart(part);
        }

        if (blockstate.multipart != null) {
            // 26.1.2 first-selector semantics: take the first resolvable case, no property matching filter
            for (BlockstateJson.MultipartCase mpc : blockstate.multipart) {
                if (mpc.apply == null) continue;
                BlockstateJson.Variant v = mpc.apply.getVariant(metadata * 31);
                if (v != null && v.model != null) {
                    BlockStateModelPart part = BakedModelCache.INSTANCE.get(
                            BakedModelCache.buildKey(v.model, v.x, v.y, v.z));
                    return ParticleIconResolver.fromPart(part);
                }
            }
        }
        return null;
    }
}
