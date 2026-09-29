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
 * Thread-safe baked model cache managing all model baking caches.
 * <p>
 * Based on Guava {@link LoadingCache} (replacing hand-written {@code StampedLock} + {@code LinkedHashMap} LRU):
 * <ul>
 *   <li>{@code maximumSize} — per-segment approximate LRU eviction, auto-removes LRU entry when over capacity</li>
 *   <li>per-key load lock — concurrent miss for same key bakes only once, avoiding race-condition duplicate bakes</li>
 *   <li>{@code recordStats} — vanilla hit/miss/load count stats</li>
 *   <li>Cache miss triggers lazy bake (CacheLoader) — bake on-demand during render and cache</li>
 * </ul>
 * <p>
 * Cache value type is {@code Optional<BlockStateModelPart>}: Guava cache disallows null,
 * so wrap result in {@link Optional}; bake failure (absent) not cached long-term, {@link #get(String)}
 * immediately invalidates to preserve "fail retry" semantics. Public API matches old implementation.
 */
public class BakedModelCache {

/** Global singleton (capacity covers one reload's worth of pre-bake results including Z-axis rotation combos,
 *  headroom prevents LRU eviction causing lazy bakes during render — see ItemBlock-ItemRender-UV-Mismatch-Diagnosis.md). */
    public static final BakedModelCache INSTANCE = new BakedModelCache(4800);

    private final int maxSize;
    private final LoadingCache<String, Optional<BlockStateModelPart>> cache;

    /** Current stitch cycle's IIcon map (set by clear(iconMap), used by lazy bake) */
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
                        float rotX, rotY, rotZ;
                        try {
                            rotX = Float.parseFloat(parts[1]);
                            rotY = Float.parseFloat(parts[2]);
                            rotZ = parts.length > 3 ? Float.parseFloat(parts[3]) : 0f;
                        } catch (NumberFormatException e) {
                            return Optional.absent();
                        }
                        // Call bake pure function (passing current stitch cycle's iconMap)
                        BlockStateModelPart result = BakingCore.bake(parts[0], rotX, rotY, rotZ, iconMap);
                        if (result != null) {
                            CatFrame.logger.debug("[BakedModelCache] lazy bake: {} | quads={}",
                                    key, result.isEmpty() ? 0 : result.getAllQuads().size());
                        }
                        return Optional.fromNullable(result);
                    }
                });
    }

    /**
     * Get baked result. Cache hit returns directly; miss triggers lazy bake (CacheLoader).
     * <p>
     * Render thread call path:
     * <pre>
     *   BlockStateModelPart part = BakedModelCache.INSTANCE.get("block/stone@0@0");
     *   if (part != null) UniformRenderPipeline.renderBlockQuads(part, ...);
     * </pre>
     *
     * @param cacheKey format: "modelPath@rotX@rotY" or "modelPath@rotX@rotY@rotZ"
     * @return baked model part, or null on parse failure
     */
    @Nullable
    public BlockStateModelPart get(String cacheKey) {
        if (cacheKey == null) return null;
        try {
            Optional<BlockStateModelPart> v = cache.getUnchecked(cacheKey);
            if (!v.isPresent()) {
                // Bake failure not cached long-term; retry next time (e.g. iconMap not ready yet)
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
     * Batch pre-bake. Called by async pre-bake pipeline.
     * <p>
     * Triggers bake for each request and writes to cache, skipping already-cached keys.
     *
     * @param requests cacheKey → BakeRequest map
     */
    public void bulkBake(Map<String, BakeRequest> requests) {
        int baked = 0;
        for (Map.Entry<String, BakeRequest> entry : requests.entrySet()) {
            String key = entry.getKey();
            if (cache.getIfPresent(key) != null) continue;
            BakeRequest req = entry.getValue();
            BlockStateModelPart result = BakingCore.bake(req.modelPath, req.rotX, req.rotY, req.rotZ, this.iconMap);
            if (result != null) {
                cache.put(key, Optional.of(result));
                baked++;
            }
        }
        CatFrame.logger.info("[BakedModelCache] bulk pre-bake: {} new models (total cache: {})",
                baked, cache.size());
    }

    /**
     * Directly write pre-baked results. Used by async pipeline after collecting results for bulk insertion.
     */
    public void bulkPut(Map<String, BlockStateModelPart> results) {
        for (Map.Entry<String, BlockStateModelPart> entry : results.entrySet()) {
            if (entry.getValue() != null) {
                cache.put(entry.getKey(), Optional.of(entry.getValue()));
            }
        }
    }

    /**
     * Clear all caches and set current stitch cycle's IIcon map.
     * <p>
     * On each resource reload, cache holds current cycle's iconMap for lazy bake,
     * instead of reading global static field.
     *
     * @param iconMap current stitch cycle's IIcon map
     */
    public void clear(@Nullable Map<String, IIcon> iconMap) {
        cache.invalidateAll();
        this.iconMap = iconMap;
        CatFrame.logger.info("[BakedModelCache] cache cleared | iconMap.size={}",
                iconMap != null ? iconMap.size() : 0);
    }

    /**
     * Clear all caches (without updating iconMap).
     */
    public void clear() {
        clear(this.iconMap);
    }

    /**
     * Get currently stored IIcon map.
     */
    @Nullable
    public Map<String, IIcon> getIconMap() {
        return iconMap;
    }

    /**
     * Get current cache entry count.
     */
    public int size() {
        return (int) cache.size();
    }

    /**
     * Get lazy bake trigger count since last clear (Guava load count).
     */
    public int getLazyBakeCount() {
        return (int) cache.stats().loadCount();
    }

    /**
     * Get cache hit count.
     */
    public long getHitCount() {
        return cache.stats().hitCount();
    }

    /**
     * Get cache miss count.
     */
    public long getMissCount() {
        return cache.stats().missCount();
    }

    /**
     * Dump cache statistics.
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
     * Parse cacheKey into [modelPath, rotX, rotY] or [modelPath, rotX, rotY, rotZ].
     * cacheKey format: "modelPath@rotX@rotY" or "modelPath@rotX@rotY@rotZ"
     * Backward compatible with old 3-segment format.
     */
    @Nullable
    private static String[] parseCacheKey(String cacheKey) {
        // Find all '@' separators from right to left
        int lastAt = cacheKey.lastIndexOf('@');
        if (lastAt < 0) return null;
        int secondLastAt = cacheKey.lastIndexOf('@', lastAt - 1);
        if (secondLastAt < 0) return null;

        // Check for third '@' (Z rotation)
        int thirdLastAt = cacheKey.lastIndexOf('@', secondLastAt - 1);
        if (thirdLastAt >= 0) {
            // 4-segment format: modelPath@rotX@rotY@rotZ
            String modelPath = cacheKey.substring(0, thirdLastAt);
            String rotX = cacheKey.substring(thirdLastAt + 1, secondLastAt);
            String rotY = cacheKey.substring(secondLastAt + 1, lastAt);
            String rotZ = cacheKey.substring(lastAt + 1);
            return new String[]{modelPath, rotX, rotY, rotZ};
        } else {
            // 3-segment format (backward compat): modelPath@rotX@rotY
            String modelPath = cacheKey.substring(0, secondLastAt);
            String rotX = cacheKey.substring(secondLastAt + 1, lastAt);
            String rotY = cacheKey.substring(lastAt + 1);
            return new String[]{modelPath, rotX, rotY};
        }
    }

    /**
     * Build cacheKey (no Z rotation, backward compat).
     */
    public static String buildKey(String modelPath, float rotX, float rotY) {
        return modelPath + "@" + formatRot(rotX) + "@" + formatRot(rotY);
    }

    /**
     * Build cacheKey (with Z rotation).
     * float formatting: integer angles output without decimal (e.g. 90), non-integers with decimal (e.g. 22.5).
     */
    public static String buildKey(String modelPath, float rotX, float rotY, float rotZ) {
        return modelPath + "@" + formatRot(rotX) + "@" + formatRot(rotY) + "@" + formatRot(rotZ);
    }

    /**
     * Format rotation angle as compact string: integers without decimal, non-integers with significant digits.
     */
    private static String formatRot(float v) {
        if (v == Math.floor(v) && !Float.isInfinite(v)) {
            return Integer.toString((int) v);
        }
        return Float.toString(v);
    }

    /**
     * Bake request data.
     */
    public static class BakeRequest {
        public final String modelPath;
        public final float rotX;
        public final float rotY;
        public final float rotZ;

        public BakeRequest(String modelPath, float rotX, float rotY, float rotZ) {
            this.modelPath = modelPath;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
        }

        public static BakeRequest of(String modelPath) {
            return new BakeRequest(modelPath, 0, 0, 0);
        }

        public static BakeRequest of(String modelPath, float rotY) {
            return new BakeRequest(modelPath, 0, rotY, 0);
        }

        public static BakeRequest of(String modelPath, float rotX, float rotY) {
            return new BakeRequest(modelPath, rotX, rotY, 0);
        }

        public static BakeRequest of(String modelPath, float rotX, float rotY, float rotZ) {
            return new BakeRequest(modelPath, rotX, rotY, rotZ);
        }
    }
}
