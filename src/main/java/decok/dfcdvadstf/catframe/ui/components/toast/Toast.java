package decok.dfcdvadstf.catframe.ui.components.toast;

import decok.dfcdvadstf.catframe.ui.components.Renderable;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * High-version style Toast interface.<br>
 * Extends {@link GuiEventListener} and {@link Renderable}, integrated into the unified component system.
 * </p>
 *
 * <p>
 * <strong>BREAKING CHANGE note:</strong>
 * The old {@code render(int, int, float)} rendering contract has been
 * removed; the rendering entry point is unified as
 * {@link Renderable#extractRenderState} (driven frame by frame by
 * {@code ToastManager}); implementations extending {@code AbstractComponent}
 * (such as {@code BaseToast}) should override {@code renderWidget} to perform
 * the actual drawing.
 * {@code FontRenderer} must be obtained through
 * {@code Minecraft.getMinecraft().fontRenderer}.
 * </p>
 */
public interface Toast extends GuiEventListener, Renderable {

    /** Default Toast width */
    int DEFAULT_WIDTH = 160;

    /** Default slot height */
    int SLOT_HEIGHT = 32;

    /**
     * Get the desired visibility state.
     */
    Visibility getWantedVisibility();

    /**
     * Update the Toast state.
     *
     * @param manager           Toast manager
     * @param fullyVisibleForMs fully visible duration in ms
     */
    void update(ToastManager manager, long fullyVisibleForMs);

    /**
     * Get the unique token for deduplication.
     */
    default Object getToken() {
        return this;
    }

    /**
     * Sound played when this Toast slides in (visibility changes to
     * {@link Visibility#SHOW}).
     * Played through the {@code SoundHandler} directly, so it is safe in any render
     * context
     * (GUI, HUD, or the main menu) where {@code thePlayer} may be {@code null}.
     *
     * @return sound ResourceLocation, or {@code null} for silence
     */
    default ResourceLocation getShowSound() {
        return null;
    }

    /**
     * Sound played when this Toast slides out (visibility changes to
     * {@link Visibility#HIDE}).
     *
     * @return sound ResourceLocation, or {@code null} for silence
     */
    default ResourceLocation getHideSound() {
        return null;
    }

    /**
     * The screen corner this Toast slides in from and stacks at.
     *
     * @return the corner, never null; defaults to {@link ToastCorner#TOP_RIGHT}
     */
    default ToastCorner getCorner() {
        return ToastCorner.TOP_RIGHT;
    }

    /**
     * Get the Toast width.
     */
    default int width() {
        return getWidth();
    }

    /**
     * Get the Toast height.
     */
    default int height() {
        return getHeight();
    }

    /**
     * Get the number of occupied slot count.
     */
    default int occupiedSlotCount() {
        return (int) Math.ceil((double) height() / SLOT_HEIGHT);
    }

    /**
     * Calculate the X position (with slide-in animation).
     * Right corners slide in from the right edge, left corners from the left edge,
     * as decided by {@link #getCorner()}.
     *
     * @param screenWidth    screen width
     * @param visiblePortion visible portion (0.0-1.0)
     */
    default float xPos(int screenWidth, float visiblePortion) {
        return getCorner().isRight()
                ? screenWidth - width() * visiblePortion
                : width() * (visiblePortion - 1.0F);
    }

    /**
     * Calculate the Y position.
     * Top corners stack slots downward from the top edge, bottom corners upward
     * from
     * the bottom edge, as decided by {@link #getCorner()}.
     *
     * @param screenHeight   screen height
     * @param firstSlotIndex starting slot index
     */
    default float yPos(int screenHeight, int firstSlotIndex) {
        float slotOffset = firstSlotIndex * height();
        return getCorner().isBottom()
                ? screenHeight - height() - slotOffset
                : slotOffset;
    }

    /**
     * Callback when rendering is finished.
     */
    default void onFinishedRendering() {
    }

    /** Toast visibility enum */
    enum Visibility {
        SHOW,
        HIDE
    }
}
