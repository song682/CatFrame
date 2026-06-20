package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import java.util.function.Consumer;

/**
 * <p>
 * Tab 接口。<br>
 * 所有自定义标签页必须实现此接口。
 * </p>
 * <p>
 * Tab interface.<br>
 * All custom tabs must implement this interface.
 * </p>
 *
 * <p>
 * 此接口同时兼容原有 1.7.10 Forge 风格（基于 int ID + GuiButton 体系）和
 * 高版本 Minecraft（26.1+）风格（基于 Consumer 访问控件 + ScreenRectangle 布局）。
 * 新实现建议逐步迁移到高版本风格方法。
 * </p>
 */
public interface Tab {
    // ──── Original 1.7.10-style API ────

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
        return new ResourceLocation("catframe", "textures/gui/tabs/tabs.png");
    }

    /**
     * <p>
     * 获取此标签页的标题（{@link Text} 格式），用于 TabButton 显示。<br>
     * 默认实现返回 {@code Text.literal(getTabName())}。
     * </p>
     * <p>
     * Get the tab title as a {@link Text}, for display on the TabButton.<br>
     * Defaults to {@code Text.literal(getTabName())}.
     * </p>
     */
    default Text getTabTitle() {
        return Text.literal(getTabName());
    }

    /**
     * <p>
     * 获取此标签页的额外旁白文本，用于无障碍朗读。<br>
     * 默认返回空文本。
     * </p>
     * <p>
     * Get extra narration text for this tab, used by screen readers.<br>
     * Defaults to empty text.
     * </p>
     */
    default Text getTabExtraNarration() {
        return Text.literal("");
    }

    /**
     * <p>
     * 遍历此标签页的所有子控件，供 TabManager 管理生命周期。<br>
     * 控件类型不限于 {@link GuiButton}，也可以是 {@code GuiTextField} 等。<br>
     * 对标高版本 {@code Tab.visitChildren(Consumer<AbstractWidget>)}。<br>
     * 默认实现为空操作。
     * </p>
     * <p>
     * Visit all child widgets of this tab, for lifecycle management by TabManager.<br>
     * Widgets are not limited to {@link GuiButton}; can be {@code GuiTextField} etc.<br>
     * Counterpart of higher versions' {@code Tab.visitChildren(Consumer<AbstractWidget>)}.<br>
     * Default implementation is a no-op.
     * </p>
     */
    default void visitChildren(Consumer<Object> visitor) {
    }

    /**
     * <p>
     * 在指定的屏幕矩形区域内执行此标签页的布局。<br>
     * 对标高版本 {@code Tab.doLayout(ScreenRectangle)}。<br>
     * 默认实现为空操作。
     * </p>
     * <p>
     * Lay out this tab within the given screen rectangle.<br>
     * Counterpart of higher versions' {@code Tab.doLayout(ScreenRectangle)}.<br>
     * Default implementation is a no-op.
     * </p>
     */
    default void doLayout(ScreenRectangle rectangle) {
    }
}
