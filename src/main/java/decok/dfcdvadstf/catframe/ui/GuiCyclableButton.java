package decok.dfcdvadstf.catframe.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A generic cyclable button — similar to modern Minecraft's {@code CyclingButtonWidget<T>}.
 * Supports click and scroll-wheel cycling through a typed list of values.
 * Left-click or scroll down cycles forward; scroll up cycles backward.
 * <p>
 * 泛型循环按钮 — 类似高版本 Minecraft 的 {@code CyclingButtonWidget<T>}。
 * 支持通过点击和滚轮在类型化的值列表中循环切换。
 * 左键点击或向下滚动为正向切换；向上滚动为反向切换。
 *
 * @param <T> the type of values to cycle through / 循环切换的值类型
 */
public class GuiCyclableButton<T> extends GuiButton {

    // ──── Interfaces ────

    private final Values<T> values;
    private final ValueToText<T> valueToText;
    private final UpdateCallback<T> callback;

    // ──── Fields ────
    private final String label;
    private int index;
    private T value;
    GuiCyclableButton(int id, int x, int y, int width, int height,
                      String label, int index, T value,
                      Values<T> values, ValueToText<T> valueToText,
                      UpdateCallback<T> callback) {
        super(id, x, y, width, height, "");
        this.label = label;
        this.index = index;
        this.value = value;
        this.values = values;
        this.valueToText = valueToText;
        this.callback = callback;
        updateText();
    }

    /**
     * Creates a new Builder for a GuiCyclableButton with the given value-to-text function.
     * <p>
     * 使用给定的值到文本函数创建新的 Builder。
     *
     * @param valueToText function that converts T to display text / 将 T 转换为显示文本的函数
     */
    public static <T> Builder<T> builder(ValueToText<T> valueToText) {
        return new Builder<>(valueToText);
    }

    /**
     * Convenience builder for Boolean on/off cycling.
     * <p>
     * Boolean 开/关循环的便捷构建器。
     */
    public static Builder<Boolean> onOffBuilder() {
        return new Builder<Boolean>(value -> value ? "ON" : "OFF")
                .values(Arrays.asList(Boolean.TRUE, Boolean.FALSE));
    }

    // ──── Constructor (used by Builder) ────

    /**
     * Cycles forward or backward through the value list.
     * <p>
     * 在值列表中正向或反向切换。
     */
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

    // ──── Cycling logic ────

    /**
     * Handles mouse scroll input.
     * Scroll up (positive delta) cycles backward; scroll down (negative delta) cycles forward.
     * <p>
     * 处理鼠标滚轮输入。
     * 向上滚动（正 delta）反向切换；向下滚动（负 delta）正向切换。
     */
    public void mouseScrolled(int delta) {
        if (!enabled) return;
        if (delta > 0) {
            cycle(-1);
        } else if (delta < 0) {
            cycle(1);
        }
    }

    /**
     * Cycles forward (+1) on left-click.
     * <p>
     * 左键点击时正向切换。
     */
    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            cycle(1);
            return true;
        }
        return false;
    }

    /**
     * Gets the current value.
     * <p>
     * 获取当前值。
     */
    public T getValue() {
        return this.value;
    }

    // ──── Value access ────

    /**
     * Sets the current value programmatically (syncs index from values list).
     * <p>
     * 以编程方式设置当前值（从值列表同步索引）。
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
        updateText();
    }

    /**
     * Updates the button's display text from current value via {@link ValueToText}.
     * If a label is set, displays as "label: valueText"; otherwise just "valueText".
     * <p>
     * 通过 {@link ValueToText} 从当前值更新按钮显示文本。
     * 若设置了 label，显示为 "label: valueText"；否则仅显示 "valueText"。
     */
    public void updateText() {
        if (valueToText != null && value != null) {
            String text = valueToText.apply(value);
            this.displayString = (label != null && !label.isEmpty())
                    ? label + ": " + text
                    : text;
        }
    }

    /**
     * Provides the list of values to cycle through.
     * Supports dynamic value lists (e.g. different values when Alt is held).
     * <p>
     * 提供循环值列表的接口。
     * 支持动态值列表（例如按住 Alt 时显示不同的值）。
     */
    public interface Values<T> {
        /**
         * Creates a simple static Values instance from a collection.
         * <p>
         * 从集合创建一个简单的静态 Values 实例。
         */
        static <T> Values<T> of(Collection<T> values) {
            final List<T> list = new ArrayList<>(values);
            return new Values<T>() {
                @Override
                public List<T> getCurrent() {
                    return list;
                }

                @Override
                public List<T> getDefaults() {
                    return list;
                }
            };
        }

        List<T> getCurrent();

        List<T> getDefaults();
    }

    // ──── Builder ────

    /**
     * Converts a value of type T to its display text.
     * <p>
     * 将类型 T 的值转换为显示文本。
     */
    public interface ValueToText<T> {
        String apply(T value);
    }

    /**
     * Callback invoked when the button's value changes via cycling.
     * <p>
     * 按钮值通过循环切换变更时调用的回调。
     */
    public interface UpdateCallback<T> {
        void onValueChange(GuiCyclableButton<T> button, T value);
    }

    /**
     * Builder for constructing a {@link GuiCyclableButton}.
     * <p>
     * 用于构建 {@link GuiCyclableButton} 的构建器。
     */
    public static class Builder<T> {
        private final ValueToText<T> valueToText;
        private Values<T> values;
        private T initialValue;
        private int initialIndex = 0;

        public Builder(ValueToText<T> valueToText) {
            this.valueToText = valueToText;
        }

        /**
         * Sets the values to cycle through (varargs).
         * <p>
         * 设置要循环的值（可变参数）。
         */
        @SafeVarargs
        public final Builder<T> values(T... values) {
            return this.values(Arrays.asList(values));
        }

        /**
         * Sets the values to cycle through (collection).
         * <p>
         * 设置要循环的值（集合）。
         */
        public Builder<T> values(Collection<T> values) {
            return this.values(GuiCyclableButton.Values.of(values));
        }

        /**
         * Sets a dynamic Values provider.
         * <p>
         * 设置动态 Values 提供者。
         */
        public Builder<T> values(Values<T> values) {
            this.values = values;
            return this;
        }

        /**
         * Sets the initial value.
         * <p>
         * 设置初始值。
         */
        public Builder<T> initially(T value) {
            this.initialValue = value;
            if (this.values != null) {
                int i = this.values.getDefaults().indexOf(value);
                if (i != -1) this.initialIndex = i;
            }
            return this;
        }

        /**
         * Builds the button with a label prefix (displayed as "label: valueText").
         * <p>
         * 使用标签前缀构建按钮（显示为 "label: valueText"）。
         */
        public GuiCyclableButton<T> build(int id, int x, int y, int width, int height,
                                          String label, UpdateCallback<T> callback) {
            if (this.values == null || this.values.getDefaults().isEmpty()) {
                throw new IllegalStateException("No values for cycle button");
            }
            List<T> list = this.values.getDefaults();
            T value = this.initialValue != null ? this.initialValue : list.get(this.initialIndex);
            return new GuiCyclableButton<>(id, x, y, width, height,
                    label, this.initialIndex, value, this.values, this.valueToText, callback);
        }

        /**
         * Builds the button without a label (valueToText produces the full display string).
         * <p>
         * 不使用标签构建按钮（valueToText 产生完整的显示字符串）。
         */
        public GuiCyclableButton<T> build(int id, int x, int y, int width, int height,
                                          UpdateCallback<T> callback) {
            return build(id, x, y, width, height, "", callback);
        }
    }
}