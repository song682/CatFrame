package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.Component;
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
    /** @deprecated 只保留按钮，新代码请用 {@link #tabWidgets} */
    @Deprecated
    protected List<GuiButton> tabButtons = new ArrayList<>();
    /**
     * 通用控件列表，不限于 GuiButton，也可放 GuiTextField、Component 等。
     */
    protected List<Object> tabWidgets = new ArrayList<>();
    /**
     * 高版本风格组件列表。
     */
    protected List<Component> tabComponents = new ArrayList<>();
    protected boolean visible = true;
    protected int tabId;
    protected String tabNameKey;

    /**
     * Tab button texture.  Defaults to {@link Tab#DEFAULT_TAB_TEXTURE}. / Tab 按钮纹理。默认 {@link Tab#DEFAULT_TAB_TEXTURE}。
     */
    protected ResourceLocation tabTexture = Tab.DEFAULT_TAB_TEXTURE;

    public AbstractScreenTab(int tabId, String tabNameKey) {
        this.tabId = tabId;
        this.tabNameKey = tabNameKey;
        this.mc = Minecraft.getMinecraft();
    }

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        this.tabManager = tabManager;
        tabButtons.clear();
        tabWidgets.clear();
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
        if (tabNameKey != null && tabNameKey.contains(":")) {
            return Text.translatable(tabNameKey);
        }
        return Text.literal(getTabName());
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        for (GuiButton button : tabButtons) {
            button.visible = visible;
        }
    }

    /**
     * 注册一个 GuiButton 到 Tab（同时加入 buttonList）。
     */
    protected void addButton(GuiButton button) {
        tabButtons.add(button);
        tabWidgets.add(button);
        tabManager.addButton(button);
    }

    /**
     * 注册一个任意类型的控件到 Tab（不加入 buttonList）。
     * 适用于 GuiTextField 等非按钮控件。
     */
    protected void addWidget(Object widget) {
        tabWidgets.add(widget);
    }

    /**
     * 注册一个高版本风格的 Component 到 Tab。
     */
    protected void addComponent(Component component) {
        tabComponents.add(component);
        tabWidgets.add(component);
    }

    /**
     * <p>
     * 遍历此 Tab 的所有 {@link #tabWidgets} 控件。<br>
     * Visit all {@link #tabWidgets} of this tab.
     * </p>
     */
    @Override
    public void visitChildren(Consumer<Object> visitor) {
        for (Object widget : tabWidgets) {
            if (!(widget instanceof Component) || !tabComponents.contains(widget)) {
                visitor.accept(widget);
            }
        }
    }

    @Override
    public void visitComponents(Consumer<Component> visitor) {
        for (Component component : tabComponents) {
            visitor.accept(component);
        }
        for (Object widget : tabWidgets) {
            if (widget instanceof Component && !tabComponents.contains(widget)) {
                visitor.accept((Component) widget);
            }
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