package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.LoadingDotsText;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.layouts.FrameLayout;
import decok.dfcdvadstf.catframe.ui.layouts.LayoutSettings;
import decok.dfcdvadstf.catframe.ui.layouts.LinearLayout;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

import java.util.function.Consumer;

/**
 * <p>
 * 加载状态标签页 —— 在加载完成之前显示加载动画提示。<br>
 * 对标高版本 Minecraft 的 {@code LoadingTab}。
 * </p>
 * <p>
 * Loading tab — displays a loading animation while content is being prepared.<br>
 * Counterpart of higher Minecraft versions' {@code LoadingTab}.
 * </p>
 *
 * <p>Usage / 用法:</p>
 * <pre>{@code
 * TabRegistry.registerTab("mybar", () -> new LoadingTab(
 *     200, "catframe:tab.loading",
 *     Text.translatable("catframe:tab.loading.title"),
 *     Text.translatable("catframe:tab.loading.status")
 * ), 200, "catframe:tab.loading");
 * }</pre>
 */
public class LoadingTab extends AbstractScreenTab {

    private final Text title;
    private final Text loadingTitle;
    protected final LinearLayout layout = new LinearLayout(LinearLayout.Axis.VERTICAL, LinearLayout.Alignment.CENTER);
    private int tabAreaX;
    private int tabAreaY;
    private int tabAreaWidth;
    private int tabAreaHeight;

    /**
     * @param tabId       标签页 ID / tab ID
     * @param tabNameKey  本地化键名 / localization key
     * @param title       标签页标题（用于 TabButton 显示） / tab title for TabButton display
     * @param loadingTitle 加载中标题（用于旁白） / loading title for narration
     */
    public LoadingTab(int tabId, String tabNameKey, Text title, Text loadingTitle) {
        super(tabId, tabNameKey);
        this.title = title;
        this.loadingTitle = loadingTitle;
    }

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        super.initGui(tabManager, width, height);
        this.layout.clear();
        LayoutSettings settings = this.layout.newChildLayoutSettings();
        settings.align(0.5F, 0.5F).paddingBottom(30);
    }

    @Override
    public Text getTabTitle() {
        return title;
    }

    @Override
    public Text getTabExtraNarration() {
        return loadingTitle;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        FontRenderer font = mc.fontRenderer;
        String loadingText = LoadingDotsText.get(System.currentTimeMillis());
        int textWidth = font.getStringWidth(loadingText);

        int centerX = tabAreaX + tabAreaWidth / 2;
        int centerY = tabAreaY + tabAreaHeight / 2;
        int textX = centerX - textWidth / 2;
        int textY = centerY - font.FONT_HEIGHT / 2;

        font.drawStringWithShadow(loadingText, textX, textY, 0xFFFFFF);
    }

    @Override
    public void visitChildren(Consumer<Object> visitor) {
        // No interactive child widgets
        // 没有交互子控件
    }

    @Override
    public void doLayout(ScreenRectangle rectangle) {
        this.tabAreaX = rectangle.x;
        this.tabAreaY = rectangle.y;
        this.tabAreaWidth = rectangle.width;
        this.tabAreaHeight = rectangle.height;
        this.layout.arrangeElements();
        FrameLayout.alignInRectangle(
                this.layout,
                rectangle.x, rectangle.y,
                rectangle.width, rectangle.height,
                0.5F, 0.5F
        );
    }

    @Override
    public void actionPerformed(GuiButton button) {
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }
}
