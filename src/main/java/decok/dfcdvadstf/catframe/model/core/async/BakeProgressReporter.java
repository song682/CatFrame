package decok.dfcdvadstf.catframe.model.core.async;

import cpw.mods.fml.common.ProgressManager;
import cpw.mods.fml.common.ProgressManager.ProgressBar;
import decok.dfcdvadstf.catframe.CatFrame;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two-level progress bar reporter for the FML loading screen.
 * <p>
 * Rendered result (SplashProgress draws both bars at once):
 * <pre>
 *   ┌─────────────────────────────────────────────┐
 *   │ CatFrame - Loading: Searching BlockState Models │  ← phaseBar (1/3)
 *   │                                             │
 *   │              (no detailBar during search)   │
 *   └─────────────────────────────────────────────┘
 *
 *   ┌─────────────────────────────────────────────┐
 *   │ CatFrame - Loading: Baking                  │  ← phaseBar (3/3)
 *   │ Baking: minecraft:block/stone               │  ← detailBar (42/671)
 *   └─────────────────────────────────────────────┘
 * </pre>
 * <p>
 * Threading model:
 * <ul>
 *   <li>all phase/finish methods are called on the <b>main thread</b></li>
 *   <li>bake workers push completion notices via {@link #reportBakeComplete(String)}</li>
 * </ul>
 */
@SuppressWarnings("deprecation") // ProgressManager: FML marks it deprecated but it is actually a stable public API
public class BakeProgressReporter {

    private static final String PHASE_BAR_TITLE = "CatFrame - Loading";
    private static final String DETAIL_BAR_TITLE = "Baking";

    /** Outer: phase progress bar (Searching BlockState Models → Searching Item Models → Baking) */
    private ProgressBar phaseBar;
    private int phaseTotal;
    private int phaseStepped;

    /** Inner: bake detail progress bar (each step shows namespace:path) */
    private ProgressBar detailBar;
    private int detailTotal;
    private int detailStepped;

    /** Lock-free queue for completion notices pushed by worker threads */
    private final ConcurrentLinkedQueue<String> completedNames = new ConcurrentLinkedQueue<>();
    /** Completion counter incremented by worker threads */
    private final AtomicInteger completedCount = new AtomicInteger(0);

    // ==================== Lifecycle ====================

    /**
     * Pushes the outer phase progress bar.
     *
     * @param phases the total number of phases (usually 3: search block models / search item models / bake)
     */
    public void begin(int phases) {
        this.phaseTotal = phases;
        this.phaseStepped = 0;
        this.phaseBar = ProgressManager.push(PHASE_BAR_TITLE, phaseTotal);
    }

    /**
     * Steps the outer phase progress bar (used during search phases).
     *
     * @param phase the phase name, e.g. "Searching BlockState Models", "Searching Item Models"
     */
    public void stepPhase(String phase) {
        if (phaseBar != null && phaseStepped < phaseTotal) {
            phaseBar.step(phase);
            phaseStepped++;
        }
    }

    /**
     * Enters the baking phase: steps the outer bar to "Baking" and pushes the inner detail bar.
     *
     * @param bakeTaskCount the total number of bake tasks
     */
    public void beginBaking(int bakeTaskCount) {
        // Step the outer bar to "Baking"
        stepPhase("Baking");

        // Push the inner detail bar
        this.detailTotal = bakeTaskCount;
        this.detailStepped = 0;
        this.detailBar = ProgressManager.push(DETAIL_BAR_TITLE, Math.max(1, bakeTaskCount));
    }

    // ==================== Baking phase (async reporting) ====================

    /**
     * Called by a worker thread after a bake completes to push the model path into the queue.
     * Thread-safe (ConcurrentLinkedQueue + AtomicInteger).
     */
    public void reportBakeComplete(String modelPath) {
        completedNames.add(modelPath);
        completedCount.incrementAndGet();
    }

    /**
     * Polls completed bake notices on the main thread and advances the <b>inner</b> detail progress bar.
     *
     * @return true when all bake tasks have been stepped through
     */
    public boolean pollAndStep() {
        if (detailBar == null || detailStepped >= detailTotal) return true;

        String name = completedNames.poll();
        if (name != null) {
            detailBar.step(name);
            detailStepped++;
        }
        return detailStepped >= detailTotal;
    }

    /**
     * Normal finish: pops the inner bar first, then the outer bar.
     */
    public void finish() {
        if (detailBar != null) {
            ProgressManager.pop(detailBar);
            detailBar = null;
        }
        if (phaseBar != null) {
            ProgressManager.pop(phaseBar);
            phaseBar = null;
        }
    }

    /**
     * Force finish: fills the remaining inner steps, then pops both bars. Used on timeout/interruption.
     *
     * @param reason the message used to fill the steps (e.g. "timeout", "interrupted")
     */
    public void finishForcefully(String reason) {
        if (detailBar != null) {
            while (detailStepped < detailTotal) {
                detailBar.step(reason);
                detailStepped++;
            }
            ProgressManager.pop(detailBar);
            detailBar = null;
        }
        // If the outer phase bar has not finished stepping (should not happen in theory, but handled defensively)
        if (phaseBar != null) {
            while (phaseStepped < phaseTotal) {
                phaseBar.step(reason);
                phaseStepped++;
            }
            ProgressManager.pop(phaseBar);
            phaseBar = null;
        }
        CatFrame.logger.warn("[BakeProgress] force-finished: '{}'", reason);
    }

    // ==================== State queries ====================

    public int getDetailTotal() {
        return detailTotal;
    }

    public int getDetailStepped() {
        return detailStepped;
    }

    public boolean isActive() {
        return phaseBar != null || detailBar != null;
    }
}
