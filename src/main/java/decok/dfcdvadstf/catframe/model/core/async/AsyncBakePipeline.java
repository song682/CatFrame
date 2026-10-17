package decok.dfcdvadstf.catframe.model.core.async;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.core.baking.BakingCore;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Asynchronous pre-bake pipeline, orchestrated with Guava {@link ListenableFuture}.
 * <p>
 * Architecture (Fork-Join pattern, replacing the original Akka Actor Per-Stage):
 * <pre>
 *   triggerAsyncBake()
 *   ├── collect all model paths to bake (collectModelPaths)
 *   ├── topological sort (ModelResolver.topologicalSort)
 *   ├── generate BakeTasks (each model × common rotation combinations)
 *   ├── submit each task as a ListenableFuture (RenderExecutors shared pool runs BakingCore.bake in parallel)
 *   └── Futures.allAsList + FutureCallback → bulkPut into BakedModelCache on completion
 * </pre>
 * <p>
 * Triggered in {@link VanillaTextureTracker#onTextureStitchPost}, at which point the iconMap has
 * already been passed in as a parameter so baking can resolve texture references correctly.
 * <p>
 * {@link BakingCore#bake} is a pure, stateless function and is naturally safe to call concurrently
 * from a thread pool.
 */
public class AsyncBakePipeline {

    // ==================== Task/result carriers (plain POJOs) ====================

    /** A bake task: one model + rotation combination. */
    public static class BakeTask {
        public final String modelPath;
        public final float rotX;
        public final float rotY;
        public final float rotZ;
        @Nullable
        public final Map<String, IIcon> iconMap;

        public BakeTask(String modelPath, float rotX, float rotY, float rotZ, @Nullable Map<String, IIcon> iconMap) {
            this.modelPath = modelPath;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
            this.iconMap = iconMap;
        }
    }

    /** Bake result: cacheKey → baked part (part may be null, meaning the bake failed). */
    public static class BakeResult {
        public final String cacheKey;
        @Nullable
        public final BlockStateModelPart part;

        public BakeResult(String cacheKey, @Nullable BlockStateModelPart part) {
            this.cacheKey = cacheKey;
            this.part = part;
        }
    }

    // ==================== Public API ====================

    /**
     * Barrier wait timeout (seconds). Parallel baking of thousands of models is usually far faster
     * than this; the timeout is only a safety valve against hanging forever, after which it degrades
     * to {@link BakedModelCache} lazy baking.
     */
    private static final long BAKE_BARRIER_TIMEOUT_SECONDS = 60L;

    /**
     * Dispatch result: the aggregate future + start time + task count + individual future list
     * (for progress reporting). dispatch returns null when there are no models to bake.
     */
    private static final class Dispatch {
        final ListenableFuture<List<BakeResult>> all;
        final List<ListenableFuture<BakeResult>> futures;
        final List<BakeTask> tasks;
        final long startTime;
        final int totalTasks;

        Dispatch(ListenableFuture<List<BakeResult>> all,
                 List<ListenableFuture<BakeResult>> futures,
                 List<BakeTask> tasks,
                 long startTime, int totalTasks) {
            this.all = all;
            this.futures = futures;
            this.tasks = tasks;
            this.startTime = startTime;
            this.totalTasks = totalTasks;
        }
    }

    /**
     * Triggers asynchronous pre-baking (non-blocking). Kept for legacy callers.
     * <p>
     * Returns immediately after submitting all bake tasks to the shared thread pool; baking runs in
     * the background in parallel and, once all tasks finish, the results are automatically loaded
     * into {@link BakedModelCache}.
     *
     * @param iconMap the IIcon map for the current stitch cycle
     */
    public static void triggerAsyncBake(@Nullable final Map<String, IIcon> iconMap) {
        final Dispatch d = dispatch(iconMap);
        if (d == null) return;

        // Aggregate all results → load them into the cache (callback runs on a pool thread)
        Futures.addCallback(d.all, new FutureCallback<List<BakeResult>>() {
            @Override
            public void onSuccess(List<BakeResult> results) {
                applyResults(results, d.startTime);
            }

            @Override
            public void onFailure(Throwable t) {
                CatFrame.logger.error("[AsyncBake] pipeline failed: {}", t.getMessage(), t);
            }
        });

        CatFrame.logger.info("[AsyncBake] pipeline triggered (non-blocking) | iconMap.size={}",
                iconMap != null ? iconMap.size() : 0);
    }

    /**
     * Triggers pre-baking and blocks until everything is complete ("async prepare, sync switch").
     * <p>
     * Matches modern Minecraft {@code ModelManager.reload()} with its
     * {@code preparationBarrier::wait} + {@code apply()}: baking still runs <b>in parallel</b> on
     * the background thread pool (async preparation), but this method blocks the calling thread
     * (the stitch main thread) until every model is baked and <b>synchronously</b> loads the results
     * into {@link BakedModelCache} (sync switch), guaranteeing that after it returns the render
     * system always reads already-baked {@link BlockStateModelPart}s with zero on-the-fly baking at
     * runtime.
     * <p>
     * The first-frame lazy baking of {@link BakedModelCache} is thereby reduced to a rarely triggered
     * safety net (covering only model/rotation combinations outside the pre-baked set, or acting as
     * a fallback after a barrier timeout).
     * <p>
     * Note: {@link BakingCore#bake} is a pure function that does not depend on the GL context and is
     * safe to execute on a background thread, so blocking the main thread to wait cannot deadlock.
     *
     * @param iconMap the IIcon map for the current stitch cycle
     */
    public static void triggerBakeBlocking(@Nullable final Map<String, IIcon> iconMap) {
        // ===== 1. Collect model paths in stages =====
        Set<String> blockPaths = collectBlockModelPaths();
        Set<String> itemPaths = collectItemModelPaths();
        Set<String> allPaths = new LinkedHashSet<>(blockPaths);
        allPaths.addAll(itemPaths);

        if (allPaths.isEmpty()) {
            CatFrame.logger.info("[AsyncBake] no models to bake, skip");
            return;
        }

        // ===== 2. Topological sort + task generation =====
        List<String> sorted = ModelResolver.topologicalSort(allPaths);
        List<BakeTask> tasks = generateTasks(sorted, iconMap);
        final int totalTasks = tasks.size();
        final long startTime = System.nanoTime();

        CatFrame.logger.info("[AsyncBake] dispatching {} bake tasks for {} models",
                totalTasks, allPaths.size());

        // ===== 3. Two-level progress reporter =====
        final BakeProgressReporter progress = new BakeProgressReporter();
        progress.begin(3); // 3 phases: search blocks, search items, baking
        progress.stepPhase("Searching BlockState Models");
        progress.stepPhase("Searching Item Models");
        progress.beginBaking(totalTasks);

        // ===== 4. Submit all bake tasks to the thread pool =====
        List<ListenableFuture<BakeResult>> futures = new ArrayList<>(totalTasks);
        for (final BakeTask task : tasks) {
            ListenableFuture<BakeResult> f = RenderExecutors.get().submit(new Callable<BakeResult>() {
                @Override
                public BakeResult call() {
                    String cacheKey = BakedModelCache.buildKey(task.modelPath, task.rotX, task.rotY, task.rotZ);
                    BlockStateModelPart part;
                    try {
                        part = BakingCore.bake(task.modelPath, task.rotX, task.rotY, task.rotZ, task.iconMap);
                    } catch (Throwable t) {
                        // [FIX] Per-task isolation: a single model throwing must not fail the
                        //     fail-fast aggregate and discard the whole round (item models would
                        //     vanish). A failed model yields a null part; applyResults skips it
                        //     and the render path lazily retries it.
                        CatFrame.logger.error("[AsyncBake] bake task failed for '{}' @{}@{}@{}: {}",
                                task.modelPath, task.rotX, task.rotY, task.rotZ, t.toString());
                        part = null;
                    }
                    return new BakeResult(cacheKey, part);
                }
            });
            // Completion callback → push into the reporter queue
            Futures.addCallback(f, new FutureCallback<BakeResult>() {
                @Override
                public void onSuccess(BakeResult r) {
                    progress.reportBakeComplete(task.modelPath);
                }

                @Override
                public void onFailure(Throwable t) {
                    progress.reportBakeComplete(task.modelPath);
                }
            });
            futures.add(f);
        }

        // ===== 5. Main thread polls progress =====
        long deadline = System.nanoTime() + BAKE_BARRIER_TIMEOUT_SECONDS * 1_000_000_000L;
        while (!progress.pollAndStep()) {
            if (System.nanoTime() > deadline) {
                CatFrame.logger.warn("[AsyncBake] progress timed out after {}s ({}/{} done)",
                        BAKE_BARRIER_TIMEOUT_SECONDS, progress.getDetailStepped(), progress.getDetailTotal());
                progress.finishForcefully("timeout");
                break;
            }
            try { Thread.sleep(5); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                progress.finishForcefully("interrupted");
                break;
            }
        }
        if (progress.isActive()) {
            progress.finish();
        }

        // ===== 6. Collect results and load them into the cache =====
        ListenableFuture<List<BakeResult>> allFuture = Futures.allAsList(futures);
        try {
            List<BakeResult> results = allFuture.get(5, TimeUnit.SECONDS);
            applyResults(results, startTime);
            CatFrame.logger.info("[AsyncBake] barrier complete (blocking) | iconMap.size={}",
                    iconMap != null ? iconMap.size() : 0);
        } catch (TimeoutException te) {
            CatFrame.logger.warn("[AsyncBake] result collection timed out; falling back to lazy baking");
            Futures.addCallback(allFuture, new FutureCallback<List<BakeResult>>() {
                @Override
                public void onSuccess(List<BakeResult> results) {
                    applyResults(results, startTime);
                }

                @Override
                public void onFailure(Throwable t) {
                    CatFrame.logger.error("[AsyncBake] deferred pipeline failed: {}", t.getMessage(), t);
                }
            });
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            CatFrame.logger.warn("[AsyncBake] barrier interrupted; falling back to lazy baking");
        } catch (ExecutionException ee) {
            CatFrame.logger.error("[AsyncBake] barrier failed: {}", ee.getMessage(), ee);
        }
    }

    /**
     * Collects model paths, generates tasks, submits them to the thread pool, and returns the
     * aggregate future. Returns {@code null} when there are no models to bake.
     */
    @Nullable
    private static Dispatch dispatch(@Nullable final Map<String, IIcon> iconMap) {
        // 1. Collect all model paths to bake
        Set<String> modelPaths = new LinkedHashSet<>(collectBlockModelPaths());
        modelPaths.addAll(collectItemModelPaths());
        if (modelPaths.isEmpty()) {
            CatFrame.logger.info("[AsyncBake] no models to bake, skip");
            return null;
        }

        // 2. Topological sort
        List<String> sorted = ModelResolver.topologicalSort(modelPaths);

        // 3. Generate bake tasks (each model × common rotation combinations)
        List<BakeTask> tasks = generateTasks(sorted, iconMap);
        final int totalTasks = tasks.size();
        final long startTime = System.nanoTime();

        CatFrame.logger.info("[AsyncBake] dispatching {} bake tasks for {} models",
                totalTasks, modelPaths.size());

        // 4. Submit each task as a ListenableFuture (BakingCore.bake is a pure function, safe to run concurrently)
        List<ListenableFuture<BakeResult>> futures = new ArrayList<>(totalTasks);
        for (final BakeTask task : tasks) {
            ListenableFuture<BakeResult> f = RenderExecutors.get().submit(new Callable<BakeResult>() {
                @Override
                public BakeResult call() {
                    String cacheKey = BakedModelCache.buildKey(task.modelPath, task.rotX, task.rotY, task.rotZ);
                    BlockStateModelPart part;
                    try {
                        part = BakingCore.bake(task.modelPath, task.rotX, task.rotY, task.rotZ, task.iconMap);
                    } catch (Throwable t) {
                        // [FIX] Per-task isolation (see blocking path): one bad model must not
                        //     fail the fail-fast aggregate and discard the whole bake round.
                        CatFrame.logger.error("[AsyncBake] bake task failed for '{}' @{}@{}@{}: {}",
                                task.modelPath, task.rotX, task.rotY, task.rotZ, t.toString());
                        part = null;
                    }
                    return new BakeResult(cacheKey, part);
                }
            });
            futures.add(f);
        }

        return new Dispatch(Futures.allAsList(futures), futures, tasks, startTime, totalTasks);
    }

    /**
     * Bulk-loads bake results into {@link BakedModelCache}. Shared by the async callback and the blocking barrier.
     */
    private static void applyResults(List<BakeResult> results, long startTime) {
        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        Map<String, BlockStateModelPart> resultMap = new HashMap<>();
        for (BakeResult r : results) {
            if (r != null && r.part != null) {
                resultMap.put(r.cacheKey, r.part);
            }
        }
        BakedModelCache.INSTANCE.bulkPut(resultMap);
        CatFrame.logger.info("[AsyncBake] complete: {} models baked in {}ms (cache size: {})",
                resultMap.size(), elapsed, BakedModelCache.INSTANCE.size());
    }

    /**
     * Shuts down the underlying thread pool. Called on mod unload or game shutdown.
     */
    public static void shutdown() {
        RenderExecutors.shutdown();
    }

    // ==================== Internal: task collection and generation ====================

    /**
     * Collects block model paths (from blockstates + model_mappings.blocks).
     * All paths carry a namespace prefix (e.g. "minecraft:block/stone").
     */
    private static Set<String> collectBlockModelPaths() {
        Set<String> paths = new LinkedHashSet<>();

        // Collect from blockstates (namespace is the outer key)
        for (Map.Entry<String, Map<String, BlockstateJson>> nsEntry : ModelManagerDataLoader.loadedBlockstates.entrySet()) {
            String namespace = nsEntry.getKey();
            for (BlockstateJson bs : nsEntry.getValue().values()) {
                collectPathsFromBlockstate(bs, paths, namespace);
            }
        }

        // Collect from model_mappings.blocks (skip state_mapping mode: values are blockstate
        // names, their models are already covered by the loadedBlockstates branch above)
        for (Map.Entry<String, VanillaModelManager.ModelMappings> entry : ModelManagerDataLoader.loadedMappings.entrySet()) {
            String namespace = entry.getKey();
            VanillaModelManager.ModelMappings mappings = entry.getValue();
            if (mappings.state_mapping) continue;
            if (mappings.blocks != null) {
                for (String path : mappings.blocks.values()) {
                    paths.add(ensureNamespace(path, namespace));
                }
            }
        }

        return paths;
    }

    /**
     * Collects item model paths (from loadedItemStates + interfaceItemStates + model_mappings.items).
     * All paths carry a namespace prefix.
     */
    private static Set<String> collectItemModelPaths() {
        Set<String> paths = new LinkedHashSet<>();

        // Collect from model_mappings.items (skip state_mapping mode: values are ItemState
        // names, their models are already covered by the loadedItemStates branch below)
        for (Map.Entry<String, VanillaModelManager.ModelMappings> entry : ModelManagerDataLoader.loadedMappings.entrySet()) {
            String namespace = entry.getKey();
            VanillaModelManager.ModelMappings mappings = entry.getValue();
            if (mappings.state_mapping) continue;
            if (mappings.items != null) {
                for (String path : mappings.items.values()) {
                    paths.add(ensureNamespace(path, namespace));
                }
            }
        }

        // Collect all model paths referenced by the ItemState decision trees
        for (Map.Entry<String, Map<String, ItemStateNode>> nsEntry : ModelManagerDataLoader.loadedItemStates.entrySet()) {
            String namespace = nsEntry.getKey();
            for (ItemStateNode root : nsEntry.getValue().values()) {
                Set<String> raw = new LinkedHashSet<>();
                root.collectModelPaths(raw);
                for (String path : raw) {
                    paths.add(ensureNamespace(path, namespace));
                }
            }
        }

        // Collect from the IItemState interface declarations (e.g. dual-model hand paths)
        for (IItemStateProvider provider : ModelManagerDataLoader.interfaceItemStates.values()) {
            paths.addAll(provider.getDeclaredModelPaths());
        }

        return paths;
    }

    /**
     * Ensures a model path carries a namespace prefix.
     * Paths already containing ":" are returned unchanged; otherwise the owning namespace is prepended.
     */
    private static String ensureNamespace(String path, String namespace) {
        if (path == null) return namespace + ":";
        return path.contains(":") ? path : namespace + ":" + path;
    }

    private static void collectPathsFromBlockstate(BlockstateJson bs, Set<String> paths, String namespace) {
        if (bs.variants != null) {
            for (BlockstateJson.VariantEntry entry : bs.variants.values()) {
                if (entry.isArray()) {
                    for (BlockstateJson.Variant v : entry.list) {
                        if (v.model != null) paths.add(ensureNamespace(v.model, namespace));
                    }
                } else if (entry.single != null && entry.single.model != null) {
                    paths.add(ensureNamespace(entry.single.model, namespace));
                }
            }
        }
        if (bs.multipart != null) {
            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                if (mpc.apply != null) {
                    if (mpc.apply.isArray()) {
                        for (BlockstateJson.Variant v : mpc.apply.list) {
                            if (v.model != null) paths.add(ensureNamespace(v.model, namespace));
                        }
                    } else if (mpc.apply.single != null && mpc.apply.single.model != null) {
                        paths.add(ensureNamespace(mpc.apply.single.model, namespace));
                    }
                }
            }
        }
    }

    /**
     * Generates bake tasks for each model (including common rotation combinations).
     */
    private static List<BakeTask> generateTasks(List<String> sortedPaths, @Nullable Map<String, IIcon> iconMap) {
        List<BakeTask> tasks = new ArrayList<>();
        float[] rotations = {0, 90, 180, 270};

        for (String path : sortedPaths) {
            // Base bake (no rotation)
            tasks.add(new BakeTask(path, 0, 0, 0, iconMap));

            // Y-axis rotations
            for (float rotY : rotations) {
                if (rotY != 0) {
                    tasks.add(new BakeTask(path, 0, rotY, 0, iconMap));
                }
            }
        }
        return tasks;
    }
}
