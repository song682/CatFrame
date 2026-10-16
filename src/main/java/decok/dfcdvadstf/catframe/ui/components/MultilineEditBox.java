package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiDrawing;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import java.util.function.Consumer;

/**
 * <p>
 * Multi-line edit box — based on {@link AbstractTextAreaWidget}, counterpart of
 * the
 * high-version Minecraft {@code MultiLineEditBox}. Supports line wrapping,
 * scrolling,
 * selection highlighting and character/line limits.<br>
 * Keyboard editing is delegated to {@link MultilineTextField}; scrolling is
 * driven by
 * the cursor listener {@link #scrollToCursor()} (Up/Down do not trigger the
 * parent's
 * page scroll, matching the high-version behaviour).
 * </p>
 */
public class MultilineEditBox extends AbstractTextAreaWidget {

    /** Insert cursor (vertical bar) colour */
    private static final int CURSOR_COLOR = 0xFFD0D0D0;
    /** Placeholder text colour (white at 80% alpha) */
    private static final int PLACEHOLDER_TEXT_COLOR = 0xCCE0E0E0;
    /** Default text colour */
    private static final int DEFAULT_TEXT_COLOR = 0xFFE0E0E0;
    /** Selection highlight colour */
    private static final int HIGHLIGHT_COLOR = 0xFF00008B;

    private final FontRenderer font;
    private final String placeholder;
    private final MultilineTextField textField;
    private int textColor = DEFAULT_TEXT_COLOR;
    private boolean textShadow = true;
    private int cursorCounter;

    public MultilineEditBox(int x, int y, int width, int height, String placeholder) {
        this(x, y, width, height, placeholder, true, true);
    }

    public MultilineEditBox(int x, int y, int width, int height, String placeholder,
            boolean showBackground, boolean showDecorations) {
        super(x, y, width, height, ScrollbarSettings.defaultSettings((int) (9.0 / 2.0)), showBackground,
                showDecorations);
        this.font = Minecraft.getMinecraft().fontRenderer;
        this.placeholder = placeholder != null ? placeholder : "";
        this.textField = new MultilineTextField(this.font, width - this.totalInnerPadding());
        this.textField.setCursorListener(this::scrollToCursor);
    }

    // ──── Configuration ────

    public void setCharacterLimit(int characterLimit) {
        this.textField.setCharacterLimit(characterLimit);
    }

    public void setLineLimit(int lineLimit) {
        this.textField.setLineLimit(lineLimit);
    }

