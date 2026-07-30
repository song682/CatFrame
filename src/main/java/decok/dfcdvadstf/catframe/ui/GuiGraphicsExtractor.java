package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.core.component.DataComponents;
import decok.dfcdvadstf.catframe.core.component.predicates.ItemStackComponents;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.pipeline.QuadWriter;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderSubmit;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import decok.dfcdvadstf.catframe.ui.render.GuiRenderState;
import decok.dfcdvadstf.catframe.ui.render.GuiRenderState.ItemRenderState;
import decok.dfcdvadstf.catframe.ui.render.pip.*;
import decok.dfcdvadstf.catframe.ui.tooltip.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
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
 *       由本类 {@link #renderEnchantmentGlint(RenderSubmit, Tessellator)} 实现，
 *       管线 {@code FeatureRenderDispatcher} 逐提交项驱动，覆盖全部物品阶段</li>
 *   <li>26.1.2 的 {@code GuiRenderState} 分层收集器 →
 *       独立的 {@link GuiRenderState} 完整实现</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * GuiGraphicsExtractor gui = GuiGraphicsExtractor.getInstance();
 * gui.resetForNewFrame();          // 帧开始（drawScreen HEAD）
 * gui.item(stack, x, y);           // 提取：快照矩阵 + 收集状态（不立即绘制）
 * gui.extractDeferredElements();   // 帧末（drawScreen RETURN）统一 flush 物品 + tooltip
 * }</pre>
 */
public class GuiGraphicsExtractor {

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

    /** 延迟 tooltip — 对标 26.1.2 {@code GuiGraphics.deferredTooltip} */
    @Nullable
    private Runnable deferredTooltip;

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

