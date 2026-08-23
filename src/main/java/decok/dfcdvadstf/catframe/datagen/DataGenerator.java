package decok.dfcdvadstf.catframe.datagen;

import com.google.gson.JsonElement;
import decok.dfcdvadstf.catframe.datagen.util.JsonWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Schedules {@link DataProvider}s and aggregates per-provider statistics.
 * <p>
 * Providers run in parallel on a shared thread pool (bounded by the number of
 * cores and providers); each provider returns a future so write throughput
 * scales with core count — important at million-scale resource counts.
 * <p>
 * {@link #saveAll} mirrors the modern Minecraft {@code DataProvider.saveAll}:
 * it serializes a collected map and writes every entry through the cache in
 * parallel.
 * <p>
 * 调度 {@link DataProvider} 并汇总各 provider 统计。Provider 在共享线程池上
 * 并行执行（上限取核数与 provider 数较小者）；每个 provider 返回 future，
 * 使写盘吞吐随核数扩展——百万级资源规模下至关重要。
 * {@link #saveAll} 仿照高版本 {@code DataProvider.saveAll}：将收集的 Map
 * 序列化并通过缓存并行写出每个条目。
 */
public class DataGenerator {

    /** Default thread pool size for parallel provider execution. */
    private static final int PARALLELISM = Math.max(2,
            Runtime.getRuntime().availableProcessors());

    private final Map<String, DataProvider> providers = new LinkedHashMap<>();

    /**
     * Register a provider. Later registrations overwrite same-named ones.
     *
     * @param provider provider to add
     * @return this generator (chainable)
     */
    public DataGenerator addProvider(DataProvider provider) {
        providers.put(provider.getName(), provider);
        return this;
    }

    /**
     * Run all registered providers against the given asset root.
     * Blocks until every provider has finished writing.
     *
     * @param assetRoot the {@code assets} output directory
     * @return run summary with per-provider statistics
     */
    public RunResult runAll(Path assetRoot) {
        return runAll(new PackOutput(assetRoot));
    }

    /**
     * Run all registered providers against the given pack output.
     * Blocks until every provider has finished writing.
     *
     * @param output pack output
     * @return run summary with per-provider statistics
     */
    public RunResult runAll(PackOutput output) {
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(PARALLELISM, Math.max(1, providers.size())), runnable -> {
                    Thread t = new Thread(runnable, "CatFrame-Datagen");
                    t.setDaemon(true);
                    return t;
                });
        try {
            List<ProviderStat> stats = new ArrayList<>();
            List<CompletableFuture<ProviderStat>> futures = new ArrayList<>();
            for (DataProvider provider : providers.values()) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> runOne(provider, output), pool));
            }
            for (CompletableFuture<ProviderStat> future : futures) {
                stats.add(future.join());
            }
            return new RunResult(stats);
        } finally {
            pool.shutdown();
        }
    }

    private ProviderStat runOne(DataProvider provider, PackOutput output) {
        long start = System.nanoTime();
        CachedOutput cache = new CachedOutput();
        Throwable error = null;
        try {
            provider.run(output, cache).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = e;
        } catch (ExecutionException e) {
            error = e.getCause() != null ? e.getCause() : e;
        } catch (CompletionException e) {
            error = e.getCause() != null ? e.getCause() : e;
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        return new ProviderStat(provider.getName(), cache.writtenCount(),
                cache.skippedCount(), millis, error);
    }

    /**
     * Serialize a collected map and write every entry in parallel.
     * <p>
     * Equivalent to the modern Minecraft {@code DataProvider.saveAll}:
     * each key maps to a serializable value; the path getter derives the target
     * path; values are serialized through {@link JsonWriter}.
     *
     * @param cache       shared output cache (thread-safe)
     * @param contents    collected entries (key → value)
     * @param serializer  value → JSON tree
     * @param pathGetter  key → target path
     * @param <K>         entry key type
     * @param <V>         entry value type
     * @return future completing when all entries have been written
     */
    public static <K, V> CompletableFuture<?> saveAll(CachedOutput cache,
                                                       Map<K, V> contents,
                                                       Function<V, JsonElement> serializer,
                                                       Function<K, Path> pathGetter) {
        List<CompletableFuture<?>> writes = new ArrayList<>(contents.size());
        for (Map.Entry<K, V> entry : contents.entrySet()) {
            Path path = pathGetter.apply(entry.getKey());
            JsonElement json = serializer.apply(entry.getValue());
            writes.add(CompletableFuture.runAsync(() ->
                    JsonWriter.write(path, json, cache), ioPool()));
        }
        return CompletableFuture.allOf(writes.toArray(new CompletableFuture<?>[0]));
    }

    private static ExecutorService IO_POOL;

    private static synchronized ExecutorService ioPool() {
        if (IO_POOL == null) {
            IO_POOL = Executors.newFixedThreadPool(PARALLELISM, runnable -> {
                Thread t = new Thread(runnable, "CatFrame-Datagen-IO");
                t.setDaemon(true);
                return t;
            });
        }
        return IO_POOL;
    }

    /**
     * Per-provider statistics of one run.
     */
    public static class ProviderStat {
        public final String name;
        public final int written;
        public final int skipped;
        public final long millis;
        public final Throwable error;

        ProviderStat(String name, int written, int skipped, long millis, Throwable error) {
            this.name = name;
            this.written = written;
            this.skipped = skipped;
            this.millis = millis;
            this.error = error;
        }
    }

    /**
     * Aggregated run result: provider stats plus success flag.
     */
    public static class RunResult {
        public final List<ProviderStat> providers;

        RunResult(List<ProviderStat> providers) {
            this.providers = providers;
        }

        /**
         * @return {@code true} if every provider completed without error
         */
        public boolean success() {
            for (ProviderStat stat : providers) {
                if (stat.error != null) return false;
            }
            return true;
        }

        /**
         * First provider error, if any.
         *
         * @return error or {@code null}
         */
        public Throwable firstError() {
            for (ProviderStat stat : providers) {
                if (stat.error != null) return stat.error;
            }
            return null;
        }

        /**
         * Total number of files written across all providers.
         *
         * @return written count
         */
        public int totalWritten() {
            int total = 0;
            for (ProviderStat stat : providers) {
                total += stat.written;
            }
            return total;
        }

        /**
         * Total number of files skipped (unchanged content) across all providers.
         *
         * @return skipped count
         */
        public int totalSkipped() {
            int total = 0;
            for (ProviderStat stat : providers) {
                total += stat.skipped;
            }
            return total;
        }
    }
}
