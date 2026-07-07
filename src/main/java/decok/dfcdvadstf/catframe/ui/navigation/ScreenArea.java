package decok.dfcdvadstf.catframe.ui.navigation;

/**
 * 屏幕区域 — 任何具有 GUI 边界信息的渲染状态的公共接口。
 * <p>
 * 对标 26.1.2 {@code ScreenArea}，被 {@code GuiRenderState} 用于
 * 自动分层判定（{@code findAppropriateNode}）：根据 bounds 的相交/包含关系
 * 决定新元素应归属到 Node 树的哪一层。
 *
 * <p>所有加入 {@code GuiRenderState} 的渲染状态类型（ItemRenderState、
 * ElementRenderState 等）均需实现此接口。</p>
 */
public interface ScreenArea {

    /**
     * 返回此区域在屏幕上的边界矩形。
     *
     * @return 边界矩形，若此区域无固定边界则返回 {@code null}
     */
    ScreenRectangle bounds();
}
