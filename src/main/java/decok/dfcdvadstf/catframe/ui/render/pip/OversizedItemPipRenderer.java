package decok.dfcdvadstf.catframe.ui.render.pip;

/**
 * Oversized item PiP renderer — delegates to {@link ItemGuiDrawer} with
 * {@code allowOversized=true} to draw, never throwing {@code WrongUIOverScaleException},
 * never clamping, and not setting a scissor (natural size overflows the slot).
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
