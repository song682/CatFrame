package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.Text;

import java.util.Objects;

/**
 * <p>
 * 图标按钮组件 —— 对标高版本 Minecraft 的 {@code IconButton}。<br>
 * 继承 {@link Button}，在按钮背景与文本之上叠加一个居中的图标组件
 * （如 {@link ImageWidget}）。图标按原始尺寸参与构造（对标高版本
 * {@code IconButton} 以图标尺寸作为按钮尺寸的构造器），也可经显式尺寸
 * 构造更大的按钮区域，图标始终水平 / 垂直居中于按钮内部。
 * </p>
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
 * 1.7.10 无矩阵栈，高版本 {@code pose().translate} 的等价做法是绘制前临时
 * 平移图标坐标、绘制后复原，故图标类型须为可重定位的 {@link AbstractComponent}。<br>
 * 1.7.10 has no pose stack; the equivalent of the high version's
 * {@code pose().translate} is to temporarily relocate the icon before drawing
 * and restore it afterwards, hence the icon must be a repositionable
 * {@link AbstractComponent}.
 * </p>
 */
public class IconedButton extends Button {

    /** 居中绘制的图标组件 / the icon widget drawn centred inside the button */
    private final AbstractComponent icon;

    /**
     * 以图标自身尺寸作为按钮尺寸创建图标按钮 —— 对标高版本
     * {@code IconButton(int, int, Renderable, OnPress, Component)}。<br>
     * Creates an icon button sized by the icon — counterpart of the high-version
     * {@code IconButton(int, int, Renderable, OnPress, Component)} constructor.
     */
    public IconedButton(int x, int y, Text message, AbstractComponent icon, OnPress onPress) {
        this(x, y, Objects.requireNonNull(icon, "icon").getWidth(), icon.getHeight(), message, icon, onPress);
    }

    /**
     * 完整构造器 —— 显式指定按钮尺寸，图标居中。<br>
     * Full constructor — explicit button size, icon drawn centred.
     */
    public IconedButton(int x, int y, int width, int height, Text message, AbstractComponent icon, OnPress onPress) {
        super(x, y, width, height, message, onPress);
        this.icon = Objects.requireNonNull(icon, "icon");
    }

    /**
     * 创建按钮构建器 —— 对标高版本 {@code IconButton} 的构造入口。<br>
     * Creates a new IconedButton builder — counterpart of the high-version
     * construction entry.
     */
    public static Builder builder(Text message, AbstractComponent icon, OnPress onPress) {
        return new Builder(message, icon, onPress);
    }

    /**
     * 获取当前图标组件。<br>
     * Returns the current icon widget.
     */
    public AbstractComponent getIcon() {
        return icon;
    }

    /**
     * 绘制按钮背景与文本后叠加居中图标 —— 对标高版本
     * {@code IconButton#renderWidget}：先经 super 绘制 {@link Button} 的
     * 背景与文本，再将图标平移至按钮中心绘制。高版本借助矩阵栈平移，
     * 此处以「保存 / 设置 / 复原」图标坐标等价实现。<br>
     * Renders the button background and text via super, then draws the icon
     * centred inside the button — counterpart of the high-version
     * {@code IconButton#renderWidget}. The high version translates the pose
     * stack; here the icon coordinates are saved, set and restored instead.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWidget(graphics, mouseX, mouseY, partialTicks);

        // 图标居中位置 / centred icon position
        int iconX = x + (width - icon.getWidth()) / 2;
        int iconY = y + (height - icon.getHeight()) / 2;

        // 无矩阵栈：临时平移图标至按钮中心，绘制后复原原坐标。
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
     * <p>
     * 用于构建 {@link IconedButton} 的构建器。
     * </p>
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
         * 同时设置按钮宽高。<br>
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
