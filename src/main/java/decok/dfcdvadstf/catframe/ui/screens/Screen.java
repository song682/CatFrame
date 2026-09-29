package decok.dfcdvadstf.catframe.ui.screens;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
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
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * Base screen for the CatFrame UI library — extends vanilla {@link GuiScreen}
 * while inlining the
 * component-container / focus-navigation / event-dispatch behaviour of the
 * high-version Minecraft
 * {@code Screen} (which extends {@code AbstractContainerEventHandler}). By
 * implementing
 * {@link GuiEventListener} and {@link ContainerEventHandler}, the screen itself
 * is the
 * root of the
 * CatFrame component tree; implementing {@link CatFrameInputScreen} lets
 * {@code MixinGuiScreen}
 * route split LWJGL2 keyboard events to it ({@link #getEventRoot()} returns
 * {@code this}).
 * </p>
 *
 * <h3>Lifecycle</h3>
 * <ul>
 * <li>{@link #initGui()} — vanilla calls this at {@code setWorldAndResolution} (including each resize). This class
 * rebuilds components ({@link #clearWidgets()} → {@link #init()} →
 * {@link #setInitialFocus()}),
 * matching the 1.7.10 convention of "rebuild widgets in initGui".</li>
 * <li>{@link #init()} — subclasses add components here via {@link #addRenderableWidget(GuiEventListener)}
 * etc.</li>
 * <li>{@link #updateScreen()} → {@link #tick()}; {@link #onGuiClosed()} →
 * {@link #removed()}.</li>
 * </ul>
 *
 * <h3>Event contract</h3>
 * <p>
 * Split keyboard events ({@code keyPressed}/{@code keyReleased}/{@code charTyped}) are dispatched via
 * {@code ScreenKeyboardInput} (Tab focus navigation and {@code keyReleased} only reach components through it);
 * this class's vanilla {@link #keyTyped(char, int)} is driven by
 * {@code super.handleKeyboardInput()}, handles Esc-to-close and then forwards to
 * the focused child via {@link #dispatchKeyTyped(char, int)} — the
 * <strong>only</strong> input channel for leaf components that implement just
 * {@code keyTyped} (the legacy keyTyped bridge inside {@code ScreenKeyboardInput}
 * that used to coexist with it was removed after causing double input).
 * </p>
 * </p>
 */
public abstract class Screen extends GuiScreen implements GuiEventListener, ContainerEventHandler, CatFrameInputScreen {

    /** Screen title */
    protected final Text title;

    /**
     * All interactive children (focusable / event targets)
     */
    private final List<GuiEventListener> children = new ArrayList<>();

    /** Children that should be rendered each frame */
    private final List<GuiEventListener> renderables = new ArrayList<>();

    @Nullable
    private GuiEventListener focusedChild;
    private boolean dragging;

    // ──── Construction ────

    protected Screen(final Text title) {
        this.title = title;
    }

    /** @return this screen's title */
    public Text getTitle() {
        return this.title;
    }

    /** @return the Minecraft client */
    public Minecraft getMinecraft() {
        return this.mc;
    }

    /** @return the font renderer */
    public FontRenderer getFont() {
        return this.fontRendererObj;
    }

    // ──── Vanilla lifecycle → CatFrame lifecycle ────

    /**
     * Vanilla init hook. {@code setWorldAndResolution} calls this on first show and each resize
     * (at which point {@link #width}/{@link #height} are already populated by vanilla). This class
     * rebuilds the component tree accordingly.
     */
    @Override
    public void initGui() {
        clearWidgets();
        clearFocus();
        init();
        setInitialFocus();
    }

    /**
     * Subclasses build the UI here.
     * <p>
     * Subclasses build the UI here via {@link #addRenderableWidget(GuiEventListener)} /
     * {@link #addWidget(GuiEventListener)} /
     * {@link #addRenderableOnly(GuiEventListener)} to register components.
     * </p>
     */
    protected void init() {
    }

    @Override
    public void updateScreen() {
        tick();
    }

    /** Per-tick update hook. */
    public void tick() {
    }

    @Override
    public void onGuiClosed() {
        removed();
    }

    /** Called when the screen is removed. */
    public void removed() {
    }

    // ──── Widget registration ────

    /**
     * Add a component that is both rendered and receives events / focus.
     */
    protected <T extends GuiEventListener> T addRenderableWidget(final T widget) {
        this.renderables.add(widget);
        this.children.add(widget);
        return widget;
    }

    /**
     * Add a component that receives events / focus but is rendered elsewhere.
     */
    protected <T extends GuiEventListener> T addWidget(final T widget) {
        this.children.add(widget);
        return widget;
    }

    /**
     * Add a render-only component (no events / focus).
     */
    protected <T extends GuiEventListener> T addRenderableOnly(final T renderable) {
        this.renderables.add(renderable);
        return renderable;
    }

    /** Remove a previously registered component. */
    protected void removeWidget(final GuiEventListener widget) {
        this.renderables.remove(widget);
        if (this.focusedChild == widget) {
            clearFocus();
        }
        this.children.remove(widget);
    }

    /** Clear all registered components. */
    protected void clearWidgets() {
        this.renderables.clear();
        this.children.clear();
    }

    /**
     * Collect item tooltip text lines — mirrors 26.1.2 {@code Screen.getTooltipFromItem(Minecraft, ItemStack)}.
     * <p>
     * 1.7.10 adaptation: line text delegates to vanilla {@link ItemStack#getTooltip}; coloring follows
     * vanilla {@code GuiScreen.renderToolTip} rules (first line rarity color, rest {@code GRAY}),
     * ensuring item tooltip visual parity after switching to CatFrame pipeline.
     * </p>
     * <p>
     * Compatibility: {@link ItemStack#getTooltip} internally calls item-side {@code Item#addInformation}
     * (Item.java 736-741) and fires Forge {@code ItemTooltipEvent} via {@code ForgeEventFactory#onItemTooltip} —
     * both 1.7.10 legacy hooks remain effective through this delegation,
     * line text collection should continue through that path.
     * </p>
     */
    @SuppressWarnings("unchecked")
    public static List<String> getTooltipFromItem(final Minecraft mc, final ItemStack itemStack) {
        final List<String> lines = itemStack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, (i == 0 ? itemStack.getRarity().rarityColor : EnumChatFormatting.GRAY.toString()) + lines.get(i));
        }
        return lines;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return this.children;
    }

    // ──── Rendering ────

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        renderBackground(mouseX, mouseY, partialTicks);
        for (int i = 0; i < this.renderables.size(); i++) {
            final GuiEventListener renderable = this.renderables.get(i);
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
     */
    protected void renderBackground(final int mouseX, final int mouseY, final float partialTicks) {
        drawDefaultBackground();
    }

    // ──── Mouse events (vanilla → CatFrame dispatch) ────

    /**
     * Overridden as {@code public} (widening the vanilla {@code protected}) to
     * satisfy
     * {@link GuiEventListener#mouseClicked(int, int, int)}. Forwards to vanilla
     * buttons
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
     * The vanilla path drives {@code keyTyped} →
     * {@link #dispatchKeyTyped(char, int)}, the sole input channel for leaf
     * components that override only {@code keyTyped}.
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
     */
    @Override
    public void handleKeyboardInput() {
        ScreenKeyboardInput.handleCurrentEvent(getEventRoot());
        super.handleKeyboardInput();
    }

    // ──── Keyboard (vanilla path: Esc only) ────

    /**
     * Overridden as {@code public} to satisfy
     * {@link GuiEventListener#keyTyped(char, int)}. Handles Esc-to-close and
     * then forwards to the focused child via {@link #dispatchKeyTyped(char, int)}.
     * <p>
     * Driven by {@code super.handleKeyboardInput()} (vanilla fires it only on key
     * press). Leaf components (edit boxes, buttons, etc.) override just
     * {@code keyTyped} — they do not implement the split-method equivalents — so
     * this dispatch is their <strong>only</strong> keyboard input channel. The
     * legacy {@code keyTyped} bridge inside
     * {@code ScreenKeyboardInput.handleCurrentEvent} was removed because it
     * delivered every keystroke twice (here and via the vanilla path).
     * </p>
     */
    @Override
    public void keyTyped(final char typedChar, final int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE && shouldCloseOnEsc() && this.mc.currentScreen == this) {
            onClose();
        }
        dispatchKeyTyped(typedChar, keyCode);
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
    public GuiEventListener getEventRoot() {
        return this;
    }

    /**
     * Returns {@code true}: this base drives its own keyboard dispatch in
     * {@link #handleKeyboardInput()}, so {@code MixinGuiScreen} must not dispatch
     * for it again.
     */
    @Override
    public boolean handlesKeyboardDispatchInternally() {
        return true;
    }

    // ──── Close / Esc ────

    /** @return whether Esc closes this screen */
    public boolean shouldCloseOnEsc() {
        return true;
    }

    /** Close this screen. */
    public void onClose() {
        this.mc.displayGuiScreen(null);
    }

    /** @return whether this screen pauses a single-player world */
    public boolean isPauseScreen() {
        return true;
    }

    /**
     * Whether this screen pauses the game when opened. Defaults to
     * {@link #isPauseScreen()} or
     * {@link OverlayManager#isPausingGame()}.
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
     */
    protected void setInitialFocus() {
    }

    /** Move focus to the given target's resolved path. */
    protected void setInitialFocus(final GuiEventListener target) {
        final ComponentPath path = target.nextFocusPath(new FocusNavigationEvent.TabNavigation(true));
        if (path != null) {
            clearFocus();
            path.applyFocus(true);
        }
    }

    /** Clear the current focus. */
    public void clearFocus() {
        setFocused((GuiEventListener) null);
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(final FocusNavigationEvent event) {
        return nextFocusPathInContainer(event);
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
