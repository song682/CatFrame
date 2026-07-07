package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * GUI 渲染上下文管理器 — 对标 26.1.2 {@code GuiGraphics}。
 * <p>
 * 职责：在 GUI 上下文（物品栏、容器界面等）中管理物品渲染所需的 GL 状态，
 * 封装 {@code depth / lighting / alpha test / blend} 的显式设置与恢复，
 * 使 {@code UniformRenderPipeline.renderItemQuads} 保持纯粹的 quad 处理职责，
 * 不依赖调用方预设的 GL 环境。
 *
 * <h3>与 26.1.2 的对应关系</h3>
 * <ul>
 *   <li>26.1.2 的 {@code RenderPipelines.GUI_ITEM} 隐式管理全部 GL 状态 →
 *       本类在 1.7.10 GL 即时模式下显式管理</li>
 *   <li>26.1.2 的 {@code GuiGraphics.item()} 通过 {@code GuiItemRenderState} 提取渲染状态 →
 *       本类委托给 {@link IItemStateProvider#render}，并将渲染记录提交到 {@link GuiRenderState}</li>
 *   <li>26.1.2 的附魔光效由 render pipeline 的 glint shader 处理 →
 *       本类通过 {@link #renderEnchantmentGlint()} 手动实现</li>
 *   <li>26.1.2 的 {@code GuiRenderState} 分层收集器 →
 *       独立的 {@link GuiRenderState} 完整实现</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * GuiGraphicsExtractor gui = new GuiGraphicsExtractor();
 * gui.item(stack, 0, 0);  // 在 GUI 中渲染物品（含附魔光效）
 *
 * // 帧开始时重置
 * gui.getRenderState().reset();
 * }</pre>
 */
public class GuiGraphicsExtractor {

    /** 附魔光纹纹理 — 对标 26.1.2 的 enchantment glint shader 效果 */
    private static final ResourceLocation ENCHANTMENT_GLINT =
            new ResourceLocation("textures/misc/enchanted_item_glint.png");

    /**
     * GL 状态保存掩码：覆盖 enable 位、纹理绑定、当前颜色。
     * 不使用 GL_COLOR_BUFFER_BIT — 避免保存/恢复 framebuffer 颜色内容。
     */
    private static final int GL_SAVE_MASK =
            GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT;

    private final Minecraft mc;

    /** 完整分层状态收集器（对标 26.1.2 GuiRenderState） */
    private final GuiRenderState renderState;

    public GuiGraphicsExtractor() {
        this.mc = Minecraft.getMinecraft();
        this.renderState = new GuiRenderState();
    }

    // ==================== 物品渲染 ====================

    /**
     * 在 GUI 中渲染物品（无种子偏移）。
     */
    public void item(ItemStack stack) {
        item(stack, 0, 0);
    }

    /**
     * 在 GUI 中渲染物品。
     * <p>
     * 完整流程：
     * <ol>
     *   <li>{@code glPushAttrib} 保存当前 GL 状态</li>
     *   <li>设置 GUI 物品渲染 GL 环境（disable depth/lighting, enable alpha/blend）</li>
     *   <li>委托 {@link IItemStateProvider#render} 渲染模型</li>
     *   <li>若物品有附魔，渲染附魔光效</li>
     *   <li>将渲染记录提交到 {@link GuiRenderState}（自动分层）</li>
     *   <li>{@code glPopAttrib} 恢复所有 GL 状态</li>
     * </ol>
     *
     * @param stack 待渲染的物品栈
     * @param x     GUI 槽位 X 坐标（像素）
     * @param y     GUI 槽位 Y 坐标（像素）
     */
    public void item(ItemStack stack, int x, int y) {
        if (stack == null || stack.getItem() == null) return;

        IItemStateProvider model = VanillaModelManager.ModelRegistration
                .getRegisteredItemModel(stack.getItem());
        if (model == null) return;

        // 保存完整 GL 状态 — 不依赖手动逐条恢复
        GL11.glPushAttrib(GL_SAVE_MASK);
        setupItemRenderState();
        try {
            // GUI 上下文不需要反抵消预变换（Forge INVENTORY 路径无 Forge 前置变换）
            model.render(stack, RenderPhase.ITEM_GUI, null);

            // 附魔光效 — 在模型渲染后叠加
            if (stack.isItemEnchanted()) {
                renderEnchantmentGlint();
            }

            // 提交到分层状态收集器（自动分层）
            renderState.addItem(new GuiRenderState.ItemRenderState(stack, x, y));
        } finally {
            // 一条 glPopAttrib 恢复所有 pushAttrib 前保存的状态
            GL11.glPopAttrib();
        }
    }

    // ==================== GL 状态管理 ====================

    /**
     * 设置物品渲染所需的 GL 状态。
     * <p>
     * 对标 26.1.2 {@code RenderPipelines.GUI_ITEM} 的隐式 GL 状态管理。
     * 因为外层使用 {@code glPushAttrib/glPopAttrib}，此处无需手动记录原始状态。
     */
    private void setupItemRenderState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    // ==================== 附魔光效 ====================

    /**
     * 渲染附魔光效 — 对标 26.1.2 的 enchantment glint shader。
     * <p>
     * 在 26.1.2 中，附魔光效由 render pipeline 的 glint shader 自动处理：
     * 当 {@code ItemStack.hasFoil()} 为 true 时，pipeline 在渲染模型后
     * 自动叠加一层带动画 UV 滚动的附魔纹理。
     * <p>
     * 在 1.7.10 GL 即时模式下，需要手动实现：
     * <ul>
     *   <li>禁用颜色写入（{@code glColorMask(false,false,false,false)}）</li>
     *   <li>启用深度测试 + 深度写入（确保光效只在有模型几何的位置显示）</li>
     *   <li>使用 {@code GL_DST_COLOR + GL_ONE} 混合（乘法叠加效果）</li>
     *   <li>绑定附魔纹理，渲染两层带动画 UV 滚动的全屏 quad</li>
     * </ul>
     */
    private void renderEnchantmentGlint() {
        Tessellator t = Tessellator.instance;

        // 深度测试确保光效只在有模型几何的位置显示
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);

        // 禁用颜色写入 — 只修改深度缓冲，不直接写入颜色
        GL11.glColorMask(false, false, false, false);

        // 乘法叠加混合：最终色 = 源色 × 目标色 + 目标色 × 1
        // 产生附魔特有的闪光效果
        GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_ONE);

        mc.getTextureManager().bindTexture(ENCHANTMENT_GLINT);

        // 两层动画 UV 滚动 — 与原版 RenderItem.renderEffect() 一致
        for (int pass = 0; pass < 2; pass++) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

            float speed = pass == 0 ? 8.0F : -4.0F;
            float scrollX = (float) (System.currentTimeMillis() % 3000L) / 3000.0F * speed;
            float scrollY = (float) (System.currentTimeMillis() % 48721L) / 48721.0F * speed;

            // 亮度：第一层较亮，第二层较暗
            float brightness = pass == 0 ? 0.5F : 0.25F;
            t.startDrawingQuads();
            t.setBrightness(240);
            t.setColorRGBA_F(brightness, brightness, brightness, 1.0F);

            // 覆盖整个 GUI 视口（与 renderItemQuads 的 viewport transform 匹配）
            t.addVertexWithUV(-8, 8, 0.01, scrollX, scrollY);
            t.addVertexWithUV(8, 8, 0.01, 0.5F + scrollX, scrollY);
            t.addVertexWithUV(8, -8, 0.01, 0.5F + scrollX, 0.5F + scrollY);
            t.addVertexWithUV(-8, -8, 0.01, scrollX, 0.5F + scrollY);
            t.draw();
        }

        // 恢复混合函数
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // 恢复颜色写入
        GL11.glColorMask(true, true, true, true);
    }

    // ==================== 状态访问 ====================

    /**
     * 获取分层状态收集器。
     */
    public GuiRenderState getRenderState() {
        return renderState;
    }
}
