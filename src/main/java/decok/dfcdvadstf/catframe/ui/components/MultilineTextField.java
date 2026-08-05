package decok.dfcdvadstf.catframe.ui.components;

import com.google.common.collect.Lists;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.function.Consumer;

/**
 * <p>
 * 多行文本字段 —— 纯逻辑层，管理多行文本的值、光标、选区与自动换行，
 * 不负责渲染；对标高版本 Minecraft 的 {@code MultilineTextField}。
 * </p>
 * <p>
 * Multi-line text field — pure logic layer managing the value, cursor,
 * selection
 * and automatic line wrapping of multi-line text; rendering is left to the
 * widget. Counterpart of the high-version Minecraft {@code MultilineTextField}.
 * </p>
 */
public class MultilineTextField {

    private static final Logger LOG = LogManager.getLogger("CatFrame/MultilineTextField");

    /** No character/line limit sentinel / 无字符/行数限制的哨兵值 */
    public static final int NO_LIMIT = Integer.MAX_VALUE;

    /** Pixel bias when seeking the cursor line vertically / 垂直寻行时光标的像素偏移 */
    private static final int LINE_SEEK_PIXEL_BIAS = 2;

    private final FontRenderer font;
    private final List<StringView> displayLines = Lists.newArrayList();
    private String value;
    private int cursor;
    private int selectCursor;
    private boolean selecting;
    private int characterLimit = NO_LIMIT;
    private int lineLimit = NO_LIMIT;
    private final int width;
    private Consumer<String> valueListener = s -> {
    };
    private Runnable cursorListener = () -> {
    };

    public MultilineTextField(FontRenderer font, int width) {
        this.font = font;
        this.width = width;
        this.setValue("");
    }

    // ──── Limits ────

    public int characterLimit() {
        return this.characterLimit;
    }

    public void setCharacterLimit(int characterLimit) {
        if (characterLimit < 0) {
            throw new IllegalArgumentException("Character limit cannot be negative");
        }
        this.characterLimit = characterLimit;
    }

    public void setLineLimit(int lineLimit) {
        if (lineLimit < 0) {
            throw new IllegalArgumentException("Line limit cannot be negative");
        }
        this.lineLimit = lineLimit;
    }

    public boolean hasCharacterLimit() {
        return this.characterLimit != NO_LIMIT;
    }

    public boolean hasLineLimit() {
        return this.lineLimit != NO_LIMIT;
    }

    // ──── Listeners ────

    public void setValueListener(Consumer<String> valueListener) {
        this.valueListener = valueListener;
    }

    public void setCursorListener(Runnable cursorListener) {
        this.cursorListener = cursorListener;
    }

    // ──── Value ────

    public void setValue(String value) {
        this.setValue(value, false);
    }

    public void setValue(String value, boolean allowOverflowLineLimit) {
        String newValue = this.truncateFullText(value);
        if (allowOverflowLineLimit || !this.overflowsLineLimit(newValue)) {
            this.value = newValue;
            this.cursor = this.value.length();
            this.selectCursor = this.cursor;
            this.onValueChange();
        }
    }

    public String value() {
        return this.value;
    }

    public void insertText(String input) {
        if (!input.isEmpty() || this.hasSelection()) {
            String text = this.truncateInsertionText(filterText(input));
            StringView selected = this.getSelected();
            String newValue = new StringBuilder(this.value).replace(selected.beginIndex, selected.endIndex, text)
                    .toString();
            if (!this.overflowsLineLimit(newValue)) {
                this.value = newValue;
                this.cursor = selected.beginIndex + text.length();
                this.selectCursor = this.cursor;
                this.onValueChange();
            }
        }
    }

    public void deleteText(int dir) {
        if (!this.hasSelection()) {
            this.selectCursor = clamp(this.cursor + dir, 0, this.value.length());
        }
        this.insertText("");
    }

    // ──── Cursor / selection ────

    public int cursor() {
        return this.cursor;
    }

    public void setSelecting(boolean selecting) {
        this.selecting = selecting;
    }

    public StringView getSelected() {
        return new StringView(Math.min(this.selectCursor, this.cursor), Math.max(this.selectCursor, this.cursor));
    }

    public boolean hasSelection() {
        return this.selectCursor != this.cursor;
    }

    public String getSelectedText() {
        StringView selected = this.getSelected();
        return this.value.substring(selected.beginIndex, selected.endIndex);
    }

