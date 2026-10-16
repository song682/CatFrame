package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * <p>
 * Generic cycling button — similar to the high-version Minecraft
 * {@code CyclingButtonWidget}. Extends {@link AbstractButton} for unified
 * textured background rendering. Supports click and scroll-wheel cycling
 * through a typed list of values.
 * </p>
 *
 * @param <T> the type of values to cycle through
 */
public class CyclingButton<T> extends AbstractButton {

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
                  UpdateCallback<T> callback,
                  boolean useVanillaTexture) {
        super(x, y, width, height);
        this.label = label;
        this.index = index;
        this.value = value;
        this.values = values;
        this.valueToText = valueToText;
        this.callback = callback;
        this.useVanillaTexture = useVanillaTexture;
    }

    /**
     * Creates a new Builder for a CyclingButton with the given value-to-text function.
     */
    public static <T> Builder<T> builder(ValueToText<T> valueToText) {
        return new Builder<>(valueToText);
    }

    /**
     * Convenience builder for Boolean on/off cycling.
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
     * Left-click cycles forward (+1). Delegated via {@link #onPress()} from AbstractButton,
     * which also handles sound playback.
     */
    @Override
    public void onPress() {
        cycle(1);
    }

    /**
     * Handles mouse scroll input.
     * Scroll up (positive delta) cycles backward; scroll down (negative delta) cycles forward.
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

    // ──── Value access ────

    /**
     * Gets the current value.
     */
    public T getValue() {
        return this.value;
    }

    /**
     * Sets the current value programmatically (syncs index from values list).
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
     */
    public String getDisplayText() {
        if (valueToText != null && value != null) {
            String text = valueToText.apply(value);
            return (label != null && !label.getString().isEmpty())
                ? label.getString() + " " + text
                : text;
        }
        return "";
    }

    // ──── Rendering ────

    /**
     * Draws the button background and the current value text — visibility and
     * hover state are already handled by {@link #extractRenderState}.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics, mouseX, mouseY, partialTicks);

        // Draw button text
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String displayText = getDisplayText();
        int textColor;
        if (!active) {
            textColor = TEXT_COLOR_DISABLED;
        } else if (isHovered) {
            textColor = TEXT_COLOR_HOVER;
        } else {
            textColor = TEXT_COLOR_ENABLED;
        }

        int textX = x + (width - font.getStringWidth(displayText)) / 2;
        int textY = y + (height - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(displayText, textX, textY, textColor);
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
     */
    public static class Builder<T> {
        private final ValueToText<T> valueToText;
        private Values<T> values;
        private T initialValue;
        private int initialIndex = 0;
        private Text label = Text.literal("");
        private boolean useVanillaTexture = false;

        public Builder(ValueToText<T> valueToText) {
            this.valueToText = valueToText;
        }

        @SafeVarargs
        @SuppressWarnings("varargs")
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
         * Sets the label text (displayed as "label valueText").
         */
        public Builder<T> label(Text label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the label text from a string.
         */
        public Builder<T> label(String label) {
            this.label = Text.literal(label);
            return this;
        }

        /**
         * If true, uses vanilla widgets texture instead of CatFrame custom textures.
         */
        public Builder<T> useVanillaTexture(boolean useVanilla) {
            this.useVanillaTexture = useVanilla;
            return this;
        }

        /**
         * Builds the button.
         */
        public CyclingButton<T> build(int x, int y, int width, int height,
                                      UpdateCallback<T> callback) {
            if (this.values == null || this.values.getDefaults().isEmpty()) {
                throw new IllegalStateException("No values for cycle button");
            }
            List<T> list = this.values.getDefaults();
            T value = this.initialValue != null ? this.initialValue : list.get(this.initialIndex);
            return new CyclingButton<>(x, y, width, height,
                label, this.initialIndex, value, this.values, this.valueToText, callback,
                useVanillaTexture);
        }
    }
}
