package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.overlay.Overlay;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayContext;
import decok.dfcdvadstf.catframe.ui.overlay.ScreenAnchor;

public class TitleOverlay implements Overlay {

    public static final TitleOverlay INSTANCE = new TitleOverlay();

    /** Total display duration in ticks (3s) / 总显示时长(ticks，3秒) */
    public static final int DISPLAY_TICKS = 60;

    /** Fade-out window in ticks (last 1s) / 淡出窗口(ticks，最后1秒) */
    public static final int FADE_TICKS = 20;

    @Override
    public OverlayContext getContext(){
        return OverlayContext.HUD;
    }

    @Override
    public ScreenAnchor getAnchor() {
        return ScreenAnchor.CENTER;
    }

    @Override
    public int getOffsetX() {
        return 0;
    }

    @Override
    public int getOffsetY() {
        return 0;
    }

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public void setX(int x) {

    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public void setY(int y) {

    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public void setVisible(boolean visible) {

    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void setActive(boolean active) {

    }
}
