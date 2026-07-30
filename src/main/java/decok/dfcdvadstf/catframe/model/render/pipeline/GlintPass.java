package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.core.component.DataComponents;
import decok.dfcdvadstf.catframe.core.component.predicates.ItemStackComponents;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Unified enchantment glint (Foil) pass for all item render phases.
 * 统一附魔光效（Foil）pass —— 覆盖所有物品渲染阶段。
 * <p>
 * 背景：Forge 在 {@code RenderItem.renderItemAndEffectIntoGUI} 中将原版的 GUI glint
 * 补画禁用（{@code if (false && hasEffect())}，注释 "modders must handle themselves"），
 * 且掉落物（{@code ForgeHooksClient.renderEntityItem}）、手持
 * （{@code ForgeHooksClient.renderEquippedItem}）的自定义 {@code IItemRenderer}
 * 分支同样绕开原版 glint pass。因此凡是被 CatFrame 接管渲染的物品，
 * 附魔光效必须由本管线自行绘制。
 * <p>
 * 实现对齐两个原版参照：
 * <ul>
 *   <li><b>1.7.10 {@code ItemRenderer} 手持光效</b>：重绘同一份几何 +
 *       {@code GL_TEXTURE} 矩阵两层滚动动画（周期 3000ms / 4873ms、旋转 -50°/+10°）、
 *       {@code glDepthFunc(GL_EQUAL)} 精确叠加在已写入深度的模型片段上、
 *       {@code glBlendFunc(GL_SRC_COLOR, GL_ONE)} 加色混合；</li>
 *   <li><b>26.1.2 {@code GlintTexturingStateShard}</b>：glint 采样直接使用网格的
 *       图集 UV，靠纹理矩阵放大（scale 8.0）取得合适的流纹跨度——
 *       CatFrame 的烘焙 UV 是图集坐标（跨度远小于 [0,1]），故采用 26.1.2 的
 *       scale 8.0 而非 1.7.10 面向 [0,1] UV 的 0.125。</li>
 * </ul>
 * <p>
 * 调用时机：{@link FeatureRenderDispatcher} 逐提交项绘制完正常 pass 之后、
 * {@code applyAfterPart()} 之前 —— 此时扩展链的 beforePart 状态（display 矩阵等）
 * 仍然有效，{@link QuadWriter#writeGlintQuads} 重放几何可以得到与正常 pass
 * 完全一致的顶点坐标，从而通过 {@code GL_EQUAL} 深度测试。
 */
public final class GlintPass {

    /** 附魔光纹纹理 — 与原版 {@code RenderItem.RES_ITEM_GLINT} 一致 */
    private static final ResourceLocation ENCHANTMENT_GLINT =
            new ResourceLocation("textures/misc/enchanted_item_glint.png");

    /**
     * 纹理矩阵 UV 缩放 — 对齐 26.1.2 {@code GlintTexturingStateShard(8.0F)}。
     * 烘焙 UV 为图集坐标（单 sprite 跨度约 0.03），放大 8 倍后流纹跨度
     * 与原版 [0,1] UV × 0.125 的视觉密度相当。
     */
    private static final float UV_SCALE = 8.0F;

    private GlintPass() {
    }

    /**
     * 判断提交项是否需要叠加附魔光效。
     * <p>
     * 仅物品阶段（GUI / 手持 / 掉落 / 展示框）适用；方块阶段
     * （BLOCK_WORLD / BLOCK_GUI）无 foil 语义。
     */
    public static boolean applicable(RenderSubmit s) {
        return isItemPhase(s.phase) && s.stack != null && hasFoil(s.stack);
    }

    /**
     * 判断物品是否应显示附魔光效 — 对标 26.1.2 {@code ItemStack.hasFoil()}。
     * <p>
     * 优先级：
     * <ol>
     *   <li>若有 {@link DataComponents#ENCHANTMENT_GLINT} 组件，按组件值强制开关</li>
     *   <li>否则走 {@link ItemStack#hasEffect(int)}（默认实现为 {@code isItemEnchanted()}，
     *       但金苹果等物品会覆写为 {@code damage > 0}）</li>
     * </ol>
     * 原实现位于 {@code GuiGraphicsExtractor}，随光效统一迁移至管线层共享。
     */
    public static boolean hasFoil(ItemStack stack) {
        Boolean override = ItemStackComponents.get(stack).get(DataComponents.ENCHANTMENT_GLINT);
        if (override != null) {
            return override;
        }
        return stack.hasEffect(0);
    }

    /**
     * 渲染附魔光效：重放提交项的全部 quad 几何（含 solidColor 侧面），
     * 以 glint 纹理 + 两层滚动纹理矩阵叠加绘制。
     * <p>
     * GL 状态经 {@code glPushAttrib} 全量保护（含纹理绑定），
     * 调用方无需在本方法返回后重新绑定图集。
     *
     * @param s 渲染提交项（须先通过 {@link #applicable} 检查）
     * @param t 共享 Tessellator 实例
     */
    public static void render(RenderSubmit s, Tessellator t) {
        // ENABLE_BIT: depth/blend/lighting/alpha test 开关位；DEPTH_BUFFER_BIT: depthFunc + depthMask；
        // COLOR_BUFFER_BIT: blendFunc + alphaFunc；TEXTURE_BIT: 纹理绑定；CURRENT_BIT: 当前颜色
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT);
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(ENCHANTMENT_GLINT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

            // GL_EQUAL：只在正常 pass 已写入的模型片段上叠加，光效精确贴合模型轮廓
            GL11.glDepthFunc(GL11.GL_EQUAL);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            // 加色混合 — 与原版 ItemRenderer 手持光效一致（OpenGlHelper.glBlendFunc(768, 1, ...)）
            GL11.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);

            // 两层滚动动画 — 周期 / 旋转对齐 1.7.10 ItemRenderer 手持光效
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            for (int pass = 0; pass < 2; pass++) {
                GL11.glPushMatrix();
                GL11.glScalef(UV_SCALE, UV_SCALE, UV_SCALE);
                if (pass == 0) {
                    float scroll = (float) (Minecraft.getSystemTime() % 3000L) / 3000.0F * 8.0F;
                    GL11.glTranslatef(scroll, 0.0F, 0.0F);
                    GL11.glRotatef(-50.0F, 0.0F, 0.0F, 1.0F);
                } else {
                    float scroll = (float) (Minecraft.getSystemTime() % 4873L) / 4873.0F * 8.0F;
                    GL11.glTranslatef(-scroll, 0.0F, 0.0F);
                    GL11.glRotatef(10.0F, 0.0F, 0.0F, 1.0F);
                }

                t.startDrawingQuads();
                QuadWriter.writeGlintQuads(s, t);
                t.draw();

                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /** 是否为持有 ItemStack 的物品渲染阶段（GUI / 手持 / 掉落 / 展示框）。 */
    private static boolean isItemPhase(RenderPhase phase) {
        return phase != null
                && phase != RenderPhase.BLOCK_WORLD
                && phase != RenderPhase.BLOCK_GUI;
    }
}
