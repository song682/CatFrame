package decok.dfcdvadstf.catframe.model.render;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.render.extension.FaceCullExtension;
import decok.dfcdvadstf.catframe.model.render.extension.GuiLightExtension;
import decok.dfcdvadstf.catframe.model.render.extension.ao.AOComputeExtension;
import decok.dfcdvadstf.catframe.model.render.extension.ao.AOShadeExtension;
import decok.dfcdvadstf.catframe.model.render.extension.tint.TintRenderExtension;

import java.util.ArrayList;
import java.util.List;

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
 * </ul>
 *
 * <h3>注册顺序</h3>
 * 内建扩展位于链头。模组后注册的扩展可看到内建扩展的修改结果，按 {@link #register(IModelRenderExtension)}
 * 调用顺序依次叠加。任意一个扩展把 {@link RenderContext#skip} 置 true 后，
 * 链立即终止且该 quad 不被绘制。
 */
@SideOnly(Side.CLIENT)
public final class ModelRenderRegistry {
    private static final List<IModelRenderExtension> EXTS = new ArrayList<>();
    private static boolean defaultsInstalled = false;

    private ModelRenderRegistry() {
    }

    /**
     * 注册一个自定义渲染扩展（追加到链尾）。模组应在客户端 init 阶段调用。
     * 同一实例重复注册会被忽略。
     */
    public static void register(IModelRenderExtension ext) {
        ensureDefaults();
        if (ext != null && !EXTS.contains(ext)) {
            EXTS.add(ext);
        }
    }

    /**
     * 取消注册一个扩展。
     */
    public static void unregister(IModelRenderExtension ext) {
        if (ext != null) EXTS.remove(ext);
    }

    /**
     * 当前已注册的扩展数（含内建）。
     */
    public static int size() {
        ensureDefaults();
        return EXTS.size();
    }

    /**
     * 渲染器内部使用：按注册顺序对 quad 列表调用每个扩展的 {@link IModelRenderExtension#beforePart}。
     * 在各 quad 处理循环之前调用一次。
     */
    public static void applyBeforePart(List<BakedQuad> allQuads, RenderPhase phase) {
        ensureDefaults();
        for (int i = 0, n = EXTS.size(); i < n; i++) {
            EXTS.get(i).beforePart(allQuads, phase);
        }
    }

    /**
     * 渲染器内部使用：按注册顺序调用每个扩展的 {@link IModelRenderExtension#afterPart}。
     * 在各 quad 处理循环之后调用一次。
     */
    public static void applyAfterPart() {
        ensureDefaults();
        for (int i = 0, n = EXTS.size(); i < n; i++) {
            EXTS.get(i).afterPart();
        }
    }

    /**
     * 渲染器内部使用：按注册顺序应用所有扩展。
     * 若 {@link RenderContext#skip} 被置 true，链立即终止。
     */
    public static void apply(RenderContext ctx) {
        ensureDefaults();
        for (int i = 0, n = EXTS.size(); i < n; i++) {
            EXTS.get(i).apply(ctx);
            if (ctx.skip) return;
        }
    }

    /**
     * 安装内建扩展（懒加载，第一次注册或 apply 时触发）。
     */
    private static void ensureDefaults() {
        if (defaultsInstalled) return;
        defaultsInstalled = true;
        EXTS.add(0, new AOComputeExtension());
        EXTS.add(new FaceCullExtension());
        EXTS.add(new AOShadeExtension());
        EXTS.add(new GuiLightExtension());
        EXTS.add(new TintRenderExtension());
    }
}
