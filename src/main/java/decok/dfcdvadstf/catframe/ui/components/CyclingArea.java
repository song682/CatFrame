package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 泛型循环选择区域 — 类似高版本 Minecraft 的 {@code CyclingButtonWidget}。<br>
 * 继承 {@link AbstractComponent}，融入统一组件体系。
 * 支持纹理背景（九宫格拉伸）、点击和滚轮循环、标签文本显示。
 * </p>
 * <p>
 * Generic cycling area — similar to high-version Minecraft's
 * {@code CyclingButtonWidget}. Extends {@link AbstractComponent} to integrate
 * into the unified component system. Supports textured backgrounds
 * (nine-patch stretching), click and scroll-wheel cycling, and label display.
 * </p>
 *
 * @param <T> the type of values to cycle through / 循环切换的值类型
 */
public class CyclingArea<T> extends AbstractComponent {

    // ──── Default texture ────

    /** Default button texture for normal state / 默认普通状态按钮纹理 */
    protected static final ResourceLocation DEFAULT_BUTTON_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/widgets/button.png");
    /** Default button texture for hover state / 默认悬停状态按钮纹理 */
    protected static final ResourceLocation DEFAULT_BUTTON_HOVER_TEXTURE =
            new ResourceLocation("catframe", "textures/gui/widgets/button_hover.png");
    protected static final int BUTTON_DEFAULT_W = 200;
    protected static final int BUTTON_DEFAULT_H = 20;
    protected static final int BUTTON_BORDER = 2;

    // ──── Interfaces ────

    /**
     * Provides the list of values to cycle through.
     * Supports dynamic value lists (e.g. different values when Alt is held).
     * <p>提供循环值列表的接口。支持动态值列表。</p>
     */
    public interface Values<T> {
        List<T> getCurrent();
        List<T> getDefaults();

        static <T> Values<T> of(Collection<T> values) {
            final List<T> list = new ArrayList<>(values);
            return new Values<T>() {
                @Override public List<T> getCurrent() { return list; }
                @Override public List<T> getDefaults() { return list; }
            };
        }
    }

    /**
     * Converts a value of type T to its display text.
     * <p>将类型 T 的值转换为显示文本。</p>
     */
    public interface ValueToText<T> {
        String apply(T value);
    }

    /**
     * Callback invoked when the value changes via cycling.
     * <p>值通过循环切换变更时调用的回调。</p>
     */
    public interface UpdateCallback<T> {
        void onValueChange(CyclingArea<T> area, T value);
    }

    // ──── Fields ────

    private final Values<T> values;
    private final ValueToText<T> valueToText;
    private final UpdateCallback<T> callback;
    private final Text label;
    private int index;
    private T value;

    /** Background texture for normal state / 普通状态背景纹理 */
    protected ResourceLocation normalTexture = DEFAULT_BUTTON_TEXTURE;
    /** Background texture for hover state / 悬停状态背景纹理 */
    protected ResourceLocation hoverTexture = DEFAULT_BUTTON_HOVER_TEXTURE;

    CyclingArea(int x, int y, int width, int height,
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
     * Creates a new Builder.
     * <p>创建新的 Builder。</p>
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

    // ──── Texture configuration ────

    /**
     * Sets custom background textures.
     * <p>设置自定义背景纹理。</p>
     */
    public CyclingArea<T> setTextures(ResourceLocation normal, ResourceLocation hover) {
        this.normalTexture = normal;
        this.hoverTexture = hover;
        return this;
    }

    /**
     * Sets the normal state background texture.
     * <p>设置普通状态背景纹理。</p>
     */
    public CyclingArea<T> setNormalTexture(ResourceLocation texture) {
        this.normalTexture = texture;
        return this;
    }

    /**
     * Sets the hover state background texture.
     * <p>设置悬停状态背景纹理。</p>
     */
    public CyclingArea<T> setHoverTexture(ResourceLocation texture) {
        this.hoverTexture = texture;
        return this;
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
     * Handles mouse scroll input (scroll up = cycle backward, scroll down = cycle forward).
     * <p>处理鼠标滚轮输入（向上滚动 = 反向，向下滚动 = 正向）。</p>
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

    // ──── Value access ────

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
                    ? label.getString() + " " + text
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

        // Draw textured background (nine-patch)
        ResourceLocation tex = isHovered && active ? hoverTexture : normalTexture;
        TextureStretching.drawAutoNinePatch(tex, x, y, width, height,
                BUTTON_DEFAULT_W, BUTTON_DEFAULT_H, BUTTON_BORDER);

        // Draw button text
        String displayText = getDisplayText();
        int textColor = active ? (isHovered ? 0xFFFFFF55 : 0xFFFFFF) : 0x888888;
        int textX = x + (width - font.getStringWidth(displayText)) / 2;
        int textY = y + (height - font.FONT_HEIGHT) / 2;
        font.drawStringWithShadow(displayText, textX, textY, textColor);
    }

    // ──── Builder ────

    /**
     * Builder for constructing a {@link CyclingArea}.
     * <p>用于构建 {@link CyclingArea} 的构建器。</p>
     */
    public static class Builder<T> {
        private final ValueToText<T> valueToText;
        private Values<T> values;
        private T initialValue;
        private int initialIndex;
        private Text label = Text.literal("");
        private ResourceLocation normalTexture;
        private ResourceLocation hoverTexture;

        public Builder(ValueToText<T> valueToText) {
            this.valueToText = valueToText;
        }

        @SafeVarargs
        @SuppressWarnings("varargs")
        public final Builder<T> values(T... values) {
            return this.values(Arrays.asList(values));
        }

        public Builder<T> values(Collection<T> values) {
            return this.values(CyclingArea.Values.of(values));
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
         * <p>设置标签文本（显示为 "label valueText"）。</p>
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
         * Sets custom background textures.
         * <p>设置自定义背景纹理。</p>
         */
        public Builder<T> textures(ResourceLocation normal, ResourceLocation hover) {
            this.normalTexture = normal;
            this.hoverTexture = hover;
            return this;
        }

        /**
         * Builds the CyclingArea.
         * <p>构建 CyclingArea。</p>
         */
        public CyclingArea<T> build(int x, int y, int width, int height,
                                    UpdateCallback<T> callback) {
            if (this.values == null || this.values.getDefaults().isEmpty()) {
                throw new IllegalStateException("No values for cycle area");
            }
            List<T> list = this.values.getDefaults();
            T value = this.initialValue != null ? this.initialValue : list.get(this.initialIndex);
            CyclingArea<T> area = new CyclingArea<>(x, y, width, height,
                    label, this.initialIndex, value, this.values, this.valueToText, callback);
            if (normalTexture != null) area.normalTexture = normalTexture;
            if (hoverTexture != null) area.hoverTexture = hoverTexture;
            return area;
        }
    }
}
