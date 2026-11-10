package decok.dfcdvadstf.catframe.mixin.middle.render;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into {@link GuiScreen} to drain the oversized GUI item queue before a vanilla tooltip is
 * drawn.
 * <p>
 * Vanilla draws item tooltips <b>inside</b> {@code drawScreen} ({@code GuiContainer.renderToolTip}
 * → this method), i.e. before the end-of-frame flush these screens never reach. An oversized
 * {@code oversized_in_gui} item is queued exactly so that it overflows its 16×16 slot, and the
 * PiP flush would then paint that overflow <b>over</b> the tooltip. Draining only those item states
 * at the head of {@code drawHoveringText} puts the item behind the tooltip instead, which is also
 * why this hook is a correct place: the method disables {@code GL_DEPTH_TEST} for its whole body,
 * so ordering — not depth — decides the result.
 * </p>
 * <p>
 * Screens that own the tooltip pipeline themselves (CatFrame's {@code AbstractContainerScreen}
 * suppresses the vanilla tooltip and collects it through
 * {@link GuiGraphicsExtractor#setTooltipForNextFrame}) never reach this method; their oversized
 * items stay queued and are flushed by the end-of-frame flush, followed by the deferred tooltip.
 * </p>
 * <p>
 * <b>{@code remap = false} is mandatory here</b>: {@code drawHoveringText(List,int,int,FontRenderer)}
 * is a Forge-patch-added overload, so it has no entry in any of the SRG mapping tables (verified:
 * zero hits for the name in {@code mcp-srg.srg} / {@code mcp-notch.srg} / {@code notch-srg.srg}),
 * and the annotation processor fails with "Unable to locate obfuscation mapping" when asked to
 * remap it. Forge-added methods keep their source name in both the dev and the production
 * (FML-deobfuscated) namespace, which is why the literal name — and the MCP class name in the
 * descriptor — resolves in both. Targeting the vanilla 3-argument {@code func_146283_a} instead is
 * <b>not</b> an equivalent substitute: it only serves the creative tab / chat / FML-config
 * tooltips, while {@code GuiScreen.renderToolTip} (i.e. every {@code GuiContainer} item tooltip)
 * calls this 4-argument overload directly.
 * </p>
 */
@Mixin(GuiScreen.class)
public class MixinGuiScreenPipFlush {

    /**
     * Drains the oversized GUI item states before the vanilla tooltip paints. Single chokepoint:
     * both {@code renderToolTip} and the 3-argument delegator funnel into this overload.
     * <p>
     * Deliberately calls {@link GuiGraphicsExtractor#flushOversizedGuiItems()} rather than
     * {@link GuiGraphicsExtractor#flushPip()}: GUI entity PiP states must keep their end-of-frame
     * slot, otherwise this tooltip hook would silently reorder them.
     */
    @Inject(method = "drawHoveringText(Ljava/util/List;IILnet/minecraft/client/gui/FontRenderer;)V",
            at = @At("HEAD"), remap = false)
    private void catframe$flushPipBeforeTooltip(CallbackInfo ci) {
        GuiGraphicsExtractor.getInstance().flushOversizedGuiItems();
    }
}
