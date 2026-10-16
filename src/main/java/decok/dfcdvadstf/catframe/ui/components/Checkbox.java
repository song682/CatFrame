package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

/**
 * <p>
 * Checkbox component — counterpart of the high-version Minecraft
 * {@code Checkbox}.<br>
 * Extends {@link AbstractButton}, renders the CatFrame four-state textures
 * (unchecked/checked × normal/highlighted), and supports Builder construction
 * with a value-change callback.
 * </p>
 */
public class Checkbox extends AbstractButton {

    private static final ResourceLocation CHECKBOX_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/checkbox.png");
    private static final ResourceLocation CHECKBOX_HIGHLIGHTED_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/checkbox_highlighted.png");
    private static final ResourceLocation CHECKBOX_SELECTED_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/checkbox_checked.png");
    private static final ResourceLocation CHECKBOX_SELECTED_HIGHLIGHTED_TEXTURE = new ResourceLocation("catframe",
            "textures/gui/widgets/checkbox_checked_highlighted.png");

    /** Checkbox box size in screen pixels */
    private static final int BOX_SIZE = 16;
    /** Spacing between the box and the label text */
    private static final int SPACING = 4;

    private boolean selected;
    private final OnValueChange onValueChange;

    private Checkbox(int x, int y, int width, Text message, boolean selected, OnValueChange onValueChange) {
        super(x, y, width, BOX_SIZE, message);
        this.selected = selected;
        this.onValueChange = onValueChange != null ? onValueChange : OnValueChange.NOP;
    }

    /**
     * Creates a new Checkbox builder.
     */
    public static Builder builder(Text message) {
        return new Builder(message);
    }

    @Override
    public void onPress() {
        this.selected = !this.selected;
        this.onValueChange.onValueChange(this, this.selected);
    }

    /**
     * Returns whether the checkbox is currently selected.
     */
    public boolean selected() {
        return this.selected;
    }

    /**
     * Sets the selected state programmatically (without firing the callback).
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        ResourceLocation texture;
        if (this.selected) {
            texture = (isHovered || focused) ? CHECKBOX_SELECTED_HIGHLIGHTED_TEXTURE : CHECKBOX_SELECTED_TEXTURE;
        } else {
            texture = (isHovered || focused) ? CHECKBOX_HIGHLIGHTED_TEXTURE : CHECKBOX_TEXTURE;
        }

        // 16x16 standalone texture drawn 1:1 — drawStatic enforces integer-multiple
        // upscaling.
        TextureStretching.drawStatic(texture, x, y, BOX_SIZE, BOX_SIZE, BOX_SIZE, BOX_SIZE, this.alpha);

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String label = getMessage() != null ? getMessage().getString() : "";
        int textX = x + BOX_SIZE + SPACING;
        int textY = y + BOX_SIZE / 2 - font.FONT_HEIGHT / 2;
        font.drawStringWithShadow(label, textX, textY, active ? TEXT_COLOR_ENABLED : TEXT_COLOR_DISABLED);
    }

    /**
     * Callback invoked when the selected state changes.
     */
    public interface OnValueChange {
        OnValueChange NOP = (checkbox, value) -> {
        };

        void onValueChange(Checkbox checkbox, boolean value);
    }

    /**
     * Builder for constructing a {@link Checkbox}.
     */
    public static class Builder {
        private final Text message;
        private int x;
        private int y;
        private int maxWidth = Integer.MAX_VALUE;
        private boolean selected = false;
        private OnValueChange onValueChange = OnValueChange.NOP;
        private Tooltip tooltip;

        public Builder(Text message) {
            this.message = message;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        /**
         * Caps the total widget width; the label is clamped (not wrapped) to fit.
         */
        public Builder maxWidth(int maxWidth) {
            this.maxWidth = maxWidth;
            return this;
        }

        public Builder selected(boolean selected) {
            this.selected = selected;
            return this;
        }

        public Builder onValueChange(OnValueChange onValueChange) {
            this.onValueChange = onValueChange;
            return this;
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Checkbox build() {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            String label = message != null ? message.getString() : "";
            int defaultWidth = BOX_SIZE + SPACING + font.getStringWidth(label);
            int width = Math.min(defaultWidth, maxWidth);
            Checkbox box = new Checkbox(x, y, width, message, selected, onValueChange);
            if (tooltip != null) {
                box.setTooltip(tooltip);
            }
            return box;
        }
    }
}
