package decok.dfcdvadstf.catframe.ui.render.pip;

import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * oversized 物品的 PiP 渲染状态（{@code oversized_in_gui=true}）。
 * <p>
 * 物品以其自然模型尺寸渲染（尺寸由 gui display 变换决定），与普通物品一致；
 * 唯一区别是走独立 PiP 通道：<b>不设 scissor</b>，允许模型几何溢出 16x16 槽位并正确叠层，
 * 且不触发溢出保护的钳制。
 */
public class OversizedItemRenderState extends AbstractPipRenderState {

    private final ItemStack stack;

    public OversizedItemRenderState(ItemStack stack,
                                    @Nullable float[] poseMatrix,
                                    ScreenRectangle bounds) {
        // oversized 明确不裁剪 → scissorArea 恒为 null
        super(poseMatrix, bounds, null);
        this.stack = stack;
    }

    public ItemStack getStack() {
        return stack;
    }
}
