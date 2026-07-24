package decok.dfcdvadstf.catframe.model.core.async;

import cpw.mods.fml.common.ProgressManager;
import cpw.mods.fml.common.ProgressManager.ProgressBar;
import decok.dfcdvadstf.catframe.CatFrame;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FML 加载界面双层进度条报告器。
 * <p>
 * 显示效果（SplashProgress 同时渲染两条 bar）：
 * <pre>
 *   ┌─────────────────────────────────────────────┐
 *   │ CatFrame - Loading: Searching BlockState Models │  ← phaseBar (1/3)
 *   │                                             │
 *   │              （搜索阶段无 detailBar）           │
 *   └─────────────────────────────────────────────┘
 *
 *   ┌─────────────────────────────────────────────┐
 *   │ CatFrame - Loading: Baking                  │  ← phaseBar (3/3)
 *   │ Baking: minecraft:block/stone               │  ← detailBar (42/671)
 *   └─────────────────────────────────────────────┘
 * </pre>
 * <p>
 * 线程模型：
 * <ul>
 *   <li>所有 phase/finish 方法在<b>主线程</b>调用</li>
 *   <li>烘焙 worker 通过 {@link #reportBakeComplete(String)} 推送完成通知</li>
 * </ul>
 */
@SuppressWarnings("deprecation") // ProgressManager: FML 标记 deprecated 但实际为稳定公共 API
public class BakeProgressReporter {

    private static final String PHASE_BAR_TITLE = "CatFrame - Loading";
    private static final String DETAIL_BAR_TITLE = "Baking";

    /** 外层：阶段进度条（Searching BlockState Models → Searching Item Models → Baking） */
    private ProgressBar phaseBar;
    private int phaseTotal;
    private int phaseStepped;

    /** 内层：烘焙详情进度条（每步显示 namespace:path） */
    private ProgressBar detailBar;
    private int detailTotal;
    private int detailStepped;

    /** worker 线程推送完成通知的无锁队列 */
    private final ConcurrentLinkedQueue<String> completedNames = new ConcurrentLinkedQueue<>();
    /** worker 线程递增的完成计数 */
    private final AtomicInteger completedCount = new AtomicInteger(0);

    // ==================== 生命周期 ====================

    /**
     * 推送外层阶段进度条。
     *
     * @param phases 总阶段数（通常 3：搜索方块模型 / 搜索物品模型 / 烘焙）
     */
    public void begin(int phases) {
        this.phaseTotal = phases;
        this.phaseStepped = 0;
        this.phaseBar = ProgressManager.push(PHASE_BAR_TITLE, phaseTotal);
    }

    /**
     * 步进外层阶段进度条（搜索阶段用）。
     *
     * @param phase 阶段名称，如 "Searching BlockState Models"、"Searching Item Models"
     */
    public void stepPhase(String phase) {
        if (phaseBar != null && phaseStepped < phaseTotal) {
            phaseBar.step(phase);
            phaseStepped++;
        }
    }

    /**
     * 进入烘焙阶段：步进外层 bar 到 "Baking"，并推送内层详情 bar。
     *
     * @param bakeTaskCount 烘焙任务总数
     */
    public void beginBaking(int bakeTaskCount) {
        // 外层步进到 "Baking"
        stepPhase("Baking");

        // 推送内层详情条
        this.detailTotal = bakeTaskCount;
        this.detailStepped = 0;
        this.detailBar = ProgressManager.push(DETAIL_BAR_TITLE, Math.max(1, bakeTaskCount));
    }

    // ==================== 烘焙阶段（异步上报） ====================

    /**
     * 由 worker 线程在烘焙完成后调用，将模型路径推入队列。
     * 线程安全（ConcurrentLinkedQueue + AtomicInteger）。
     */
    public void reportBakeComplete(String modelPath) {
        completedNames.add(modelPath);
        completedCount.incrementAndGet();
    }

    /**
     * 主线程轮询已完成的烘焙通知并推进<b>内层</b>详情进度条。
     *
     * @return true 表示所有烘焙任务已步进完毕
     */
    public boolean pollAndStep() {
        if (detailBar == null || detailStepped >= detailTotal) return true;

        String name = completedNames.poll();
        if (name != null) {
            detailBar.step(name);
            detailStepped++;
        }
        return detailStepped >= detailTotal;
    }

    /**
     * 正常结束：先弹内层 bar，再弹外层 bar。
     */
    public void finish() {
        if (detailBar != null) {
            ProgressManager.pop(detailBar);
            detailBar = null;
        }
        if (phaseBar != null) {
            ProgressManager.pop(phaseBar);
            phaseBar = null;
        }
    }

    /**
     * 强制结束：填充内层剩余步骤后弹出两层 bar。用于超时/中断场景。
     *
     * @param reason 填充步骤的消息（如 "timeout"、"interrupted"）
     */
    public void finishForcefully(String reason) {
        if (detailBar != null) {
            while (detailStepped < detailTotal) {
                detailBar.step(reason);
                detailStepped++;
            }
            ProgressManager.pop(detailBar);
            detailBar = null;
        }
        // 外层阶段条如果还没步完（理论上不应该，但防御性处理）
        if (phaseBar != null) {
            while (phaseStepped < phaseTotal) {
                phaseBar.step(reason);
                phaseStepped++;
            }
            ProgressManager.pop(phaseBar);
            phaseBar = null;
        }
        CatFrame.logger.warn("[BakeProgress] force-finished: '{}'", reason);
    }

    // ==================== 状态查询 ====================

    public int getDetailTotal() {
        return detailTotal;
    }

    public int getDetailStepped() {
        return detailStepped;
    }

    public boolean isActive() {
        return phaseBar != null || detailBar != null;
    }
}
