package decok.dfcdvadstf.catframe.model.render;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension;
import decok.dfcdvadstf.catframe.model.render.extension.FaceCullExtension;
import decok.dfcdvadstf.catframe.model.render.extension.GuiLightExtension;
import decok.dfcdvadstf.catframe.model.render.extension.ao.AOComputeExtension;
import decok.dfcdvadstf.catframe.model.render.extension.ao.AOShadeExtension;
import decok.dfcdvadstf.catframe.model.render.extension.tint.TintRenderExtension;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通用模型渲染扩展注册中心。模组通过本类注册 {@link IModelRenderExtension}，
 * 即可对 CatFrame 烘焙出的每一个 quad 进行修改 / 剔除 / 染色 / 调亮等处理。
 *
 * <h3>调用时机</h3>
 * VanillaModelManager 在方块世界渲染 / 物品 GUI 渲染 / 物品手持渲染三处都会
 * 调用 {@link #apply(RenderContext)}，扩展只需根据 {@link RenderContext#phase}
 * 决定是否生效。
 *
 * <h3>内建扩展（按注册顺序）</h3>
 * <ul>
 *   <li>{@link AOComputeExtension}：链头扩展，
 *       在 BLOCK_WORLD 阶段执行逐顶点 AO 计算，将结果写入 {@link RenderContext#aoBrightness} 和
 *       {@link RenderContext#aoColorMul}。</li>
 *   <li>{@link FaceCullExtension}：处理 JSON face 中的 {@code "cullface"}，
 *       根据相邻方块是否完整不透明自动剔除隐藏面。</li>
 *   <li>{@link AOShadeExtension}：处理 JSON element 中的 {@code "ambientocclusion"} 和 {@code "shade"}，
 *       为自发光/均匀照明方块提供亮度和方向阴影控制。</li>
 *   <li>{@link GuiLightExtension}：处理 JSON model 中的 {@code "gui_light"}，
 *       控制方向阴影和 GL_LIGHTING 状态。</li>
 *   <li>{@link TintRenderExtension}：处理 JSON face 中的 {@code "tintindex"}，
 *       自动调用 {@link decok.dfcdvadstf.catframe.model.render.extension.tint.TintRegistry}
 *       为草方块/树叶/水/染色物品等提供生物群系或 NBT 染色。</li>
 *   <li>{@link DisplayTransformExtension}：处理 JSON model 中的 {@code "display"}，
 *       为 GUI 和手持渲染应用对应的 GL 变换（平移、旋转、缩放）。</li>
 * </ul>
 *
 * <h3>优先级契约（v0.5+）</h3>
 * 每个扩展带一个整型 {@code priority}：<b>优先级越小越先执行</b>，同优先级按注册顺序
 * 稳定排列。内建扩展使用负优先级固定位于链头，模组扩展默认 {@link #DEFAULT_PRIORITY}=0
 * （可看到内建扩展的修改结果）；需要插入内建扩展之前的场景可传小于
 * {@link #BUILTIN_PRIORITY_BASE} 的优先级。任意一个扩展把 {@link RenderContext#skip}
 * 置 true 后，链立即终止且该 quad 不被绘制。
 *
 * <h3>错误隔离（v0.5+）</h3>
 * 单个扩展抛出的异常（含 Error）被捕获并记日志，不影响链中后续扩展与整场渲染。
 *
 * <h3>线程安全（v0.5+）</h3>
 * 扩展列表为 {@link CopyOnWriteArrayList}（注册/注销与渲染遍历并发安全），
 * 渲染路径可从任意线程进入（如 Beddium 多线程区块编译）。扩展实例自身必须无
 * 跨线程共享状态（实例字段请改用 ThreadLocal 或写入 {@link RenderContext}）。
 */
@SideOnly(Side.CLIENT)
public final class ModelRenderRegistry {

    /** 模组扩展默认优先级：0 级扩展在内建扩展（负优先级）之后执行。 */
    public static final int DEFAULT_PRIORITY = 0;

    /** 内建扩展链头优先级基数：FaceCull 最先执行，后续内建依次 +1。 */
    public static final int BUILTIN_PRIORITY_BASE = -1000;

    private static final List<ExtEntry> EXTS = new CopyOnWriteArrayList<>();
    private static boolean defaultsInstalled = false;

    private ModelRenderRegistry() {
    }

    /**
     * 注册一个自定义渲染扩展（默认优先级 0，追加到同组链尾）。模组应在客户端 init 阶段调用。
     * 同一实例重复注册 = 以最后一次调用的优先级重新定位。
     */
    public static void register(IModelRenderExtension ext) {
        register(ext, DEFAULT_PRIORITY);
    }

    /**
     * 以显式优先级注册渲染扩展。优先级越小越先执行，同优先级按注册顺序稳定排列。
     * 同一实例重复注册 = 更新优先级并重新定位。
     *
     * @param ext      渲染扩展（null 忽略）
     * @param priority 优先级（负值可插入内建扩展之前）
     */
    public static void register(IModelRenderExtension ext, int priority) {
        ensureDefaults();
        if (ext == null) return;
        // 重复注册先移除旧条目，保证同实例只占一位且以最新优先级生效
        EXTS.removeIf(e -> e.ext == ext);
        insertSorted(ext, priority);
    }

    /**
     * 取消注册一个扩展。
     */
    public static void unregister(IModelRenderExtension ext) {
        if (ext != null) EXTS.removeIf(e -> e.ext == ext);
    }

    /**
     * 当前已注册的扩展数（含内建）。
     */
    public static int size() {
        ensureDefaults();
        return EXTS.size();
    }

    /**
     * 渲染器内部使用：按优先级对 quad 列表调用每个扩展的 {@link IModelRenderExtension#beforePart}。
     * 在各 quad 处理循环之前调用一次。单个扩展异常被隔离（记日志后继续）。
     */
    public static void applyBeforePart(List<BakedQuad> allQuads, RenderPhase phase, BlockStateModelPart part) {
        ensureDefaults();
        for (ExtEntry e : EXTS) {
            try {
                e.ext.beforePart(allQuads, phase, part);
            } catch (Throwable t) {
                logExtError(e, "beforePart", t);
            }
        }
    }

    /**
     * 渲染器内部使用：按优先级调用每个扩展的 {@link IModelRenderExtension#afterPart}。
     * 在各 quad 处理循环之后调用一次。单个扩展异常被隔离（记日志后继续）。
     */
    public static void applyAfterPart() {
        ensureDefaults();
        for (ExtEntry e : EXTS) {
            try {
                e.ext.afterPart();
            } catch (Throwable t) {
                logExtError(e, "afterPart", t);
            }
        }
    }

    /**
     * 渲染器内部使用：按优先级应用所有扩展。
     * 若 {@link RenderContext#skip} 被置 true，链立即终止。
     * 单个扩展异常被隔离（记日志后继续后续扩展）。
     */
    public static void apply(RenderContext ctx) {
        ensureDefaults();
        for (ExtEntry e : EXTS) {
            try {
                e.ext.apply(ctx);
            } catch (Throwable t) {
                logExtError(e, "apply", t);
            }
            if (ctx.skip) return;
        }
    }

    /**
     * 记录单个扩展的异常（错误隔离日志）。
     */
    private static void logExtError(ExtEntry e, String hook, Throwable t) {
        CatFrame.logger.warn("[ModelRenderRegistry] extension {} failed in {}(): {}",
                e.ext.getClass().getName(), hook, t.toString(), t);
    }

    /**
     * 稳定排序插入：找到第一个 priority 大于新条目 priority 的位置插入。
     * 同优先级条目保持既有相对顺序，新条目追加到同组末尾。
     */
    private static void insertSorted(IModelRenderExtension ext, int priority) {
        int insertAt = EXTS.size();
        for (int i = 0; i < EXTS.size(); i++) {
            if (EXTS.get(i).priority > priority) {
                insertAt = i;
                break;
            }
        }
        EXTS.add(insertAt, new ExtEntry(ext, priority));
    }

    /**
     * 安装内建扩展（懒加载，第一次注册或 apply 时触发）。
     * 内建扩展使用 {@link #BUILTIN_PRIORITY_BASE} 起递增的负优先级，固定位于链头且保持既有顺序。
     */
    private static void ensureDefaults() {
        if (defaultsInstalled) return;
        defaultsInstalled = true;
        int p = BUILTIN_PRIORITY_BASE;
        // [S3 修复] FaceCullExtension 在 AOComputeExtension 之前，先剔除不可见面再计算 AO
        insertSorted(new FaceCullExtension(), p++);
        insertSorted(new AOComputeExtension(), p++);
        insertSorted(new AOShadeExtension(), p++);
        insertSorted(new GuiLightExtension(), p++);
        insertSorted(new TintRenderExtension(), p++);
        insertSorted(new DisplayTransformExtension(), p++);
    }

    /**
     * 扩展条目：扩展实例 + 注册优先级。
     */
    private static final class ExtEntry {
        final IModelRenderExtension ext;
        final int priority;

        ExtEntry(IModelRenderExtension ext, int priority) {
            this.ext = ext;
            this.priority = priority;
        }
    }
}
