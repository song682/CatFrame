package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.LoadingDotsText;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * 等待界面组件 —— 在屏幕中央显示一个小面板，带有加载动画和文本。<br>
 * 阻塞所有鼠标和键盘输入。
 * </p>
 * <p>
 * Waiting panel component — displays a small panel in the screen centre with a loading
 * animation and text message. Blocks all mouse and keyboard input.
 * </p>
 *
 * <h3>用法 / Usage</h3>
 * <pre>{@code
 * WaitingPanel waiting = new WaitingPanel(Text.literal("Loading..."));
 * // In drawScreen:
 * waiting.render(mouseX, mouseY, partialTicks);
 * // In mouseClicked / keyTyped: do nothing (panel blocks input)
 * }</pre>
 */
public class WaitingPanel extends AbstractComponent {

    /** Panel dimensions / 面板尺寸 */
    private static final int PANEL_WIDTH = 140;
    private static final int PANEL_HEIGHT = 50;

    /** Colours / 颜色 */
    private static final int OVERLAY_COLOR = 0x80000000;   // semi-transparent black overlay
    private static final int PANEL_BG_COLOR = 0xCC000000;   // panel background
    private static final int PANEL_BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFF888888;       // gray text

    /** The message displayed / 显示的消息 */
    private Text message;

    /**
     * Create a waiting panel with the given message.
     * <p>创建带有指定消息的等待面板。</p>
     *
     * @param message the message to display / 要显示的消息
     */
    public WaitingPanel(Text message) {
        this.message = message;
        this.width = PANEL_WIDTH;
        this.height = PANEL_HEIGHT;
    }

    public WaitingPanel(String message) {
        this(Text.literal(message));
    }

    // ──── Message ────

    public Text getMessage() {
        return message;
    }

    public void setMessage(Text message) {
        this.message = message;
    }

    // ──── Rendering ────

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();

        // 1. Full-screen semi-transparent overlay
        GuiDrawing.drawRect(0, 0, screenWidth, screenHeight, OVERLAY_COLOR);

        // 2. Calculate panel position (centred)
        int panelX = (screenWidth - PANEL_WIDTH) / 2;
        int panelY = (screenHeight - PANEL_HEIGHT) / 2;

        // 3. Draw panel background
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Panel fill
        GuiDrawing.drawRect(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_BG_COLOR);

        // Panel border
        GuiDrawing.drawRect(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, PANEL_BORDER_COLOR);
        GuiDrawing.drawRect(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_BORDER_COLOR);
        GuiDrawing.drawRect(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, PANEL_BORDER_COLOR);
        GuiDrawing.drawRect(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_BORDER_COLOR);

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // 4. Draw text
        FontRenderer font = mc.fontRenderer;
        String msgStr = message != null ? message.getString() : "";
        String dotsText = LoadingDotsText.get(System.currentTimeMillis());
        String fullText = msgStr + " " + dotsText;

        int textWidth = font.getStringWidth(fullText);
        int textX = panelX + (PANEL_WIDTH - textWidth) / 2;
        int textY = panelY + (PANEL_HEIGHT - font.FONT_HEIGHT) / 2;

        font.drawStringWithShadow(fullText, textX, textY, TEXT_COLOR);
    }

    // ──── Input blocking ────

    /**
     * Always blocks mouse clicks.
     * <p>始终拦截鼠标点击。</p>
     */
    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Block all input
    }

    /**
     * Always blocks key input.
     * <p>始终拦截键盘输入。</p>
     */
    @Override
    public void keyTyped(char typedChar, int keyCode) {
        // Block all input
    }

    /**
     * Always blocks scroll.
     * <p>始终拦截滚轮。</p>
     */
    @Override
    public void mouseScrolled(int delta) {
        // Block all input
    }

    /**
     * Always returns true — covers the full screen.
     * <p>始终返回 true —— 覆盖整个屏幕。</p>
     */
    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return true;
    }
}