    public int getLineCount() {
        return this.displayLines.size();
    }

    public int getLineAtCursor() {
        for (int i = 0; i < this.displayLines.size(); i++) {
            StringView view = this.displayLines.get(i);
            if (this.cursor >= view.beginIndex && this.cursor <= view.endIndex) {
                return i;
            }
        }
        return -1;
    }

    public StringView getLineView(int lineIndex) {
        return this.displayLines.get(clamp(lineIndex, 0, this.displayLines.size() - 1));
    }

    public Iterable<StringView> iterateLines() {
        return this.displayLines;
    }

    /**
     * Move the cursor absolutely, relatively, or from the end of the value.
     * <p>
     * 以绝对、相对或从文本末尾的方式移动光标。
     * </p>
     */
    public void seekCursor(Whence whence, int cursor) {
        switch (whence) {
            case ABSOLUTE:
                this.cursor = cursor;
                break;
            case RELATIVE:
                this.cursor += cursor;
                break;
            case END:
                this.cursor = this.value.length() + cursor;
        }
        this.cursor = clamp(this.cursor, 0, this.value.length());
        this.cursorListener.run();
        if (!this.selecting) {
            this.selectCursor = this.cursor;
        }
    }

    /**
     * Move the cursor to the same visual column on an adjacent line.
     * <p>
     * 将光标移动到相邻行同一视觉列。
     * </p>
     */
    public void seekCursorLine(int lineOffset) {
        if (lineOffset != 0) {
            int oldCursorLeft = this.font.getStringWidth(
                    this.value.substring(this.getCursorLineView().beginIndex, this.cursor)) + LINE_SEEK_PIXEL_BIAS;
            StringView lineView = this.getCursorLineView(lineOffset);
            int newCursor = this.font
                    .trimStringToWidth(this.value.substring(lineView.beginIndex, lineView.endIndex), oldCursorLeft)
                    .length();
            this.seekCursor(Whence.ABSOLUTE, lineView.beginIndex + newCursor);
        }
    }

    /**
     * Place the cursor at the text position under the given point (widget-local
     * coordinates, unscrolled).
     * <p>
     * 将光标定位到给定点（组件局部、未滚动坐标）下的文本位置。
     * </p>
     */
    public void seekCursorToPoint(double x, double y) {
        int left = (int) Math.floor(x);
        int top = (int) Math.floor(y / this.font.FONT_HEIGHT);
        StringView lineView = this.displayLines.get(clamp(top, 0, this.displayLines.size() - 1));
        int clickedColumn = this.font
                .trimStringToWidth(this.value.substring(lineView.beginIndex, lineView.endIndex), left).length();
        this.seekCursor(Whence.ABSOLUTE, lineView.beginIndex + clickedColumn);
    }

    /**
     * Select the whole word at the cursor.
     * <p>
     * 选中光标处的整个单词。
     * </p>
     */
    public void selectWordAtCursor() {
        StringView wordView = this.getPreviousWord();
        this.seekCursor(Whence.ABSOLUTE, wordView.beginIndex);
        this.setSelecting(true);
        this.seekCursor(Whence.ABSOLUTE, wordView.endIndex);
    }

    // ──── Keyboard ────

