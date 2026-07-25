package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.extension.tint.IItemTintProvider;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateModel;
import net.minecraft.item.ItemStack;

/**
 * 渲染桥接：把 ItemState 决策树中 JSON 声明的 tints 转交给渲染侧
 * {@link decok.dfcdvadstf.catframe.model.render.extension.tint.TintRegistry} 消费，
 * 由既有的 {@code TintRenderExtension} 按 quad 的 {@code "tintindex"} 应用颜色。
 * <p>
 * 数组位置即 tintIndex（per-layer 语义，对齐高版本 item model definition）。
 *
 * <p>Render bridge: delegates JSON-declared tints on an ItemState tree to the
 * render-side {@code TintRegistry}, so the existing {@code TintRenderExtension}
 * applies them by each quad's {@code "tintindex"}. The array position itself is
 * the tintIndex (per-layer semantics, aligned with high-version item model definitions).
 */
public final class ItemStateTintBridge implements IItemTintProvider {

    private final ItemStateModel model;

    public ItemStateTintBridge(ItemStateModel model) {
        this.model = model;
    }

    @Override
    public int getTint(ItemStack stack, int tintIndex) {
        // 物品 tint 与渲染阶段无关（均由 ItemStack 的 NBT/damage 驱动），故使用中性阶段。
        // Item tints are phase-independent (driven purely by ItemStack NBT/damage),
        // so a neutral render phase is used here.
        return model.resolveTint(stack, RenderPhase.ITEM_GUI, tintIndex);
    }
}