        // 仅对显式注册了 CatFrame 模型的物品走延迟渲染路径，其余物品由原版管线处理。
        if (!ModelRegistry.hasItemModel(stack.getItem())) return;

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
     *   <li>委托 {@link IItemStateProvider#render} 渲染模型（附魔光效由管线驱动
     *       {@link #renderEnchantmentGlint(RenderSubmit, Tessellator)} 统一叠加）</li>
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
     *   <li>委托 {@link IItemStateProvider#render} 渲染模型（附魔光效由管线驱动
     *       {@link #renderEnchantmentGlint(RenderSubmit, Tessellator)} 统一叠加）</li>
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
            // 附魔光效由管线 FeatureRenderDispatcher 驱动 renderEnchantmentGlint 统一叠加
            model.render(stack, RenderPhase.ITEM_GUI, null);
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
     * 深度测试保持启用，确保模型写入深度缓冲供
     * {@link #renderEnchantmentGlint(RenderSubmit, Tessellator)} 的
     * {@code GL_EQUAL} 深度测试精确叠加光效。
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

    // ==================== 附魔光效（统一 pass，由管线逐提交项驱动） ====================

    /** 附魔光纹纹理 — 与原版 {@code RenderItem.RES_ITEM_GLINT} 一致 */
    private static final ResourceLocation ENCHANTMENT_GLINT =
            new ResourceLocation("textures/misc/enchanted_item_glint.png");

    /**
     * 纹理矩阵 UV 缩放 — 对齐 26.1.2 {@code GlintTexturingStateShard(8.0F)}。
     * 烘焙 UV 为图集坐标（单 sprite 跨度约 0.03），放大 8 倍后流纹跨度
     * 与原版 [0,1] UV × 0.125 的视觉密度相当。
     */
    private static final float GLINT_UV_SCALE = 8.0F;

    /**
     * 单周期流纹滚动幅度（scale 前坐标系）。
     * <p>
     * 纹理矩阵先 {@code scale(GLINT_UV_SCALE)} 再 {@code translate(scroll)}，
     * 平移量会被 scale 放大 {@code GLINT_UV_SCALE} 倍；取 {@code 1/GLINT_UV_SCALE}
     * 使每周期的有效滚动恰为 1 个纹理单位（周期末尾无缝回环），
     * 与原版 1.7.10 {@code ItemRenderer}（scale 0.125 × scroll 8.0 = 1.0/周期）流速一致。
     * 此前直接沿用幅度 8.0 叠加 scale 8 放大，实际流速是原版的 64 倍（闪动过快）。
     * Effective scroll per cycle = GLINT_UV_SCALE × amplitude = 1.0 texture unit,
     * matching vanilla glint flow speed (the old 8.0 amplitude ran 64× too fast).
     */
    private static final float GLINT_SCROLL_AMPLITUDE = 1.0F / GLINT_UV_SCALE;

    /**
     * 判断提交项是否需要叠加附魔光效。
     * <p>
     * 仅物品阶段（GUI / 手持 / 掉落 / 展示框）适用；方块阶段
     * （BLOCK_WORLD / BLOCK_GUI）无 foil 语义。
     */
    public static boolean glintApplicable(RenderSubmit s) {
        return isItemGlintPhase(s.phase) && s.stack != null && hasFoil(s.stack);
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
     * 背景：Forge 在 {@code RenderItem.renderItemAndEffectIntoGUI} 中将原版的
     * GUI glint 补画禁用（{@code if (false && hasEffect())}，注释
     * "modders must handle themselves"），且掉落物 / 手持的自定义
     * {@code IItemRenderer} 分支同样绕开原版 glint pass。因此凡是被
     * CatFrame 接管渲染的物品，附魔光效必须由本方法自行绘制。
     * <p>
     * 实现对齐两个原版参照：
     * <ul>
     *   <li><b>1.7.10 {@code ItemRenderer} 手持光效</b>：重绘同一份几何 +
     *       {@code GL_TEXTURE} 矩阵两层滚动动画（周期 3000ms / 4873ms、旋转 -50°/+10°）、
     *       {@code glDepthFunc(GL_EQUAL)} 精确叠加在已写入深度的模型片段上、
     *       {@code glBlendFunc(GL_SRC_COLOR, GL_ONE)} 加色混合；</li>
     *   <li><b>26.1.2 {@code GlintTexturingStateShard}</b>：glint 采样直接使用网格的
     *       图集 UV，靠纹理矩阵放大（scale 8.0）取得合适的流纹跨度。</li>
     * </ul>
     * <p>
     * 调用时机：{@code FeatureRenderDispatcher} 逐提交项绘制完正常 pass 之后、
     * {@code applyAfterPart()} 之前 —— 此时扩展链的 beforePart 状态（display 矩阵等）
     * 仍然有效，{@link QuadWriter#writeGlintQuads} 重放几何可以得到与正常 pass
     * 完全一致的顶点坐标，从而通过 {@code GL_EQUAL} 深度测试。
     * <p>
     * GL 状态经 {@code glPushAttrib} 全量保护（含纹理绑定），
     * 调用方无需在本方法返回后重新绑定图集。
     *
     * @param s 渲染提交项（须先通过 {@link #glintApplicable} 检查）
     * @param t 共享 Tessellator 实例
     */
    public static void renderEnchantmentGlint(RenderSubmit s, Tessellator t) {
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

            // 两层滚动动画 — 周期 / 旋转对齐 1.7.10 ItemRenderer 手持光效；
            // 滚动幅度见 GLINT_SCROLL_AMPLITUDE（每周期有效滚动 1 纹理单位，与原版流速一致）
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            for (int pass = 0; pass < 2; pass++) {
                GL11.glPushMatrix();
                GL11.glScalef(GLINT_UV_SCALE, GLINT_UV_SCALE, GLINT_UV_SCALE);
                if (pass == 0) {
                    float scroll = (float) (Minecraft.getSystemTime() % 3000L) / 3000.0F
                            * GLINT_SCROLL_AMPLITUDE;
                    GL11.glTranslatef(scroll, 0.0F, 0.0F);
                    GL11.glRotatef(-50.0F, 0.0F, 0.0F, 1.0F);
                } else {
                    float scroll = (float) (Minecraft.getSystemTime() % 4873L) / 4873.0F
                            * GLINT_SCROLL_AMPLITUDE;
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
    private static boolean isItemGlintPhase(RenderPhase phase) {
        return phase != null
                && phase != RenderPhase.BLOCK_WORLD
                && phase != RenderPhase.BLOCK_GUI;
    }

    // ==================== 状态访问 ====================

    /**
     * 获取分层状态收集器。
     */
    public GuiRenderState getRenderState() {
        return renderState;
    }

    // ==================== 延迟 Tooltip（对标 26.1.2 GuiGraphics.setTooltipForNextFrame） ====================

    /**
     * 设置简单文本 tooltip。
     */
    public void setTooltipForNextFrame(String text, int x, int y) {
        List<String> lines = new ArrayList<>();
        lines.add(text);
        setTooltipForNextFrame(lines, x, y);
    }

    /**
     * 设置多行文本 tooltip（使用默认定位器）。
     */
    public void setTooltipForNextFrame(List<String> lines, int x, int y) {
        setTooltipForNextFrame(mc.fontRenderer, lines, DefaultTooltipPositioner.INSTANCE, x, y, false);
    }

    /**
     * 设置多行文本 tooltip（指定定位器）。
     */
    public void setTooltipForNextFrame(FontRenderer font, List<String> lines,
                                       ClientTooltipPositioner positioner,
                                       int xo, int yo, boolean replaceExisting) {
        setTooltipForNextFrame(font, lines, Optional.empty(), positioner, xo, yo, replaceExisting, null);
    }

    /**
     * 完整参数版 tooltip 设置。
     * <p>对标 26.1.2 {@code setTooltipForNextFrame(Font, List, Optional, ClientTooltipPositioner, int, int, boolean, Identifier)}。</p>
     */
    public void setTooltipForNextFrame(
            FontRenderer font,
            List<String> lines,
            Optional<TooltipComponent> component,
            ClientTooltipPositioner positioner,
            int xo, int yo,
            boolean replaceExisting,
            @Nullable ResourceLocation style
    ) {
        List<ClientTooltipComponent> components = new ArrayList<>();
        for (String line : lines) {
            components.add(ClientTooltipComponent.create(line));
        }
        // TODO: 当 component 非空时，插入到第二行位置（如 BundleTooltip）
        setTooltipForNextFrameInternal(font, components, xo, yo, positioner, style, replaceExisting);
    }

    /**
     * 内部统一入口 — 对标 26.1.2 {@code setTooltipForNextFrameInternal()}。
     */
    private void setTooltipForNextFrameInternal(
            FontRenderer font,
            List<ClientTooltipComponent> components,
            int xo, int yo,
            ClientTooltipPositioner positioner,
            @Nullable ResourceLocation style,
            boolean replaceExisting
    ) {
        if (!components.isEmpty()) {
            if (this.deferredTooltip == null || replaceExisting) {
                this.deferredTooltip = () -> this.tooltip(font, components, xo, yo, positioner, style);
            }
        }
    }

    /**
     * 实际渲染 tooltip — 对标 26.1.2 {@code GuiGraphics.tooltip()}。
     * <p>
     * 计算尺寸 → 定位 → 渲染背景 → 渲染文字 → 渲染图像。
     */
    public void tooltip(
            FontRenderer font,
            List<ClientTooltipComponent> lines,
            int xo, int yo,
            ClientTooltipPositioner positioner,
            @Nullable ResourceLocation style
    ) {
        if (lines.isEmpty()) return;

        // 计算 tooltip 尺寸（对标 26.1.2 同算法）
        int textWidth = 0;
        int tempHeight = lines.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent line : lines) {
            int lineWidth = line.getWidth(font);
            if (lineWidth > textWidth) textWidth = lineWidth;
            tempHeight += line.getHeight(font);
        }

        int w = textWidth;
        int h = tempHeight;

        // 获取屏幕尺寸
        int screenWidth, screenHeight;
        if (mc.currentScreen != null) {
            screenWidth = mc.currentScreen.width;
            screenHeight = mc.currentScreen.height;
        } else {
            ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            screenWidth = res.getScaledWidth();
            screenHeight = res.getScaledHeight();
        }

        // 定位
        int[] pos = positioner.positionTooltip(screenWidth, screenHeight, xo, yo, w, h);
        int x = pos[0];
        int y = pos[1];

        // 保存 OpenGL 状态并渲染
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 渲染背景（带 style 支持）
        TooltipRenderUtil.renderTooltipBackground(x, y, textWidth, tempHeight, style);

        // 渲染文字行
        int localY = y;
        for (int i = 0; i < lines.size(); i++) {
            ClientTooltipComponent line = lines.get(i);
            line.renderText(font, x, localY);
            localY += line.getHeight(font) + (i == 0 ? 2 : 0);
        }

        // 渲染图像组件
        localY = y;
        for (int i = 0; i < lines.size(); i++) {
            ClientTooltipComponent line = lines.get(i);
            line.renderImage(font, x, localY, w, h);
            localY += line.getHeight(font) + (i == 0 ? 2 : 0);
        }

        GL11.glPopAttrib();
    }

    // ==================== 延迟元素 Flush ====================

    /**
     * 帧末 flush 延迟元素 — 对标 26.1.2 {@code GuiGraphics.extractDeferredElements()}。
     * <p>
     * 在 Screen 渲染末尾调用，统一驱动两条延迟路径：
     * <ol>
     *   <li><b>路径一（物品模型）</b>：按 {@link GuiRenderState} 树的 z-order 绘制所有收集的物品（内容层）。</li>
     *   <li><b>路径三（tooltip）</b>：新建 stratum 后绘制，确保 tooltip 始终在最上层。</li>
     * </ol>
     */
    public void extractDeferredElements() {
        // 路径二（PiP）：先绘制 3D 内容层（方块模型 / 实体 / oversized 物品），位于扁平物品之下
        for (PictureInPictureRenderState state : deferredPip) {
            dispatchPip(state);
        }

        // 路径一：绘制收集到的普通物品模型（位于 tooltip 之下的内容层）
        renderState.forEachItem(this::renderDeferredItem);

        // 路径三：tooltip 始终在最上层
        if (this.deferredTooltip != null) {
            this.renderState.nextStratum();
            this.deferredTooltip.run();
            this.deferredTooltip = null;
        }
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
        this.deferredTooltip = null;
    }
}
