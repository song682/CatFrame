package decok.dfcdvadstf.catframe.ui.tab;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * Tab 接口。<br>
 * 所有自定义标签页必须实现此接口。
 * </p>
 * <p>
 * Tab interface.<br>
 * All custom tabs must implement this interface.
 * </p>
 */
public interface Tab {
    void initGui(TabManager tabManager, int width, int height);

    void drawScreen(int mouseX, int mouseY, float partialTicks);

    void actionPerformed(GuiButton button);

    void mouseClicked(int mouseX, int mouseY, int mouseButton);

    void keyTyped(char typedChar, int keyCode);

    int getTabId();

    String getTabName();

    void setVisible(boolean visible);

    /**
     * <p>
     * 获取此 Tab 使用的按钮纹理。<br>
     * 默认返回 {@code catframe:textures/gui/tabs.png}。
     * </p>
     * <p>
     * Get the button texture for this tab.<br>
     * Defaults to {@code catframe:textures/gui/tabs.png}.
     * </p>
     */
    default ResourceLocation getTabTexture() {
        return new ResourceLocation("catframe", "textures/gui/tabs.png");
    }
}
