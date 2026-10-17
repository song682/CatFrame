package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.*;

/**
 * A collection of baked quads grouped by cull direction. Similar to 1.21.5's
 * BlockStateModelPart, it indexes BakedQuads by cullface direction to speed up
 * direction queries during rendering.
 *
 * <p>Structure:
 * <ul>
 *   <li>{@link #getQuads(Direction)} — returns the quad list culled in that direction</li>
 *   <li>{@link #getGeneralQuads()} — returns the quad list with no cull direction (always rendered)</li>
 *   <li>{@link #getAllQuads()} — merges all quads, for item rendering and other no-cull scenarios</li>
 * </ul>
 */
public class BlockStateModelPart {
    private final Map<Direction, List<BakedQuad>> faceQuads;
    private final List<BakedQuad> generalQuads;
    private List<BakedQuad> allQuadsCache;

    /**
     * Model-level display transforms (propagated from ModelJson.display).
     * The keys are display scenarios ("gui", "firstperson_righthand", "thirdperson_righthand" etc.).
     * Read and applied at render time by DisplayTransformExtension.
     */
    @Nullable
    private final Map<String, ModelJson.DisplayTransform> partDisplay;

    /**
     * Particle texture captured from the model's textures.particle slot at bake time;
     * null when the slot was absent (26.1.2 semantics: mapped to missingno upstream).
     */
    @Nullable
    private final IIcon particleSlotIcon;

    private BlockStateModelPart(Map<Direction, List<BakedQuad>> faceQuads,
                                List<BakedQuad> generalQuads) {
        this(faceQuads, generalQuads, null, null);
    }

    private BlockStateModelPart(Map<Direction, List<BakedQuad>> faceQuads,
                                List<BakedQuad> generalQuads,
                                @Nullable Map<String, ModelJson.DisplayTransform> partDisplay) {
        this(faceQuads, generalQuads, partDisplay, null);
    }

    private BlockStateModelPart(Map<Direction, List<BakedQuad>> faceQuads,
                                List<BakedQuad> generalQuads,
                                @Nullable Map<String, ModelJson.DisplayTransform> partDisplay,
                                @Nullable IIcon particleSlotIcon) {
        this.faceQuads = faceQuads;
        this.generalQuads = generalQuads;
        this.partDisplay = partDisplay;
        this.particleSlotIcon = particleSlotIcon;
    }

    // ==================== Factory methods ====================

    /**
     * Builds a BlockStateModelPart from a list of BakedQuads, automatically grouping by cullface.
     */
    public static BlockStateModelPart fromQuads(List<BakedQuad> quads) {
        return fromQuads(quads, null, null);
    }

    public static BlockStateModelPart fromQuads(List<BakedQuad> quads,
                                                 @Nullable Map<String, ModelJson.DisplayTransform> display) {
        return fromQuads(quads, display, null);
    }

    /**
     * Builds a part carrying the baked particle-slot icon (null = slot absent).
     */
    public static BlockStateModelPart fromQuads(List<BakedQuad> quads,
                                                 @Nullable Map<String, ModelJson.DisplayTransform> display,
                                                 @Nullable IIcon particleSlotIcon) {
        Map<Direction, List<BakedQuad>> faceMap = new EnumMap<>(Direction.class);
        List<BakedQuad> general = new ArrayList<>();

        if (quads != null) {
            for (BakedQuad q : quads) {
                if (q.cullface != null) {
                    faceMap.computeIfAbsent(q.cullface, k -> new ArrayList<>()).add(q);
                } else {
                    general.add(q);
                }
            }
        }

        return new BlockStateModelPart(faceMap, general, display, particleSlotIcon);
    }

    /**
     * Creates from a map pre-grouped by direction (for multipart merging and similar scenarios).
     */
    public static BlockStateModelPart fromFaceMap(
            Map<Direction, List<BakedQuad>> faceMap,
            List<BakedQuad> generalQuads) {
        return fromFaceMap(faceMap, generalQuads, null);
    }

