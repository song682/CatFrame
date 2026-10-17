package decok.dfcdvadstf.catframe.model.render.pipeline;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.render.api.IRenderGroupHandler;
import decok.dfcdvadstf.catframe.model.render.api.RenderTypeKey;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Group-claiming handler registry (internal implementation).
 * <p>
 * Mirrors the concurrency/priority pattern of
 * {@link decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry}:
 * {@link CopyOnWriteArrayList} storage (registration/unregistration is concurrency-safe with
 * the render traversal), smaller priority arbitrates first ({@link #DEFAULT_PRIORITY}=0),
 * registration order is stable within the same priority, and duplicate registration = update
 * priority and reposition.
 * <p>
 * <b>Slot arbitration (one-shot on registry change)</b>: when a handler is registered or
 * unregistered, for each group in the registry's ordered snapshot the handlers are queried in
 * turn via {@link IRenderGroupHandler#claim(RenderTypeKey)} (in ascending priority order; the
 * first claimant wins), and the result is written into a {@code volatile} slot array (same index
 * as the ordered snapshot); the flush path only reads the slots — zero sorting, zero claim calls.
 * A claim exception from a single handler is isolated (treated as not claiming, and logged).
 * When a new group is added to the registry but handlers are not re-registered, the new slot has
 * no claimant → the built-in flush is used.
 */
@SideOnly(Side.CLIENT)
public final class RenderGroupHandlerRegistry {

    /** Default handler priority: at level 0, arbitration follows registration order. */
    public static final int DEFAULT_PRIORITY = 0;

    private static final List<HandlerEntry> HANDLERS = new CopyOnWriteArrayList<>();
    /** Slot arbitration snapshot (published via volatile): same index as the registry's ordered snapshot; for a given slot the smallest priority wins. */
    private static volatile IRenderGroupHandler[] slotHandlers = new IRenderGroupHandler[0];

    private RenderGroupHandlerRegistry() {
    }

    /**
     * Registers a group-claiming handler (default priority 0). Re-registering the same instance = reposition and re-arbitrate.
     */
    public static void register(IRenderGroupHandler handler) {
        register(handler, DEFAULT_PRIORITY);
    }

    /**
     * Registers a group-claiming handler with an explicit priority. Smaller priority arbitrates
     * first; within the same priority the order is stably arranged by registration order.
     * Re-registering the same instance = update priority and reposition (triggers a full
     * arbitration, which can be used to refresh the claim decision).
     *
     * @param handler  the claiming handler (null is ignored)
     * @param priority the priority
     */
    public static void register(IRenderGroupHandler handler, int priority) {
        if (handler == null) return;
        HANDLERS.removeIf(e -> e.handler == handler);
        insertSorted(handler, priority);
        rebuildSlots();
    }

    /**
     * Unregisters a handler (triggers a full arbitration; the vacated slot falls back to the built-in flush).
     */
    public static void unregister(IRenderGroupHandler handler) {
        if (handler != null) {
            HANDLERS.removeIf(e -> e.handler == handler);
            rebuildSlots();
        }
    }

    /**
     * The number of currently registered handlers.
     */
    public static int size() {
        return HANDLERS.size();
    }

    /**
     * Internal to the renderer: returns the claiming handler arbitrated for a group (null when
     * there is no claimant → built-in flush). Read-only slot snapshot, zero claim calls,
     * O(number of groups) linear scan.
     */
    static IRenderGroupHandler handlerFor(RenderTypeKey type) {
        RenderTypeKey[] snapshot = RenderTypeRegistry.orderedSnapshot();
        IRenderGroupHandler[] slots = slotHandlers;
        int len = Math.min(snapshot.length, slots.length);
        for (int i = 0; i < len; i++) {
            if (snapshot[i] == type) {
                return slots[i];
            }
        }
        return null;
    }

    /** Full arbitration: for each group in the ordered snapshot, take the claimant with the smallest priority (a claim exception = not claiming). */
    private static void rebuildSlots() {
        RenderTypeKey[] snapshot = RenderTypeRegistry.orderedSnapshot();
        IRenderGroupHandler[] slots = new IRenderGroupHandler[snapshot.length];
        for (int i = 0; i < snapshot.length; i++) {
            final RenderTypeKey type = snapshot[i];
            for (HandlerEntry e : HANDLERS) { // already in ascending priority order; the first claimant wins
                if (safeClaim(e, type)) {
                    slots[i] = e.handler;
                    break;
                }
            }
        }
        slotHandlers = slots;
    }

    /** Error isolation: a claim exception from a single handler is logged and treated as not claiming (it does not affect other handlers or the arbitration). */
    private static boolean safeClaim(HandlerEntry e, RenderTypeKey type) {
        try {
            return e.handler.claim(type);
        } catch (Throwable t) {
            CatFrame.logger.warn("[RenderGroupHandlerRegistry] handler {} failed in claim({}): {}",
                    e.handler.getClass().getName(), type.id(), t.toString(), t);
            return false;
        }
    }

    /** Stable sorted insert: same semantics as ModelRenderRegistry.insertSorted (same priority is appended to the end of the group). */
    private static void insertSorted(IRenderGroupHandler handler, int priority) {
        int insertAt = HANDLERS.size();
        for (int i = 0; i < HANDLERS.size(); i++) {
            if (HANDLERS.get(i).priority > priority) {
                insertAt = i;
                break;
            }
        }
        HANDLERS.add(insertAt, new HandlerEntry(handler, priority));
    }

    /** Handler entry: instance + registration priority. */
    private static final class HandlerEntry {
        final IRenderGroupHandler handler;
        final int priority;

        HandlerEntry(IRenderGroupHandler handler, int priority) {
            this.handler = handler;
            this.priority = priority;
        }
    }
}
