package decok.dfcdvadstf.catframe.model.render.extension.special;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4d;

/**
 * per-Item 完全自绘门（对称 {@code IItemTintProvider}）。
 * <p>
 * 第三方若要让某个 {@link net.minecraft.item.Item} 在 CatFrame 物品渲染管线中"完全接管自绘"
 * （绕过通用 {@link decok.dfcdvadstf.catframe.model.state.item.ItemStateModel} 模型求值），
 * 实现本接口并经 {@link SpecialRenderRegistry#registerItem} 注册。命中后
 * {@code RenderJsonItemModel} 会调用 {@link #renderItem}（此时管线已完成 GL 环境准备与
 * Forge 逆变换的 preTransform 计算），并跳过通用模型。
 * <p>
 * A per-item full self-draw gate, symmetric to the item tint provider. When registered and
 * {@link #handles(RenderPhase)} returns true for the current phase, the general model is
 * bypassed and {@link #renderItem} is invoked with the pipeline's prepared GL context.
 * <p>
 * 若需在自绘中递归渲染其它物品栈（如展示框多层内容），调用官方入口
 * {@code UniformRenderPipeline.renderItemStack(stack, phase, preTransform)}。
 */
@SideOnly(Side.CLIENT)
public interface IItemRenderSpecialProvider {

    /**
     * 是否接管该渲染阶段。返回 {@code false} 时该阶段回退通用模型行为。
     *
     * @param phase 当前物品渲染阶段
     * @return true 表示接管
     */
    boolean handles(RenderPhase phase);

    /**
     * 完全自绘该物品栈。GL 环境已由管线准备，{@code preTransform} 为 Forge 逆变换抵消矩阵
     * （可能为 {@code null}，如 GUI 之外的默认场景）。
     *
     * @param stack        正在渲染的物品栈
     * @param phase        当前渲染阶段
     * @param preTransform 逆变换抵消矩阵，可为 null
     */
    void renderItem(ItemStack stack, RenderPhase phase, @Nullable Matrix4d preTransform);
}
