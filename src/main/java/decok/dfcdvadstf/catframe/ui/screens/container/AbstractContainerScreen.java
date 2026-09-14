package decok.dfcdvadstf.catframe.ui.screens.container;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.core.component.DataComponents;
import decok.dfcdvadstf.catframe.core.component.predicates.ItemStackComponents;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.components.Renderable;
import decok.dfcdvadstf.catframe.ui.components.events.CatFrameInputScreen;
import decok.dfcdvadstf.catframe.ui.components.events.ComponentPath;
import decok.dfcdvadstf.catframe.ui.components.events.ContainerEventHandler;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import decok.dfcdvadstf.catframe.ui.components.events.ScreenKeyboardInput;
import decok.dfcdvadstf.catframe.ui.navigation.FocusNavigationEvent;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import decok.dfcdvadstf.catframe.ui.screens.Screen;
import decok.dfcdvadstf.catframe.ui.tooltip.ClientTooltipComponent;
import decok.dfcdvadstf.catframe.ui.tooltip.ItemTooltipImages;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * UI layer of the three-layer container abstraction. Extends vanilla
 * {@link GuiContainer} (required for slot rendering, drag state-machine,
 * shift-click, etc.) and integrates CatFrame's component/event system
 * via {@link ContainerEventHandler} and {@link CatFrameInputScreen}.<br>
 * This class is <b>only concerned with rendering and input dispatch</b> —
 * all interaction logic lives in {@link AbstractContainerMenu}.
 * </p>
 * <p>
 * 三层容器抽象的界面层。继承原版 {@link GuiContainer}（槽位渲染、拖拽状态机、
 * shift-click 等所需），并通过 {@link ContainerEventHandler} 与
 * {@link CatFrameInputScreen} 接入 CatFrame 的组件/事件体系。<br>
 * 本类<b>只关心渲染与输入派发</b>——所有交互逻辑由 {@link AbstractContainerMenu} 负责。
 * </p>
 *
 * @param <T> the menu type / 菜单类型
 */
