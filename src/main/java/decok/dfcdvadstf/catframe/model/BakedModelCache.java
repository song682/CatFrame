package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.baking.BakingCore;
import decok.dfcdvadstf.catframe.model.core.baking.ModelBaker;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

/**
 * 线程安全的烘焙模型缓存，统一管理所有模型烘焙缓存。
 * <p>
 * 设计借鉴 GTNHLib 的 {@code ThreadsafeCache} 模式：
 * <ul>
 *   <li>{@link StampedLock} 读乐观锁 — 渲染线程绝大多数时候命中缓存，无锁读，零竞争</li>
 *   <li>{@link LinkedHashMap} accessOrder=true — 原生 LRU 淘汰，超过 maxSize 自动移除最久未访问条目</li>
 *   <li>Cache miss 时触发懒烘焙 — 渲染时按需烘焙并缓存，无需在 init 阶段一次性烘焙全部模型</li>
 * </ul>
 * <p>
 * 缓存值类型为 {@link BlockStateModelPart}，这是渲染管线 {@code UniformRenderPipeline} 的直接输入，
 * 避免额外的 {@code fromQuads()} 转换。
 */
public class BakedModelCache {

    /** 全局单例 */
    public static final BakedModelCache INSTANCE = new BakedModelCache(2048);

    private final int maxSize;
    private final LinkedHashMap<String, BlockStateModelPart> cache;
    private final StampedLock lock = new StampedLock();

    /** 当前 stitch 周期的 IIcon 映射（由 clear(iconMap) 设置，懒烘焙时使用） */
    @Nullable
    private volatile Map<String, IIcon> iconMap = null;

    /** 懒烘焙统计 */
    private volatile int lazyBakeCount = 0;

    /** 缓存命中/未命中统计 */
    private volatile long hitCount = 0;
    private volatile long missCount = 0;

