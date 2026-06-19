package decok.dfcdvadstf.catframe;

import decok.dfcdvadstf.catframe.model.*;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * Bluey 毛绒玩偶的 ItemModel。
 * <p>
 * 实现"物品栏 2D + 掉落物 2D + 手持 3D"：
 * <ul>
 *   <li>{@link RenderPhase#ITEM_GUI} / {@link RenderPhase#DROPPED_ITEM_GROUND}
 *       → 接管，渲染 {@code bluey_inventory} 2D 模型（含侧面厚度）</li>
 *   <li>{@link RenderPhase#ITEM_HAND_FIRST_PERSON} / {@link RenderPhase#ITEM_HAND_THIRD_PERSON}
 *       → 接管，渲染 {@code bluey} 3D 模型</li>
 * </ul>
 */
public class BlueyPlushyItemModel implements ItemModel {

    private static final String MODEL_3D = "item/bluey";
    private static final String MODEL_2D = "item/bluey_inventory";

    private BlockStateModelPart part3D = null;
    private Map<String, ModelJson.DisplayTransform> display3D = null;

    private BlockStateModelPart part2D = null;
    private Map<String, ModelJson.DisplayTransform> display2D = null;

    /**
     * 所有阶段都接管——2D 阶段用 inventory 模型渲染，手持阶段用 3D 模型渲染。
     */
    @Override
    public boolean handles(RenderPhase phase) {
        return true;
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase) {
        if (phase == RenderPhase.ITEM_HAND_FIRST_PERSON
                || phase == RenderPhase.ITEM_HAND_THIRD_PERSON) {
            renderHand(stack, phase);
        } else if (phase == RenderPhase.ITEM_GUI
                || phase == RenderPhase.DROPPED_ITEM_GROUND) {
            render2D(stack, phase);
        }
    }

    private void renderHand(ItemStack stack, RenderPhase phase) {
        if (part3D == null) {
            part3D = VanillaModelManager.ModelRegistration.bakeModelPart(MODEL_3D, 0);
            ModelJson resolved = ModelResolver.resolve(MODEL_3D);
            display3D = (resolved != null) ? resolved.display : null;
        }
        if (part3D == null || part3D.isEmpty()) return;

        UniformRenderPipeline.renderItemQuads(part3D, stack, phase, display3D);
    }

    /**
     * 用 2D inventory 模型渲染（builtin/generated 会生成侧面，保持厚度）。
     */
    private void render2D(ItemStack stack, RenderPhase phase) {
        if (part2D == null) {
            part2D = VanillaModelManager.ModelRegistration.bakeModelPart(MODEL_2D, 0);
            ModelJson resolved = ModelResolver.resolve(MODEL_2D);
            display2D = (resolved != null) ? resolved.display : null;
        }
        if (part2D == null || part2D.isEmpty()) return;

        UniformRenderPipeline.renderItemQuads(part2D, stack, phase, display2D);
    }
}