@SideOnly(Side.CLIENT)
public abstract class AbstractContainerScreen<T extends AbstractContainerMenu>
        extends GuiContainer
        implements GuiEventListener, ContainerEventHandler, CatFrameInputScreen {

    /** The menu this screen displays. / 本界面显示的菜单。 */
    protected final T menu;

    /** Container texture dimensions in pixels. / 容器纹理尺寸（像素）。 */
    protected int imageWidth = 176;
    protected int imageHeight = 166;

    /** Top-left corner of the container texture on screen. / 容器纹理在屏幕上的左上角坐标。 */
    protected int leftPos;
    protected int topPos;

    /** The slot currently under the mouse, or {@code null}. / 鼠标下方的槽位，或 {@code null}。 */
    @Nullable
    protected Slot hoveredSlot;

    // ──── CatFrame widget system ────

    /** All interactive children. / 所有可交互子组件。 */
    private final List<GuiEventListener> children = new ArrayList<GuiEventListener>();

    /** Children that should be rendered each frame. / 每帧应渲染的子组件。 */
    private final List<GuiEventListener> renderables = new ArrayList<GuiEventListener>();

    @Nullable
    private GuiEventListener focusedChild;
    private boolean dragging;

    // ──── Construction ────

    /**
     * @param menu the menu to display / 要显示的菜单
     */
    protected AbstractContainerScreen(final T menu) {
        super(menu);
        this.menu = menu;
        this.xSize = this.imageWidth;
        this.ySize = this.imageHeight;
    }

    // ──── Vanilla lifecycle → CatFrame lifecycle ────

    /**
     * Vanilla init hook. Called on open and resize.
     * <p>原版初始化钩子。打开和调整大小时调用。</p>
     */
    @Override
    public void initGui() {
        super.initGui();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        this.guiLeft = this.leftPos;
        this.guiTop = this.topPos;
        clearWidgets();
        clearFocus();
        init();
        setInitialFocus();
    }

    /**
     * Subclasses build their CatFrame widget tree here.
     * <p>子类在此通过 {@link #addRenderableWidget(GuiEventListener)} 等构建组件树。</p>
     */
    protected void init() {
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tick();
    }

    /** Per-tick update hook. / 每 tick 更新钩子。 */
    public void tick() {
    }

    @Override
    public void onGuiClosed() {
        removed();
        super.onGuiClosed();
    }

    /** Called when the screen is closed. / 界面关闭时调用。 */
    public void removed() {
    }

    // ──── Rendering ────

    /**
     * {@inheritDoc}
     * <p>
     * Renders vanilla container content (slots, items, background), then
     * iterates CatFrame renderables via {@link GuiGraphicsExtractor}.
     * </p>
     * <p>渲染原版容器内容（槽位、物品、背景），然后通过 {@link GuiGraphicsExtractor}
     * 遍历 CatFrame 可渲染组件。</p>
     */
    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        // Vanilla GuiContainer rendering (background, slots, items, foreground)
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Update hovered slot
        this.hoveredSlot = getSlotAtPosition(mouseX, mouseY);

        // CatFrame widget rendering via GuiGraphicsExtractor
        final GuiGraphicsExtractor graphics = GuiGraphicsExtractor.getInstance();
        for (int i = 0; i < this.renderables.size(); i++) {
            final GuiEventListener renderable = this.renderables.get(i);
            if (renderable.isVisible() && renderable instanceof Renderable) {
                ((Renderable) renderable).extractRenderState(graphics, mouseX, mouseY, partialTicks);
            }
        }

        // Deferred hovered-slot tooltip — collected last so it stays topmost
        // 悬停槽位延迟 tooltip——最后收集，保证绘制在最上层
        extractTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Draw the container background texture. Called by vanilla.
     * <p>绘制容器背景纹理。由原版调用。</p>
     *
     * @param partialTicks tick 插值
     * @param mouseX       鼠标 X
     * @param mouseY       鼠标 Y
     */
    @Override
    protected abstract void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY);

    /**
     * Draw the foreground layer (labels, overlays). Called by vanilla.
     * <p>绘制前景层（标签、覆盖物）。由原版调用。</p>
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     */
    @Override
    protected void drawGuiContainerForegroundLayer(final int mouseX, final int mouseY) {
        // Default: draw title and inventory label
        this.fontRendererObj.drawString(this.menu.getContainer().getInventoryName(), 8, 6, 4210752);
        this.fontRendererObj.drawString("Inventory", 8, this.imageHeight - 96 + 2, 4210752);
    }

    // ──── Hovered-slot tooltip ────

    /**
     * Suppress the vanilla immediate tooltip draw.
     * <p>
     * 原版 {@link GuiContainer#drawScreen} 在帧尾为悬停槽位直接调用本方法绘制旧工具提示；
     * CatFrame 改由 {@link #extractTooltip} 走延迟管线统一绘制（对标 26.1.2），
     * 若保留原版路径会出现双重 tooltip，故在此显式抑制。
     * </p>
     */
    @Override
    protected void renderToolTip(final ItemStack stack, final int mouseX, final int mouseY) {
        // Handled by extractTooltip() via the CatFrame deferred tooltip pipeline.
    }

    /**
     * Collect the deferred tooltip for the hovered slot — corresponds to 26.1.2
     * {@code AbstractContainerScreen.extractTooltip()}.
     * <p>
     * Shown only when no item is carried, or when the item's tooltip image
     * declares it should stay visible while an item is held.
     * </p>
     * <p>为悬停槽位收集延迟 tooltip——对标 26.1.2。仅当未持取物品、
     * 或物品图像组件声明「持物时仍显示」时才显示。</p>
     *
     * @param graphics the rendering context / 渲染上下文
     * @param mouseX   mouse X / 鼠标 X
     * @param mouseY   mouse Y / 鼠标 Y
     */
    protected void extractTooltip(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        if (this.hoveredSlot == null || !this.hoveredSlot.getHasStack()) {
            return;
        }
        final ItemStack item = this.hoveredSlot.getStack();
        if (this.menu.getCarried(this.mc.thePlayer) == null || this.showTooltipWithItemInHand(item)) {
            // Vanilla resolved the item's custom font when present (GuiScreen.renderToolTip)
            final FontRenderer itemFont = item.getItem().getFontRenderer(item);
            final String styleId = ItemStackComponents.get(item).get(DataComponents.TOOLTIP_STYLE);
            graphics.setTooltipForNextFrame(
                    itemFont != null ? itemFont : this.fontRendererObj,
                    this.getTooltipFromContainerItem(item),
                    ItemTooltipImages.get(item),
                    mouseX, mouseY,
                    styleId != null ? new ResourceLocation(styleId) : null
            );
        }
    }

    /**
     * Whether the hovered item's tooltip stays visible while an item is held on
     * the cursor — corresponds to 26.1.2 same-name method; decided by the client
     * renderer dispatched from the item's tooltip image.
     * <p>由物品图像组件的客户端分派决定（无图像或未声明时为 {@code false}）。</p>
     */
    private boolean showTooltipWithItemInHand(final ItemStack item) {
        return ItemTooltipImages.get(item)
                .map(ClientTooltipComponent::create)
                .map(ClientTooltipComponent::showTooltipWithItemInHand)
                .orElse(false);
    }

    /**
     * Collect the tooltip lines for a container item — corresponds to 26.1.2
     * {@code AbstractContainerScreen.getTooltipFromContainerItem(ItemStack)};
     * subclasses may override (e.g. creative-style screens).
     * <p>收集容器内物品的 tooltip 文本行——对标 26.1.2；子类可覆写。</p>
     */
    protected List<String> getTooltipFromContainerItem(final ItemStack itemStack) {
        return Screen.getTooltipFromItem(this.mc, itemStack);
    }

    // ──── Mouse events (vanilla → CatFrame dispatch) ────

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
    protected void mouseClickMove(final int mouseX, final int mouseY, final int clickedMouseButton, final long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        dispatchMouseDragged(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

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
     * {@inheritDoc}
     * <p>
     * Self-dispatches split keyboard events into the component tree, then
     * delegates to vanilla. Because this base self-dispatches,
     * {@link #handlesKeyboardDispatchInternally()} returns {@code true} so
     * {@code MixinGuiScreen} skips us.
     * </p>
     * <p>自行将拆分键盘事件派发进组件树，然后委托给原版。因本基类自派发，
     * {@link #handlesKeyboardDispatchInternally()} 返回 {@code true}，
     * 令 {@code MixinGuiScreen} 跳过本屏幕。</p>
     */
    @Override
    public void handleKeyboardInput() {
        ScreenKeyboardInput.handleCurrentEvent(getEventRoot());
        super.handleKeyboardInput();
    }

    @Override
    public void keyTyped(final char typedChar, final int keyCode) {
        // Esc to close
        if (keyCode == Keyboard.KEY_ESCAPE && shouldCloseOnEsc()) {
            this.mc.thePlayer.closeScreen();
            return;
        }
        // Dispatch to focused child
        dispatchKeyTyped(typedChar, keyCode);
    }

    // ──── CatFrameInputScreen ────

    @Nullable
    @Override
    public GuiEventListener getEventRoot() {
        return this;
    }

    /**
     * Returns {@code true}: this base drives its own keyboard dispatch in
     * {@link #handleKeyboardInput()}, so {@code MixinGuiScreen} must not
     * dispatch for it again.
     * <p>返回 {@code true}：本基类在 {@link #handleKeyboardInput()} 中自行驱动键盘派发，
     * 故 {@code MixinGuiScreen} 不得再为其重复派发。</p>
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

    // ──── Widget registration ────

    /**
     * Add a component that is both rendered and receives events / focus.
     * <p>添加一个既参与渲染、又接收事件与焦点的组件。</p>
     */
    protected <E extends GuiEventListener> E addRenderableWidget(final E widget) {
        this.renderables.add(widget);
        this.children.add(widget);
        return widget;
    }

    /**
     * Add a component that receives events / focus but is rendered elsewhere.
     * <p>添加一个接收事件/焦点、但在别处渲染的组件。</p>
     */
    protected <E extends GuiEventListener> E addWidget(final E widget) {
        this.children.add(widget);
        return widget;
    }

    /**
     * Add a render-only component (no events / focus).
     * <p>添加一个仅渲染的组件（不接收事件/焦点）。</p>
     */
    protected <E extends GuiEventListener> E addRenderableOnly(final E renderable) {
        this.renderables.add(renderable);
        return renderable;
    }

    /** Remove a previously registered component. / 移除一个已注册的组件。 */
    protected void removeWidget(final GuiEventListener widget) {
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
    public List<? extends GuiEventListener> children() {
        return this.children;
    }

    // ──── ContainerEventHandler: focus / dragging state ────

    @Nullable
    @Override
    public GuiEventListener getFocused() {
        return this.focusedChild;
    }

    @Override
    public void setFocused(@Nullable final GuiEventListener focused) {
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

    /** Clear the current focus. / 清除当前焦点。 */
    public void clearFocus() {
        setFocused((GuiEventListener) null);
    }

    /**
     * Called after {@link #init()} to establish initial focus.
     * <p>{@link #init()} 之后建立初始焦点。默认空实现。</p>
     */
    protected void setInitialFocus() {
    }

    // ──── Slot lookup ────

    /**
     * Find the slot at the given screen coordinates.
     * <p>查找给定屏幕坐标处的槽位。</p>
     *
     * @param mouseX mouse X / 鼠标 X
     * @param mouseY mouse Y / 鼠标 Y
     * @return the slot, or {@code null} if none / 槽位，无则 {@code null}
     */
    @Nullable
    protected Slot getSlotAtPosition(final int mouseX, final int mouseY) {
        for (int i = 0; i < this.menu.inventorySlots.size(); i++) {
            final Slot slot = (Slot) this.menu.inventorySlots.get(i);
            if (isHovering(slot.xDisplayPosition, slot.yDisplayPosition, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Test whether the given rectangular area is being hovered.
     * <p>测试给定矩形区域是否被鼠标悬停。</p>
     */
    protected boolean isHovering(final int left, final int top, final int width, final int height, int mouseX, int mouseY) {
        mouseX -= this.leftPos;
        mouseY -= this.topPos;
        return mouseX >= left - 1 && mouseX < left + width + 1 && mouseY >= top - 1 && mouseY < top + height + 1;
    }

    // ──── Pause behaviour ────

    /**
     * Container screens do not pause the game by default.
     * <p>容器界面默认不暂停游戏。</p>
     */
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ──── GuiEventListener: geometry / state ────

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

    // ──── ContainerEventHandler: focus-path resolution ────

    @Nullable
    @Override
    public ComponentPath nextFocusPath(final FocusNavigationEvent event) {
        return nextFocusPathInContainer(event);
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
}
