package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * Text area base — provides inner padding, background/decorations toggles and a
 * scissored, scrollable drawing skeleton; subclasses implement
 * {@link #extractContents} and {@link #getInnerHeight} for the actual text
 * drawing.
 * Counterpart of the high-version Minecraft {@code AbstractTextAreaWidget}.
 * </p>
 */
public abstract class AbstractTextAreaWidget extends AbstractScrollArea {

    /** CatFrame custom text field textures */
    protected static final ResourceLocation TEXT_FIELD_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/sprites/widgets/text_field.png");
    protected static final ResourceLocation TEXT_FIELD_HIGHLIGHTED_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/sprites/widgets/text_field_highlighted.png");

    /** Text field default size from mcmeta */
    protected static final int TEXT_FIELD_DEFAULT_W = 200;
    protected static final int TEXT_FIELD_DEFAULT_H = 20;
    protected static final int TEXT_FIELD_BORDER = 1;

    /** Inner padding between content and border */
    private static final int INNER_PADDING = 4;

    /** Default total padding (both sides combined) */
    public static final int DEFAULT_TOTAL_PADDING = 8;

    private boolean showBackground = true;
    private boolean showDecorations = true;

    public AbstractTextAreaWidget(int x, int y, int width, int height, ScrollbarSettings scrollbarSettings) {
        super(x, y, width, height, scrollbarSettings);
    }

    public AbstractTextAreaWidget(
            int x, int y, int width, int height, ScrollbarSettings scrollbarSettings,
            boolean showBackground, boolean showDecorations) {
        this(x, y, width, height, scrollbarSettings);
        this.showBackground = showBackground;
        this.showDecorations = showDecorations;
    }

    // ──── Event handling ────

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Scrollbar dragging takes priority, then the default focus behaviour.
        updateScrolling(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        // Up/Down scroll the content while focused; consume the key when the scroll
        // moved.
        if (focused && (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN)) {
            double previousScrollAmount = scrollAmount();
            setScrollAmount(scrollAmount() + (keyCode == Keyboard.KEY_UP ? -1 : 1) * scrollRate());
            if (previousScrollAmount != scrollAmount()) {
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    // ──── Rendering ────

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        if (!visible)
            return;

        if (showBackground) {
            extractBackground(graphics);
        }

        // Clip contents to the inner area (1 px border inset), then translate by the
        // scroll offset while drawing — contents are drawn in unscrolled coordinates.
        // Scissor coordinates are in framebuffer pixels, so the GUI-scale-space
        // geometry is multiplied by the GUI scale factor.
        Minecraft mc = Minecraft.getMinecraft();
        int scaleFactor = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((getX() + 1) * scaleFactor,
                mc.displayHeight - (getY() + height - 1) * scaleFactor,
                (width - 2) * scaleFactor,
                (height - 2) * scaleFactor);
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, (float) (-this.scrollAmount()), 0.0F);
        extractContents(graphics, mouseX, mouseY, partialTicks);
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Scrollbar appears only when the content actually overflows.
        if (scrollable()) {
            renderScrollbar(mouseX, mouseY);
        }

        if (showDecorations) {
            extractDecorations(graphics);
        }
    }

    /**
     * Hook for drawing decorations on top of the contents (e.g. character limit
     * counters). Called after the scrollbar.
     */
    protected void extractDecorations(GuiGraphicsExtractor graphics) {
    }

    // ──── Text input (IME / paste) ────

    /**
     * Insert text at the caret as a whole, keeping the same path as manual
     * input: character filtering, length limits and the content refresh all
     * apply. Intended for bulk input such as IME commits.
     * <p>
     * The default feeds characters through {@link #keyTyped(char, int)} so
     * unoverridden subclasses still validate and refresh; editable subclasses
     * with a single-pass insert (e.g. a bulk-insert backing model) should
     * override for one refresh per commit.
     * </p>
     *
     * @param text committed text (may be null)
     */
    public void insertText(String text) {
        if (text == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            keyTyped(text.charAt(i), Keyboard.KEY_NONE);
        }
    }

    // ──── Geometry ────

    /**
     * Inner padding between content and border (single side). Public so
     * external positioning (e.g. the IME candidate-window anchor) can reuse it.
     */
    public int innerPadding() {
        return INNER_PADDING;
    }

    protected int totalInnerPadding() {
        return this.innerPadding() * 2;
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return active && visible
                && mouseX >= getX() && mouseY >= getY()
                && mouseX < getRight() + scrollbarWidth()
                && mouseY < getBottom();
    }

    @Override
    protected int scrollBarX() {
        return getRight();
    }

    @Override
    protected int contentHeight() {
        return getInnerHeight() + totalInnerPadding();
    }

    // ──── Background ────

    protected void extractBackground(GuiGraphicsExtractor graphics) {
        extractBorder(graphics, getX(), getY(), getWidth(), getHeight());
    }

    /**
     * Draw the text field border+background, highlighted when active and focused.
     */
    protected void extractBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        ResourceLocation tex = (isActive() && isFocused()) ? TEXT_FIELD_HIGHLIGHTED_TEXTURE : TEXT_FIELD_TEXTURE;
        TextureStretching.drawAutoNinePatch(tex, x, y, width, height,
                TEXT_FIELD_DEFAULT_W, TEXT_FIELD_DEFAULT_H, TEXT_FIELD_BORDER);
    }

    /**
     * Whether the given top/bottom line bounds intersect the visible content area,
     * taking the current scroll offset into account.
     */
    protected boolean withinContentAreaTopBottom(int top, int bottom) {
        return bottom - this.scrollAmount() >= this.getY() && top - this.scrollAmount() <= this.getY() + this.height;
    }

    // ──── Abstract ────

    /**
     * @return the total height of all content lines
     */
    protected abstract int getInnerHeight();

    /**
     * Draw the scrollable contents at
     * {@link #getInnerLeft()}/{@link #getInnerTop()}.
     * Called with the GL matrix already translated by {@code -scrollAmount()}.
     */
    protected abstract void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks);

    protected int getInnerLeft() {
        return this.getX() + this.innerPadding();
    }

    protected int getInnerTop() {
        return this.getY() + this.innerPadding();
    }
}
