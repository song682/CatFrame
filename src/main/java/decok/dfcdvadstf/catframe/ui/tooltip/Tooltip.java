package decok.dfcdvadstf.catframe.ui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * 工具提示数据持有类——为 Widget 层提供 tooltip 内容缓存与组件支持。
 * <p>
 * 对应 26.1.2 {@code net.minecraft.client.gui.components.Tooltip}。
 */
@SideOnly(Side.CLIENT)
public class Tooltip {

    private static final int MAX_WIDTH = 170;

    /** 原始 tooltip 文本（支持 § 格式化） */
    private final String message;

    /** 可选的 tooltip 图像/结构组件（如 BundleTooltip） */
    private final Optional<TooltipComponent> component;

    /** 可选的 tooltip 样式标识（对标 26.1.2 {@code Tooltip.style()}） */
    @Nullable
    private final ResourceLocation style;

    /** 缓存：按当前语言拆分后的文字行 */
    @Nullable
    private List<String> cachedTooltip;

    /** 缓存：上次拆分时的语言标识 */
    @Nullable
    private String cachedLanguage;

    private Tooltip(String message, Optional<TooltipComponent> component, @Nullable ResourceLocation style) {
        this.message = message;
        this.component = component;
        this.style = style;
    }

    // ========== 工厂方法 ==========

    public static Tooltip create(String message) {
        return new Tooltip(message, Optional.empty(), null);
    }

    public static Tooltip create(String message, Optional<TooltipComponent> component, @Nullable ResourceLocation style) {
        return new Tooltip(message, component, style);
    }

    // ========== 访问器 ==========

    public Optional<TooltipComponent> getComponent() {
        return component;
    }

    @Nullable
    public ResourceLocation getStyle() {
        return style;
    }

    // ========== 工具提示文字 ==========

    /**
     * 获取拆分后的 tooltip 行（带缓存）。
     */
    public List<String> getLines(Minecraft mc) {
        String currentLang = mc.getLanguageManager().getCurrentLanguage().getLanguageCode();
        if (cachedTooltip == null || !currentLang.equals(cachedLanguage)) {
            cachedTooltip = splitTooltip(mc, message);
            cachedLanguage = currentLang;
        }
        return cachedTooltip;
    }

    /**
     * 拆分文本到多行（每行不超过 MAX_WIDTH 像素）。
     */
    @SuppressWarnings("unchecked")
    public static List<String> splitTooltip(Minecraft mc, String message) {
        return mc.fontRenderer.listFormattedStringToWidth(message, MAX_WIDTH);
    }
}