    /**
     * Handle a merged key event (legacy CatFrame path). LWJGL2 key codes are
     * mapped to the high-version key semantics of
     * {@code MultilineTextField.keyPressed}.
     * <p>
     * 处理合并式按键事件（CatFrame 旧路径）。将 LWJGL2 键码映射为高版本
     * {@code MultilineTextField.keyPressed} 的按键语义。
     * </p>
     *
     * @return true if the event was consumed / 若事件被消费则返回 true
     */
    public boolean keyTyped(char typedChar, int keyCode) {
        boolean ctrl = GuiScreen.isCtrlKeyDown();
        this.selecting = GuiScreen.isShiftKeyDown();

        // Ctrl+A / C / V / X shortcuts
        // Ctrl+A / C / V / X 快捷键
        if (ctrl && keyCode == Keyboard.KEY_A) {
            this.cursor = this.value.length();
            this.selectCursor = 0;
            return true;
        } else if (ctrl && keyCode == Keyboard.KEY_C) {
            GuiScreen.setClipboardString(this.getSelectedText());
            return true;
        } else if (ctrl && keyCode == Keyboard.KEY_V) {
            String clipboard = GuiScreen.getClipboardString();
            if (clipboard != null) {
                this.insertText(clipboard);
            }
            return true;
        } else if (ctrl && keyCode == Keyboard.KEY_X) {
            GuiScreen.setClipboardString(this.getSelectedText());
            this.insertText("");
            return true;
        }

        switch (keyCode) {
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                this.insertText("\n");
                return true;
            case Keyboard.KEY_BACK:
                if (ctrl) {
                    StringView wordView = this.getPreviousWord();
                    this.deleteText(wordView.beginIndex - this.cursor);
                } else {
                    this.deleteText(-1);
                }
                return true;
            case Keyboard.KEY_DELETE:
                if (ctrl) {
                    StringView wordView = this.getNextWord();
                    this.deleteText(wordView.beginIndex - this.cursor);
                } else {
                    this.deleteText(1);
                }
                return true;
            case Keyboard.KEY_RIGHT:
                if (ctrl) {
                    this.seekCursor(Whence.ABSOLUTE, this.getNextWord().beginIndex);
                } else {
                    this.seekCursor(Whence.RELATIVE, 1);
                }
                return true;
            case Keyboard.KEY_LEFT:
                if (ctrl) {
                    this.seekCursor(Whence.ABSOLUTE, this.getPreviousWord().beginIndex);
                } else {
                    this.seekCursor(Whence.RELATIVE, -1);
                }
                return true;
            case Keyboard.KEY_DOWN:
                if (!ctrl) {
                    this.seekCursorLine(1);
                }
                return true;
            case Keyboard.KEY_UP:
                if (!ctrl) {
                    this.seekCursorLine(-1);
                }
                return true;
            case Keyboard.KEY_PRIOR: // PageUp
                this.seekCursor(Whence.ABSOLUTE, 0);
                return true;
            case Keyboard.KEY_NEXT: // PageDown
                this.seekCursor(Whence.END, 0);
                return true;
            case Keyboard.KEY_HOME:
                if (ctrl) {
                    this.seekCursor(Whence.ABSOLUTE, 0);
                } else {
                    this.seekCursor(Whence.ABSOLUTE, this.getCursorLineView().beginIndex);
                }
                return true;
            case Keyboard.KEY_END:
                if (ctrl) {
                    this.seekCursor(Whence.END, 0);
                } else {
                    this.seekCursor(Whence.ABSOLUTE, this.getCursorLineView().endIndex);
                }
                return true;
            default:
                break;
        }

        // Printable character insertion
        // 可显示字符插入
        if (typedChar != 0 && typedChar >= 32 && typedChar != 127) {
            this.insertText(String.valueOf(typedChar));
            return true;
        }
        return false;
    }

    // ──── Word helpers ────

    public StringView getPreviousWord() {
        if (this.value.isEmpty()) {
            return StringView.EMPTY;
        }
        int startPosition = clamp(this.cursor, 0, this.value.length() - 1);

        while (startPosition > 0 && Character.isWhitespace(this.value.charAt(startPosition - 1))) {
            startPosition--;
        }
        while (startPosition > 0 && !Character.isWhitespace(this.value.charAt(startPosition - 1))) {
            startPosition--;
        }

        return new StringView(startPosition, this.getWordEndPosition(startPosition));
    }

    public StringView getNextWord() {
        if (this.value.isEmpty()) {
            return StringView.EMPTY;
        }
        int startPosition = clamp(this.cursor, 0, this.value.length() - 1);

        while (startPosition < this.value.length() && !Character.isWhitespace(this.value.charAt(startPosition))) {
            startPosition++;
        }
        while (startPosition < this.value.length() && Character.isWhitespace(this.value.charAt(startPosition))) {
            startPosition++;
        }

        return new StringView(startPosition, this.getWordEndPosition(startPosition));
    }

    private int getWordEndPosition(int from) {
        int end = from;
        while (end < this.value.length() && !Character.isWhitespace(this.value.charAt(end))) {
            end++;
        }
        return end;
    }

    // ──── Internals ────

    private StringView getCursorLineView() {
        return this.getCursorLineView(0);
    }

    private StringView getCursorLineView(int lineOffset) {
        int lineIndex = this.getLineAtCursor();
        if (lineIndex < 0) {
            LOG.error("Cursor is not within text (cursor = {}, length = {})", this.cursor, this.value.length());
            return this.displayLines.get(this.displayLines.size() - 1);
        }
        return this.displayLines.get(clamp(lineIndex + lineOffset, 0, this.displayLines.size() - 1));
    }

