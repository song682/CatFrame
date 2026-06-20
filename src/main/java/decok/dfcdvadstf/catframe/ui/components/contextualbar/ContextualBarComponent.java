package decok.dfcdvadstf.catframe.ui.components.contextualbar;

import decok.dfcdvadstf.catframe.ui.components.AbstractComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/**
 * <p>
 * 上下文栏组件抽象基类。<br>
 * 对标高版本 Minecraft 的 {@code ContextualBarRenderer}。
 * </p>
 * <p>
 * Abstract base class for contextual bar components.<br>
 * Counterpart of the high-version Minecraft {@code ContextualBarRenderer}.
 * </p>
 */
public abstract class ContextualBarComponent extends AbstractComponent {

    /** Standard contextual bar width / 标准上下文栏宽度 */
    public static final int BAR_WIDTH = 182;

    /** Standard contextual bar height / 标准上下文栏高度 */
    public static final int BAR_HEIGHT = 5;

    /** Bottom margin for bar positioning / 底部边距 */
    public static final int MARGIN_BOTTOM = 24;

    public ContextualBarComponent() {
        super(0, 0, BAR_WIDTH, BAR_HEIGHT);
    }

    /**
     * Recalculate the bar position based on screen dimensions.
     * <p>根据屏幕尺寸重新计算栏位置。</p>
     */
    protected void updatePosition() {
        ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft(),
            Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight);
        int screenWidth = res.getScaledWidth();
        int screenHeight = res.getScaledHeight();
        this.x = (screenWidth - BAR_WIDTH) / 2;
        this.y = screenHeight - MARGIN_BOTTOM - BAR_HEIGHT;
    }

    /**
     * Render the experience level text.
     * <p>渲染经验等级文本。</p>
     */
    protected static void renderLevelText(int experienceLevel, int screenWidth, int screenHeight) {
        String text = String.valueOf(experienceLevel);
        Minecraft mc = Minecraft.getMinecraft();
        int textWidth = mc.fontRenderer.getStringWidth(text);
        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - MARGIN_BOTTOM - 9 - 2;

        // Shadow
        mc.fontRenderer.drawString(text, x + 1, y, 0xFF000000);
        mc.fontRenderer.drawString(text, x - 1, y, 0xFF000000);
        mc.fontRenderer.drawString(text, x, y + 1, 0xFF000000);
        mc.fontRenderer.drawString(text, x, y - 1, 0xFF000000);
        // Main text
        mc.fontRenderer.drawString(text, x, y, 0xFF809080);
    }
}