    public static BlockStateModelPart fromFaceMap(
            Map<Direction, List<BakedQuad>> faceMap,
            List<BakedQuad> generalQuads,
            @Nullable Map<String, ModelJson.DisplayTransform> display) {
        return new BlockStateModelPart(
                faceMap != null ? new EnumMap<>(faceMap) : new EnumMap<>(Direction.class),
                generalQuads != null ? new ArrayList<>(generalQuads) : new ArrayList<>(),
                display
        );
    }

    /**
     * An empty BlockStateModelPart.
     */
    public static BlockStateModelPart empty() {
        return new BlockStateModelPart(new EnumMap<>(Direction.class), new ArrayList<>(), null);
    }

    /**
     * Gets the model-level display transforms.
     */
    @Nullable
    public Map<String, ModelJson.DisplayTransform> getDisplay() {
        return partDisplay;
    }

    /**
     * Creates a new BlockStateModelPart replacing the existing display with the given display.
     * Returns this when the display is the same as the current one (avoiding unnecessary allocation).
     */
    public BlockStateModelPart withDisplay(@Nullable Map<String, ModelJson.DisplayTransform> display) {
        if (display == this.partDisplay) return this;
        if (display != null && display.equals(this.partDisplay)) return this;
        return new BlockStateModelPart(faceQuads, generalQuads, display, particleSlotIcon);
    }

    // ==================== Queries ====================

    /**
     * Returns all quads culled in the given direction. Returns an empty list when there
     * are no quads for that direction.
     * [W4 fix] Returns an immutable view to prevent callers from accidentally modifying internal state.
     */
    public List<BakedQuad> getQuads(Direction side) {
        List<BakedQuad> q = faceQuads.get(side);
        return q != null ? Collections.unmodifiableList(q) : Collections.emptyList();
    }

    /**
     * Returns all quads with no cull direction (always rendered).
     * [W4 fix] Returns an immutable view.
     */
    public List<BakedQuad> getGeneralQuads() {
        return Collections.unmodifiableList(generalQuads);
    }

    /**
     * Returns all quads (regardless of direction), for item rendering and other no-cull scenarios.
     */
    public List<BakedQuad> getAllQuads() {
        if (allQuadsCache == null) {
            int total = generalQuads.size();
            for (List<BakedQuad> list : faceQuads.values()) {
                total += list.size();
            }
            allQuadsCache = new ArrayList<>(total);
            allQuadsCache.addAll(generalQuads);
            for (List<BakedQuad> list : faceQuads.values()) {
                allQuadsCache.addAll(list);
            }
        }
        return allQuadsCache;
    }

    /**
     * Whether there are any quads (including directional and non-directional ones).
     */
    public boolean isEmpty() {
        if (!generalQuads.isEmpty()) return false;
        for (List<BakedQuad> list : faceQuads.values()) {
            if (!list.isEmpty()) return false;
        }
        return true;
    }

    // ==================== Atlas splitting (mirroring 26.1.2 per-quad atlas) ====================

    /** Lazy cache: index 0 = blocks-atlas sub-part, index 1 = items-atlas sub-part, either slot may be null. */
    private BlockStateModelPart[] atlasSplitCache;

    /**
     * Splits parts by each quad's bake-time atlas flag (mirroring 26.1.2
     * {@code BakedQuad.MaterialInfo}'s per-quad RenderType).
     * <p>
     * When fully homogeneous (the common case), the corresponding slot directly references
     * {@code this}, the other slot is null, and there is zero allocation; when atlases are
     * mixed, each builds its own sub-part (carrying the same display), aligning with 26.1.2's
     * layered-item cross-atlas capability. The result is cached and accessed only by the
     * render thread.
     *
     * @return a length-2 array: [0]=blocks-atlas part, [1]=items-atlas part, either slot may be null
     */
    public BlockStateModelPart[] atlasSplit() {
        if (atlasSplitCache == null) {
            boolean hasBlock = false, hasItem = false;
            for (BakedQuad q : getAllQuads()) {
                if (q.blockAtlas) hasBlock = true; else hasItem = true;
                if (hasBlock && hasItem) break;
            }
            if (!hasItem) {
                // All blocks (including empty parts)
                atlasSplitCache = new BlockStateModelPart[]{this, null};
            } else if (!hasBlock) {
                atlasSplitCache = new BlockStateModelPart[]{null, this};
            } else {
                List<BakedQuad> blockQuads = new ArrayList<>();
                List<BakedQuad> itemQuads = new ArrayList<>();
                for (BakedQuad q : getAllQuads()) {
                    (q.blockAtlas ? blockQuads : itemQuads).add(q);
                }
                atlasSplitCache = new BlockStateModelPart[]{
                        fromQuads(blockQuads, partDisplay, particleSlotIcon),
                        fromQuads(itemQuads, partDisplay, particleSlotIcon)};
            }
        }
        return atlasSplitCache;
    }

