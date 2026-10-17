package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command buffer: accumulates {@link RenderSubmit} grouped by {@link RenderTypeKey}, mirroring
 * the {@code SubmitNodeStorage} in the vanilla 26w+ pipeline (one command chain per render type).
 * <p>
 * Uses a {@link HashMap} keyed by registry entry (O(1) on the submit path); the traversal order is
 * determined by the ordered snapshot of {@link RenderTypeRegistry#forEachInOrder} (sortKey
 * ascending, solid before translucent), and
 * {@link FeatureRenderDispatcher#flushBatched(SubmitNodeStorage)} uses this to perform sorted
 * batched drawing. Runtime registry changes (a third party adding a group) are reflected in the
 * flush order immediately.
 * <p>
 * <b>Thread model</b>: client rendering is single-threaded; this class does no synchronization and
 * relies on {@link RenderCommandBuffers} holding the currently active scope in a {@link ThreadLocal}.
 */
public final class SubmitNodeStorage {

    private final Map<RenderTypeKey, List<RenderSubmit>> groups = new HashMap<>();

    /** Appends a submission to its render group. */
    public void submit(RenderSubmit s) {
        List<RenderSubmit> list = groups.get(s.type);
        if (list == null) {
            list = new ArrayList<>();
            groups.put(s.type, list);
        }
        list.add(s);
    }

    /** Whether there is no submission at all. */
    public boolean isEmpty() {
        if (groups.isEmpty()) return true;
        for (List<RenderSubmit> list : groups.values()) {
            if (!list.isEmpty()) return false;
        }
        return true;
    }

    /** Clears all groups (reuses the buffer). */
    public void clear() {
        groups.clear();
    }

    /**
     * A view of the non-empty groups produced in the order of the registry's ordered snapshot
     * (sortKey ascending).
     * <p>
     * The group contents (submit lists) are neither sorted nor copied; only a small number of
     * read-only entries for non-empty groups are collected (count = the number of groups that
     * actually have submissions, 4 built in, so the allocation is negligible).
     */
    public List<Map.Entry<RenderTypeKey, List<RenderSubmit>>> groups() {
        List<Map.Entry<RenderTypeKey, List<RenderSubmit>>> result = new ArrayList<>(4);
        RenderTypeRegistry.forEachInOrder(type -> {
            List<RenderSubmit> list = groups.get(type);
            if (list != null && !list.isEmpty()) {
                result.add(new AbstractMap.SimpleImmutableEntry<>(type, list));
            }
        });
        return result;
    }
}
