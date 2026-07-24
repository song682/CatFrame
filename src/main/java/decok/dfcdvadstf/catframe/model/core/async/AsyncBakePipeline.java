package decok.dfcdvadstf.catframe.model.core.async;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.core.baking.BakingCore;
import decok.dfcdvadstf.catframe.model.impl.ModernItem;
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
 * 异步预烘焙管线，使用 Guava {@link ListenableFuture} 编排。
 * <p>
 * 架构（Fork-Join 模式，替代原 Akka Actor Per-Stage）：
 * <pre>
 *   triggerAsyncBake()
 *   ├── 收集所有待烘焙模型路径（collectModelPaths）
 *   ├── 拓扑排序（ModelResolver.topologicalSort）
 *   ├── 生成 BakeTask（每模型 × 常见旋转组合）
 *   ├── 每个任务提交为 ListenableFuture（RenderExecutors 共享池并行执行 BakingCore.bake）
 *   └── Futures.allAsList + FutureCallback → 完成后 bulkPut 灌入 BakedModelCache
 * </pre>
 * <p>
 * 在 {@link VanillaTextureTracker#onTextureStitchPost} 中被触发，
 * 此时 iconMap 已通过参数传入，烘焙可以正确解析纹理引用。
 * <p>
 * {@link BakingCore#bake} 为纯函数、无状态，天然可被线程池并发调用。
 */
public class AsyncBakePipeline {

    // ==================== 任务/结果载体（普通 POJO） ====================

    /** 烘焙任务：一个模型 + 旋转组合。 */
    public static class BakeTask {
        public final String modelPath;
        public final int rotX;
        public final int rotY;
        @Nullable
        public final Map<String, IIcon> iconMap;

        public BakeTask(String modelPath, int rotX, int rotY, @Nullable Map<String, IIcon> iconMap) {
            this.modelPath = modelPath;
            this.rotX = rotX;
            this.rotY = rotY;
            this.iconMap = iconMap;
        }
    }

    /** 烘焙结果：cacheKey → 烘焙部件（part 可能为 null 表示烘焙失败）。 */
    public static class BakeResult {
        public final String cacheKey;
        @Nullable
        public final BlockStateModelPart part;

        public BakeResult(String cacheKey, @Nullable BlockStateModelPart part) {
            this.cacheKey = cacheKey;
            this.part = part;
        }
    }

    // ==================== 公共 API ====================

    /**
     * 屏障等待超时（秒）。并行烘焙数千个模型通常远快于此；
     * 超时仅作为防止永久挂起的安全阀，超时后退化到 {@link BakedModelCache} 懒烘焙。
     */
    private static final long BAKE_BARRIER_TIMEOUT_SECONDS = 60L;

    /**
     * 提交结果：汇聚 future + 起始时间 + 任务数 + 个体 future 列表（进度上报用）。
     * dispatch 返回 null 表示无模型可烘焙。
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
     * 触发异步预烘焙（非阻塞）。由旧调用方保留。
     * <p>
     * 提交所有烘焙任务到共享线程池后立即返回，烘焙在后台并行执行，
     * 全部完成后结果自动灌入 {@link BakedModelCache}。
     *
     * @param iconMap 当前 stitch 周期的 IIcon 映射
     */
    public static void triggerAsyncBake(@Nullable final Map<String, IIcon> iconMap) {
        final Dispatch d = dispatch(iconMap);
        if (d == null) return;

        // 汇聚所有结果 → 灌入缓存（回调在池线程执行）
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
     * 触发预烘焙并阻塞至全部完成（"异步准备，同步切换"）。
     * <p>
     * 对标高版本 {@code ModelManager.reload()} 的 {@code preparationBarrier::wait} + {@code apply()}：
     * 烘焙仍在后台线程池<b>并行</b>执行（异步准备），但本方法阻塞调用线程（stitch 主线程）
     * 直到所有模型烤完，并<b>同步</b>将结果灌入 {@link BakedModelCache}（同步切换），
     * 保证返回后渲染系统永远读到已烤好的 {@link BlockStateModelPart}，运行时零现场烘焙。
     * <p>
     * {@link BakedModelCache} 的首帧懒烘焙由此降级为极少触发的安全网（仅覆盖
     * 预烤集合未包含的模型/旋转组合，或屏障超时的兜底）。
     * <p>
     * 注意：{@link BakingCore#bake} 为纯函数、不依赖 GL 上下文，可在后台线程安全执行，
     * 因此主线程阻塞等待不会造成死锁。
     *
     * @param iconMap 当前 stitch 周期的 IIcon 映射
     */
    public static void triggerBakeBlocking(@Nullable final Map<String, IIcon> iconMap) {
        // ===== 1. 分阶段收集模型路径 =====
        Set<String> blockPaths = collectBlockModelPaths();
        Set<String> itemPaths = collectItemModelPaths();
        Set<String> allPaths = new LinkedHashSet<>(blockPaths);
        allPaths.addAll(itemPaths);

        if (allPaths.isEmpty()) {
            CatFrame.logger.info("[AsyncBake] no models to bake, skip");
            return;
        }

        // ===== 2. 拓扑排序 + 生成任务 =====
        List<String> sorted = ModelResolver.topologicalSort(allPaths);
        List<BakeTask> tasks = generateTasks(sorted, iconMap);
        final int totalTasks = tasks.size();
        final long startTime = System.nanoTime();

        CatFrame.logger.info("[AsyncBake] dispatching {} bake tasks for {} models",
                totalTasks, allPaths.size());

        // ===== 3. 双层进度报告器 =====
        final BakeProgressReporter progress = new BakeProgressReporter();
        progress.begin(3); // 3 phases: search blocks, search items, baking
        progress.stepPhase("Searching BlockState Models");
        progress.stepPhase("Searching Item Models");
        progress.beginBaking(totalTasks);

        // ===== 4. 提交所有烘焙任务到线程池 =====
        List<ListenableFuture<BakeResult>> futures = new ArrayList<>(totalTasks);
        for (final BakeTask task : tasks) {
            ListenableFuture<BakeResult> f = RenderExecutors.get().submit(new Callable<BakeResult>() {
                @Override
                public BakeResult call() {
                    String cacheKey = BakedModelCache.buildKey(task.modelPath, task.rotX, task.rotY);
                    BlockStateModelPart part = BakingCore.bake(task.modelPath, task.rotX, task.rotY, task.iconMap);
                    return new BakeResult(cacheKey, part);
                }
            });
            // 完成回调 → 推入 reporter 队列
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

        // ===== 5. 主线程轮询进度 =====
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

        // ===== 6. 收集结果并灌入缓存 =====
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
     * 收集模型路径、生成任务并提交到线程池，返回汇聚 future。
     * 无模型可烘焙时返回 {@code null}。
     */
    @Nullable
    private static Dispatch dispatch(@Nullable final Map<String, IIcon> iconMap) {
        // 1. 收集所有待烘焙的模型路径
        Set<String> modelPaths = new LinkedHashSet<>(collectBlockModelPaths());
        modelPaths.addAll(collectItemModelPaths());
        if (modelPaths.isEmpty()) {
            CatFrame.logger.info("[AsyncBake] no models to bake, skip");
            return null;
        }

        // 2. 拓扑排序
        List<String> sorted = ModelResolver.topologicalSort(modelPaths);

        // 3. 生成烘焙任务（每个模型 × 常见旋转组合）
        List<BakeTask> tasks = generateTasks(sorted, iconMap);
        final int totalTasks = tasks.size();
        final long startTime = System.nanoTime();

        CatFrame.logger.info("[AsyncBake] dispatching {} bake tasks for {} models",
                totalTasks, modelPaths.size());

        // 4. 每个任务提交为 ListenableFuture（BakingCore.bake 纯函数，可并发）
        List<ListenableFuture<BakeResult>> futures = new ArrayList<>(totalTasks);
        for (final BakeTask task : tasks) {
            ListenableFuture<BakeResult> f = RenderExecutors.get().submit(new Callable<BakeResult>() {
                @Override
                public BakeResult call() {
                    String cacheKey = BakedModelCache.buildKey(task.modelPath, task.rotX, task.rotY);
                    BlockStateModelPart part = BakingCore.bake(task.modelPath, task.rotX, task.rotY, task.iconMap);
                    return new BakeResult(cacheKey, part);
                }
            });
            futures.add(f);
        }

        return new Dispatch(Futures.allAsList(futures), futures, tasks, startTime, totalTasks);
    }

    /**
     * 将烘焙结果批量灌入 {@link BakedModelCache}。异步回调与阻塞屏障共用。
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
     * 关闭底层线程池。在模组卸载或游戏关闭时调用。
     */
    public static void shutdown() {
        RenderExecutors.shutdown();
    }

    // ==================== 内部：任务收集与生成 ====================

    /**
     * 收集方块模型路径（从 blockstates + model_mappings.blocks）。
     * 路径均带 namespace 前缀（如 "minecraft:block/stone"）。
     */
    private static Set<String> collectBlockModelPaths() {
        Set<String> paths = new LinkedHashSet<>();

        // 从 blockstates 收集（namespace 为外层 key）
        for (Map.Entry<String, Map<String, BlockstateJson>> nsEntry : ModelManagerDataLoader.loadedBlockstates.entrySet()) {
            String namespace = nsEntry.getKey();
            for (BlockstateJson bs : nsEntry.getValue().values()) {
                collectPathsFromBlockstate(bs, paths, namespace);
            }
        }

        // 从 model_mappings.blocks 收集
        for (Map.Entry<String, VanillaModelManager.ModelMappings> entry : ModelManagerDataLoader.loadedMappings.entrySet()) {
            String namespace = entry.getKey();
            VanillaModelManager.ModelMappings mappings = entry.getValue();
            if (mappings.blocks != null) {
                for (String path : mappings.blocks.values()) {
                    paths.add(ensureNamespace(path, namespace));
                }
            }
        }

        return paths;
    }

    /**
     * 收集物品模型路径（从 loadedItemStates + interfaceItemStates + model_mappings.items）。
     * 路径均带 namespace 前缀。
     */
    private static Set<String> collectItemModelPaths() {
        Set<String> paths = new LinkedHashSet<>();

        // 从 model_mappings.items 收集
        for (Map.Entry<String, VanillaModelManager.ModelMappings> entry : ModelManagerDataLoader.loadedMappings.entrySet()) {
            String namespace = entry.getKey();
            VanillaModelManager.ModelMappings mappings = entry.getValue();
            if (mappings.items != null) {
                for (String path : mappings.items.values()) {
                    paths.add(ensureNamespace(path, namespace));
                }
            }
        }

        // 从 ItemState 决策树收集所有引用的模型路径
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

        // 从 IItemState 的 ModernItem 收集（双模型物品的 hand 模型等）
        for (Object obj : ModelManagerDataLoader.interfaceItemStates.keySet()) {
            if (obj instanceof ModernItem) {
                ModernItem mi = (ModernItem) obj;
                String modelPath = mi.getModelPath();
                if (modelPath != null) {
                    // ModernItem 通常已带 namespace
                    paths.add(modelPath);
                }
                if (mi.hasDualModels()) {
                    paths.add(mi.getHandModelPath());
                }
            }
        }

        return paths;
    }

    /**
     * 确保模型路径带有 namespace 前缀。
     * 已含 ":" 的路径原样返回，否则补上所属 namespace。
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
                if (mpc.apply != null && mpc.apply.model != null) {
                    paths.add(ensureNamespace(mpc.apply.model, namespace));
                }
            }
        }
    }

    /**
     * 为每个模型生成烘焙任务（包含常见旋转组合）。
     */
    private static List<BakeTask> generateTasks(List<String> sortedPaths, @Nullable Map<String, IIcon> iconMap) {
        List<BakeTask> tasks = new ArrayList<>();
        int[] rotations = {0, 90, 180, 270};

        for (String path : sortedPaths) {
            // 基础烘焙（无旋转）
            tasks.add(new BakeTask(path, 0, 0, iconMap));

            // Y 轴旋转
            for (int rotY : rotations) {
                if (rotY != 0) {
                    tasks.add(new BakeTask(path, 0, rotY, iconMap));
                }
            }
        }
        return tasks;
    }
}
