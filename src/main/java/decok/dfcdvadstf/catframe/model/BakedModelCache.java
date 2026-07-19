package decok.dfcdvadstf.catframe.model;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.baking.BakingCore;
import decok.dfcdvadstf.catframe.model.core.baking.ModelBaker;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * 线程安全的烘焙模型缓存，统一管理所有模型烘焙缓存。
 * <p>
 * 基于 Guava {@link LoadingCache} 实现（替代原手写 {@code StampedLock} + {@code LinkedHashMap} LRU）：
 * <ul>
 *   <li>{@code maximumSize} — 段内近似 LRU 驱逐，超过上限自动移除最久未访问条目</li>
 *   <li>per-key 加载锁 — 同一 key 的并发 miss 只烘焙一次，避免竞态重复烘焙</li>
 *   <li>{@code recordStats} — 原生命中/未命中/加载次数统计</li>
 *   <li>Cache miss 时触发懒烘焙（CacheLoader）— 渲染时按需烘焙并缓存</li>
 * </ul>
 * <p>
 * 缓存值类型为 {@code Optional<BlockStateModelPart>}：Guava 缓存不允许存 null，
 * 用 {@link Optional} 包裹烘焙结果；烘焙失败（absent）不长期缓存，{@link #get(String)}
 * 中立即 invalidate 以保留"失败可重试"语义。对外 API 与旧实现完全一致。
 */
public class BakedModelCache {

    /** 全局单例 */
    public static final BakedModelCache INSTANCE = new BakedModelCache(2048);

    private final int maxSize;
    private final LoadingCache<String, Optional<BlockStateModelPart>> cache;

    /** 当前 stitch 周期的 IIcon 映射（由 clear(iconMap) 设置，懒烘焙时使用） */
    @Nullable
    private volatile Map<String, IIcon> iconMap = null;

    public BakedModelCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(maxSize)
                .recordStats()
                .build(new CacheLoader<String, Optional<BlockStateModelPart>>() {
                    @Override
                    public Optional<BlockStateModelPart> load(String key) {
                        String[] parts = parseCacheKey(key);
                        if (parts == null) return Optional.absent();
                        int rotX, rotY;
                        try {
                            rotX = Integer.parseInt(parts[1]);
                            rotY = Integer.parseInt(parts[2]);
                        } catch (NumberFormatException e) {
                            return Optional.absent();
                        }
                        // 调用烘焙纯函数（传递当前 stitch 周期的 iconMap）
                        BlockStateModelPart result = BakingCore.bake(parts[0], rotX, rotY, iconMap);
                        if (result != null) {
                            CatFrame.logger.debug("[BakedModelCache] lazy bake: {} | quads={}",
                                    key, result.isEmpty() ? 0 : result.getAllQuads().size());
                        }
                        return Optional.fromNullable(result);
                    }
                });
    }

    /**
     * 获取烘焙结果。命中缓存直接返回，miss 时触发懒烘焙（CacheLoader）。
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
        try {
            Optional<BlockStateModelPart> v = cache.getUnchecked(cacheKey);
            if (!v.isPresent()) {
                // 烘焙失败不长期缓存，下次重试（例如 iconMap 尚未就绪）
                cache.invalidate(cacheKey);
                return null;
            }
            return v.get();
        } catch (Exception e) {
            CatFrame.logger.warn("[BakedModelCache] get('{}') failed: {}", cacheKey, e.getMessage());
            cache.invalidate(cacheKey);
            return null;
        }
    }

    /**
     * 批量预烘焙。由异步预烘焙管线调用。
     * <p>
     * 对每个请求触发烘焙并写入缓存，跳过已缓存的 key。
     *
     * @param requests cacheKey → BakeRequest 映射
     */
    public void bulkBake(Map<String, BakeRequest> requests) {
        int baked = 0;
        for (Map.Entry<String, BakeRequest> entry : requests.entrySet()) {
            String key = entry.getKey();
            if (cache.getIfPresent(key) != null) continue;
            BakeRequest req = entry.getValue();
            BlockStateModelPart result = BakingCore.bake(req.modelPath, req.rotX, req.rotY, this.iconMap);
            if (result != null) {
                cache.put(key, Optional.of(result));
                baked++;
            }
        }
        CatFrame.logger.info("[BakedModelCache] bulk pre-bake: {} new models (total cache: {})",
                baked, cache.size());
    }

    /**
     * 直接写入已烘焙的结果。用于异步管线收集完结果后批量灌入。
     */
    public void bulkPut(Map<String, BlockStateModelPart> results) {
        for (Map.Entry<String, BlockStateModelPart> entry : results.entrySet()) {
            if (entry.getValue() != null) {
                cache.put(entry.getKey(), Optional.of(entry.getValue()));
            }
        }
    }

    /**
     * 清除所有缓存并设置当前 stitch 周期的 IIcon 映射。
     * <p>
     * 每次资源重载时，缓存持有当前周期的 iconMap，懒烘焙时直接使用，
     * 而非读全局静态字段。
     *
     * @param iconMap 当前 stitch 周期的 IIcon 映射
     */
    public void clear(@Nullable Map<String, IIcon> iconMap) {
        cache.invalidateAll();
        this.iconMap = iconMap;
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
        return (int) cache.size();
    }

    /**
     * 获取自上次 clear 以来懒烘焙触发的次数（Guava load 次数）。
     */
    public int getLazyBakeCount() {
        return (int) cache.stats().loadCount();
    }

    /**
     * 获取缓存命中次数。
     */
    public long getHitCount() {
        return cache.stats().hitCount();
    }

    /**
     * 获取缓存未命中次数。
     */
    public long getMissCount() {
        return cache.stats().missCount();
    }

    /**
     * 打印缓存统计信息。
     */
    public void dumpStats() {
        CacheStats stats = cache.stats();
        long hitCount = stats.hitCount();
        long missCount = stats.missCount();
        long total = hitCount + missCount;
        double hitRate = total > 0 ? (hitCount * 100.0 / total) : 0;
        CatFrame.logger.info("[BakedModelCache] stats: size={}, hits={}, misses={}, hitRate={}%, lazyBakes={}",
                size(), hitCount, missCount, String.format("%.1f", hitRate), stats.loadCount());
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
