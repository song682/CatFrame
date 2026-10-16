package decok.dfcdvadstf.catframe.ui.render.pip;

/**
 * Picture-in-picture renderer — counterpart of the modern Minecraft {@code PictureInPictureRenderer<T>}.
 * <p>
 * Each {@link PictureInPictureRenderState} type has a corresponding renderer implementation.
 * {@code GuiGraphicsExtractor} maintains a {@code Class<?> → PictureInPictureRenderer<?>} dispatch
 * table; at the end-of-frame flush it looks up the state's runtime type and calls
 * {@link #prepare(PictureInPictureRenderState)}.
 *
 * @param <T> the state type handled by this renderer
 */
public interface PictureInPictureRenderer<T extends PictureInPictureRenderState> {

    /** The state type handled by this renderer — the key in the dispatch table. */
    Class<T> getStateClass();

    /**
     * Draws a PiP state.
     * <p>
     * Implementations must handle the full sequence themselves: {@code glPushAttrib} →
     * restore the captured modelview matrix snapshot ({@link PipGl#restore}) →
     * GL settings such as depth/lighting → the actual draw → {@code glPopAttrib}/{@code glPopMatrix},
     * keeping GL state consistent before and after drawing (isolated 3D viewport semantics).
     */
    void prepare(T state);
}
