package decok.dfcdvadstf.catframe.model.core.async;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import decok.dfcdvadstf.catframe.CatFrame;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Globally shared bake thread pool based on Guava {@link ListeningExecutorService}.
 * <ul>
 *   <li>async pre-baking by {@link decok.dfcdvadstf.catframe.model.core.async.AsyncBakePipeline}</li>
 *   <li>parallel multi-namespace loading by {@link decok.dfcdvadstf.catframe.model.ModelManagerDataLoader}</li>
 * </ul>
 * <p>
 * The thread count reuses the original {@code Supervisor} worker count
 * {@code max(1, availableProcessors-1)}, and daemon threads are used to avoid blocking JVM exit.
 * The whole pool is lazily initialized and reusable.
 */
public final class RenderExecutors {

    private RenderExecutors() {}

    private static volatile ListeningExecutorService instance;

    /** Daemon thread factory; threads are named CatFrame-Bake-N. */
    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger idx = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "CatFrame-Bake-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    };

    /**
     * Returns the shared thread pool (lazily initialized, thread-safe).
     */
    public static ListeningExecutorService get() {
        ListeningExecutorService local = instance;
        if (local == null) {
            synchronized (RenderExecutors.class) {
                local = instance;
                if (local == null) {
                    int n = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
                    ExecutorService base = Executors.newFixedThreadPool(n, THREAD_FACTORY);
                    local = MoreExecutors.listeningDecorator(base);
                    instance = local;
                    CatFrame.logger.info("[RenderExecutors] initialized thread pool with {} workers", n);
                }
            }
        }
        return local;
    }

    /**
     * Shuts down the thread pool. Called on mod unload or game shutdown.
     */
    public static synchronized void shutdown() {
        if (instance != null) {
            instance.shutdownNow();
            try {
                instance.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            instance = null;
            CatFrame.logger.info("[RenderExecutors] thread pool shut down");
        }
    }
}