    private void onValueChange() {
        this.reflowDisplayLines();
        this.valueListener.accept(this.value);
        this.cursorListener.run();
    }

    /**
     * Wrap the value into display lines, honouring hard '\n' breaks and soft
     * width-based breaks. Format codes (§x) advance no pen width.
     * <p>
     * 将文本按硬换行 '\n' 与宽度软换行拆成显示行。格式码（§x）不计宽度。
     * </p>
     */
    private void reflowDisplayLines() {
        this.displayLines.clear();
        if (this.value.isEmpty()) {
            this.displayLines.add(StringView.EMPTY);
        } else {
            int lineStart = 0;
            int lineWidth = 0;
            for (int i = 0; i < this.value.length(); i++) {
                char c = this.value.charAt(i);
                if (c == '\n') {
                    this.displayLines.add(new StringView(lineStart, i));
                    lineStart = i + 1;
                    lineWidth = 0;
                } else if (c == '\u00a7' && i + 1 < this.value.length()) {
                    i++; // format code: colours the next char, advances no pen
                } else {
                    int w = this.font.getCharWidth(c);
                    if (lineWidth + w > this.width && lineWidth > 0) {
                        this.displayLines.add(new StringView(lineStart, i));
                        lineStart = i;
                        lineWidth = w;
                    } else {
                        lineWidth += w;
                    }
                }
            }
            this.displayLines.add(new StringView(lineStart, this.value.length()));
        }
    }

    /**
     * Count the wrapped display lines of the given text (same rules as
     * {@link #reflowDisplayLines()}).
     * <p>
     * 统计给定文本的换行显示行数（规则与 {@link #reflowDisplayLines()} 一致）。
     * </p>
     */
    private int countDisplayLines(String text) {
        if (text.isEmpty()) {
            return 1;
        }
        int lineWidth = 0;
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines++;
                lineWidth = 0;
            } else if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
            } else {
                int w = this.font.getCharWidth(c);
                if (lineWidth + w > this.width && lineWidth > 0) {
                    lines++;
                    lineWidth = w;
                } else {
                    lineWidth += w;
                }
            }
        }
        return lines + 1;
    }

    private String truncateFullText(String input) {
        return this.hasCharacterLimit() ? truncateStringIfNecessary(input, this.characterLimit, false) : input;
    }

    private String truncateInsertionText(String input) {
        String truncatedInput = input;
        if (this.hasCharacterLimit()) {
            int remainingCharacters = Math.max(0, this.characterLimit - this.value.length());
            truncatedInput = truncateStringIfNecessary(input, remainingCharacters, false);
        }
        return truncatedInput;
    }

    private boolean overflowsLineLimit(String newValue) {
        return this.hasLineLimit() && this.countDisplayLines(newValue) > this.lineLimit;
    }

    // ──── Static helpers ────

    private static String truncateStringIfNecessary(String s, int maxLength, boolean reverse) {
        if (s.length() <= maxLength) {
            return s;
        }
        return reverse ? s.substring(s.length() - maxLength) : s.substring(0, maxLength);
    }

    /**
     * Filter out characters not allowed in chat, keeping line breaks.
     * <p>
     * 过滤聊天不允许的字符，保留换行符。
     * </p>
     */
    private static String filterText(String input) {
        StringBuilder filtered = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\n' || (c >= 32 && c != 127)) {
                filtered.append(c);
            }
        }
        return filtered.toString();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ──── Nested types ────

    /**
     * A view over [beginIndex, endIndex) of the value — counterpart of the
     * high-version {@code StringView} record.
     * <p>
     * 对文本值 [beginIndex, endIndex) 区间的视图 —— 对标高版本 {@code StringView} record。
     * </p>
     */
    public static final class StringView {

        private static final StringView EMPTY = new StringView(0, 0);

        public final int beginIndex;
        public final int endIndex;

        public StringView(int beginIndex, int endIndex) {
            this.beginIndex = beginIndex;
            this.endIndex = endIndex;
        }
    }

    /**
     * Cursor movement base — counterpart of the high-version {@code Whence} enum.
     * <p>
     * 光标移动基准 —— 对标高版本 {@code Whence} 枚举。
     * </p>
     */
    public enum Whence {
        ABSOLUTE, RELATIVE, END
    }
}
