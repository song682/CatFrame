package decok.dfcdvadstf.catframe.compact.vanilla;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.render.GuiGraphicsExtractor;
import net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent;

/**
 * <p>
 * {@link GuiGraphicsExtractor} 的帧生命周期驱动器（纯 Forge 实现）。<br>
 * 通过 Forge 的 {@link DrawScreenEvent} 在每一帧屏幕绘制的前/后驱动延迟渲染管线的
 * <b>重置</b>与<b>flush</b>，取代原先挂在 {@code GuiScreen.drawScreen} HEAD/RETURN 的 mixin 注入。
 * </p>
 * <p>
 * <b>为什么改用 Forge 事件：</b>{@code GuiContainer}（背包、箱子、熔炉、创造模式等——
 * 绝大多数物品 tooltip 出现的界面）<b>重写了 {@code drawScreen} 且不调用 {@code super}</b>，
 * 导致注入到 {@code GuiScreen.drawScreen} HEAD/RETURN 的 reset/flush 在容器界面中永不触发；
 * tooltip 被 {@code renderToolTip} 重定向捕获后却因缺少 flush 而丢失。
 * Forge 的 {@link DrawScreenEvent.Pre}/{@link DrawScreenEvent.Post} 由
 * {@code ForgeHooksClient.drawScreen} 包裹在 {@code currentScreen.drawScreen} 调用两侧触发，
 * 对<b>所有</b>屏幕（含 {@code GuiContainer} 子类）生效，因此能可靠地驱动帧生命周期。
 * </p>
 * <p>
 * The frame-lifecycle driver for {@link GuiGraphicsExtractor} (pure Forge). Uses Forge's
 * {@link DrawScreenEvent} to reset and flush the deferred render pipeline around each screen
 * draw, replacing the previous mixin injections on {@code GuiScreen.drawScreen} HEAD/RETURN
 * (which never fired for {@code GuiContainer} subclasses that override {@code drawScreen}).
 * </p>
 */
@SideOnly(Side.CLIENT)
public class ClientScreenGraphicsHandler {

    /**
     * 帧开始：屏幕绘制前重置延迟渲染状态（物品 / PiP / tooltip）。
     * <p>对标原 {@code MixinGuiScreen} 的 {@code drawScreen} HEAD 注入。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDrawScreenPre(DrawScreenEvent.Pre event) {
        GuiGraphicsExtractor.getInstance().resetForNewFrame();
    }

    /**
     * 帧末：屏幕绘制后统一 flush 延迟元素（物品模型 / PiP / tooltip），
     * 确保 tooltip 始终渲染在最上层。
     * <p>对标原 {@code MixinGuiScreen} 的 {@code drawScreen} RETURN 注入。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDrawScreenPost(DrawScreenEvent.Post event) {
        GuiGraphicsExtractor.getInstance().extractDeferredElements();
    }
}
