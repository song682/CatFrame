package decok.dfcdvadstf.catframe.ui.tab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumDifficulty;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractScreenTab implements Tab {
    protected TabManager tabManager;
    protected Minecraft mc;
    protected List<GuiButton> tabButtons = new ArrayList<>();
    protected boolean visible = true;
    protected int tabId;
    protected String tabNameKey;

    /** Tab button texture.  Defaults to {@code catframe:textures/gui/tabs.png}. / Tab 按钮纹理。默认 {@code catframe:textures/gui/tabs.png}。 */
    protected ResourceLocation tabTexture = new ResourceLocation("catframe", "textures/gui/tabs.png");

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