    public BakedModelCache(int maxSize) {
        this.maxSize = maxSize;
        // accessOrder=true: 每次 get/put 自动将条目移到末尾（最近访问）
        // removeEldestEntry: 超过 maxSize 时自动淘汰最久未访问的条目
        this.cache = new LinkedHashMap<String, BlockStateModelPart>(64, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BlockStateModelPart> eldest) {
                return size() >= BakedModelCache.this.maxSize;
            }
        };
    }

    /**
     * 获取烘焙结果。优先走缓存（乐观读），miss 时触发懒烘焙。
     * <p>
     * 渲染线程调用路径：
     * <pre>
     *   BlockStateModelPart part = BakedModelCache.INSTANCE.get("block/stone@0@0");
     *   if (part != null) UniformRenderPipeline.renderBlockQuads(part, ...);
     * </pre>
     *
     * @param cacheKey 格式为 "modelPath@rotX@rotY"
     * @return 烘焙后的模型部件，模型解析失败返回 null
     */
    @Nullable
    public BlockStateModelPart get(String cacheKey) {
        if (cacheKey == null) return null;

        // 1. 乐观读（无锁）— 绝大多数时候走这条路径
        long stamp = lock.tryOptimisticRead();
        BlockStateModelPart cached = cache.get(cacheKey);
        if (lock.validate(stamp)) {
            if (cached != null) { hitCount++; return cached; }
        }
        // 乐观读失败（有并发写入）→ 降级为读锁重试
        if (cached != null) { hitCount++; return cached; }

        stamp = lock.readLock();
        try {
            cached = cache.get(cacheKey);
            if (cached != null) { hitCount++; return cached; }
        } finally {
            lock.unlockRead(stamp);
        }

        // 2. Cache miss → 触发懒烘焙
        missCount++;
        return bakeAndCache(cacheKey);
    }

    /**
     * 烘焙单个模型并写入缓存。
     * <p>
     * 委托给 {@link ModelBaker#bake(String, int, int)} 执行实际烘焙，
     * 结果同时写入本缓存和 ModelBaker 的内部缓存。
     */
    @Nullable
    private BlockStateModelPart bakeAndCache(String cacheKey) {
        // 解析 cacheKey → modelPath + rotX + rotY
        String[] parts = parseCacheKey(cacheKey);
        if (parts == null) return null;
        String modelPath = parts[0];
        int rotX, rotY;
        try {
            rotX = Integer.parseInt(parts[1]);
            rotY = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }

        // 调用烘焙纯函数（传递当前 stitch 周期的 iconMap）
        BlockStateModelPart result = BakingCore.bake(modelPath, rotX, rotY, iconMap);

        if (result != null) {
            long stamp = lock.writeLock();
            try {
                cache.put(cacheKey, result);
            } finally {
                lock.unlockWrite(stamp);
            }
            lazyBakeCount++;
            CatFrame.logger.debug("[BakedModelCache] lazy bake: {} | quads={}",
                    cacheKey, result.isEmpty() ? 0 : result.getAllQuads().size());
        }
        return result;
    }

    /**
     * 批量预烘焙。由异步预烘焙管线（Phase 4）调用。
     * <p>
     * 对每个请求执行烘焙并写入缓存，跳过已缓存的 key。
     *
     * @param requests cacheKey → BakeRequest 映射
     */
    public void bulkBake(Map<String, BakeRequest> requests) {
        int baked = 0;
        for (Map.Entry<String, BakeRequest> entry : requests.entrySet()) {
            String key = entry.getKey();
            // 快速检查是否已缓存（读锁）
            long stamp = lock.readLock();
            boolean exists;
            try {
                exists = cache.containsKey(key);
            } finally {
                lock.unlockRead(stamp);
            }
            if (exists) continue;

            BakeRequest req = entry.getValue();
            BlockStateModelPart result = BakingCore.bake(req.modelPath, req.rotX, req.rotY, this.iconMap);
            if (result != null) {
                long ws = lock.writeLock();
                try {
                    cache.put(key, result);
                } finally {
                    lock.unlockWrite(ws);
                }
                baked++;
            }
        }
        CatFrame.logger.info("[BakedModelCache] bulk pre-bake: {} new models (total cache: {})",
                baked, cache.size());
    }

    /**
     * 直接写入已烘焙的结果。用于 Phase 4 异步管线收集完结果后批量灌入。
     */
    public void bulkPut(Map<String, BlockStateModelPart> results) {
        long stamp = lock.writeLock();
        try {
            for (Map.Entry<String, BlockStateModelPart> entry : results.entrySet()) {
                cache.put(entry.getKey(), entry.getValue());
            }
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * 清除所有缓存并设置当前 stitch 周期的 IIcon 映射。
     * <p>
     * 对标高版本 {@code MaterialBaker} 的实例化闭包模式：每次资源重载时，
     * 缓存持有当前周期的 iconMap，懒烘焙时直接使用，而非读全局静态字段。
     *
     * @param iconMap 当前 stitch 周期的 IIcon 映射
     */
    public void clear(@Nullable Map<String, IIcon> iconMap) {
        long stamp = lock.writeLock();
        try {
            cache.clear();
            this.iconMap = iconMap;
            lazyBakeCount = 0;
            hitCount = 0;
            missCount = 0;
        } finally {
            lock.unlockWrite(stamp);
        }
        CatFrame.logger.info("[BakedModelCache] cache cleared | iconMap.size={}",
                iconMap != null ? iconMap.size() : 0);
    }

    /**
     * 清除所有缓存（不更新 iconMap）。
     */
    public void clear() {
        clear(this.iconMap);
    }

    /**
     * 获取当前存储的 IIcon 映射。
     */
    @Nullable
    public Map<String, IIcon> getIconMap() {
        return iconMap;
    }

    /**
     * 获取当前缓存条目数。
     */
    public int size() {
        long stamp = lock.readLock();
        try {
            return cache.size();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * 获取自上次 clear 以来懒烘焙触发的次数。
     */
    public int getLazyBakeCount() {
        return lazyBakeCount;
    }

    /**
     * 获取缓存命中次数。
     */
    public long getHitCount() {
        return hitCount;
    }

    /**
     * 获取缓存未命中次数。
     */
    public long getMissCount() {
        return missCount;
    }

    /**
     * 打印缓存统计信息。
     */
    public void dumpStats() {
        long total = hitCount + missCount;
        double hitRate = total > 0 ? (hitCount * 100.0 / total) : 0;
        CatFrame.logger.info("[BakedModelCache] stats: size={}, hits={}, misses={}, hitRate={:.1f}%, lazyBakes={}",
                size(), hitCount, missCount, hitRate, lazyBakeCount);
    }

    /**
     * 解析 cacheKey 为 [modelPath, rotX, rotY]。
     * cacheKey 格式: "modelPath@rotX@rotY"
     */
    @Nullable
    private static String[] parseCacheKey(String cacheKey) {
        int lastAt = cacheKey.lastIndexOf('@');
        if (lastAt < 0) return null;
        int secondLastAt = cacheKey.lastIndexOf('@', lastAt - 1);
        if (secondLastAt < 0) return null;

        String modelPath = cacheKey.substring(0, secondLastAt);
        String rotX = cacheKey.substring(secondLastAt + 1, lastAt);
        String rotY = cacheKey.substring(lastAt + 1);
        return new String[]{modelPath, rotX, rotY};
    }

    /**
     * 构建 cacheKey。与 {@link ModelBaker} 的 cacheKey 格式保持一致。
     */
    public static String buildKey(String modelPath, int rotX, int rotY) {
        return modelPath + "@" + rotX + "@" + rotY;
    }

    /**
     * 烘焙请求数据。
     */
    public static class BakeRequest {
        public final String modelPath;
        public final int rotX;
        public final int rotY;

        public BakeRequest(String modelPath, int rotX, int rotY) {
            this.modelPath = modelPath;
            this.rotX = rotX;
            this.rotY = rotY;
        }

        public static BakeRequest of(String modelPath) {
            return new BakeRequest(modelPath, 0, 0);
        }

        public static BakeRequest of(String modelPath, int rotY) {
            return new BakeRequest(modelPath, 0, rotY);
        }

        public static BakeRequest of(String modelPath, int rotX, int rotY) {
            return new BakeRequest(modelPath, rotX, rotY);
        }
    }
}
