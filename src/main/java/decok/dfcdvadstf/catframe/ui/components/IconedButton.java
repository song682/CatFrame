package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;

import java.util.Objects;

/**
 * <p>
 * Icon button component — counterpart of the high-version Minecraft
 * {@code IconButton}. Extends {@link Button} and overlays a centred icon
 * widget (e.g. an {@link ImageWidget}) on top of the button background and
 * text. The icon's own size participates in construction (counterpart of the
 * high-version constructor sizing the button by the icon), while an explicit
 * size may also be given for a larger button area; the icon is always centred
 * horizontally / vertically inside the button.
 * </p>
 * <p>
 * 1.7.10 has no pose stack; the equivalent of the high version's
 * {@code pose().translate} is to temporarily relocate the icon before drawing
 * and restore it afterwards, hence the icon must be a repositionable
 * {@link AbstractComponent}.
 * </p>
 */
public class IconedButton extends Button {

    /** the icon widget drawn centred inside the button */
    private final AbstractComponent icon;

    /**
     * Creates an icon button sized by the icon — counterpart of the high-version
     * {@code IconButton(int, int, Renderable, OnPress, Component)} constructor.
     */
    public IconedButton(int x, int y, Text message, AbstractComponent icon, OnPress onPress) {
        this(x, y, Objects.requireNonNull(icon, "icon").getWidth(), icon.getHeight(), message, icon, onPress);
    }

    /**
     * Full constructor — explicit button size, icon drawn centred.
     */
    public IconedButton(int x, int y, int width, int height, Text message, AbstractComponent icon, OnPress onPress) {
        super(x, y, width, height, message, onPress);
        this.icon = Objects.requireNonNull(icon, "icon");
    }

    /**
     * Creates a new IconedButton builder — counterpart of the high-version
     * construction entry.
     */
    public static Builder builder(Text message, AbstractComponent icon, OnPress onPress) {
        return new Builder(message, icon, onPress);
    }

    /**
     * Returns the current icon widget.
     */
    public AbstractComponent getIcon() {
        return icon;
    }

    /**
     * Renders the button background and text via super, then draws the icon
     * centred inside the button — counterpart of the high-version
     * {@code IconButton#renderWidget}. The high version translates the pose
     * stack; here the icon coordinates are saved, set and restored instead.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWidget(graphics, mouseX, mouseY, partialTicks);

        // centred icon position
        int iconX = x + (width - icon.getWidth()) / 2;
        int iconY = y + (height - icon.getHeight()) / 2;

        // No pose stack: temporarily relocate the icon to the button centre,
        // draw it, then restore the original coordinates.
        int oldX = icon.getX();
        int oldY = icon.getY();
        icon.setX(iconX);
        icon.setY(iconY);
        try {
            icon.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        } finally {
            icon.setX(oldX);
            icon.setY(oldY);
        }
    }

    // ──── Builder ────

    /**
     * Builder for constructing an {@link IconedButton}.
     */
    public static class Builder {
        private final Text message;
        private final AbstractComponent icon;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;
        private boolean useVanillaTexture = false;

        public Builder(Text message, AbstractComponent icon, OnPress onPress) {
            this.message = message;
            this.icon = Objects.requireNonNull(icon, "icon");
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        /**
         * Sets both button width and height in one call.
         */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder useVanillaTexture(boolean useVanilla) {
            this.useVanillaTexture = useVanilla;
            return this;
        }

        public IconedButton build() {
            IconedButton button = new IconedButton(x, y, width, height, message, icon, onPress);
            button.useVanillaTexture = useVanillaTexture;
            return button;
        }
    }
}