    public void setValueListener(Consumer<String> valueListener) {
        this.textField.setValueListener(valueListener);
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    public void setTextShadow(boolean textShadow) {
        this.textShadow = textShadow;
    }

    // ──── Value ────

    public void setValue(String value) {
        this.textField.setValue(value);
    }

    public void setValue(String value, boolean allowOverflowLineLimit) {
        this.textField.setValue(value, allowOverflowLineLimit);
    }

    public String getValue() {
        return this.textField.value();
    }

    /**
     * Insert text at the caret as a whole, delegating to the inner
     * {@link MultilineTextField} so character filtering, character/line limits
     * and a single valueListener refresh all apply. Bulk input such as IME
     * commits should use this entry.
     *
     * @param text text to insert (may be null)
     */
    @Override
    public void insertText(String text) {
        this.textField.insertText(text);
    }

    // ──── Event handling ────

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!focused)
            return;
        // Deliberately not calling super: cursor movement and scrolling are both
        // driven by MultilineTextField + scrollToCursor, so Up/Down must not also
        // trigger the parent's page scroll (mirrors MultiLineEditBox.keyPressed).
        this.textField.keyTyped(typedChar, keyCode);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0) {
            this.textField.setSelecting(GuiScreen.isShiftKeyDown());
            this.seekCursorScreen(mouseX, mouseY);
            this.cursorCounter = 0;
        }
    }

    @Override
    public void mouseDrag(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
        super.mouseDrag(mouseX, mouseY, mouseButton, timeSinceLastClick);
        // Drag on the scrollbar drags the scroller, not the selection.
        if (mouseButton == 0 && !isOverScrollbar(mouseX, mouseY)) {
            this.textField.setSelecting(true);
            this.seekCursorScreen(mouseX, mouseY);
        }
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            this.cursorCounter = 0;
        }
    }

    // ──── Rendering ────

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        this.cursorCounter++;

        String value = this.textField.value();
        if (value.isEmpty() && !isFocused()) {
            // Placeholder hint, wrapped like real contents.
            if (!this.placeholder.isEmpty()) {
                this.font.drawSplitString(this.placeholder, getInnerLeft(), getInnerTop(),
                        this.width - this.totalInnerPadding(), PLACEHOLDER_TEXT_COLOR);
            }
            return;
        }

        int cursor = this.textField.cursor();
        boolean showCursor = isFocused() && (this.cursorCounter / 6) % 2 == 0;
        boolean insertCursor = cursor < value.length();
        int cursorX = 0;
        int cursorY = 0;
        int drawTop = getInnerTop();
        int innerLeft = getInnerLeft();
        boolean hasDrawnCursor = false;

        for (MultilineTextField.StringView lineView : this.textField.iterateLines()) {
            boolean lineWithinVisibleBounds = this.withinContentAreaTopBottom(drawTop, drawTop + this.font.FONT_HEIGHT);
            if (!hasDrawnCursor && showCursor && insertCursor && cursor >= lineView.beginIndex
                    && cursor <= lineView.endIndex) {
                if (lineWithinVisibleBounds) {
                    String textBeforeCursor = value.substring(lineView.beginIndex, cursor);
                    int textBeforeCursorPosRight = innerLeft + this.font.getStringWidth(textBeforeCursor);
                    String textAfterCursor = value.substring(cursor, lineView.endIndex);
                    this.font.drawString(textBeforeCursor, innerLeft, drawTop, this.textColor, this.textShadow);
                    this.font.drawString(textAfterCursor, textBeforeCursorPosRight, drawTop, this.textColor,
                            this.textShadow);
                    cursorX = textBeforeCursorPosRight;
                    cursorY = drawTop;
                    if (showCursor) {
                        // Insert cursor: vertical bar between characters.
                        GuiDrawing.drawRect(cursorX, cursorY - 1, cursorX + 1, cursorY + this.font.FONT_HEIGHT,
                                CURSOR_COLOR);
                    }
                    hasDrawnCursor = true;
                }
            } else if (lineWithinVisibleBounds) {
                String substring = value.substring(lineView.beginIndex, lineView.endIndex);
                this.font.drawString(substring, innerLeft, drawTop, this.textColor, this.textShadow);
                if (showCursor && !insertCursor) {
                    cursorX = innerLeft + this.font.getStringWidth(substring);
                    cursorY = drawTop;
                }
            }
            drawTop += this.font.FONT_HEIGHT;
        }

        if (showCursor && !insertCursor && this.withinContentAreaTopBottom(cursorY, cursorY + this.font.FONT_HEIGHT)) {
            // Append cursor: underscore after the last character.
            this.font.drawStringWithShadow("_", cursorX, cursorY, CURSOR_COLOR);
        }

        // Selection highlight
        if (this.textField.hasSelection()) {
            MultilineTextField.StringView selection = this.textField.getSelected();
            int drawX = getInnerLeft();
            drawTop = getInnerTop();
            for (MultilineTextField.StringView lineView : this.textField.iterateLines()) {
                if (selection.beginIndex > lineView.endIndex) {
                    drawTop += this.font.FONT_HEIGHT;
                } else {
                    if (lineView.beginIndex > selection.endIndex) {
                        break;
                    }
                    if (this.withinContentAreaTopBottom(drawTop, drawTop + this.font.FONT_HEIGHT)) {
                        int drawBegin = this.font.getStringWidth(
                                value.substring(lineView.beginIndex,
                                        Math.max(selection.beginIndex, lineView.beginIndex)));
                        int drawEnd;
                        if (selection.endIndex > lineView.endIndex) {
                            drawEnd = this.width - this.innerPadding();
                        } else {
                            drawEnd = this.font
                                    .getStringWidth(value.substring(lineView.beginIndex, selection.endIndex));
                        }
                        GuiDrawing.drawRect(drawX + drawBegin, drawTop, drawX + drawEnd,
                                drawTop + this.font.FONT_HEIGHT, HIGHLIGHT_COLOR);
                    }
                    drawTop += this.font.FONT_HEIGHT;
                }
            }
        }
    }

    @Override
    protected void extractDecorations(GuiGraphicsExtractor graphics) {
        super.extractDecorations(graphics);
        if (this.textField.hasCharacterLimit()) {
            int characterLimit = this.textField.characterLimit();
            String countText = this.textField.value().length() + "/" + characterLimit;
            this.font.drawStringWithShadow(countText,
                    this.getX() + this.width - this.font.getStringWidth(countText),
                    this.getY() + this.height + 4, 0xFFA0A0A0);
        }
    }

    @Override
    protected int getInnerHeight() {
        return this.font.FONT_HEIGHT * this.textField.getLineCount();
    }

    // ──── Scrolling / cursor seeking ────

    private void scrollToCursor() {
        double scrollAmount = this.scrollAmount();
        MultilineTextField.StringView firstFullyVisibleLine = this.textField
                .getLineView((int) (scrollAmount / this.font.FONT_HEIGHT));
        if (this.textField.cursor() <= firstFullyVisibleLine.beginIndex) {
            scrollAmount = this.textField.getLineAtCursor() * this.font.FONT_HEIGHT;
        } else {
            MultilineTextField.StringView lastFullyVisibleLine = this.textField
                    .getLineView((int) ((scrollAmount + this.height) / this.font.FONT_HEIGHT) - 1);
            if (this.textField.cursor() > lastFullyVisibleLine.endIndex) {
                scrollAmount = this.textField.getLineAtCursor() * this.font.FONT_HEIGHT
                        - this.height + this.font.FONT_HEIGHT + this.totalInnerPadding();
            }
        }
        this.setScrollAmount(scrollAmount);
    }

    private void seekCursorScreen(int x, int y) {
        double mouseX = x - this.getX() - this.innerPadding();
        double mouseY = y - this.getY() - this.innerPadding() + this.scrollAmount();
        this.textField.seekCursorToPoint(mouseX, mouseY);
    }
}
