package decok.dfcdvadstf.catframe.ui.tab;

import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.Component;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
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

    /**
     * Default tab button texture. / 默认 Tab 按钮纹理。
     */
    ResourceLocation DEFAULT_TAB_TEXTURE = new ResourceLocation(Tags.MODID, "textures/gui/tabs/tab.png");

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
     * 默认返回 {@link #DEFAULT_TAB_TEXTURE}。
     * </p>
     * <p>
     * Get the button texture for this tab.<br>
     * Defaults to {@link #DEFAULT_TAB_TEXTURE}.
     * </p>
     */
    default ResourceLocation getTabTexture() {
        return DEFAULT_TAB_TEXTURE;
    }

    /**
     * <p>
     * 获取此标签页的四状态按钮纹理组。<br>
     * 若返回非 null，则 {@link #getTabTexture()} 被忽略。<br>
     * 默认返回 null。<br>
     * Get the four-state button textures for this tab.<br>
     * If non-null, {@link #getTabTexture()} is ignored.<br>
     * Defaults to null.
     * </p>
     */
    @Nullable
    default TabTextures getTabTextures() {
        return null;
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
     * 高版本风格：遍历此标签页的所有 {@link Component} 子控件。<br>
     * 对标高版本 {@code Tab.visitChildren(Consumer<AbstractWidget>)}。<br>
     * 默认实现委托给 {@link #visitChildren(Consumer)}。<br>
     * 新代码应直接重写此方法以利用参数类型安全。<br>
     * High-version style: visit all {@link Component} children of this tab.<br>
     * Counterpart of high-version {@code Tab.visitChildren(Consumer<AbstractWidget>)}.<br>
     * Default delegates to {@link #visitChildren(Consumer)}.<br>
     * New code should override this for type safety.
     * </p>
     */
    default void visitComponents(Consumer<Component> visitor) {
        visitChildren(obj -> {
            if (obj instanceof Component) {
                visitor.accept((Component) obj);
            }
        });
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

    /**
     * <p>
     * 四状态按钮纹理组。<br>
     * 封装 normal / highlighted / selected / selected+highlighted 四种纹理。<br>
     * 若使用单参数构造器 {@link #TabTextures(ResourceLocation)}，四个状态使用同一纹理。<br>
     * Four-state button textures.<br>
     * Wraps normal / highlighted / selected / selected+highlighted textures.<br>
     * If using the single-argument constructor {@link #TabTextures(ResourceLocation)},
     * all four states share the same texture.
     * </p>
     */
    final class TabTextures {

        public final ResourceLocation normal;
        public final ResourceLocation highlighted;
        public final ResourceLocation selected;
        public final ResourceLocation selectedHighlighted;

        /**
         * All four states use the same texture. / 四个状态使用同一纹理。
         */
        public TabTextures(ResourceLocation single) {
            this(single, single, single, single);
        }

        /**
         * Four distinct textures per state. / 四个状态各自独立纹理。
         */
        public TabTextures(ResourceLocation normal, ResourceLocation highlighted,
                           ResourceLocation selected, ResourceLocation selectedHighlighted) {
            this.normal = normal;
            this.highlighted = highlighted;
            this.selected = selected;
            this.selectedHighlighted = selectedHighlighted;
        }
    }
}
