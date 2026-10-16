package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * PiP render state for oversized items ({@code oversized_in_gui=true}).
 * <p>
 * The item renders at its natural model size (determined by the gui display transform),
 * just like a normal item; the only difference is that it goes through the dedicated PiP
 * pipeline: <b>no scissor is set</b>, allowing the model geometry to overflow the 16x16
 * slot while still layering correctly, and the overflow-guard clamping is not triggered.
 */
public class OversizedItemRenderState extends AbstractPipRenderState {

    private final ItemStack stack;

    public OversizedItemRenderState(ItemStack stack,
                                    @Nullable float[] poseMatrix,
                                    ScreenRectangle bounds) {
        // oversized explicitly does not clip → scissorArea is always null
        super(poseMatrix, bounds, null);
        this.stack = stack;
    }

    public ItemStack getStack() {
        return stack;
    }
}
