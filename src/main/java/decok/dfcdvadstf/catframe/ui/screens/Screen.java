package decok.dfcdvadstf.catframe.ui.screens;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.Component;
import decok.dfcdvadstf.catframe.ui.components.Renderable;
import decok.dfcdvadstf.catframe.ui.components.events.CatFrameInputScreen;
import decok.dfcdvadstf.catframe.ui.components.events.ComponentPath;
import decok.dfcdvadstf.catframe.ui.components.events.ContainerEventHandler;
import decok.dfcdvadstf.catframe.ui.components.events.ScreenKeyboardInput;
import decok.dfcdvadstf.catframe.ui.navigation.FocusNavigationEvent;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * CatFrame UI 库的界面基类 —— 继承原版 {@link GuiScreen}，同时把高版本 Minecraft
 * {@code net.minecraft.client.gui.screens.Screen}（extends
 * {@code AbstractContainerEventHandler}）
 * 的「组件容器 + 焦点导航 + 事件分发」能力内联进来。<br>
 * 通过实现 {@link Component} 与 {@link ContainerEventHandler}，本屏幕本身即是
 * CatFrame 组件树的根容器；实现 {@link CatFrameInputScreen} 后，
 * {@code MixinGuiScreen} 会把 LWJGL2 拆分键盘事件路由到本屏幕
 * （{@link #getEventRoot()} 返回 {@code this}）。
 * </p>
 * <p>
 * Base screen for the CatFrame UI library — extends vanilla {@link GuiScreen}
 * while inlining the
 * component-container / focus-navigation / event-dispatch behaviour of the
 * high-version Minecraft
 * {@code Screen} (which extends {@code AbstractContainerEventHandler}). By
 * implementing
 * {@link Component} and {@link ContainerEventHandler}, the screen itself is the
 * root of the
 * CatFrame component tree; implementing {@link CatFrameInputScreen} lets
 * {@code MixinGuiScreen}
 * route split LWJGL2 keyboard events to it ({@link #getEventRoot()} returns
 * {@code this}).
 * </p>
 *
 * <h3>生命周期 / Lifecycle</h3>
 * <ul>
 * <li>{@link #initGui()} — 原版在 {@code setWorldAndResolution} 时调用（含每次 resize）。本类
 * 在此重建组件（{@link #clearWidgets()} → {@link #init()} →
 * {@link #setInitialFocus()}），
 * 契合 1.7.10「在 initGui 中重建控件」的惯例。</li>
 * <li>{@link #init()} — 子类在此通过 {@link #addRenderableWidget(Component)}
 * 等添加组件。</li>
 * <li>{@link #updateScreen()} → {@link #tick()}；{@link #onGuiClosed()} →
 * {@link #removed()}。</li>
 * </ul>
 *
 * <h3>事件契约 / Event contract</h3>
 * <p>
 * 拆分键盘事件（{@code keyPressed}/{@code keyReleased}/{@code charTyped}）经
 * {@code ScreenKeyboardInput} 派发；本类的原版 {@link #keyTyped(char, int)}
 * <strong>只</strong>处理
 * Esc 关闭，<strong>不</strong>向子组件转发，以免与拆分事件重复分发
 * （见 {@link CatFrameInputScreen} 契约）。<br>
 * Split keyboard events are dispatched via {@code ScreenKeyboardInput}; this
 * class's vanilla
 * {@link #keyTyped(char, int)} handles <strong>only</strong> Esc-to-close and
 * does
 * <strong>not</strong> forward to children, to avoid double dispatch.
 * </p>
 */
public abstract class Screen extends GuiScreen implements Component, ContainerEventHandler, CatFrameInputScreen {

    /** Screen title / 界面标题 */
    protected final Text title;

    /**
     * All interactive children (focusable / event targets) / 所有可交互子组件（可聚焦 / 事件目标）
     */
    private final List<Component> children = new ArrayList<>();

    /** Children that should be rendered each frame / 每帧应渲染的子组件 */
    private final List<Component> renderables = new ArrayList<>();

    @Nullable
    private Component focusedChild;
    private boolean dragging;

    // ──── Construction ────

    protected Screen(final Text title) {
        this.title = title;
    }

    /** @return this screen's title / 本界面的标题 */
    public Text getTitle() {
        return this.title;
    }

    /** @return the Minecraft client / Minecraft 客户端实例 */
    public Minecraft getMinecraft() {
        return this.mc;
    }

    /** @return the font renderer / 字体渲染器 */
    public FontRenderer getFont() {
        return this.fontRendererObj;
    }

    // ──── Vanilla lifecycle → CatFrame lifecycle ────

    /**
     * 原版初始化钩子。{@code setWorldAndResolution} 会在初次显示与每次 resize 时调用它
     * （此时 {@link #width}/{@link #height} 已被原版填好）。本类据此重建组件树。
     */
    @Override
    public void initGui() {
        clearWidgets();
        clearFocus();
        init();
        setInitialFocus();
    }

    /**
     * 子类在此构建界面：通过 {@link #addRenderableWidget(Component)} /
     * {@link #addWidget(Component)} / {@link #addRenderableOnly(Component)} 注册组件。
     * <p>
     * Subclasses build the UI here.
     * </p>
     */
    protected void init() {
    }

    @Override
    public void updateScreen() {
        tick();
    }

    /** Per-tick update hook. / 每 tick 更新钩子。 */
    public void tick() {
    }

    @Override
    public void onGuiClosed() {
        removed();
    }

    /** Called when the screen is removed. / 界面被移除时调用。 */
    public void removed() {
    }

    // ──── Widget registration ────

    /**
     * Add a component that is both rendered and receives events / focus.
     * <p>
     * 添加一个既参与渲染、又接收事件与焦点的组件。
     * </p>
     */
    protected <T extends Component> T addRenderableWidget(final T widget) {
        this.renderables.add(widget);
        this.children.add(widget);
        return widget;
    }

    /**
     * Add a component that receives events / focus but is rendered elsewhere.
     * <p>
     * 添加一个接收事件/焦点、但在别处渲染的组件。
     * </p>
     */
    protected <T extends Component> T addWidget(final T widget) {
        this.children.add(widget);
        return widget;
    }

    /**
     * Add a render-only component (no events / focus).
     * <p>
     * 添加一个仅渲染的组件（不接收事件/焦点）。
     * </p>
     */
    protected <T extends Component> T addRenderableOnly(final T renderable) {
        this.renderables.add(renderable);
        return renderable;
    }

    /** Remove a previously registered component. / 移除一个已注册的组件。 */
    protected void removeWidget(final Component widget) {
        this.renderables.remove(widget);
        if (this.focusedChild == widget) {
            clearFocus();
        }
        this.children.remove(widget);
    }

    /** Clear all registered components. / 清空所有已注册组件。 */
    protected void clearWidgets() {
        this.renderables.clear();
        this.children.clear();
    }

    @Override
    public List<? extends Component> children() {
        return this.children;
    }

    // ──── Rendering ────

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        renderBackground(mouseX, mouseY, partialTicks);
        for (int i = 0; i < this.renderables.size(); i++) {
            final Component renderable = this.renderables.get(i);
            if (renderable.isVisible() && renderable instanceof Renderable) {
                ((Renderable) renderable).extractRenderState(GuiGraphicsExtractor.getInstance(),
                        mouseX, mouseY, partialTicks);
            }
        }
        // Render any vanilla GuiButtons/labels a subclass may still use (no-op when
        // empty).
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * Draw the screen background. Defaults to the vanilla dimmed/dirt background.
     * <p>
     * 绘制界面背景，默认使用原版变暗/泥土背景。子类可覆盖。
     * </p>
     */
    protected void renderBackground(final int mouseX, final int mouseY, final float partialTicks) {
        drawDefaultBackground();
    }

    // ──── Mouse events (vanilla → CatFrame dispatch) ────

    /**
     * Overridden as {@code public} (widening the vanilla {@code protected}) to
     * satisfy
     * {@link Component#mouseClicked(int, int, int)}. Forwards to vanilla buttons
     * first, then
     * dispatches to CatFrame children.
     */
    @Override
    public void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        dispatchMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseMovedOrUp(final int mouseX, final int mouseY, final int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        dispatchMouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(final int mouseX, final int mouseY, final int clickedMouseButton,
            final long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        dispatchMouseDragged(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    /**
     * Vanilla {@code handleMouseInput} does not surface the scroll wheel, so we
     * read it here
     * and dispatch a normalised scroll delta to the child under the cursor.
     * <p>
     * 原版 {@code handleMouseInput} 不暴露滚轮，这里读取并向鼠标下子组件派发归一化滚动量。
     * </p>
     */
    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        final int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            final int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            final int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            dispatchMouseScrolled(mouseX, mouseY, wheel > 0 ? 1 : -1);
        }
    }

    // ──── Keyboard (self-dispatch of split events) ────

    /**
     * Self-dispatches the split keyboard events ({@code keyPressed} /
     * {@code keyReleased} /
     * {@code charTyped}) into the component tree, then delegates to vanilla.
     * Reading LWJGL2's
     * current event here — inside the {@code while (Keyboard.next())} loop, before
     * vanilla's
     * {@code keyTyped} — mirrors exactly what {@code MixinGuiScreen} does for
     * foreign hosts.
     * <p>
     * Because this base self-dispatches,
     * {@link #handlesKeyboardDispatchInternally()} returns
     * {@code true} so {@code MixinGuiScreen} skips us and no key is delivered
     * twice. The upshot:
     * {@code ui.screens.Screen} is self-sufficient (it works even if the mixin were
     * absent), while
     * the mixin stays as the retrofit path for screens that cannot extend this
     * base.
     * </p>
     * <p>
     * 本基类自行把拆分键盘事件派发进组件树，随后委托原版。此处（在 {@code while(Keyboard.next())}
     * 循环内、原版 {@code keyTyped} 之前）读取 LWJGL2 当前事件，与 {@code MixinGuiScreen} 为外部宿主
     * 所做的完全一致。因本基类自派发，{@link #handlesKeyboardDispatchInternally()} 返回 {@code true}，
     * 令 {@code MixinGuiScreen} 跳过本屏幕，从而不会重复派发。
     * </p>
     */
    @Override
    public void handleKeyboardInput() {
        ScreenKeyboardInput.handleCurrentEvent(getEventRoot());
        super.handleKeyboardInput();
    }

    // ──── Keyboard (vanilla path: Esc only) ────

    /**
     * Overridden as {@code public} to satisfy
     * {@link Component#keyTyped(char, int)}. Handles
     * <strong>only</strong> Esc-to-close and intentionally does not forward to
     * children — split
     * key events arrive via {@code ScreenKeyboardInput}. The
     * {@code mc.currentScreen == this}
     * guard makes the bridge + vanilla double-invocation harmless.
     */
    @Override
    public void keyTyped(final char typedChar, final int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && shouldCloseOnEsc() && this.mc.currentScreen == this) {
            onClose();
        }
    }

    @Override
    public boolean keyPressed(final int keyCode) {
        return dispatchKeyPressed(keyCode);
    }

    @Override
    public boolean keyReleased(final int keyCode) {
        return dispatchKeyReleased(keyCode);
    }

    @Override
    public boolean charTyped(final char codePoint) {
        return dispatchCharTyped(codePoint);
    }

    // ──── CatFrameInputScreen ────

    @Nullable
    @Override
    public Component getEventRoot() {
        return this;
    }

    /**
     * Returns {@code true}: this base drives its own keyboard dispatch in
     * {@link #handleKeyboardInput()}, so {@code MixinGuiScreen} must not dispatch
     * for it again.
     * <p>
     * 返回 {@code true}：本基类在 {@link #handleKeyboardInput()} 中自行驱动键盘派发，
     * 故 {@code MixinGuiScreen} 不得再为其重复派发。
     * </p>
     */
    @Override
    public boolean handlesKeyboardDispatchInternally() {
        return true;
    }

    // ──── Close / Esc ────

    /** @return whether Esc closes this screen / Esc 是否关闭本界面 */
    public boolean shouldCloseOnEsc() {
        return true;
    }

    /** Close this screen. / 关闭本界面。 */
    public void onClose() {
        this.mc.displayGuiScreen(null);
    }

    /** @return whether this screen pauses a single-player world / 本界面是否暂停单人世界 */
    public boolean isPauseScreen() {
        return true;
    }

    /**
     * Whether this screen pauses the game when opened. Defaults to
     * {@link #isPauseScreen()} or
     * {@link OverlayManager#isPausingGame()}.
     * <p>
     * 本界面是否暂停游戏。默认为 {@link #isPauseScreen()} 或
     * {@link OverlayManager#isPausingGame()}。
     * </p>
     */
    @Override
    public boolean doesGuiPauseGame() {
        return isPauseScreen() || OverlayManager.INSTANCE.isPausingGame();
    }

    // ──── Focus navigation ────

    /**
     * Called after {@link #init()} to establish initial focus. No-op by default;
     * subclasses may
     * override to focus a specific widget.
     * <p>
     * {@link #init()} 之后建立初始焦点，默认空实现。
     * </p>
     */
    protected void setInitialFocus() {
    }

    /** Move focus to the given target's resolved path. / 将焦点移动到给定目标解析出的路径。 */
    protected void setInitialFocus(final Component target) {
        final ComponentPath path = target.nextFocusPath(new FocusNavigationEvent.TabNavigation(true));
        if (path != null) {
            clearFocus();
            path.applyFocus(true);
        }
    }

    /** Clear the current focus. / 清除当前焦点。 */
    public void clearFocus() {
        setFocused((Component) null);
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(final FocusNavigationEvent event) {
        return nextFocusPathInContainer(event);
    }

    // ──── ContainerEventHandler: focus / dragging state ────

    @Nullable
    @Override
    public Component getFocused() {
        return this.focusedChild;
    }

    @Override
    public void setFocused(@Nullable final Component focused) {
        if (this.focusedChild == focused) {
            return;
        }
        if (this.focusedChild != null) {
            this.focusedChild.setFocused(false);
        }
        this.focusedChild = focused;
        if (focused != null) {
            focused.setFocused(true);
        }
    }

    @Override
    public boolean isDragging() {
        return this.dragging;
    }

    @Override
    public void setDragging(final boolean dragging) {
        this.dragging = dragging;
    }

    // Resolve the Component vs ContainerEventHandler default-method conflicts
    // explicitly.
    @Override
    public boolean isFocused() {
        return ContainerEventHandler.super.isFocused();
    }

    @Override
    public void setFocused(final boolean focused) {
        ContainerEventHandler.super.setFocused(focused);
    }

    // ──── Component: geometry / state ────

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public void setX(final int x) {
    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public void setY(final int y) {
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void setVisible(final boolean visible) {
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void setActive(final boolean active) {
    }

    @Override
    public boolean isMouseOver(final int mouseX, final int mouseY) {
        return true;
    }

    @Override
    public ScreenRectangle getRectangle() {
        return new ScreenRectangle(0, 0, this.width, this.height);
    }
}
