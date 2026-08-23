package decok.dfcdvadstf.catframe.datagen;

import java.util.concurrent.CompletableFuture;

/**
 * Data provider contract: generates one category of JSON resources.
 * <p>
 * Mirrors the modern Minecraft {@code DataProvider} shape: each provider owns
 * one resource category (blockstates, models, item states, tags...), collects
 * entries locally and writes them through {@link PackOutput} + {@link CachedOutput}.
 * Returning a future allows the {@link DataGenerator} to schedule providers in
 * parallel and to aggregate timing statistics.
 * <p>
 * 数据提供者契约：负责生成某一类 JSON 资源。
 * 与高版本 Minecraft {@code DataProvider} 同构：每个 provider 掌管一类资源
 * （blockstate、模型、items 决策树、tag...），本地收集条目后经
 * {@link PackOutput} + {@link CachedOutput} 写出。返回 future 便于
 * {@link DataGenerator} 并行调度与耗时统计。
 */
public interface DataProvider {

    /**
     * Generate all entries of this provider's category.
     *
     * @param output the pack output (asset root + per-category path providers)
     * @param cache  shared output cache (thread-safe; also collects write stats)
     * @return a future completing when all files of this provider have been written
     */
    CompletableFuture<?> run(PackOutput output, CachedOutput cache);

    /**
     * Human-readable provider name for logs and statistics.
     *
     * @return provider display name
     */
    String getName();
}
