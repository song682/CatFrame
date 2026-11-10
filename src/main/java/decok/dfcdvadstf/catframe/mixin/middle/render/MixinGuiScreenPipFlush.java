package decok.dfcdvadstf.catframe.mixin.middle.render;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link GuiScreen} to flush the PiP queue before a vanilla tooltip is drawn.
 * <p>
 * Vanilla draws item tooltips <b>inside</b> {@code drawScreen} ({@code GuiContainer.renderToolTip}
 * → this method), i.e. before the end-of-frame flush these screens never reach. An oversized
 * {@code oversized_in_gui} item is queued exactly so that it overflows its 16×16 slot, and the
 * PiP flush would then paint that overflow <b>over</b> the tooltip. Flushing at the head of
 * {@code drawHoveringText} puts the item behind the tooltip instead, which is also why this hook
 * is a correct place: the method disables {@code GL_DEPTH_TEST} for its whole body, so ordering —
 * not depth — decides the result.
 * </p>
 * <p>
 * Screens that own the tooltip pipeline themselves (CatFrame's {@code AbstractContainerScreen}
 * suppresses the vanilla tooltip and collects it through
 * {@link GuiGraphicsExtractor#setTooltipForNextFrame}) never reach this method; their oversized
 * items stay queued and are flushed by the end-of-frame flush, followed by the deferred tooltip.
 * </p>
 */
@Mixin(GuiScreen.class)
public class MixinGuiScreenPipFlush {

    /**
     * Drains the PiP queue before the vanilla tooltip paints.
     * <p>Overload-qualified descriptor: only the {@code FontRenderer}-taking variant draws, the
     * 3-argument one delegates to it.</p>
     */
    @Inject(method = "drawHoveringText(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V", at = @At("HEAD"))
    private void catframe$flushPipBeforeTooltip(CallbackInfo ci) {
        GuiGraphicsExtractor.getInstance().flushPip();
    }
}
