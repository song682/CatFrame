package decok.dfcdvadstf.catframe.model.state.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.ParticleIconResolver;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import decok.dfcdvadstf.catframe.model.core.baking.AtlasGuard;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.core.baking.ModelBaker;
import decok.dfcdvadstf.catframe.model.state.*;
import decok.dfcdvadstf.catframe.model.state.property.Property;
import net.minecraft.block.Block;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Resident state block model — single-implementation convergence.
 * <p>
 * This class unifies the four previously parallel data-driven world block models
 * via {@link Builder} configuration:
 * <ul>
 *   <li>{@code LazyBlockstateModel} — regular variants / multipart (typed
 *       {@link BlockStateDefinition} driven properties)</li>
 *   <li>{@code LazyRedirectModel} — per-meta redirect to another blockstate ({@link IMetadataBlockstateRedirect})</li>
 *   <li>{@code StairsBlockModel} — runtime corner shape (dynamic properties + resolver)</li>
 *   <li>{@code PaneMultipartRedirectModel} — runtime connection state (dynamic properties + resolver, face-map merge)</li>
 * </ul>
 *
 * <h3>Property sources (priority)</h3>
 * <ol>
 *   <li>typed {@link BlockStateDefinition}: {@code def.getStateFromMeta(meta)} resolves the resident state,
 *       then {@link DynamicPropertyResolver} overrides dynamic properties</li>
 *   <li>no typed definition but implements {@link IBlockStateProvider}: per-render fallback calls
 *       {@link IBlockStateProvider#getStateProperties}; a non-empty property table participates in variant matching</li>
 *   <li>neither: falls back to the blockstate's {@code meta=} / numeric key / {@code normal}</li>
 * </ol>
 *
 * <h3>Variant key unification</h3>
 * Variant matching uniformly goes through {@link RenderDispatcher#buildVariantKey(Map)} (alphabetical order),
 * fully consistent with the data-driven path.
 */
@SideOnly(Side.CLIENT)
public final class ResidentStateModel implements BlockStateModel {

    /**
     * Runtime dynamic property resolver: writes properties computed from the world
     * (e.g. stairs' {@code shape}, pane's {@code north/east/south/west}) into the property map.
     */
    @FunctionalInterface
    public interface DynamicPropertyResolver {
        void resolve(IBlockAccess world, int x, int y, int z, int meta, Map<String, String> props);

        /**
         * Render veto hook: lets a resolver suppress rendering for the current world state.
         * Returning false makes collectPartsWithProps emit an empty part, which the render
         * caller treats as "nothing to draw" - the vanilla equivalent of a renderer
         * returning false. Defaults to true; existing resolvers are unaffected.
         */
        default boolean shouldRender(IBlockAccess world, int x, int y, int z, int meta) {
            return true;
        }
    }

    private final Block block;
    @Nullable
    private final BlockstateJson bs;
    @Nullable
    private final BlockStateDefinition<?> def;
    @Nullable
    private final IMetadataBlockstateRedirect redirect;
    @Nullable
    private final String namespace;
    @Nullable
    private final DynamicPropertyResolver dynamic;
    private final boolean fullModel;
    /** pane mode: per-face merge + AtlasGuard baking (aligned with the original PaneMultipartRedirectModel). */
    private final boolean connectionMultipart;

    /** Cache of the target blockstate resolved for each meta in redirect mode. */
    private final Map<Integer, BlockstateJson> redirectCache = new HashMap<>();

    private ResidentStateModel(Builder b) {
        this.block = b.block;
        this.bs = b.bs;
        this.def = b.def;
        this.redirect = b.redirect;
        this.namespace = b.namespace;
        this.dynamic = b.dynamic;
        this.fullModel = b.fullModel;
        this.connectionMultipart = b.connectionMultipart;
        // Validate variant keys against the typed state definition once at build time;
        // invalid property=value keys are invalidated → builtin/missing (MissingNo).
        BlockstateKeyValidator.validate(this.bs, this.def, describeOwner());
    }

    /** Human-readable owner for validator logs. */
    private String describeOwner() {
        String id = Block.blockRegistry.getNameForObject(block);
        return "blockstate of " + (id != null ? id : String.valueOf(block));
    }

    // ==================== Render entry ====================

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        return collectPartsWithProps(world, x, y, z, metadata).part;
    }

    @Override
    public CollectedPart collectPartsWithProps(IBlockAccess world, int x, int y, int z, int metadata) {
        BlockstateJson target = resolveTarget(metadata);
        if (target == null) return new CollectedPart(BlockStateModelPart.empty(), null);

        // Render veto: a dynamic resolver may suppress rendering for the current world
        // state (double plant upper half with a missing block below - renderBlockDoublePlant
        // parity; avoids the phantom upper half during the post-break sync window).
        if (dynamic != null && !dynamic.shouldRender(world, x, y, z, metadata)) {
            return new CollectedPart(BlockStateModelPart.empty(), null);
        }

        Map<String, String> props = resolveProps(world, x, y, z, metadata);
        if (dynamic != null) {
            if (props == null) props = new HashMap<>();
            dynamic.resolve(world, x, y, z, metadata, props);
        }

        BlockStateModelPart part;
        if (connectionMultipart) {
            part = collectConnectionMultipart(world, x, y, z, target, props);
        } else if (target.variants != null) {
            part = collectVariants(world, x, y, z, metadata, target, props);
        } else if (target.multipart != null) {
            part = collectMultipart(world, x, y, z, target, props);
        } else {
            part = BlockStateModelPart.empty();
        }

        // After matching, wrap a read-only view carried along with the submission (guards
        // against extension tampering); stays null when props is null or empty (a plain
        // block with neither def nor dynamic), at zero extra cost.
        Map<String, String> exposed = (props == null || props.isEmpty())
                ? null : Collections.unmodifiableMap(props);
        return new CollectedPart(part, exposed);
    }

    @Override
    public boolean isFullModel() {
        return fullModel;
    }

    // ==================== Particle icon resolution (model-driven) ====================

    /**
     * Particle icon resolution (model-driven, aligned with 26.1.2 BlockStateModel#particleMaterial).
     * <p>
     * Uses the same matching logic as {@link #collectPartsWithProps} but without world/coordinate
     * context (the block may already be removed when break particles are generated): variants use
     * {@code metadata * 31} to determine the seed, and resolution failure follows the same fallback
     * chain as collectVariants (meta-0 variant → builtin/missing); multipart / connection multipart
     * take the model of the **first resolvable case** (26.1.2 first-selector semantics, independent
     * of the property matching result).
     */
    @Override
    public IIcon particleIcon(int metadata) {
        BlockstateJson target = resolveTarget(metadata);
        if (target == null) return null;

        if (target.variants != null) {
            return particleFromVariant(metadata, target);
        }
        if (target.multipart != null) {
            // 26.1.2 first-selector semantics: take the first resolvable case, no property matching filter
            for (BlockstateJson.MultipartCase mpc : target.multipart) {
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

    /**
     * variants path: coordinate-less seed resolution + meta-0 / builtin/missing fallback
     * (consistent with the {@link #collectVariants} fallback chain).
     */
    @Nullable
    private IIcon particleFromVariant(int metadata, BlockstateJson target) {
        Map<String, String> props = resolveProps(null, 0, 0, 0, metadata);
        int seed = metadata * 31;
        BlockstateJson.Variant variant = resolveVariant(metadata, target, props, seed);
        if (variant == null || variant.model == null) {
            variant = resolveVariant(0, target, propsForMeta(0), seed);
        }

        BlockStateModelPart part;
        if (variant != null && variant.model != null) {
            part = BakedModelCache.INSTANCE.get(
                    BakedModelCache.buildKey(variant.model, variant.x, variant.y, variant.z));
        } else {
            part = BakedModelCache.INSTANCE.get(BakedModelCache.buildKey("builtin/missing", 0, 0, 0));
        }
        return ParticleIconResolver.fromPart(part);
    }

    // ==================== Target blockstate resolution ====================

    @Nullable
    private BlockstateJson resolveTarget(int metadata) {
        if (redirect == null) return bs;

        BlockstateJson cached = redirectCache.get(metadata);
        if (cached != null) return cached;

        String targetName = redirect.redirect(metadata);
        if (targetName == null) return null;

        BlockstateJson target = null;
        Map<String, BlockstateJson> nsMap = ModelManagerDataLoader.loadedBlockstates.get(namespace);
        if (nsMap != null) target = nsMap.get(targetName);
        if (target == null) {
            target = ModelManagerDataLoader.cachedRedirectBlockstates.get(namespace + ":" + targetName);
        }
        if (target == null) {
            target = ModelManagerDataLoader.loadSingleBlockstate(namespace, targetName);
            if (target != null) {
                if (nsMap == null) {
                    nsMap = new HashMap<>();
                    ModelManagerDataLoader.loadedBlockstates.put(namespace, nsMap);
                }
                nsMap.put(targetName, target);
            }
        }
        if (target != null) {
            // Redirect targets are resolved lazily → validate their variant keys here as well
            BlockstateKeyValidator.validate(target, def, describeOwner());
            redirectCache.put(metadata, target);
        }
        return target;
    }

    // ==================== Property sources ====================

    @Nullable
    private Map<String, String> resolveProps(IBlockAccess world, int x, int y, int z, int metadata) {
        if (def != null) {
            BlockState state = def.getStateFromMeta(metadata);
            return propsFromState(state);
        }
        // Without a typed definition, fall back to the legacy provider: call
        // getStateProperties per render, restoring IBlockStateProvider's dynamic property
        // contract — otherwise the block would be shadowed by the resident model with no
        // property source and fall into the variants first-key fallback (every metadata
        // always rendering the first variant). When the provider returns null / an empty
        // table, the existing meta= / numeric key / normal fallback chain stays unchanged.
        if (block instanceof IBlockStateProvider) {
            Map<String, String> providerProps =
                    ((IBlockStateProvider) block).getStateProperties(world, x, y, z, metadata);
            if (providerProps != null && !providerProps.isEmpty()) return providerProps;
        }
        return null;
    }

    private static Map<String, String> propsFromState(BlockState state) {
        Map<String, String> props = new HashMap<>();
        BlockStateDefinition<?> d = state.getDefinition();
        if (d != null) {
            Property<?>[] properties = d.getProperties();
            List<String> valueNames = state.getValueNames();
            for (int i = 0; i < properties.length && i < valueNames.size(); i++) {
                props.put(properties[i].getName(), valueNames.get(i));
            }
        }
        return props;
    }

    // ==================== variants matching (BakedModelCache flattening) ====================

    private BlockStateModelPart collectVariants(IBlockAccess world, int x, int y, int z,
                                                int metadata, BlockstateJson target,
                                                @Nullable Map<String, String> props) {
        // dynamic (e.g. stairs) produces weighted randomness by position; a pure mapper/def uses the meta seed (preserving prior behavior)
        int seed = (dynamic != null) ? (x * 3129871 ^ z * 116129781 ^ y) : (metadata * 31);
        BlockstateJson.Variant variant = resolveVariant(metadata, target, props, seed);
        if (variant == null || variant.model == null) {
            // Fall back to the meta 0 variant
            variant = resolveVariant(0, target, propsForMeta(0), seed);
        }

        String modelPath;
        float rotX, rotY, rotZ;
        if (variant != null && variant.model != null) {
            modelPath = variant.model;
            rotX = variant.x;
            rotY = variant.y;
            rotZ = variant.z;
        } else {
            modelPath = "builtin/missing";
            rotX = 0;
            rotY = 0;
            rotZ = 0;
        }

        String cacheKey = BakedModelCache.buildKey(modelPath, rotX, rotY, rotZ);
        BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
        return part != null ? part : BlockStateModelPart.empty();
    }

    @Nullable
    private Map<String, String> propsForMeta(int metadata) {
        if (def != null) return propsFromState(def.getStateFromMeta(metadata));
        return null;
    }

    @Nullable
    private BlockstateJson.Variant resolveVariant(int metadata, BlockstateJson target,
                                                  @Nullable Map<String, String> props, int seed) {
        String variantKey;
        if (props != null) {
            variantKey = RenderDispatcher.buildVariantKey(props);
        } else if (hasMetaVariantKeys(target)) {
            variantKey = "meta=" + metadata;
        } else {
            variantKey = findNumberKeyForMeta(target, metadata);
        }

        BlockstateJson.VariantEntry entry = target.variants.get(variantKey);
        if (entry == null) entry = target.variants.get("normal");
        if (entry == null) return null;

        return entry.getVariant(seed);
    }

    // ==================== Regular multipart matching (flattening) ====================

    private BlockStateModelPart collectMultipart(IBlockAccess world, int x, int y, int z,
                                                 BlockstateJson target,
                                                 @Nullable Map<String, String> props) {
        Map<String, String> matchProps = (props != null) ? props : Collections.emptyMap();
        List<BakedQuad> allQuads = new ArrayList<>();
        int seed = x * 3129871 ^ z * 116129781 ^ y;

        for (BlockstateJson.MultipartCase mpc : target.multipart) {
            boolean applies = (mpc.when == null) || mpc.when.matches(matchProps);
            if (applies && mpc.apply != null) {
                BlockstateJson.Variant v = mpc.apply.getVariant(seed);
                if (v != null && v.model != null) {
                    String partKey = BakedModelCache.buildKey(v.model, v.x, v.y, v.z);
                    BlockStateModelPart bakedPart = BakedModelCache.INSTANCE.get(partKey);
                    if (bakedPart != null && !bakedPart.isEmpty()) {
                        allQuads.addAll(bakedPart.getAllQuads());
                    }
                }
            }
        }

        if (allQuads.isEmpty()) return BlockStateModelPart.empty();
        return BlockStateModelPart.fromQuads(allQuads);
    }

    // ==================== Connection multipart (pane, face-map merge + AtlasGuard) ====================

    private BlockStateModelPart collectConnectionMultipart(IBlockAccess world, int x, int y, int z,
                                                           BlockstateJson target,
                                                           @Nullable Map<String, String> props) {
        if (target.multipart == null) return BlockStateModelPart.empty();
        Map<String, String> matchProps = (props != null) ? props : Collections.emptyMap();
        int seed = x * 3129871 ^ z * 116129781 ^ y;

        if (CatFrameConfig.shouldLogDebug()) {
            CatFrame.logger.info("[ResidentPane] block={} props={} cases={}",
                    Block.blockRegistry.getNameForObject(block), matchProps,
                    target.multipart.size());
        }

        Map<Direction, List<BakedQuad>> mergedFace = new EnumMap<>(Direction.class);
        List<BakedQuad> mergedGeneral = new ArrayList<>();

        for (BlockstateJson.MultipartCase mpc : target.multipart) {
            boolean applies = (mpc.when == null) || mpc.when.matches(matchProps);
            if (applies && mpc.apply != null) {
                BlockstateJson.Variant v = mpc.apply.getVariant(seed);
                if (v != null && v.model != null) {
                    BlockStateModelPart part = AtlasGuard.gate(
                            ModelBaker.bake(v.model, v.x, v.y, v.z), v.model);
                    if (part != null) {
                        for (Direction dir : Direction.values()) {
                            mergedFace.computeIfAbsent(dir, k -> new ArrayList<>())
                                    .addAll(part.getQuads(dir));
                        }
                        mergedGeneral.addAll(part.getGeneralQuads());
                    }
                }
            }
        }

        if (mergedGeneral.isEmpty() && mergedFace.isEmpty()) {
            return BlockStateModelPart.empty();
        }
        return BlockStateModelPart.fromFaceMap(mergedFace, mergedGeneral);
    }

    // ==================== Helpers ====================

    private static boolean hasMetaVariantKeys(BlockstateJson target) {
        if (target.variants == null) return false;
        for (String key : target.variants.keySet()) {
            if (key != null && key.startsWith("meta=")) {
                try {
                    Integer.parseInt(key.substring(5));
                    return true;
                } catch (NumberFormatException ignored) { }
            }
        }
        return false;
    }

    private static String findNumberKeyForMeta(BlockstateJson target, int meta) {
        if (target.variants == null) return "normal";
        String metaStr = String.valueOf(meta);
        if (target.variants.containsKey(metaStr)) return metaStr;
        if (target.variants.containsKey("normal")) return "normal";
        return target.variants.keySet().stream().findFirst().orElse("normal");
    }

    // ==================== Builder ====================

    public static Builder builder(Block block) {
        return new Builder(block);
    }

    public static final class Builder {
        private final Block block;
        private BlockstateJson bs;
        private BlockStateDefinition<?> def;
        private IMetadataBlockstateRedirect redirect;
        private String namespace;
        private DynamicPropertyResolver dynamic;
        private boolean fullModel = true;
        private boolean connectionMultipart = false;

        private Builder(Block block) {
            this.block = block;
        }

        public Builder blockstate(BlockstateJson bs) {
            this.bs = bs;
            return this;
        }

        public Builder stateDefinition(BlockStateDefinition<?> def) {
            this.def = def;
            return this;
        }

        public Builder redirect(IMetadataBlockstateRedirect redirect, String namespace) {
            this.redirect = redirect;
            this.namespace = namespace;
            return this;
        }

        public Builder dynamic(DynamicPropertyResolver dynamic) {
            this.dynamic = dynamic;
            return this;
        }

        public Builder fullModel(boolean fullModel) {
            this.fullModel = fullModel;
            return this;
        }

        /** Enables pane connection multipart mode (face-map merge + AtlasGuard, isFullModel=false). */
        public Builder connectionMultipart() {
            this.connectionMultipart = true;
            this.fullModel = false;
            return this;
        }

        public ResidentStateModel build() {
            return new ResidentStateModel(this);
        }
    }
}
