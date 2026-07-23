package decok.dfcdvadstf.catframe.ui.components.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.FontRenderer;

/**
 * 客户端工具提示组件接口——渲染 tooltip 中的文字或图像行。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent}。
 *
 * @see ClientTextTooltip
 */
@SideOnly(Side.CLIENT)
public interface ClientTooltipComponent {

    /**
     * 从格式化字符串创建文本组件。
     */
    static ClientTooltipComponent create(String text) {
        return new ClientTextTooltip(text);
    }

    /**
     * 获取文字宽度（用于计算 tooltip 整体宽度）。
     */
    int getWidth(FontRenderer font);

    /**
     * 获取本行高度（通常为 10）。
     */
    int getHeight(FontRenderer font);

    /**
     * 是否在手中有物品时仍显示 tooltip。
     */
    default boolean showTooltipWithItemInHand() {
        return false;
    }

    /**
     * 渲染文字。
     */
    default void renderText(FontRenderer font, int x, int y) {
    }

    /**
     * 渲染图像组件（如 Bundle 的物品格）。
     */
    default void renderImage(FontRenderer font, int x, int y, int w, int h) {
    }
}
