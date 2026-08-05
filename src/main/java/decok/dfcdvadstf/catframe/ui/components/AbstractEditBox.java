package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiDrawing;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

/**
 * <p>
 * 文本框组件 —— 基于 {@link AbstractTextAreaWidget}，对标高版本 Minecraft 的
 * {@code EditBox}。<br>
 * 支持纹理背景（useVanilla 切换原版黑白框）、原版风格光标（末尾_内部|）或强制竖线光标。
 * </p>
 * <p>
 * Edit box component — based on {@link AbstractTextAreaWidget}, counterpart of
 * the
 * high-version Minecraft {@code EditBox}. Supports textured background
 * (useVanilla
 * toggles vanilla grey/black box), vanilla-style cursor (_ at end, | inside
 * text),
 * or forced vertical bar cursor.
 * </p>
 */
public abstract class AbstractEditBox extends AbstractTextAreaWidget {

    private Text message;
    private String text = "";
    private String hint = "";
    private int maxLength = 32;
    private int cursorPosition;
    private int selectionEnd;
    private int cursorCounter;

    /**
     * If true, use CatFrame texture-based background; if false, solid colour.
     * <p>
     * 为 true 时使用 CatFrame 纹理背景；false 时纯色。
     * </p>
     */
    protected boolean useTextureBackground = true;

    /**
     * If true, use vanilla grey/black box instead of CatFrame texture or solid
     * colour.
     * <p>
     * 为 true 时使用原版灰/黑框，替代 CatFrame 纹理或纯色。
     * </p>
     */
    protected boolean useVanillaTexture = false;

    /**
     * If true, always draw vertical bar cursor (|). If false, match vanilla
     * behaviour:
     * underscore (_) at end of text, vertical bar (|) when editing inside text.
     * <p>
     * 为 true 时始终绘制竖线光标(|)。为 false 时匹配原版行为：末尾_，文字内部|。
     * </p>
     */
    protected boolean forceVerticalCursor = false;

    public AbstractEditBox(int x, int y, int width, int height) {
        // Scroll rate 4 mirrors vanilla MultiLineEditBox's (int)(9.0 / 2.0).
        // 滚动量 4 对齐原版 MultiLineEditBox 的 (int)(9.0 / 2.0)。
        super(x, y, width, height, ScrollbarSettings.defaultSettings(4));
    }

    public AbstractEditBox(int x, int y, int width, int height, Text message) {
        this(x, y, width, height);
        this.message = message;
    }

    // ──── Configuration ────

    /**
     * If true, use vanilla grey/black text field background.
     * <p>
     * 为 true 时使用原版灰/黑文本框背景。
     * </p>
     */
    public AbstractEditBox setUseVanillaTexture(boolean useVanilla) {
        this.useVanillaTexture = useVanilla;
        return this;
    }

    /**
     * If true, always draw vertical bar cursor instead of vanilla
     * underscore-at-end.
     * <p>
     * 为 true 时始终绘制竖线光标，而非原版的末尾下划线。
     * </p>
     */
    public AbstractEditBox setForceVerticalCursor(boolean force) {
        this.forceVerticalCursor = force;
        return this;
    }

    // ──── Text management ────

    public void setText(String text) {
        this.text = text;
        this.cursorPosition = text.length();
        this.selectionEnd = cursorPosition;
        onTextChanged();
    }

    public String getText() {
        return text;
    }

