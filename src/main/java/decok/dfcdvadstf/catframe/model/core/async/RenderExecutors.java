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
 * 全局共享的烘焙线程池，基于 Guava {@link ListeningExecutorService}。
 * <ul>
 *   <li>{@link decok.dfcdvadstf.catframe.model.core.async.AsyncBakePipeline} 的异步预烘焙</li>
 *   <li>{@link decok.dfcdvadstf.catframe.model.ModelManagerDataLoader} 的多 namespace 并行加载</li>
 * </ul>
 * <p>
 * 线程数沿用原 {@code Supervisor} 的 worker 计数 {@code max(1, availableProcessors-1)}，
 * 使用 daemon 线程避免阻止 JVM 退出。全池懒初始化、可复用。
 */
public final class RenderExecutors {

    private RenderExecutors() {}

    private static volatile ListeningExecutorService instance;

    /** daemon 线程工厂，线程命名 CatFrame-Bake-N。 */
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
     * 获取共享线程池（懒初始化，线程安全）。
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
     * 关闭线程池。在模组卸载或游戏关闭时调用。
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
