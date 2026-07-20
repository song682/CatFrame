package decok.dfcdvadstf.catframe.mixin.middle;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin into GuiScreen to redirect vanilla tooltip rendering through CatFrame's
 * deferred tooltip pipeline ({@link GuiGraphicsExtractor}).
 * <p>
 * Intercepts:
 * <ul>
 *   <li>{@code drawHoveringText} — generic text tooltip</li>
 *   <li>{@code renderToolTip} — item tooltip</li>
 *   <li>{@code drawScreen} HEAD — frame reset; RETURN — flush deferred tooltip</li>
 * </ul>
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen extends Gui {

    /**
     * 帧开始时重置 GuiGraphicsExtractor 状态。
     */
    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void catframe$resetGuiGraphics(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiGraphicsExtractor.getInstance().resetForNewFrame();
    }

    /**
     * 帧末 flush 延迟 tooltip — 确保 tooltip 始终渲染在最上层。
     */
    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void catframe$flushTooltip(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiGraphicsExtractor.getInstance().extractDeferredElements();
    }

    /**
     * 接管原版 func_146283_a (drawHoveringText 的包装) — 重定向到延迟 tooltip 管线。
     * <p>使用 SRG 名因为 drawHoveringText 本身不在 mcp-srg 映射中。</p>
     */
    @SuppressWarnings("unchecked")
    @Inject(method = "func_146283_a", at = @At("HEAD"), cancellable = true)
    private void catframe$redirectDrawHoveringText(List lines, int x, int y, CallbackInfo ci) {
        if (lines == null || lines.isEmpty()) return;
        GuiGraphicsExtractor.getInstance().setTooltipForNextFrame(
                (List<String>) lines, x, y);
        ci.cancel();
    }

    /**
     * 接管原版 renderToolTip — 重定向到延迟 tooltip 管线（含 DataComponent style 支持）。
     */
    @Inject(method = "renderToolTip", at = @At("HEAD"), cancellable = true)
    private void catframe$redirectRenderToolTip(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (stack == null) return;
        GuiGraphicsExtractor.getInstance().setTooltipForNextFrame(
                Minecraft.getMinecraft().fontRenderer, stack, x, y);
        ci.cancel();
    }
}
