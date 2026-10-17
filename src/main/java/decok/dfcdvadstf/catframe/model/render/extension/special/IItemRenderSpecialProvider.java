package decok.dfcdvadstf.catframe.model.render.extension.special;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;

/**
 * Per-item full self-draw gate (symmetric to the item tint provider).
 * <p>
 * A third party that wants a particular {@link net.minecraft.item.Item} to "fully take
 * over its own drawing" in the CatFrame item rendering pipeline (bypassing the general
 * {@link decok.dfcdvadstf.catframe.model.state.item.ItemStateModel} model evaluation)
 * implements this interface and registers it via {@link SpecialRenderRegistry#registerItem}.
 * On a hit (i.e. {@link #handles(RenderPhase)} returns true for the current phase),
 * {@code RenderJsonItemModel} calls {@link #renderItem} (by which point the pipeline has
 * already prepared the GL environment and computed the Forge counter-transform
 * preTransform) and skips the general model.
 * <p>
 * To recursively render other item stacks during a self-draw (e.g. multi-layer item frame
 * contents), call the official entry point
 * {@code UniformRenderPipeline.renderItemStack(stack, phase, preTransform)}.
 */
@SideOnly(Side.CLIENT)
public interface IItemRenderSpecialProvider {

    /**
     * Whether to take over this render phase. Returning {@code false} falls back to the
     * general model behaviour for that phase.
     *
     * @param phase the current item render phase
     * @return true to take over
     */
    boolean handles(RenderPhase phase);

    /**
     * Fully self-draws this item stack. The GL environment has already been prepared by
     * the pipeline, and {@code preTransform} is the Forge counter-transform matrix
     * (may be {@code null}, e.g. in default scenarios other than the GUI).
     *
     * @param stack        the item stack being rendered
     * @param phase        the current render phase
     * @param preTransform the counter-transform matrix, may be null
     */
    void renderItem(ItemStack stack, RenderPhase phase, @Nullable Matrix4d preTransform);
}