    /**
     * Whether all quads come from the blocks atlas (used by the block world-path gate
     * {@code AtlasGuard}, mirroring 26.1.2 {@code SimpleModelWrapper.bake}'s
     * forbiddenSprites check).
     */
    public boolean allQuadsInBlockAtlas() {
        for (BakedQuad q : getAllQuads()) {
            if (!q.blockAtlas) return false;
        }
        return true;
    }

    /**
     * Merges two BlockStateModelParts (for multipart composition).
     */
    public BlockStateModelPart merge(BlockStateModelPart other) {
        Map<Direction, List<BakedQuad>> mergedFace = new EnumMap<>(Direction.class);
        mergedFace.putAll(this.faceQuads);
        for (Map.Entry<Direction, List<BakedQuad>> entry : other.faceQuads.entrySet()) {
            mergedFace.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
        }
        List<BakedQuad> mergedGeneral = new ArrayList<>(this.generalQuads);
        mergedGeneral.addAll(other.generalQuads);
        // Merge display: prefer this one's, fall back to other's if this has none
        Map<String, ModelJson.DisplayTransform> mergedDisplay = this.partDisplay != null
                ? this.partDisplay
                : other.partDisplay;
        // Particle slot: this-first priority, mirroring the display merge.
        IIcon mergedParticle = this.particleSlotIcon != null ? this.particleSlotIcon : other.particleSlotIcon;
        return new BlockStateModelPart(mergedFace, mergedGeneral, mergedDisplay, mergedParticle);
    }

    // ==================== Added (mirroring 26.1.2) ====================

    /**
     * Whether ambient occlusion is enabled.
     * <p>
     * Iterates over all quads checking the ambientOcclusion field.
     * If any quad's ambientOcclusion is not {@code false}, AO is considered enabled.
     *
     * @return true = ambient occlusion enabled
     */
    public boolean useAmbientOcclusion() {
        // Check directional quads
        for (List<BakedQuad> list : faceQuads.values()) {
            for (BakedQuad q : list) {
                if (q.ambientOcclusion != Boolean.FALSE) {
                    return true;
                }
            }
        }
        // Check non-directional quads
        for (BakedQuad q : generalQuads) {
            if (q.ambientOcclusion != Boolean.FALSE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Particle texture captured from the model's textures.particle slot at bake time.
     * <p>
     * Null when the slot was absent — mapped to missingno by ParticleIconResolver under
     * high-version semantics.
     *
     * @return the IIcon of the explicit particle slot, or null when the slot is absent
     */
    @Nullable
    public IIcon particleSlotIcon() {
        return particleSlotIcon;
    }

    /**
     * Particle texture.
     * <p>
     * Returns the {@link IIcon} of the first available quad.
     * Returns null if the model has no quads.
     *
     * @return the IIcon used for particles, may be null
     */
    @Nullable
    public IIcon particleIcon() {
        // Check directional quads first
        for (List<BakedQuad> list : faceQuads.values()) {
            for (BakedQuad q : list) {
                if (q.icon != null) return q.icon;
            }
        }
        // Then check non-directional quads
        for (BakedQuad q : generalQuads) {
            if (q.icon != null) return q.icon;
        }
        return null;
    }
}
