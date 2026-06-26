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
     * <p>
     * 可选的 Text 标题，优先级高于 {@code tabNameKey}。<br>
     * 当通过 {@link #AbstractScreenTab(int, Text)} 构造时使用此字段。<br>
     * Optional Text title, takes priority over {@code tabNameKey}.<br>
     * Set when using the {@link #AbstractScreenTab(int, Text)} constructor.
     * </p>
     */
    protected Text tabTitleText;

    /**
     * Tab button texture.  Defaults to {@link Tab#DEFAULT_TAB_TEXTURE}. / Tab 按钮纹理。默认 {@link Tab#DEFAULT_TAB_TEXTURE}。
     */
    protected ResourceLocation tabTexture = Tab.DEFAULT_TAB_TEXTURE;

    /**
     * Optional four-state button textures.  When non-null, takes priority over {@link #tabTexture}. / 可选的四状态按钮纹理组。非 null 时优先级高于 {@link #tabTexture}。
     */
    @javax.annotation.Nullable
    protected Tab.TabTextures tabTextures;

    public AbstractScreenTab(int tabId, String tabNameKey) {
        this.tabId = tabId;
        this.tabNameKey = tabNameKey;
        this.mc = Minecraft.getMinecraft();
    }

    /**
     * <p>
     * 使用显式 {@link Text} 标题创建标签页。<br>
     * 推荐使用此构造器以获得正确的延迟翻译行为。<br>
     * Create a tab with an explicit {@link Text} title.<br>
     * This constructor is recommended for correct lazy translation behaviour.
     * </p>
     *
     * <pre>{@code
     *   // Translatable flat key (lazy translation via I18n)
     *   super(100, Text.translatable("tab.game"));
     *
     *   // Literal fallback
     *   super(100, Text.literal("My Tab"));
     * }</pre>
     */
    public AbstractScreenTab(int tabId, Text tabTitle) {
        this.tabId = tabId;
        this.tabTitleText = tabTitle;
        this.mc = Minecraft.getMinecraft();
    }

    @Override
    public void initGui(TabManager tabManager, int width, int height) {
        this.tabManager = tabManager;
        tabButtons.clear();
        tabWidgets.clear();
        tabComponents.clear();
    }

    @Override
    public int getTabId() {
        return tabId;
    }

    @Override
    public String getTabName() {
        if (tabNameKey == null) return "";
        return I18n.format(tabNameKey);
    }

    /**
     * <p>
     * 返回此标签页的标题。<br>
     * 优先级：{@link #tabTitleText} > {@link #tabNameKey} 回退。<br>
     * Returns the title of this tab.<br>
     * Priority: {@link #tabTitleText} > {@link #tabNameKey} fallback.
     * </p>
     */
    @Override
    public Text getTabTitle() {
        if (tabTitleText != null) {
            return tabTitleText;
        }
        return tabNameKey != null
                ? Text.translatable(tabNameKey)
                : Text.literal("");
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

    @javax.annotation.Nullable
    @Override
    public Tab.TabTextures getTabTextures() {
        return tabTextures;
    }

    /**
     * Set a custom tab button texture for this tab.
     * <p>为此 Tab 设置自定义按钮纹理。</p>
     */
    public void setTabTexture(ResourceLocation texture) {
        this.tabTexture = texture;
    }

    /**
     * Set custom four-state button textures for this tab.
     * When set, these take priority over the single texture set via {@link #setTabTexture(ResourceLocation)}.
     * <p>为此 Tab 设置自定义四状态按钮纹理组。设置后将优先于 {@link #setTabTexture(ResourceLocation)} 的单纹理。</p>
     */
    public void setTabTextures(Tab.TabTextures textures) {
        this.tabTextures = textures;
    }
}