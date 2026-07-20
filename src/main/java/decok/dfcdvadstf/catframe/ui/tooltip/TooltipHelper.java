package decok.dfcdvadstf.catframe.ui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.core.component.predicates.ItemStackComponents;
import decok.dfcdvadstf.catframe.core.component.predicates.PatchedDataComponentMap;
import decok.dfcdvadstf.catframe.core.component.predicates.RegisteredComponents;
import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 屏幕级工具提示辅助方法——与 26.1.2 {@code Screen.getTooltipFromItem()} 等效。
 * <p>
 * 提供从 ItemStack 获取 tooltip 行、从 TOOLTIP_STYLE DataComponent 读取样式等能力。
 */
@SideOnly(Side.CLIENT)
public class TooltipHelper {

    /**
     * 从 ItemStack 获取完整的 tooltip 文字行（含物品名称、属性、Lore 等）。
     * <p>
     * 对应 26.1.2 {@code Screen.getTooltipFromItem()}。
     *
     * @param mc        Minecraft 实例
     * @param itemStack 目标物品
     * @return 格式化后的 tooltip 行（§ 颜色码）
     */
    @SuppressWarnings("unchecked")
    public static List<String> getTooltipFromItem(Minecraft mc, ItemStack itemStack) {
        return itemStack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
    }

    /**
     * 获取物品的 tooltip 图像组件（如 BundleContent）。
     * 当前版本尚未实现，返回空。
     */
    public static java.util.Optional<TooltipComponent> getTooltipImage(ItemStack itemStack) {
        return java.util.Optional.empty();
    }

    /**
     * 获取物品的 tooltip 样式 ID（来自 DataComponent TOOLTIP_STYLE）。
     * 通过 {@link ItemStackComponents} 访问 DataComponent 系统。
     */
    @Nullable
    public static ResourceLocation getTooltipStyle(ItemStack itemStack) {
        if (itemStack == null) return null;
        try {
            PatchedDataComponentMap components = ItemStackComponents.get(itemStack);
            if (components != null) {
                String styleId = components.get(RegisteredComponents.TOOLTIP_STYLE);
                if (styleId != null && !styleId.isEmpty()) {
                    return new ResourceLocation(styleId);
                }
            }
        } catch (Exception ignored) {
            // DataComponent system not available
        }
        return null;
    }

    /**
     * 在屏幕上渲染物品的 tooltip（延迟到帧末）。
     *
     * @param font    字体渲染器
     * @param stack   物品
     * @param mouseX  鼠标 X
     * @param mouseY  鼠标 Y
     */
    public static void renderItemTooltip(FontRenderer font, ItemStack stack, int mouseX, int mouseY) {
        if (stack == null) return;
        GuiGraphicsExtractor.getInstance().setTooltipForNextFrame(font, stack, mouseX, mouseY);
    }
}
