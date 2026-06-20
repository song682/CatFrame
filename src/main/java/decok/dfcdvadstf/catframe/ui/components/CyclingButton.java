package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 泛型循环按钮 — 类似高版本 Minecraft 的 {@code CyclingButtonWidget}。<br>
 * 继承 {@link AbstractComponent}，融入统一组件体系。
 * 支持通过点击和滚轮在类型化的值列表中循环切换。
 * </p>
 * <p>
 * Generic cycling button — similar to the high-version Minecraft
 * {@code CyclingButtonWidget}. Extends {@link AbstractComponent} to integrate
 * into the unified component system. Supports click and scroll-wheel cycling
 * through a typed list of values.
 * </p>
 *
 * @param <T> the type of values to cycle through / 循环切换的值类型
 */
public class CyclingButton<T> extends AbstractComponent {

    // ──── Interfaces ────

    private final Values<T> values;
    private final ValueToText<T> valueToText;
    private final UpdateCallback<T> callback;

    // ──── Fields ────

    private final Text label;
    private int index;
    private T value;

    CyclingButton(int x, int y, int width, int height,
                  Text label, int index, T value,
                  Values<T> values, ValueToText<T> valueToText,
                  UpdateCallback<T> callback) {
        super(x, y, width, height);
        this.label = label;
        this.index = index;
        this.value = value;
        this.values = values;
        this.valueToText = valueToText;
        this.callback = callback;
    }

    /**
     * Creates a new Builder for a CyclingButton with the given value-to-text function.
     * <p>使用给定的值到文本函数创建新的 Builder。</p>
     */
    public static <T> Builder<T> builder(ValueToText<T> valueToText) {
        return new Builder<>(valueToText);
    }

    /**
     * Convenience builder for Boolean on/off cycling.
     * <p>Boolean 开/关循环的便捷构建器。</p>
     */
    public static Builder<Boolean> onOffBuilder() {
        return new Builder<Boolean>((Boolean value) -> value ? "ON" : "OFF")
            .values(Arrays.asList(Boolean.TRUE, Boolean.FALSE));
    }

    // ──── Cycling logic ────

    private void cycle(int amount) {
        List<T> list = this.values.getCurrent();
        if (list.isEmpty()) return;
        this.index = ((this.index + amount) % list.size() + list.size()) % list.size();
        T newValue = list.get(this.index);
        internalSetValue(newValue);
        if (callback != null) {
            callback.onValueChange(this, newValue);
        }
    }

    /**
     * Handles mouse scroll input.
     * Scroll up (positive delta) cycles backward; scroll down (negative delta) cycles forward.
     * <p>处理鼠标滚轮输入。向上滚动（正 delta）反向；向下滚动（负 delta）正向。</p>
     */
    @Override
    public void mouseScrolled(int delta) {
        if (!active) return;
        if (delta > 0) {
            cycle(-1);
        } else if (delta < 0) {
            cycle(1);
        }
    }

    /**
     * Cycles forward (+1) on left-click.
     * <p>左键点击时正向切换。</p>
     */
    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!active || !isMouseOver(mouseX, mouseY)) return;
        if (mouseButton == 0) {
            cycle(1);
        }
    }

    /**
     * Gets the current value.
     * <p>获取当前值。</p>
     */
    public T getValue() {
        return this.value;
    }

    /**
     * Sets the current value programmatically (syncs index from values list).
     * <p>以编程方式设置当前值（从值列表同步索引）。</p>
     */
    public void setValue(T value) {
        List<T> list = this.values.getCurrent();
        int i = list.indexOf(value);
        if (i != -1) {
            this.index = i;
        }
        internalSetValue(value);
    }

    private void internalSetValue(T value) {
        this.value = value;
    }

    /**
     * Returns the current display text (label + value).
     * <p>返回当前显示文本（标签 + 值）。</p>
     */
    public String getDisplayText() {
        if (valueToText != null && value != null) {
            String text = valueToText.apply(value);
            return (label != null && !label.getString().isEmpty())
                ? label.getString() + ": " + text
                : text;
        }
        return "";
    }

    // ──── Rendering ────

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        updateHoverState(mouseX, mouseY);
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Draw button background
        int bgColor = isHovered ? 0xFF555555 : 0xFF333333;
        drawRect(x, y, x + width, y + height, bgColor);

        // Draw button text
        String displayText = getDisplayText();
        int textColor = active ? (isHovered ? 0xFFFFFF55 : 0xFFFFFF) : 0x888888;
        int textX = x + (width - font.getStringWidth(displayText)) / 2;
        int textY = y + (height - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(displayText, textX, textY, textColor);
    }

    private void drawRect(int left, int top, int right, int bottom, int color) {
        GuiDrawing.drawRect(left, top, right, bottom, color);
    }

    // ──── ValueToText interface ────

    public interface ValueToText<T> {
        String apply(T value);
    }

    // ──── Values interface ────

    public interface Values<T> {
        static <T> Values<T> of(Collection<T> values) {
            final List<T> list = new ArrayList<>(values);
            return new Values<T>() {
                @Override
                public List<T> getCurrent() { return list; }
                @Override
                public List<T> getDefaults() { return list; }
            };
        }

        List<T> getCurrent();
        List<T> getDefaults();
    }

    // ──── UpdateCallback interface ────

    public interface UpdateCallback<T> {
        void onValueChange(CyclingButton<T> button, T value);
    }

    // ──── Builder ────

    /**
     * Builder for constructing a {@link CyclingButton}.
     * <p>用于构建 {@link CyclingButton} 的构建器。</p>
     */
    public static class Builder<T> {
        private final ValueToText<T> valueToText;
        private Values<T> values;
        private T initialValue;
        private int initialIndex = 0;
        private Text label = Text.literal("");

        public Builder(ValueToText<T> valueToText) {
            this.valueToText = valueToText;
        }

        @SafeVarargs
        public final Builder<T> values(T... values) {
            return this.values(Arrays.asList(values));
        }

        public Builder<T> values(Collection<T> values) {
            return this.values(CyclingButton.Values.of(values));
        }

        public Builder<T> values(Values<T> values) {
            this.values = values;
            return this;
        }

        public Builder<T> initially(T value) {
            this.initialValue = value;
            if (this.values != null) {
                int i = this.values.getDefaults().indexOf(value);
                if (i != -1) this.initialIndex = i;
            }
            return this;
        }

        /**
         * Sets the label text (displayed as "label: valueText").
         * <p>设置标签文本（显示为 "label: valueText"）。</p>
         */
        public Builder<T> label(Text label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the label text from a string.
         * <p>从字符串设置标签文本。</p>
         */
        public Builder<T> label(String label) {
            this.label = Text.literal(label);
            return this;
        }

        /**
         * Builds the button.
         * <p>构建按钮。</p>
         */
        public CyclingButton<T> build(int x, int y, int width, int height,
                                      UpdateCallback<T> callback) {
            if (this.values == null || this.values.getDefaults().isEmpty()) {
                throw new IllegalStateException("No values for cycle button");
            }
            List<T> list = this.values.getDefaults();
            T value = this.initialValue != null ? this.initialValue : list.get(this.initialIndex);
            return new CyclingButton<>(x, y, width, height,
                label, this.initialIndex, value, this.values, this.valueToText, callback);
        }
    }
}
