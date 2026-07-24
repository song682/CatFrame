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
     * 提交结果：汇聚 future + 起始时间 + 任务数。dispatch 返回 null 表示无模型可烘焙。
     */
    private static final class Dispatch {
        final ListenableFuture<List<BakeResult>> all;
        final long startTime;
        final int totalTasks;

        Dispatch(ListenableFuture<List<BakeResult>> all, long startTime, int totalTasks) {
            this.all = all;
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
        final Dispatch d = dispatch(iconMap);
        if (d == null) return;

        try {
            // 屏障：阻塞至所有并行烘焙任务完成（后台线程池并行执行）
            List<BakeResult> results = d.all.get(BAKE_BARRIER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // 同步切换：在本线程灌入缓存，返回后缓存即就绪（不依赖异步回调的时序）
            applyResults(results, d.startTime);
            CatFrame.logger.info("[AsyncBake] barrier complete (blocking) | iconMap.size={}",
                    iconMap != null ? iconMap.size() : 0);
        } catch (TimeoutException te) {
            CatFrame.logger.warn("[AsyncBake] barrier timed out after {}s ({} tasks); "
                    + "falling back to lazy baking", BAKE_BARRIER_TIMEOUT_SECONDS, d.totalTasks);
            // 不取消：后台任务继续跑，完成后由异步回调灌入缓存；同时懒烘焙兜底
            Futures.addCallback(d.all, new FutureCallback<List<BakeResult>>() {
                @Override
                public void onSuccess(List<BakeResult> results) {
                    applyResults(results, d.startTime);
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
        Set<String> modelPaths = collectModelPaths();
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

        return new Dispatch(Futures.allAsList(futures), startTime, totalTasks);
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
     * 收集所有需要烘焙的模型路径。
     * 从 loadedBlockstates / loadedMappings / loadedItemStates / interfaceItemStates 中提取。
     */
    private static Set<String> collectModelPaths() {
        Set<String> paths = new LinkedHashSet<>();

        // 从 blockstates 收集
        for (Map<String, BlockstateJson> nsMap : ModelManagerDataLoader.loadedBlockstates.values()) {
            for (BlockstateJson bs : nsMap.values()) {
                collectPathsFromBlockstate(bs, paths);
            }
        }

        // 从 model_mappings 收集
        for (VanillaModelManager.ModelMappings mappings : ModelManagerDataLoader.loadedMappings.values()) {
            if (mappings.blocks != null) {
                paths.addAll(mappings.blocks.values());
            }
            if (mappings.items != null) {
                paths.addAll(mappings.items.values());
            }
        }

        // 从 ItemState 决策树收集所有引用的模型路径
        for (Map<String, ItemStateNode> nsItemStates : ModelManagerDataLoader.loadedItemStates.values()) {
            for (ItemStateNode root : nsItemStates.values()) {
                root.collectModelPaths(paths);
            }
        }

        // 从 IItemState 的 ModernItem 收集（双模型物品的 hand 模型等）
        for (Object obj : ModelManagerDataLoader.interfaceItemStates.keySet()) {
            if (obj instanceof ModernItem) {
                ModernItem mi = (ModernItem) obj;
                String modelPath = mi.getModelPath();
                if (modelPath != null) {
                    paths.add(modelPath);
                }
                if (mi.hasDualModels()) {
                    paths.add(mi.getHandModelPath());
                }
            }
        }

        return paths;
    }

    private static void collectPathsFromBlockstate(BlockstateJson bs, Set<String> paths) {
        if (bs.variants != null) {
            for (BlockstateJson.VariantEntry entry : bs.variants.values()) {
                if (entry.isArray()) {
                    for (BlockstateJson.Variant v : entry.list) {
                        if (v.model != null) paths.add(v.model);
                    }
                } else if (entry.single != null && entry.single.model != null) {
                    paths.add(entry.single.model);
                }
            }
        }
        if (bs.multipart != null) {
            for (BlockstateJson.MultipartCase mpc : bs.multipart) {
                if (mpc.apply != null && mpc.apply.model != null) {
                    paths.add(mpc.apply.model);
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
