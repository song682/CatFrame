package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * <p>
 * 文本框组件 —— 基于 {@link AbstractComponent}，对标高版本 Minecraft 的 {@code EditBox}。<br>
 * 包装原版 {@code GuiTextField} 的核心功能。
 * </p>
 * <p>
 * Edit box component — based on {@link AbstractComponent}, counterpart of the
 * high-version Minecraft {@code EditBox}. Wraps core {@code GuiTextField} functionality.
 * </p>
 */
public class EditBox extends AbstractComponent {

    private Text message;
    private String text = "";
    private String hint = "";
    private int maxLength = 32;
    private boolean focused;
    private int cursorPosition;
    private int selectionEnd;

    public EditBox(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public EditBox(int x, int y, int width, int height, Text message) {
        super(x, y, width, height);
        this.message = message;
    }

    // ──── Text management ────

    public void setText(String text) {
        this.text = text;
        this.cursorPosition = text.length();
        this.selectionEnd = cursorPosition;
    }

    public String getText() {
        return text;
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

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public boolean isFocused() {
        return focused;
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
        if (mouseButton == 0) {
            focused = isMouseOver(mouseX, mouseY);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (!focused) return;

        if (keyCode == 14) { // Backspace
            if (cursorPosition > 0) {
                text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
                cursorPosition--;
                selectionEnd = cursorPosition;
            }
        } else if (keyCode == 28) { // Enter
            // ignore
        } else if (keyCode == 211) { // Delete
            if (cursorPosition < text.length()) {
                text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1);
                selectionEnd = cursorPosition;
            }
        } else if (typedChar != 0 && text.length() < maxLength) {
            text = text.substring(0, cursorPosition) + typedChar + text.substring(cursorPosition);
            cursorPosition++;
            selectionEnd = cursorPosition;
        }
    }

    // ──── Rendering ────

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Draw background
        int bgColor = focused ? 0xFF333366 : 0xFF333333;
        drawRect(x, y, x + width, y + height, bgColor);

        // Draw border
        int borderColor = focused ? 0xFFFFFFFF : 0xFF888888;
        drawRect(x, y, x + width, y + 1, borderColor);
        drawRect(x, y + height - 1, x + width, y + height, borderColor);
        drawRect(x, y, x + 1, y + height, borderColor);
        drawRect(x + width - 1, y, x + width, y + height, borderColor);

        // Draw text or hint
        String displayText = text;
        int textColor = 0xFFFFFF;
        if (text.isEmpty() && !focused) {
            displayText = hint;
            textColor = 0x888888;
        }

        int textX = x + 4;
        int textY = y + (height - font.FONT_HEIGHT) / 2;
        String clipped = font.trimStringToWidth(displayText, width - 8);
        font.drawStringWithShadow(clipped, textX, textY, textColor);

        // Draw cursor
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = textX + font.getStringWidth(clipped.substring(0,
                Math.min(cursorPosition, clipped.length())));
            font.drawStringWithShadow("|", cursorX, textY, 0xFFFFFF);
        }
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        GuiDrawing.drawRect(left, top, right, bottom, color);
    }
}
