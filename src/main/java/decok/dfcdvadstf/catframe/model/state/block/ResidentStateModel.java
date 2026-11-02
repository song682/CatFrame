package decok.dfcdvadstf.catframe.model.state.block;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.adapter.vanilla.model.ChunkProbeCache;
import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.IBlockStateProvider;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.ParticleIconResolver;
import decok.dfcdvadstf.catframe.model.RenderDispatcher;
import decok.dfcdvadstf.catframe.model.core.baking.AtlasGuard;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
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
         * Typed re-resolution: folds this resolver's string output into the canonical (interned)
         * state instance through the O(1) {@link BlockState#setValue} neighbor jumps, so the memo
         * can key on the state reference itself (pointer equality, the 26.1.2 {@code modelByState}
         * identity-hash equivalent).
         * <p>
         * The default implementation runs {@link #resolve} (single source of truth for the
         * computation), then maps every name/value pair back onto the definition's domain.
         * Returns {@code null} when there is no base state, or when any name / value lies outside
         * the definition — the caller then falls back to the legacy string-code memo path, so
         * behavior is never changed by a resolver that stays in-domain only.
         */
        @SuppressWarnings({"rawtypes", "unchecked"})
        default BlockState resolveState(IBlockAccess world, int x, int y, int z, int meta, BlockState base) {
            if (base == null) return null;
            BlockStateDefinition<?> def = base.getDefinition();
            if (def == null) return null;
            Map<String, String> out = new HashMap<>();
            resolve(world, x, y, z, meta, out);
            BlockState state = base;
            for (Map.Entry<String, String> e : out.entrySet()) {
                Property prop = def.getPropertyByName(e.getKey());
                if (prop == null) return null;
                Comparable value = prop.getValueByName(e.getValue());
                if (value == null) return null;
                state = state.setValue(prop, value);
            }
            return state;
        }

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

    /**
     * Static-layer memo: full state index (joinCode) → merged part, bucketed per target
     * blockstate instance (identity-keyed, so redirect targets — e.g. the 16 stained pane
     * colors — stay separate). Render thread only; cleared when the bake-cache generation
     * changes (resource reload / re-stitch).
     * <p>
     * Legacy lane: only reached when {@link DynamicPropertyResolver#resolveState} is unavailable
     * (no definition) or steps out of the property domain; the canonical-state memo below is
     * the fast lane for in-domain typed resolvers.
     */
    private final Map<BlockstateJson, Map<Integer, BlockStateModelPart>> partMemo = new IdentityHashMap<>();
    private long partMemoGeneration = -1;
    /**
     * Canonical-state memo: per target blockstate, the interned {@link BlockState} reference
     * (full property combination, reached via O(1) setValue jumps) → merged part + props
     * snapshot. Identity-keyed lookup is the pointer-equality equivalent of 26.1.2's
     * {@code IdentityHashMap<BlockState, BlockStateModel>}: no string re-parsing, no code
     * encoding, no boxing on the hit path. The props snapshot is determined by the canonical
     * instance, so sharing its read-only view across positions cannot bleed state.
     */
    private final Map<BlockstateJson, IdentityHashMap<BlockState, MemoEntry>> stateMemo = new IdentityHashMap<>();
    /** Per-target memo eligibility (JSON-structure property, stable across reloads). */
    private final Map<BlockstateJson, Boolean> memoEligibility = new IdentityHashMap<>();

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

        // Compile-window probe scratch: inside a chunk build (mixin-opened window) neighbor
        // reads of this resolution go through the per-thread (Block, meta) cache; outside a
        // window wrap returns the world untouched. Only dynamic resolvers probe the world.
        if (dynamic != null && world != null) world = ChunkProbeCache.wrap(world);

        // Render veto: a dynamic resolver may suppress rendering for the current world
        // state (double plant upper half with a missing block below - renderBlockDoublePlant
        // parity; avoids the phantom upper half during the post-break sync window).
        if (dynamic != null && !dynamic.shouldRender(world, x, y, z, metadata)) {
            return new CollectedPart(BlockStateModelPart.empty(), null);
        }

        // Typed fast lane: with a definition present, fold the resolver output into the canonical
        // (interned) state instance and key the memo by that reference. A miss builds the part once
        // per distinct combination; every later position with the same combination hits the identity
        // lookup (no props map is even built on a hit). Out-of-domain resolvers return null and
        // keep the legacy string-code lane below.
        if (def != null && dynamic != null && isMemoEligible(target)) {
            BlockState canonical = dynamic.resolveState(world, x, y, z, metadata,
                    def.getStateFromMeta(metadata));
            if (canonical != null) {
                syncMemoGeneration();
                IdentityHashMap<BlockState, MemoEntry> byState = stateMemo.get(target);
                MemoEntry hit = (byState == null) ? null : byState.get(canonical);
                if (hit != null) {
                    return new CollectedPart(hit.part, hit.propsView);
                }
                Map<String, String> jumpedProps = propsFromState(canonical);
                BlockStateModelPart jumped = collectForProps(world, x, y, z, metadata, target, jumpedProps);
                Map<String, String> jumpedView = jumpedProps.isEmpty()
                        ? null : Collections.unmodifiableMap(jumpedProps);
                stateMemo.computeIfAbsent(target, k -> new IdentityHashMap<BlockState, MemoEntry>())
                        .put(canonical, new MemoEntry(jumped, jumpedView));
                return new CollectedPart(jumped, jumpedView);
            }
        }

        Map<String, String> props = resolveProps(world, x, y, z, metadata);

        if (dynamic != null) {
            if (props == null) props = new HashMap<>();
            dynamic.resolve(world, x, y, z, metadata, props);
        }

        // Static-layer memoization: the resolved property table maps to a discrete code (the
        // full cartesian-product state index), and (target, code) → merged part is cached, so
        // repeated positions skip matching and merging entirely. The code is recomputed per
        // position, so neighbor changes self-heal exactly as before. Weighted-array targets
        // are excluded (position-seeded selection must keep re-rolling), and a -1 code
        // (missing / out-of-domain property) bypasses the memo — slow path unchanged.
        BlockStateModelPart part = null;
        int code = -1;
        if (def != null && isMemoEligible(target)) {
            syncMemoGeneration();
            code = def.stateIndexOf(props);
            if (code >= 0) {
                Map<Integer, BlockStateModelPart> byCode = partMemo.get(target);
                if (byCode != null) {
                    part = byCode.get(code);
                }
            }
        }

        if (part != null) {
            // Memo hit: props still flow out as the read-only tint view — exposure unchanged
            Map<String, String> exposed = (props == null || props.isEmpty())
                    ? null : Collections.unmodifiableMap(props);
            return new CollectedPart(part, exposed);
        }

        if (connectionMultipart || target.variants != null || target.multipart != null) {
            part = collectForProps(world, x, y, z, metadata, target, props);
        } else {
            part = BlockStateModelPart.empty();
        }

        if (code >= 0) {
            partMemo.computeIfAbsent(target, k -> new HashMap<>()).put(code, part);
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
                            BakedModelCache.buildKey(v.model, v.x, v.y, v.z, v.uvlock));
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
                    BakedModelCache.buildKey(variant.model, variant.x, variant.y, variant.z, variant.uvlock));
        } else {
            part = BakedModelCache.INSTANCE.get(BakedModelCache.buildKey("builtin/missing", 0, 0, 0));
        }
        return ParticleIconResolver.fromPart(part);
    }

    // ==================== Static-layer memoization (state-index joinCode) ====================

    /**
     * Drop the whole part memo when the bake-cache generation changed (resource reload /
     * re-stitch cycle): memoized parts carry UVs baked against the previous iconMap.
     */
    private void syncMemoGeneration() {
        long gen = BakedModelCache.INSTANCE.getGeneration();
        if (gen != partMemoGeneration) {
            partMemo.clear();
            stateMemo.clear();
            partMemoGeneration = gen;
        }
    }

    /**
     * Shared dispatch of the three collect branches (used by both the canonical-state fast
     * lane and the legacy string-code lane).
     */
    private BlockStateModelPart collectForProps(IBlockAccess world, int x, int y, int z, int metadata,
                                                BlockstateJson target, @Nullable Map<String, String> props) {
        if (connectionMultipart) {
            return collectConnectionMultipart(world, x, y, z, target, props);
        }
        if (target.variants != null) {
            return collectVariants(world, x, y, z, metadata, target, props);
        }
        if (target.multipart != null) {
            return collectMultipart(world, x, y, z, target, props);
        }
        return BlockStateModelPart.empty();
    }

    /** Memo value: merged part + the read-only props snapshot exposed to the tint pipeline. */
    private static final class MemoEntry {
        final BlockStateModelPart part;
        @Nullable final Map<String, String> propsView;

        MemoEntry(BlockStateModelPart part, @Nullable Map<String, String> propsView) {
            this.part = part;
            this.propsView = propsView;
        }
    }

    /**
     * Eligibility computed once per target blockstate: any weighted variant array makes the
     * selection position-seeded, so its result must not be keyed by state code alone. Arrays
     * whose weights sum to 1 always resolve to the same entry and stay eligible.
     */
    private boolean isMemoEligible(@Nullable BlockstateJson target) {
        if (target == null) return false;
        Boolean cached = memoEligibility.get(target);
        if (cached != null) return cached;
        boolean eligible = computeMemoEligible(target);
        memoEligibility.put(target, eligible);
        return eligible;
    }

    private static boolean computeMemoEligible(BlockstateJson target) {
        if (target.variants != null) {
            for (BlockstateJson.VariantEntry entry : target.variants.values()) {
                if (isWeightedArray(entry)) return false;
            }
        }
        if (target.multipart != null) {
            for (BlockstateJson.MultipartCase mpc : target.multipart) {
                if (isWeightedArray(mpc.apply)) return false;
            }
        }
        return true;
    }

    private static boolean isWeightedArray(@Nullable BlockstateJson.VariantEntry entry) {
        if (entry == null || !entry.isArray()) return false;
        int totalWeight = 0;
        for (BlockstateJson.Variant v : entry.list) {
            totalWeight += Math.max(v.weight, 1);
        }
        return totalWeight > 1;
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
        boolean uvlock;
        if (variant != null && variant.model != null) {
            modelPath = variant.model;
            rotX = variant.x;
            rotY = variant.y;
            rotZ = variant.z;
            uvlock = variant.uvlock;
        } else {
            modelPath = "builtin/missing";
            rotX = 0;
            rotY = 0;
            rotZ = 0;
            uvlock = false;
        }

        String cacheKey = BakedModelCache.buildKey(modelPath, rotX, rotY, rotZ, uvlock);
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
                    String partKey = BakedModelCache.buildKey(v.model, v.x, v.y, v.z, v.uvlock);
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
                    // Route through the central bake cache (same resolve→bake pipeline and
                    // iconMap source as ModelBaker.bake, which never caches by contract);
                    // the atlas gate still applies per part, so missingno replacement is unchanged.
                    BlockStateModelPart part = AtlasGuard.gate(
                            BakedModelCache.INSTANCE.get(
                                    BakedModelCache.buildKey(v.model, v.x, v.y, v.z, v.uvlock)), v.model);
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
