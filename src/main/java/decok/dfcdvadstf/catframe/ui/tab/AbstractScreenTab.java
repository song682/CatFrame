package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractScreenTab implements Tab {
    protected TabManager tabManager;
    protected Minecraft mc;
    protected List<GuiButton> tabButtons = new ArrayList<>();
    protected boolean visible = true;
    protected int tabId;
    protected String tabNameKey;

    /**
     * Tab button texture.  Defaults to {@code catframe:textures/gui/tabs.png}. / Tab 按钮纹理。默认 {@code catframe:textures/gui/tabs/tabs.png}。
     */
    protected ResourceLocation tabTexture = new ResourceLocation("catframe", "textures/gui/tabs/tabs.png");

    public AbstractScreenTab(int tabId, String tabNameKey) {
        this.tabId = tabId;
        this.tabNameKey = tabNameKey;
        this.mc = Minecraft.getMinecraft();
    }

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        this.tabManager = tabManager;
        tabButtons.clear();
    }

    @Override
    public int getTabId() {
        return tabId;
    }

    @Override
    public String getTabName() {
        return I18n.format(tabNameKey);
    }

    /**
     * <p>
     * 返回基于 {@code tabNameKey} 的可翻译标题。<br>
     * Returns a translatable title based on {@code tabNameKey}.
     * </p>
     */
    @Override
    public Text getTabTitle() {
        return Text.translatable(tabNameKey);
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        for (GuiButton button : tabButtons) {
            button.visible = visible;
        }
    }

    protected void addButton(GuiButton button) {
        tabButtons.add(button);
        tabManager.addButton(button);
    }

    /**
     * <p>
     * 遍历此 Tab 的所有 {@link #tabButtons} 控件。<br>
     * Visit all {@link #tabButtons} of this tab.
     * </p>
     */
    @Override
    public void visitChildren(Consumer<Object> visitor) {
        for (GuiButton button : tabButtons) {
            visitor.accept(button);
        }
    }

    @Override
    public ResourceLocation getTabTexture() {
        return tabTexture;
    }

    /**
     * Set a custom tab button texture for this tab.
     * <p>为此 Tab 设置自定义按钮纹理。</p>
     */
    public void setTabTexture(ResourceLocation texture) {
        this.tabTexture = texture;
    }
}