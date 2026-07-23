package decok.dfcdvadstf.catframe.ui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.components.FontHelper;
import net.minecraft.client.gui.FontRenderer;

/**
 * 文本工具提示组件——渲染一行文字。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip}。
 * 使用 UI 包的 {@link FontHelper} 绘制，与 UI 包样式系统一致。
 */
@SideOnly(Side.CLIENT)
public class ClientTextTooltip implements ClientTooltipComponent {

    private final String text;

    public ClientTextTooltip(String text) {
        this.text = text;
    }

    @Override
    public int getWidth(FontRenderer font) {
        return font.getStringWidth(text);
    }

    @Override
    public int getHeight(FontRenderer font) {
        return 10;
    }

    @Override
    public void renderText(FontRenderer font, int x, int y) {
        FontHelper.draw(text, x, y, null);
    }
}
