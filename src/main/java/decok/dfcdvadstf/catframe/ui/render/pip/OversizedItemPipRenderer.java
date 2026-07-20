package decok.dfcdvadstf.catframe.ui.render.pip;

/**
 * oversized 物品 PiP 渲染器 — 委托给 {@link ItemGuiDrawer} 以 {@code allowOversized=true}
 * 绘制，永不抛 {@code WrongUIOverScaleException}、不钳制、不设 scissor（自然尺寸溢出槽位）。
 */
public class OversizedItemPipRenderer implements PictureInPictureRenderer<OversizedItemRenderState> {

    private final ItemGuiDrawer drawer;

    public OversizedItemPipRenderer(ItemGuiDrawer drawer) {
        this.drawer = drawer;
    }

    @Override
    public Class<OversizedItemRenderState> getStateClass() {
        return OversizedItemRenderState.class;
    }

    @Override
    public void prepare(OversizedItemRenderState state) {
        drawer.draw(state.getStack(), state.poseMatrix(), true);
    }
}