    /**
     * Current caret position (character index), for external anchoring such as
     * the IME candidate-window anchor.
     * <p>
     * 当前光标位置（字符索引），供 IME 候选窗锚点等外部定位使用。
     * </p>
     */
    public int getCursorPosition() {
        return cursorPosition;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getHint() {
        return hint;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public Text getMessage() {
        return message;
    }

    public void setMessage(Text message) {
        this.message = message;
    }

    // ──── Event handling ────

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0) {
            focused = isMouseOver(mouseX, mouseY);
            if (focused)
                cursorCounter = 0;
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!focused)
            return;

        // Ctrl+A: select all
        if (keyCode == 30 && isCtrlKeyDown()) {
            cursorPosition = text.length();
            selectionEnd = 0;
            return;
        }
        // Ctrl+C: copy
        if (keyCode == 46 && isCtrlKeyDown()) {
            setClipboardString(getSelectedText());
            return;
        }
        // Ctrl+V: paste (whole-insert path, shared with IME commits)
        // Ctrl+V：粘贴（与 IME 提交共用的整体插入路径）
        if (keyCode == 47 && isCtrlKeyDown()) {
            insertText(getClipboardString());
            return;
        }
        // Ctrl+X: cut
        if (keyCode == 45 && isCtrlKeyDown()) {
            setClipboardString(getSelectedText());
            deleteSelection();
            onTextChanged();
            return;
        }

        if (keyCode == 14) { // Backspace
            if (selectionEnd != cursorPosition) {
                deleteSelection();
            } else if (cursorPosition > 0) {
                text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
                cursorPosition--;
                selectionEnd = cursorPosition;
            }
            onTextChanged();
        } else if (keyCode == 28) { // Enter
            // ignore
        } else if (keyCode == 211) { // Delete
            if (selectionEnd != cursorPosition) {
                deleteSelection();
            } else if (cursorPosition < text.length()) {
                text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1);
                selectionEnd = cursorPosition;
            }
            onTextChanged();
        } else if (keyCode == 203) { // Left arrow
            if (cursorPosition > 0) {
                cursorPosition--;
                if (!GuiScreen.isShiftKeyDown())
                    selectionEnd = cursorPosition;
            }
        } else if (keyCode == 205) { // Right arrow
            if (cursorPosition < text.length()) {
                cursorPosition++;
                if (!GuiScreen.isShiftKeyDown())
                    selectionEnd = cursorPosition;
            }
        } else if (keyCode == 199) { // Home
            cursorPosition = 0;
            if (!GuiScreen.isShiftKeyDown())
                selectionEnd = cursorPosition;
        } else if (keyCode == 207) { // End
            cursorPosition = text.length();
            if (!GuiScreen.isShiftKeyDown())
                selectionEnd = cursorPosition;
        } else if (typedChar != 0 && text.length() < maxLength && ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
            if (selectionEnd != cursorPosition)
                deleteSelection();
            text = text.substring(0, cursorPosition) + typedChar + text.substring(cursorPosition);
            cursorPosition++;
            selectionEnd = cursorPosition;
            onTextChanged();
        }
    }

    /**
     * Insert text at the caret as a whole, keeping the same path as Ctrl+V
     * paste: per-character filtering and the maxLength limit apply, and the
     * content refresh fires exactly once. Bulk input such as IME commits
     * should use this entry.
     * <p>
     * 在光标处整体插入文本，与 Ctrl+V 粘贴路径一致：逐字符过滤与 maxLength
     * 限制保持，内容刷新只触发一次。IME 提交等批量输入应走此入口。
     * </p>
     *
     * @param text text to insert (may be null / 待插入文本，可为 null)
     */
    @Override
    public void insertText(String text) {
        if (text == null) {
            return;
        }
        for (int i = 0; i < text.length() && this.text.length() < maxLength; i++) {
            char c = text.charAt(i);
            if (ChatAllowedCharacters.isAllowedCharacter(c)) {
                this.text = this.text.substring(0, cursorPosition) + c + this.text.substring(cursorPosition);
                cursorPosition++;
            }
        }
        selectionEnd = cursorPosition;
        onTextChanged();
    }

    private void deleteSelection() {
        if (selectionEnd == cursorPosition)
            return;
        int start = Math.min(cursorPosition, selectionEnd);
        int end = Math.max(cursorPosition, selectionEnd);
        text = text.substring(0, start) + text.substring(end);
        cursorPosition = start;
        selectionEnd = start;
    }

    private String getSelectedText() {
        if (selectionEnd == cursorPosition)
            return "";
        int start = Math.min(cursorPosition, selectionEnd);
        int end = Math.max(cursorPosition, selectionEnd);
        return text.substring(start, end);
    }

    private static boolean isCtrlKeyDown() {
        return GuiScreen.isCtrlKeyDown();
    }

    private static void setClipboardString(String s) {
        GuiScreen.setClipboardString(s);
    }

    private static String getClipboardString() {
        return GuiScreen.getClipboardString();
    }

    // ──── Text change hook ────

    /**
     * Called whenever the text content changes. Subclasses can override to react.
     * <p>
     * 当文本内容变化时调用。子类可重写以响应变化。
     * </p>
     */
    protected void onTextChanged() {
    }

    // ──── ChatAllowedCharacters helper ────

    private static class ChatAllowedCharacters {
        static boolean isAllowedCharacter(char c) {
            return c >= 32 && c != 127;
        }
    }

    // ──── Rendering ────

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics) {
        boolean focused = isFocused();

        if (useVanillaTexture) {
            drawVanillaBackground(focused);
        } else if (useTextureBackground) {
            super.extractBackground(graphics);
        } else {
            int bgColor = focused ? 0xFF333366 : 0xFF333333;
            drawRect(x, y, x + width, y + height, bgColor);

            int borderColor = focused ? 0xFFFFFFFF : 0xFF888888;
            drawRect(x, y, x + width, y + 1, borderColor);
            drawRect(x, y + height - 1, x + width, y + height, borderColor);
            drawRect(x, y, x + 1, y + height, borderColor);
            drawRect(x + width - 1, y, x + width, y + height, borderColor);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Update cursor counter
        cursorCounter++;

        boolean focused = isFocused();

        // Draw text or hint
        String displayText = text;
        int textColor = 0xFFFFFF;
        if (text.isEmpty() && !focused) {
            displayText = hint;
            textColor = 0x888888;
        }

        int textX = getInnerLeft();
        int textY = getY() + (height - font.FONT_HEIGHT) / 2;
        String clipped = font.trimStringToWidth(displayText, width - 8);
        font.drawStringWithShadow(clipped, textX, textY, textColor);

        // Draw cursor (blinking)
        if (focused && (cursorCounter / 6) % 2 == 0) {
            int cursorX = textX + font.getStringWidth(clipped.substring(0,
                    Math.min(cursorPosition, clipped.length())));

            boolean atEnd = cursorPosition >= text.length() && text.length() < maxLength;
            if (forceVerticalCursor || !atEnd) {
                // Vertical bar cursor (inside text or forced)
                Gui.drawRect(cursorX, textY - 1, cursorX + 1, textY + font.FONT_HEIGHT + 1, 0xFFFFFFFF);
            } else {
                // Underscore cursor (at end with room, vanilla style)
                font.drawStringWithShadow("_", cursorX, textY, 0xFFFFFF);
            }
        }
    }

    @Override
    protected int getInnerHeight() {
        // Single-line box: content height equals the visible height, so it never
        // scrolls.
        // 单行文本框：内容高度等于可视高度，因此永不滚动。
        return height - totalInnerPadding();
    }

    /**
     * Draw vanilla-style border + black fill background. When focused the border
     * is highlighted white, matching vanilla {@code EditBox}.
     * <p>
     * 绘制原版风格边框 + 黑色填充背景。获得焦点时边框高亮为白色，与原版 {@code EditBox} 一致。
     * </p>
     */
    private void drawVanillaBackground(boolean focused) {
        int borderColor = focused ? 0xFFFFFFFF : 0xFFA0A0A0;
        drawRect(x - 1, y - 1, x + width + 1, y + height + 1, borderColor);
        drawRect(x, y, x + width, y + height, 0xFF000000);
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        GuiDrawing.drawRect(left, top, right, bottom, color);
    }
}
