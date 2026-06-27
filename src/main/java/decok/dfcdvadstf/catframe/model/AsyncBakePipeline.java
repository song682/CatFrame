package decok.dfcdvadstf.catframe.model;

import akka.actor.*;
import akka.routing.*;
import scala.concurrent.duration.Duration;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 异步预烘焙管线，使用 Akka Actor 编排。
 * <p>
 * 架构（Actor Per-Stage 模式）：
 * <pre>
 *   Supervisor Actor
 *   ├── 收集所有待烘焙模型路径
 *   ├── 拓扑排序（ModelResolver.topologicalSort）
 *   ├── 分发 BakeTask 消息给 Worker Actors
 *   └── 收集 BakeResult → 灌入 BakedModelCache
 *
 *   Worker Actor (N 个实例)
 *   └── 收到 BakeTask → BakingCore.bake() → 回复 BakeResult
 * </pre>
 * <p>
 * 在 {@link VanillaTextureTracker#onTextureStitchPost} 中被触发，
 * 此时 globalIconMap 已设置，烘焙可以正确解析纹理引用。
 */
public class AsyncBakePipeline {

    // ==================== 消息协议 ====================

    /** 启动烘焙管线 */
    public static final Object START = "START";

    /** 烘焙任务：发送给 Worker */
    public static class BakeTask {
        public final String modelPath;
        public final int rotX;
        public final int rotY;

        public BakeTask(String modelPath, int rotX, int rotY) {
            this.modelPath = modelPath;
            this.rotX = rotX;
            this.rotY = rotY;
        }
    }

    /** 烘焙结果：Worker → Supervisor */
    public static class BakeResult {
        public final String cacheKey;
        public final BlockStateModelPart part;

        public BakeResult(String cacheKey, BlockStateModelPart part) {
            this.cacheKey = cacheKey;
            this.part = part;
        }
    }

    /** 所有烘焙完成：Supervisor → sender (主线程) */
    public static class BakeComplete {
        public final int totalBaked;
        public final long elapsedMs;

        public BakeComplete(int totalBaked, long elapsedMs) {
            this.totalBaked = totalBaked;
            this.elapsedMs = elapsedMs;
        }
    }

    // ==================== Supervisor Actor ====================

    /**
     * 编排 Actor：收集任务 → 拓扑排序 → 分发给 Workers → 收集结果 → 灌入缓存。
     */
    public static class Supervisor extends UntypedActor {

        private final List<BakeResult> results = new ArrayList<>();
        private int pendingTasks = 0;
        private int totalTasks = 0;
        private long startTime;
        private ActorRef originalSender;

        /** Worker pool */
        private final Router workerRouter;
        private final int workerCount;

        public Supervisor() {
            workerCount = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            List<Routee> routees = new ArrayList<>();
            for (int i = 0; i < workerCount; i++) {
                ActorRef worker = getContext().actorOf(Props.create(Worker.class), "bake-worker-" + i);
                routees.add(new ActorRefRoutee(worker));
            }
            workerRouter = new Router(new akka.routing.RoundRobinRoutingLogic(), routees);
        }

        @Override
        public void onReceive(Object message) {
            if (message == START) {
                startTime = System.nanoTime();
                originalSender = getSender();

                // 1. 收集所有待烘焙的模型路径
                Set<String> modelPaths = collectModelPaths();
                if (modelPaths.isEmpty()) {
                    getSender().tell(new BakeComplete(0, 0), getSelf());
                    getContext().stop(getSelf());
                    return;
                }

                // 2. 拓扑排序
                List<String> sorted = ModelResolver.topologicalSort(modelPaths);

                // 3. 生成烘焙任务（每个模型 × 常见旋转组合）
                List<BakeTask> tasks = generateTasks(sorted);
                totalTasks = tasks.size();
                pendingTasks = totalTasks;

                CatFrame.logger.info("[AsyncBake] dispatching {} bake tasks for {} models ({} workers)",
                        totalTasks, modelPaths.size(), workerCount);

                // 4. 分发给 Workers
                for (BakeTask task : tasks) {
                    workerRouter.route(task, getSelf());
                }

            } else if (message instanceof BakeResult) {
                BakeResult result = (BakeResult) message;
                results.add(result);
                pendingTasks--;

                if (pendingTasks <= 0) {
                    // 所有任务完成 → 灌入 BakedModelCache
                    long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                    Map<String, BlockStateModelPart> resultMap = new HashMap<>();
                    for (BakeResult r : results) {
                        if (r.part != null) {
                            resultMap.put(r.cacheKey, r.part);
                        }
                    }
                    BakedModelCache.INSTANCE.bulkPut(resultMap);

                    CatFrame.logger.info("[AsyncBake] complete: {} models baked in {}ms (cache size: {})",
                            resultMap.size(), elapsed, BakedModelCache.INSTANCE.size());

                    // 通知主线程
                    if (originalSender != null) {
                        originalSender.tell(new BakeComplete(resultMap.size(), elapsed), getSelf());
                    }
                    getContext().stop(getSelf());
                }
            }
        }

        /**
         * 收集所有需要烘焙的模型路径。
         * 从 loadedBlockstates 和 loadedMappings 中提取。
         */
        private Set<String> collectModelPaths() {
            Set<String> paths = new LinkedHashSet<>();

            // 从 blockstates 收集
            for (Map<String, BlockstateJson> nsMap : VanillaModelManager.loadedBlockstates.values()) {
                for (BlockstateJson bs : nsMap.values()) {
                    collectPathsFromBlockstate(bs, paths);
                }
            }

            // 从 model_mappings 收集
            for (VanillaModelManager.ModelMappings mappings : VanillaModelManager.loadedMappings.values()) {
                if (mappings.blocks != null) {
                    paths.addAll(mappings.blocks.values());
                }
                if (mappings.items != null) {
                    paths.addAll(mappings.items.values());
                }
            }

            // 从 IItemJsonModel 收集
            for (Object ijm : VanillaModelManager.interfaceItemModels.values()) {
                String modelPath = ((IItemJsonModel) ijm).getModelPath();
                if (modelPath != null) {
                    paths.add(modelPath);
                }
            }

            return paths;
        }

        private void collectPathsFromBlockstate(BlockstateJson bs, Set<String> paths) {
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
        private List<BakeTask> generateTasks(List<String> sortedPaths) {
            List<BakeTask> tasks = new ArrayList<>();
            int[] rotations = {0, 90, 180, 270};

            for (String path : sortedPaths) {
                // 基础烘焙（无旋转）
                tasks.add(new BakeTask(path, 0, 0));

                // Y 轴旋转
                for (int rotY : rotations) {
                    if (rotY != 0) {
                        tasks.add(new BakeTask(path, 0, rotY));
                    }
                }
            }
            return tasks;
        }
    }

    // ==================== Worker Actor ====================

    /**
     * 工作 Actor：接收 BakeTask，调用 BakingCore.bake()，回复 BakeResult。
     * 纯计算单元，无状态，天然线程安全。
     */
    public static class Worker extends UntypedActor {

        @Override
        public void onReceive(Object message) {
            if (message instanceof BakeTask) {
                BakeTask task = (BakeTask) message;
                String cacheKey = BakedModelCache.buildKey(task.modelPath, task.rotX, task.rotY);

                // 调用烘焙纯函数
                BlockStateModelPart part = BakingCore.bake(task.modelPath, task.rotX, task.rotY);

                // 回复结果给 Supervisor
                getSender().tell(new BakeResult(cacheKey, part), getSelf());
            }
        }
    }

    // ==================== 公共 API ====================

    private static ActorSystem actorSystem;

    /**
     * 触发异步预烘焙。由 {@link VanillaTextureTracker#onTextureStitchPost} 调用。
     * <p>
     * 非阻塞：创建 Supervisor Actor 后立即返回，烘焙在后台线程池中执行。
     * 烘焙完成后结果自动灌入 {@link BakedModelCache}。
     */
    public static void triggerAsyncBake() {
        if (actorSystem == null) {
            actorSystem = ActorSystem.create("CatFrame-BakePipeline");
        }

        ActorRef supervisor = actorSystem.actorOf(
                Props.create(Supervisor.class), "bake-supervisor-" + System.currentTimeMillis());
        supervisor.tell(START, ActorRef.noSender());

        CatFrame.logger.info("[AsyncBake] pipeline triggered");
    }

    /**
     * 关闭 ActorSystem。在模组卸载或游戏关闭时调用。
     */
    public static void shutdown() {
        if (actorSystem != null) {
            actorSystem.shutdown();
            actorSystem.awaitTermination(Duration.create(5, TimeUnit.SECONDS));
            actorSystem = null;
        }
    }
}
