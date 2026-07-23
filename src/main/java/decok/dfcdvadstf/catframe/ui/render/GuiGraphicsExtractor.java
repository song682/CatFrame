package decok.dfcdvadstf.catframe.ui.render;

import decok.dfcdvadstf.catframe.core.component.DataComponents;
import decok.dfcdvadstf.catframe.core.component.predicates.ItemStackComponents;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import decok.dfcdvadstf.catframe.ui.render.pip.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.util.*;

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
 * GuiGraphicsExtractor gui = GuiGraphicsExtractor.getInstance();
 * gui.resetForNewFrame();          // 帧开始（drawScreen HEAD）
 * gui.item(stack, x, y);           // 提取：快照矩阵 + 收集状态（不立即绘制）
 * gui.extractDeferredElements();   // 帧末（drawScreen RETURN）统一 flush 物品
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

    /** 全局单例 — 1.7.10 没有每帧创建 GuiGraphics 的机制，用单例替代 */
    private static final GuiGraphicsExtractor INSTANCE = new GuiGraphicsExtractor();

    /**
     * modelview 矩阵快照/恢复缓冲（客户端单线程复用）。
     * <p>采集时 {@code glGetFloat} 写入后立即拷贝为 {@code float[16]}，flush 时再填回供 {@code glLoadMatrix}。</p>
     */
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    private final Minecraft mc;

    /** 完整分层状态收集器（对标 26.1.2 GuiRenderState） */
    private final GuiRenderState renderState;

    /**
     * 帧内 PiP 提交队列 — 对标 26.1.2 {@code GuiRenderState} 收集的 PiP 状态。
     * <p>3D 方块模型 / GUI 实体 / oversized 物品在采集阶段入队，帧末 flush 时按类型分派绘制。</p>
     */
    private final List<PictureInPictureRenderState> deferredPip = new ArrayList<>();

    /** PiP 分派表：状态运行时类型 → 对应渲染器 — 对标 26.1.2 {@code PictureInPictureRenderer} 注册表。 */
    private final Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pipDispatchTable =
            new HashMap<>();

    /** GUI 物品绘制回调实现 — 供 {@link OversizedItemPipRenderer} 复用本类的物品渲染逻辑。 */
    private final ItemGuiDrawer itemDrawer = new ItemGuiDrawerImpl();

    public GuiGraphicsExtractor() {
        this.mc = Minecraft.getMinecraft();
        this.renderState = new GuiRenderState();

        // 注册 PiP 渲染器分派表（构造期常量，帧间不清）
        registerPip(new OversizedItemPipRenderer(itemDrawer));
        registerPip(new EntityPipRenderer());
    }

    /** 注册一个 PiP 渲染器，以其处理的状态类型为分派 key。 */
    private void registerPip(PictureInPictureRenderer<?> renderer) {
        pipDispatchTable.put(renderer.getStateClass(), renderer);
    }

    /**
     * 获取全局单例。
     */
    public static GuiGraphicsExtractor getInstance() {
        return INSTANCE;
    }

    // ==================== 物品渲染 ====================

    /**
     * 在 GUI 中渲染物品（无种子偏移）。
     */
    public void item(ItemStack stack) {
        item(stack, 0, 0);
    }

    /**
     * 在 GUI 中渲染物品（<b>延迟到帧末</b>）。
     * <p>
     * 对标 LaterRenderer.md 路径一 / 26.1.2 {@code GuiGraphics.item()}：<b>提取阶段不绘制</b>，
     * 仅快照当前 modelview 矩阵并将渲染状态收集到 {@link GuiRenderState} 树，
     * 实际 GL 绘制推迟到 {@link #extractDeferredElements()}。
     * <p>
     * 与 {@code RenderCommandBuffers} 的<b>作用域内延迟</b>（begin→submit→endScope 均在同一 GL
     * 上下文内 flush，无需矩阵快照）不同：<b>帧末延迟</b>的 flush 发生在 {@code drawScreen}
     * 返回时，GL 上下文已切换，故必须在此快照 modelview 矩阵，帧末 {@code glLoadMatrix} 恢复。
     *
     * @param stack 待渲染的物品栈
     * @param x     GUI 槽位 X 坐标（像素，仅用于分层/追踪）
     * @param y     GUI 槽位 Y 坐标（像素，仅用于分层/追踪）
     */
    public void item(ItemStack stack, int x, int y) {
        if (stack == null || stack.getItem() == null) return;

        IItemStateProvider model = ModelRegistry.getRegisteredItemModel(stack.getItem());
        if (model == null) return;

        // 延迟渲染：仅快照调用点的 modelview 矩阵 + 收集状态，不立即绘制。
        float[] pose = captureModelViewMatrix();

        // oversized_in_gui=true 的物品走独立 PiP 通道：绕开 GuiRenderState 自动分层、不设 scissor，
        // 允许模型几何自然溢出 16x16 槽位。
        if (ModelRegistry.isOversizedInGui(stack.getItem())) {
            deferredPip.add(new OversizedItemRenderState(stack, pose, new ScreenRectangle(x, y, 16, 16)));
        } else {
            renderState.addItem(new GuiRenderState.ItemRenderState(stack, x, y, pose));
        }
    }

    /**
     * 实际绘制一个已收集的物品渲染状态 — 对标 26.1.2 {@code GuiRenderer.prepareItemElements()} 的消费端。
     * <p>
     * 从旧 {@code item()} 抽取的即时渲染逻辑，由 {@link #extractDeferredElements()} 帧末统一驱动：
     * <ol>
     *   <li>{@code glPushAttrib} 保存 GL 状态 + 设置物品渲染环境</li>
     *   <li>{@code glLoadMatrix} 恢复收集时的 modelview 矩阵（精确复现调用点的位置/缩放）</li>
     *   <li>委托 {@link IItemStateProvider#render} 渲染模型 + 叠加附魔光效</li>
     * </ol>
     */
    private void renderDeferredItem(GuiRenderState.ItemRenderState state) {
        drawItemModel(state.getStack(), state.getPoseMatrix(), false);
    }

    /**
     * GUI 物品模型的实际绘制核心 — 供延迟物品路径与 PiP oversized 通道复用。
     * <ol>
     *   <li>{@code glPushAttrib} 保存 GL 状态 + 设置物品渲染环境</li>
     *   <li>{@code glLoadMatrix} 恢复收集时的 modelview 矩阵（精确复现调用点的位置/缩放）</li>
     *   <li>委托 {@link IItemStateProvider#render} 渲染模型 + 叠加附魔光效</li>
     * </ol>
     *
     * @param stack          物品栈
     * @param pose           采集时的 modelview 矩阵快照，可为 null
     * @param allowOversized 预留标志：为未来溢出钳制支持保留。当前无实时钳制逻辑，
     *                       两分支渲染一致（oversized 物品的差异体现在采集侧走独立 PiP 通道）。
     */
    private void drawItemModel(ItemStack stack, @Nullable float[] pose, boolean allowOversized) {
        if (stack == null || stack.getItem() == null) return;

        IItemStateProvider model = ModelRegistry.getRegisteredItemModel(stack.getItem());
        if (model == null) return;

        // 保存完整 GL 状态 — 不依赖手动逐条恢复
        GL11.glPushAttrib(GL_SAVE_MASK);
        setupItemRenderState();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            // 恢复收集时的 modelview 矩阵 — 帧末 GL 上下文已切换，需据快照重建调用点变换
            if (pose != null) {
                MATRIX_BUFFER.clear();
                MATRIX_BUFFER.put(pose);
                MATRIX_BUFFER.flip();
                GL11.glLoadMatrix(MATRIX_BUFFER);
            }

            // GUI 上下文不需要反抵消预变换（Forge INVENTORY 路径无 Forge 前置变换）
            model.render(stack, RenderPhase.ITEM_GUI, null);

            // 附魔光效 — 在模型渲染后叠加
            if (hasFoil(stack)) {
                renderEnchantmentGlint();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /**
     * {@link ItemGuiDrawer} 的内部实现 — 委托到 {@link #drawItemModel}，
     * 供 pip 包的 {@link OversizedItemPipRenderer} 复用物品渲染逻辑，
     * 避免把 {@code draw()} 泄漏进本类公共 API。
     */
    private final class ItemGuiDrawerImpl implements ItemGuiDrawer {
        @Override
        public void draw(ItemStack stack, @Nullable float[] pose, boolean allowOversized) {
            drawItemModel(stack, pose, allowOversized);
        }
    }

    /**
     * 快照当前 modelview 矩阵 — 供帧末延迟渲染恢复调用点的 GL 变换。
     */
    private static float[] captureModelViewMatrix() {
        MATRIX_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX_BUFFER);
        float[] m = new float[16];
        MATRIX_BUFFER.get(m);
        return m;
    }

    // ==================== PiP 提交（GUI 实体） ====================

    /**
     * 在 GUI 中渲染实体（<b>延迟到帧末</b>，走独立 PiP 通道）。
     *
     * @param entity 待渲染实体
     * @param scale  缩放
     * @param lookX  实体朝向的水平偏移（对标 {@code GuiInventory.drawEntityOnScreen} 的 mouseX）
     * @param lookY  实体朝向的垂直偏移
     * @param x      GUI 槽位 X 坐标（像素）
     * @param y      GUI 槽位 Y 坐标（像素）
     */
    public void entity(EntityLivingBase entity, int scale, float lookX, float lookY, int x, int y) {
        if (entity == null) return;
        float[] pose = captureModelViewMatrix();
        deferredPip.add(new GuiEntityRenderState(
                entity, scale, lookX, lookY, pose, new ScreenRectangle(x, y, 16, 16), null));
    }

    // ==================== GL 状态管理 ====================

    /**
     * 设置物品渲染所需的 GL 状态。
     * <p>
     * 对标 26.1.2 {@code RenderPipelines.GUI_ITEM} 的隐式 GL 状态管理。
     * 深度测试保持启用，确保模型写入深度缓冲供附魔光效的深度遮罩使用。
     * 因为外层使用 {@code glPushAttrib/glPopAttrib}，此处无需手动记录原始状态。
     */
    private void setupItemRenderState() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    // ==================== 附魔光效 ====================

    /**
     * 判断物品是否应显示附魔光效 — 对标 26.1.2 {@code ItemStack.hasFoil()}。
     * <p>
     * 优先级：
     * <ol>
     *   <li>若有 {@link DataComponents#ENCHANTMENT_GLINT} 组件，按组件值强制开关</li>
     *   <li>否则走 {@link ItemStack#hasEffect(int)}（默认实现为 {@code isItemEnchanted()}，
     *       但金苹果等物品会覆写为 {@code damage > 0}）</li>
     * </ol>
     */
    private static boolean hasFoil(ItemStack stack) {
        Boolean override = ItemStackComponents.get(stack).get(DataComponents.ENCHANTMENT_GLINT);
        if (override != null) {
            return override;
        }
        return stack.hasEffect(0);
    }

    /**
     * 渲染附魔光效 — 对标原版 {@code RenderItem.renderEffect()} 的两阶段渲染。
     * <p>
     * 阶段 1（深度遮罩）：{@code colorMask(false,false,false,true)}，
     *   只写 alpha 通道 + 深度缓冲，不写 RGB。建立模型轮廓的深度遮罩。
     * <p>
     * 阶段 2（颜色渲染）：{@code colorMask(true,true,true,true)}，
     *   启用深度测试 + alpha 混合，渲染两层动画 UV 滚动的 glint 纹理。
     *   深度测试确保光效只在有模型几何的位置显示。
     */
    private void renderEnchantmentGlint() {
        Tessellator t = Tessellator.instance;

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);

        // ---- 阶段 1：深度遮罩 ----
        // 只写 alpha + 深度，不写 RGB — 建立模型轮廓的深度遮罩
        GL11.glColorMask(false, false, false, true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        t.startDrawingQuads();
        t.setColorOpaque_I(-1);
        t.addVertex(-8, 8, 0.01);
        t.addVertex(8, 8, 0.01);
        t.addVertex(8, -8, 0.01);
        t.addVertex(-8, -8, 0.01);
        t.draw();

        // ---- 阶段 2：颜色渲染 ----
        // 恢复全通道颜色写入 + 深度测试
        GL11.glColorMask(true, true, true, true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        mc.getTextureManager().bindTexture(ENCHANTMENT_GLINT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        // 两层动画 UV 滚动 — 与原版 RenderItem.renderEffect() 一致
        for (int pass = 0; pass < 2; pass++) {
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
    }

    // ==================== 状态访问 ====================

    /**
     * 获取分层状态收集器。
     */
    public GuiRenderState getRenderState() {
        return renderState;
    }

    // ==================== 延迟元素 Flush ====================

    /**
     * 帧末 flush 延迟元素 — 对标 26.1.2 {@code GuiGraphics.extractDeferredElements()}。
     * <p>
     * 在 Screen 渲染末尾调用，统一驱动两条延迟路径：
     * <ol>
     *   <li><b>路径一（物品模型）</b>：按 {@link GuiRenderState} 树的 z-order 绘制所有收集的物品（内容层）。</li>
     * </ol>
     */
    public void extractDeferredElements() {
        // 路径二（PiP）：先绘制 3D 内容层（方块模型 / 实体 / oversized 物品），位于扁平物品之下
        for (PictureInPictureRenderState state : deferredPip) {
            dispatchPip(state);
        }

        // 路径一：绘制收集到的普通物品模型（内容层）
        renderState.forEachItem(this::renderDeferredItem);
    }

    /**
     * 按 PiP 状态的运行时类型查分派表并绘制。
     */
    @SuppressWarnings("unchecked")
    private void dispatchPip(PictureInPictureRenderState state) {
        PictureInPictureRenderer<PictureInPictureRenderState> renderer =
                (PictureInPictureRenderer<PictureInPictureRenderState>) pipDispatchTable.get(state.getClass());
        if (renderer != null) {
            renderer.prepare(state);
        }
    }

    /**
     * 帧开始时重置状态。
     */
    public void resetForNewFrame() {
        this.renderState.reset();
        this.deferredPip.clear();
    }
}
