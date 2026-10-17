package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Render group registry — the runtime render type registry, matching the render type
 * registry in the vanilla 26w+ deferred rendering pipeline.
 * <p>
 * Replaces the pre-refactor {@code RenderType} enum: built-in groups (see static
 * fields such as {@link #BLOCK_ATLAS_SOLID}) and third-party registrations are
 * uniformly exposed as {@link RenderTypeKey} entry handles — the submit path holds the
 * entry reference directly (O(1), zero lookup), and the flush path iterates a read-only
 * snapshot in <b>explicit sort-key</b> order.
 * <p>
 * <b>flush order</b>: ascending by {@link RenderTypeKey#sortKey()} (smaller flushes
 * first), with equal keys stably ordered by registration order — precisely replicating
 * the pre-refactor flush order (BLOCK_SOLID=0 → ITEM_SOLID=1 → BLOCK_TRANSLUCENT=2 →
 * ITEM_TRANSLUCENT=3), ensuring translucents blend correctly on top of opaques.
 * <p>
 * <b>About culling</b>: the face-cull switch is not encoded into the group, but
 * carried per {@link RenderSubmit#disableCull} (the item path disables culling, the
 * block path keeps it), because block GUI and block items have different cull policies
 * under the same atlas + blend group. At flush time it is set uniformly from the cull
 * flag of the submissions within the group (submissions within a scope are
 * homogeneous).
 * <p>
 * <b>Thread safety (v0.5+ contract)</b>: entries are stored in an internal
 * {@link CopyOnWriteArrayList} (registration/update may run on any thread, and
 * compound operations are synchronized on the class lock); after each change it
 * reorders and publishes an immutable snapshot (a {@code volatile} array), and the
 * flush path reads the snapshot without sorting — concurrent registration from
 * background baking threads does not break the render thread's iteration.
 */
public final class RenderTypeRegistry {

    /** Built-in sort key: block atlas · opaque (0, flushed first). */
    public static final int SORT_BLOCK_SOLID = 0;
    /** Built-in sort key: item atlas · opaque (1). */
    public static final int SORT_ITEM_SOLID = 1;
    /** Built-in sort key: block atlas · translucent (2). */
    public static final int SORT_BLOCK_TRANSLUCENT = 2;
    /** Built-in sort key: item atlas · translucent (3, flushed last). */
    public static final int SORT_ITEM_TRANSLUCENT = 3;
    /** Built-in sort key: block atlas · destroy decal (4; destroy rendering always uses flushInline and does not participate in batch sorting). */
    public static final int SORT_BLOCK_DESTROY = 4;

    /** Built-in group: block atlas · opaque (block world inline, block item in hand, etc.). */
    public static final RenderTypeKey BLOCK_ATLAS_SOLID;
    /** Built-in group: item atlas · opaque (ordinary items in hand / displayed, etc., no blend needed). */
    public static final RenderTypeKey ITEM_ATLAS_SOLID;
    /** Built-in group: block atlas · translucent (block GUI, block item GUI / dropped / item frame). */
    public static final RenderTypeKey BLOCK_ATLAS_TRANSLUCENT;
    /** Built-in group: item atlas · translucent (item GUI / dropped / item frame). */
    public static final RenderTypeKey ITEM_ATLAS_TRANSLUCENT;
    /** Built-in group: block atlas · destroy decal (destroy overlay, matching 26.1.2 DESTROY_TYPES). */
    public static final RenderTypeKey BLOCK_ATLAS_DESTROY;

    /** Registration entry storage (write path is concurrency-safe; iteration only happens while rebuilding the snapshot). */
    private static final CopyOnWriteArrayList<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    /** Sorted snapshot (published via volatile): the flush path iterates it read-only, rebuilt when the registry changes. */
    private static volatile RenderTypeKey[] orderedSnapshot = new RenderTypeKey[0];

    static {
        BLOCK_ATLAS_SOLID = register(
                "block_atlas_solid", TextureMap.locationBlocksTexture, false, SORT_BLOCK_SOLID);
        ITEM_ATLAS_SOLID = register(
                "item_atlas_solid", TextureMap.locationItemsTexture, false, SORT_ITEM_SOLID);
        BLOCK_ATLAS_TRANSLUCENT = register(
                "block_atlas_translucent", TextureMap.locationBlocksTexture, true, SORT_BLOCK_TRANSLUCENT);
        ITEM_ATLAS_TRANSLUCENT = register(
                "item_atlas_translucent", TextureMap.locationItemsTexture, true, SORT_ITEM_TRANSLUCENT);
        // Destroy decal group: multiplicative blend state is owned by vanilla
        // drawBlockDamageTexture, so blend stays false here.
        BLOCK_ATLAS_DESTROY = register(
                "block_atlas_destroy", TextureMap.locationBlocksTexture, false, SORT_BLOCK_DESTROY);
    }

    private RenderTypeRegistry() {
    }

    /**
     * Registers (or updates) a render group.
     * <p>
     * A duplicate id is treated as an update: the original registration order is kept
     * (stable sort tie-break), and the snapshot is rebuilt after the fields are
     * updated. Third-party mods may register new groups (including custom sort
     * positions) during client init.
     *
     * @param id        stable group identifier (unique registration key, e.g. {@code catframe:block_solid})
     * @param atlas     the texture atlas to bind (blocks / items atlas)
     * @param blend     whether alpha blending (translucent) must be enabled
     * @param sortKey   explicit sort key: smaller flushes first; equal keys are stably ordered by registration order
     * @return the registry entry handle (usable directly afterwards for submitting and flushing the group)
     */
    public static RenderTypeKey register(ResourceLocation id, ResourceLocation atlas,
                                         boolean blend, int sortKey) {
        if (id == null) {
            throw new IllegalArgumentException("render type id must not be null");
        }
        synchronized (RenderTypeRegistry.class) {
            for (Entry e : ENTRIES) {
                if (e.id.equals(id)) {
                    // Duplicate id: update the fields (registration order unchanged) → rebuild the snapshot
                    e.update(atlas, blend, sortKey);
                    rebuildSnapshot();
                    return e;
                }
            }
            Entry entry = new Entry(id, ENTRIES.size(), atlas, blend, sortKey);
            ENTRIES.add(entry);
            rebuildSnapshot();
            return entry;
        }
    }

    /**
     * Convenience overload of
     * {@link #register(ResourceLocation, ResourceLocation, boolean, int)} that builds
     * the id as {@code catframe:<name>} (consistent with the pre-refactor enum's
     * {@code id()} semantics).
     */
    public static RenderTypeKey register(String id, ResourceLocation atlas,
                                         boolean blend, int sortKey) {
        return register(new ResourceLocation("catframe", id), atlas, blend, sortKey);
    }

    /**
     * Iterates the current snapshot in ascending sort-key order (read-only, no sorting
     * or copying). Consumed by {@link SubmitNodeStorage#groups()} and the flush path;
     * the snapshot is published by the registry on change.
     */
    public static void forEachInOrder(Consumer<RenderTypeKey> action) {
        RenderTypeKey[] snapshot = orderedSnapshot;
        for (RenderTypeKey key : snapshot) {
            action.accept(key);
        }
    }

    /**
     * The current sorted snapshot array (read-only convention: callers must not modify
     * the array or its elements). Used by same-package slot arbitration (handler
     * claiming) and other read-only scenarios that need index alignment.
     */
    static RenderTypeKey[] orderedSnapshot() {
        return orderedSnapshot;
    }

    /**
     * Resolves a built-in group from the atlas choice and blend requirement (behavior
     * identical to the pre-refactor {@code RenderType.of()}).
     *
     * @param blockAtlas  true = use the blocks atlas, false = use the items atlas
     * @param translucent true = blending needed (translucent), false = opaque
     */
    public static RenderTypeKey of(boolean blockAtlas, boolean translucent) {
        if (blockAtlas) {
            return translucent ? BLOCK_ATLAS_TRANSLUCENT : BLOCK_ATLAS_SOLID;
        }
        return translucent ? ITEM_ATLAS_TRANSLUCENT : ITEM_ATLAS_SOLID;
    }

    /** Reorder after a change: sortKey ascending + registration-order tie-break, published as an immutable snapshot. */
    private static void rebuildSnapshot() {
        List<Entry> sorted = new ArrayList<>(ENTRIES);
        sorted.sort(Comparator.comparingInt(Entry::sortKey)
                .thenComparingInt(e -> e.registrationOrder));
        orderedSnapshot = sorted.toArray(new RenderTypeKey[0]);
    }

    /**
     * Registry entry: mutable (a duplicate id registration updates the fields and
     * rebuilds the snapshot), and the registration order never changes once assigned
     * (stable tie-break for equal sortKeys).
     */
    private static final class Entry implements RenderTypeKey {
        private final ResourceLocation id;
        /** First-registration order: sort tie-break (stably ordered by registration order for equal sortKeys). */
        private final int registrationOrder;
        private volatile ResourceLocation atlas;
        private volatile boolean blend;
        private volatile int sortKey;

        Entry(ResourceLocation id, int registrationOrder,
              ResourceLocation atlas, boolean blend, int sortKey) {
            this.id = id;
            this.registrationOrder = registrationOrder;
            this.atlas = atlas;
            this.blend = blend;
            this.sortKey = sortKey;
        }

        void update(ResourceLocation atlas, boolean blend, int sortKey) {
            this.atlas = atlas;
            this.blend = blend;
            this.sortKey = sortKey;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public ResourceLocation atlas() {
            return atlas;
        }

        @Override
        public boolean blend() {
            return blend;
        }

        @Override
        public int sortKey() {
            return sortKey;
        }
    }
}
