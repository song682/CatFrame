package decok.dfcdvadstf.catframe.model.render.api;

import java.util.List;

/**
 * 渲染分组认领 SPI —— 第三方模组接管整组 flush 的执行端扩展点。
 * <p>
 * 注册：经内部注册表（{@code pipeline.RenderGroupHandlerRegistry}）注册，同一分组
 * 多个 handler 认领时取优先级最小者（仲裁发生在注册/注销时）。认领成功后，该分组的
 * 所有提交项以 {@link RenderSubmitView} 只读视图移交 handler 绘制，CatFrame 不再执行
 * 内建 flush 逻辑（纹理绑定 / GL 状态 / 逐部件生命周期由 handler 全权接管）。
 * <p>
 * <b>契约（v0.5+）</b>：
 * <ul>
 *   <li><b>线程</b>：flush 恒在调用方（渲染）线程执行 —— 不得自行跨线程提交 GL 调用。</li>
 *   <li><b>GL 状态</b>：handler 自管理 —— 组内按需设置（混合/剔除/纹理），组后恢复现场。</li>
 *   <li><b>回退规则</b>：无 handler / claim() 返回 false / flush() 抛异常（含 Error）→
 *       原样回退内建 flushGroup，默认路径零行为变化。</li>
 *   <li><b>inline 提交不可认领</b>：{@link RenderPhase#BLOCK_WORLD} 世界渲染走
 *       {@code flushInline} 内联路径（写入 vanilla chunk 的 Tessellator 大批次），
 *       本 SPI 不适用于该路径 —— 世界几何体须经收集端改道（如渲染作用域）后，
 *       在执行端作用域内才可被认领。</li>
 *   <li><b>claim 时机</b>：claim() 判定在注册/注销仲裁时调用一次；如需运行时动态切换
 *       认领，请重新注册该 handler（重复注册 = 更新优先级并重新仲裁）。</li>
 * </ul>
 *
 * <h3>OIT（Opaque-independent Translucency）边界设计（供外部 OIT 模组对接）</h3>
 * <p>CatFrame 本体的延迟命令管线已具备 OIT-like 前提（提交/执行分离 + 保留式
 * {@code RenderSubmit}）；真正的 OIT 三组件归属外部模组实现：
 * <ul>
 *   <li><b>收集端</b>：{@code RenderWorldEvent.Pre/Post} 是 Forge 标准事件，
 *       外部模组直接订阅即可（CatFrame 无需提供钩子）。</li>
 *   <li><b>执行端</b>：外部模组可在 {@code RenderWorldLastEvent} 中认领半透明分组
 *       （如 {@code catframe:block_atlas_translucent}）→ 本 SPI 的
 *       {@link #claim(RenderTypeKey)} 即执行端接管入口；或自建管线。</li>
 *   <li><b>门控端</b>：模组方块经 ISBRH 接入 VMM 管线、原版方块需外部模组自行 mixin
 *       —— CatFrame 不提供世界门控。</li>
 *   <li><b>后台线程零 GL</b>（Beddium 多线程区块编译）：收集时编译 display list 违反
 *       GL 状态所有权 → 外部实现应 CPU 固化几何 + 渲染线程延迟编译。</li>
 * </ul>
 * <p>
 * Group-claim SPI: an external mod may take over the whole flush of a render
 * group. No handler / failed claim / handler exception all fall back to the
 * built-in flush path; inline BLOCK_WORLD submissions are never claimable.
 */
public interface IRenderGroupHandler {

    /**
     * 认领判定：返回 true 表示本 handler 接管该分组的 flush。
     * 仲裁在注册/注销时执行一次；实现应保持轻量、无副作用。
     *
     * @param type 分组条目句柄（id / atlas / blend / sortKey 只读）
     */
    boolean claim(RenderTypeKey type);

    /**
     * 认领成功后执行整组绘制（按传入顺序处理全部提交项）。
     * 抛出的异常（含 Error）会被隔离并回退内建 flushGroup。
     *
     * @param group 该分组的全部提交项（只读视图，调用方不得修改）
     */
    void flush(List<RenderSubmitView> group);
}
