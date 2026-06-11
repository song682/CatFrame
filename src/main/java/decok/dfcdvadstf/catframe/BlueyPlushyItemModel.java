package decok.dfcdvadstf.catframe;

import decok.dfcdvadstf.catframe.model.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.ItemModel;
import decok.dfcdvadstf.catframe.model.ModelJson;
import decok.dfcdvadstf.catframe.model.ModelResolver;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * Bluey 毛绒玩偶的 ItemModel。
 * <p>
 * 按照文档实现"快捷栏 2D + 手持 3D"：
 * <ul>
 *   <li>{@link RenderPhase#ITEM_GUI} → 不接管，走原版 2D 图标渲染（{@link #handles(RenderPhase)} 返回 false）</li>
 *   <li>{@link RenderPhase#ITEM_HAND_FIRST_PERSON} / {@link RenderPhase#ITEM_HAND_THIRD_PERSON}
 *       → 接管，渲染 {@code bluey} 3D 模型</li>
 * </ul>
 */
public class BlueyPlushyItemModel implements ItemModel {

    private static final String MODEL_3D = "item/bluey";

    private BlockStateModelPart part3D = null;
    private Map<String, ModelJson.DisplayTransform> display3D = null;

    /**
     * GUI 走原版 2D 图标，手持走自定义 3D 模型。
     */
    @Override
    public boolean handles(RenderPhase phase) {
        return phase != RenderPhase.ITEM_GUI;
    }

    @Override
    public void render(ItemStack stack, RenderPhase phase) {
        if (phase == RenderPhase.ITEM_HAND_FIRST_PERSON
                || phase == RenderPhase.ITEM_HAND_THIRD_PERSON) {
            renderHand(stack, phase);
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
}
