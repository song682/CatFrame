package decok.dfcdvadstf.catframe.ui.components.toast;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 系统 Toast<br>
 * 用于显示系统通知、错误信息等。
 * </p>
 * <p>
 * System Toast — for system notifications, error messages, etc.
 * </p>
 */
public class SystemToast extends BaseToast {

    private static final ResourceLocation SYSTEM_TOAST_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/toast/system.png");

    /** System icon texture / 系统图标纹理 */
    private static final ResourceLocation SYSTEM_ICON_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/toast/icons/system.png");

    /** Icon size in pixels / 图标尺寸(像素) */
    private static final int ICON_SIZE = 16;

    private final SystemToastId id;
    private String title;
    private List<String> messageLines;
    private long lastChanged;
    private boolean changed;
    private boolean forceHide;

    public SystemToast(SystemToastId id, String title, String message) {
        this.id = id;
        this.title = title;
        setBackgroundTexture(SYSTEM_TOAST_TEXTURE);
        this.messageLines = splitText(message != null ? message : "", 180);
        this.width = Math.max(160, 30 + Math.max(
            mc.fontRenderer.getStringWidth(title),
            message == null ? 0 : mc.fontRenderer.getStringWidth(message)
        ));
        this.height = 20 + Math.max(this.messageLines.size(), 1) * 12;
    }

    private List<String> splitText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        FontRenderer font = mc.fontRenderer;

        if (text.isEmpty()) return lines;

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (font.getStringWidth(testLine) > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            } else {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            }
        }

        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        if (changed) {
            lastChanged = fullyVisibleForMs;
            changed = false;
        }

        long timeSinceUpdate = fullyVisibleForMs - lastChanged;
        wantedVisibility = !forceHide && timeSinceUpdate < id.displayTime ?
            Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    protected void renderContent(FontRenderer fontRenderer, long fullyVisibleForMs) {
        // Draw icon / 绘制图标
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.getTextureManager().bindTexture(SYSTEM_ICON_TEXTURE);
        int iconY = (height - ICON_SIZE) / 2;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(1, iconY + ICON_SIZE, 0, 0, 1);
        tess.addVertexWithUV(1 + ICON_SIZE, iconY + ICON_SIZE, 0, 1, 1);
        tess.addVertexWithUV(1 + ICON_SIZE, iconY, 0, 1, 0);
        tess.addVertexWithUV(1, iconY, 0, 0, 0);
        tess.draw();
        GL11.glDisable(GL11.GL_BLEND);

        int titleX = 20;
        int titleY = messageLines.isEmpty() ? 12 : 7;
        fontRenderer.drawString(title, titleX, titleY, 0xFFFFFF);

        for (int i = 0; i < messageLines.size(); i++) {
            int lineY = 18 + i * 12;
            fontRenderer.drawString(messageLines.get(i), titleX, lineY, 0x555555);
        }
    }

    @Override
    public Object getToken() {
        return id;
    }

    public void forceHide() {
        this.forceHide = true;
    }

    public void reset(String title, String message) {
        this.title = title;
        this.messageLines = splitText(message != null ? message : "", 180);
        this.changed = true;
    }

    public static void add(ToastManager toastManager, SystemToastId id, String title, String message) {
        toastManager.addToast(new SystemToast(id, title, message));
    }

    public static void addOrUpdate(ToastManager toastManager, SystemToastId id, String title, String message) {
        SystemToast toast = toastManager.getToast(SystemToast.class, id);
        if (toast == null) {
            add(toastManager, id, title, message);
        } else {
            toast.reset(title, message);
        }
    }

    public static class SystemToastId {
        private final long displayTime;

        public SystemToastId(long displayTime) {
            this.displayTime = displayTime;
        }

        public SystemToastId() {
            this(5000L);
        }
    }
}
