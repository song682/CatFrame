package decok.dfcdvadstf.catframe.ui.components.toast;

import net.minecraft.client.gui.FontRenderer;

/**
 * <p>
 * 简单 Toast<br>
 * 用于显示简单的单行或双行通知。
 * </p>
 * <p>
 * Simple Toast — for single or double line notifications.
 * </p>
 */
public class SimpleToast extends BaseToast {

    private final long displayTime;
    private String title;
    private String description;

    public SimpleToast(String title) {
        this(title, null, DEFAULT_DISPLAY_TIME);
    }

    public SimpleToast(String title, String description) {
        this(title, description, DEFAULT_DISPLAY_TIME);
    }

    public SimpleToast(String title, String description, long displayTime) {
        this.title = title;
        this.description = description;
        this.displayTime = displayTime;

        if (description != null && !description.isEmpty()) {
            this.height = 48;
        } else {
            this.height = 32;
        }

        int titleWidth = mc.fontRenderer.getStringWidth(title);
        int descWidth = description != null ? mc.fontRenderer.getStringWidth(description) : 0;
        this.width = Math.max(DEFAULT_WIDTH, Math.max(titleWidth, descWidth) + 40);
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        wantedVisibility = fullyVisibleForMs < displayTime ?
            Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    protected void renderContent(FontRenderer fontRenderer, long fullyVisibleForMs) {
        int x = 18;
        int titleY = (description != null && !description.isEmpty()) ? 8 : 12;
        fontRenderer.drawString(title, x, titleY, 0xFFFFFF);

        if (description != null && !description.isEmpty()) {
            fontRenderer.drawString(description, x, 26, 0xAAAAAA);
        }
    }

    public static SimpleToast success(String message) {
        return new SimpleToast("\u00a7a\u2713 " + message);
    }

    public static SimpleToast warning(String message) {
        return new SimpleToast("\u00a7e\u26A0 " + message);
    }

    public static SimpleToast error(String message) {
        return new SimpleToast("\u00a7c\u2717 " + message);
    }

    public static SimpleToast info(String message) {
        return new SimpleToast("\u00a7b\u2139 " + message);
    }
}
