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

    /** Minimum width when content is short / 内容较少时的最小宽度 */
    private static final int MIN_WIDTH = 80;

    /** Vertical padding (top + bottom) / 垂直内边距(上下合计) */
    private static final int VERTICAL_PADDING = 10;

    /** Horizontal padding (left + right) / 水平内边距(左右合计) */
    private static final int HORIZONTAL_PADDING = 24;

    public SimpleToast(String title, String description, long displayTime) {
        this.title = title;
        this.description = description;
        this.displayTime = displayTime;

        int titleWidth = mc.fontRenderer.getStringWidth(title);
        int descWidth = description != null ? mc.fontRenderer.getStringWidth(description) : 0;
        int textWidth = Math.max(titleWidth, descWidth);

        // Adaptive sizing: shrink when content is short / 自适应尺寸：内容少时缩小
        this.width = Math.max(MIN_WIDTH, textWidth + HORIZONTAL_PADDING);

        if (description != null && !description.isEmpty()) {
            this.height = 10 + mc.fontRenderer.FONT_HEIGHT * 2 + 6;
        } else {
            this.height = VERTICAL_PADDING + mc.fontRenderer.FONT_HEIGHT;
        }
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        wantedVisibility = fullyVisibleForMs < displayTime ?
            Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    protected void renderContent(FontRenderer fontRenderer, long fullyVisibleForMs) {
        int x = HORIZONTAL_PADDING / 2;
        boolean hasDesc = description != null && !description.isEmpty();

        if (hasDesc) {
            int titleY = (height - fontRenderer.FONT_HEIGHT * 2 - 4) / 2;
            fontRenderer.drawString(title, x, titleY, 0xFFFFFF);
            fontRenderer.drawString(description, x, titleY + fontRenderer.FONT_HEIGHT + 4, 0xAAAAAA);
        } else {
            int titleY = (height - fontRenderer.FONT_HEIGHT) / 2;
            fontRenderer.drawString(title, x, titleY, 0xFFFFFF);
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
