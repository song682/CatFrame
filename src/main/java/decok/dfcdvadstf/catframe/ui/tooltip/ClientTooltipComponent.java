package decok.dfcdvadstf.catframe.ui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.FontRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

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
     * 从结构化组件创建客户端渲染组件。
     * <p>
     * 对标 26.1.2 {@code ClientTooltipComponent.create(TooltipComponent)}；
     * 1.7.10 无 sealed 类型体系，改用运行时类型分派表（与 PiP 分派表同策略），
     * 渲染工厂由 {@link #register} 登记。
     *
     * @param component 结构化 tooltip 组件
     * @return 对应的客户端渲染组件
     * @throws IllegalArgumentException 该组件类型的渲染工厂未注册
     */
    static ClientTooltipComponent create(TooltipComponent component) {
        final Function<TooltipComponent, ClientTooltipComponent> factory = Dispatch.FACTORIES.get(component.getClass());
        if (factory == null) {
            throw new IllegalArgumentException("Unknown TooltipComponent: " + component.getClass().getName());
        }
        return factory.apply(component);
    }

    /**
     * 登记结构化组件类型的渲染工厂（1.7.10 扩展机制）。
     *
     * @param type    组件运行时类型
     * @param factory 由该类型组件创建客户端渲染组件的工厂
     */
    static void register(final Class<? extends TooltipComponent> type,
                         final Function<TooltipComponent, ClientTooltipComponent> factory) {
        Dispatch.FACTORIES.put(type, factory);
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
     * 渲染图像组件。
     */
    default void renderImage(FontRenderer font, int x, int y, int w, int h) {
    }

    /**
     * 分派表持有者——接口字段隐含 public static final，可变状态收纳于嵌套类。
     */
    final class Dispatch {

        private Dispatch() {}

        /** TooltipComponent 运行时类型 → 客户端渲染组件工厂。 */
        static final Map<Class<? extends TooltipComponent>, Function<TooltipComponent, ClientTooltipComponent>> FACTORIES =
                new HashMap<>();
    }
}
