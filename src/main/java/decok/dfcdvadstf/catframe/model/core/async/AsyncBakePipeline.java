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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

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
     * 触发异步预烘焙。由 {@link VanillaTextureTracker#onTextureStitchPost} 调用。
     * <p>
     * 非阻塞：提交所有烘焙任务到共享线程池后立即返回，烘焙在后台并行执行。
     * 全部完成后结果自动灌入 {@link BakedModelCache}。
     * <p>
     * iconMap 作为参数传入（而非读取全局静态字段），避免多 stitch 周期之间的竞态覆盖。
     *
     * @param iconMap 当前 stitch 周期的 IIcon 映射
     */
    public static void triggerAsyncBake(@Nullable final Map<String, IIcon> iconMap) {
        // 1. 收集所有待烘焙的模型路径
        Set<String> modelPaths = collectModelPaths();
        if (modelPaths.isEmpty()) {
            CatFrame.logger.info("[AsyncBake] no models to bake, skip");
            return;
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

        // 5. 汇聚所有结果 → 灌入缓存（回调在池线程执行）
        ListenableFuture<List<BakeResult>> all = Futures.allAsList(futures);
        Futures.addCallback(all, new FutureCallback<List<BakeResult>>() {
            @Override
            public void onSuccess(List<BakeResult> results) {
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

            @Override
            public void onFailure(Throwable t) {
                CatFrame.logger.error("[AsyncBake] pipeline failed: {}", t.getMessage(), t);
            }
        });

        CatFrame.logger.info("[AsyncBake] pipeline triggered | iconMap.size={}",
                iconMap != null ? iconMap.size() : 0);
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
