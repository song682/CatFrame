package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * <p>
 * 文本区域基类 —— 提供内边距、背景/装饰开关与滚动裁剪的文本编辑区域骨架，
 * 子类实现 {@link #extractContents} 与 {@link #getInnerHeight} 完成具体文本绘制。<br>
 * 对标高版本 Minecraft 的 {@code AbstractTextAreaWidget}。
 * </p>
 * <p>
 * Text area base — provides inner padding, background/decorations toggles and a
 * scissored, scrollable drawing skeleton; subclasses implement
 * {@link #extractContents} and {@link #getInnerHeight} for the actual text
 * drawing.
 * Counterpart of the high-version Minecraft {@code AbstractTextAreaWidget}.
 * </p>
 */
public abstract class AbstractTextAreaWidget extends AbstractScrollArea {

    /** CatFrame custom text field textures / CatFrame 自定义文本框纹理 */
    protected static final ResourceLocation TEXT_FIELD_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/text_field.png");
    protected static final ResourceLocation TEXT_FIELD_HIGHLIGHTED_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/text_field_highlighted.png");

    /** Text field default size from mcmeta / 文本框默认尺寸 */
    protected static final int TEXT_FIELD_DEFAULT_W = 200;
    protected static final int TEXT_FIELD_DEFAULT_H = 20;
    protected static final int TEXT_FIELD_BORDER = 1;

    /** Inner padding between content and border / 内容与边框之间的内边距 */
    private static final int INNER_PADDING = 4;

    /** Default total padding (both sides combined) / 默认总内边距（两侧合计） */
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
        // 滚动条拖拽优先，随后是默认的焦点行为。
        updateScrolling(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        // Up/Down scroll the content while focused; consume the key when the scroll
        // moved.
        // 聚焦时上下键滚动内容；滚动量发生变化时消费该按键。
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
        // 用 scissor 将内容裁剪到内区（边框内缩 1px），绘制期间按滚动量平移，
        // 子类在未滚动坐标系中直接绘制即可。
        int displayHeight = Minecraft.getMinecraft().displayHeight;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(getX() + 1, displayHeight - getY() - height + 1, width - 2, height - 2);
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, (float) (-this.scrollAmount()), 0.0F);
        extractContents(graphics, mouseX, mouseY, partialTicks);
        GL11.glPopMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Scrollbar appears only when the content actually overflows.
        // 仅当内容溢出时才显示滚动条。
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
     * <p>
     * 内容之上的装饰绘制钩子（如字数统计）。在滚动条之后调用。
     * </p>
     */
    protected void extractDecorations(GuiGraphicsExtractor graphics) {
    }

    // ──── Text input (IME / paste) ────

    /**
     * Insert text at the caret as a whole, keeping the same path as manual
     * input: character filtering, length limits and the content refresh all
     * apply. Intended for bulk input such as IME commits.
     * <p>
     * 在光标处整体插入文本，与手动输入的路径保持一致：字符过滤、长度限制
     * 与内容刷新全部生效。供 IME 提交等批量输入使用。
     * </p>
     * <p>
     * The default feeds characters through {@link #keyTyped(char, int)} so
     * unoverridden subclasses still validate and refresh; editable subclasses
     * with a single-pass insert (e.g. a bulk-insert backing model) should
     * override for one refresh per commit.
     * </p>
     * <p>
     * 默认实现将字符逐个送入 {@link #keyTyped(char, int)}，未覆盖的子类
     * 依然获得校验与刷新；具备一体化插入能力的可编辑子类（如背后有批量
     * 插入模型）应覆盖以实现每次提交只刷新一次。
     * </p>
     *
     * @param text committed text (may be null / 提交文本，可为 null)
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
     * <p>
     * 内容与边框之间的内边距（单侧）。公开以供外部定位（如 IME 候选窗锚点）复用。
     * </p>
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
     * <p>
     * 绘制文本框边框+背景；激活且聚焦时高亮。
     * </p>
     */
    protected void extractBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        ResourceLocation tex = (isActive() && isFocused()) ? TEXT_FIELD_HIGHLIGHTED_TEXTURE : TEXT_FIELD_TEXTURE;
        TextureStretching.drawAutoNinePatch(tex, x, y, width, height,
                TEXT_FIELD_DEFAULT_W, TEXT_FIELD_DEFAULT_H, TEXT_FIELD_BORDER);
    }

    /**
     * Whether the given top/bottom line bounds intersect the visible content area,
     * taking the current scroll offset into account.
     * <p>
     * 给定的上下行边界是否与可见内容区相交（计入当前滚动量）。
     * </p>
     */
    protected boolean withinContentAreaTopBottom(int top, int bottom) {
        return bottom - this.scrollAmount() >= this.getY() && top - this.scrollAmount() <= this.getY() + this.height;
    }

    // ──── Abstract ────

    /**
     * @return the total height of all content lines / 全部内容行的总高度
     */
    protected abstract int getInnerHeight();

    /**
     * Draw the scrollable contents at
     * {@link #getInnerLeft()}/{@link #getInnerTop()}.
     * Called with the GL matrix already translated by {@code -scrollAmount()}.
     * <p>
     * 在 {@link #getInnerLeft()}/{@link #getInnerTop()} 处绘制可滚动内容。
     * 调用时 GL 矩阵已按 {@code -scrollAmount()} 平移。
     * </p>
     */
    protected abstract void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks);

    protected int getInnerLeft() {
        return this.getX() + this.innerPadding();
    }

    protected int getInnerTop() {
        return this.getY() + this.innerPadding();
    }
}
