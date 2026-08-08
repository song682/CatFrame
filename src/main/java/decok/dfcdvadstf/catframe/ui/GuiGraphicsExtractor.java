package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.core.component.DataComponents;
import decok.dfcdvadstf.catframe.core.component.predicates.ItemStackComponents;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.pipeline.QuadWriter;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderSubmit;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import decok.dfcdvadstf.catframe.ui.render.GuiRenderState;
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
 * GUI Rendering Context Manager — Corresponding to 26.1.2 {@code GuiGraphics}.
 * <p>
 * Responsibilities: Manage the GL state needed for item rendering in GUI contexts (inventory, container screens, etc.),
 * encapsulate explicit settings and restorations of {@code depth / lighting / alpha test / blend},
 * keep {@code UniformRenderPipeline.renderItemQuads} focused purely on quad processing,
 * and avoid relying on any GL environment preset by the caller.
 *
 * <h3>Relationship with 26.1.2</h3>
 * <ul>
 * <li>26.1.2's {@code RenderPipelines.GUI_ITEM} implicitly manages all GL state →
 * This class explicitly manages GL in 1.7.10 immediate mode</li>
 * <li>26.1.2's {@code GuiGraphics.item()} extracts render state via {@code GuiItemRenderState} →
 * This class delegates to {@link IItemStateProvider#render} and submits rendering records to {@link GuiRenderState}</li>
 * <li>26.1.2's enchantment glint handled by the render pipeline's glint shader →
 * Implemented by this class with {@link #renderEnchantmentGlint(RenderSubmit, Tessellator)},
 * with the pipeline's {@code FeatureRenderDispatcher} driving per submitted item, covering all item phases</li>
 * <li>26.1.2's {@code GuiRenderState} layered collector →
 * Fully implemented with a standalone {@link GuiRenderState}</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * GuiGraphicsExtractor gui = GuiGraphicsExtractor.getInstance();
 * gui.resetForNewFrame(); // Start of frame (drawScreen HEAD)
 * gui.item(stack, x, y); // Extract: snapshot matrix, collect state (doesn’t draw immediately)
 * gui.extractDeferredElements(); // End of frame (drawScreen RETURN) flush all items and tooltips
 * }</pre>
 */
public class GuiGraphicsExtractor {

    /**
     * GL state save mask: overwrites the enable bits, texture bindings, and current color.
     * Does not use GL_COLOR_BUFFER_BIT — avoids saving/restoring the framebuffer color contents.
     */
    private static final int GL_SAVE_MASK =
            GL11.GL_ENABLE_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT;

    /** Global singleton — 1.7.10 doesn't have a per-frame GuiGraphics creation mechanism, using a singleton instead */
    private static final GuiGraphicsExtractor INSTANCE = new GuiGraphicsExtractor();

    /**
     * modelview matrix snapshot/restore buffer (reused by single-threaded client).
     * <p>During capture, {@code glGetFloat} writes are immediately copied into {@code float[16]}, and during flush, they are filled back for {@code glLoadMatrix}.</p>
     */
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    private final Minecraft mc;

    /** Complete layered state collector (aligned with 26.1.2 GuiRenderState) */
    private final GuiRenderState renderState;

    /** Delayed tooltip — corresponds to 26.1.2 {@code GuiGraphics.deferredTooltip} */
    @Nullable
    private Runnable deferredTooltip;

    /**
     * In-frame PiP (Picture-in-Picture) submission queue — corresponds to the PiP state collected by 26.1.2 {@code GuiRenderState}.
     * <p>3D block models / GUI entities / oversized items are queued during the collection phase and dispatched for rendering by type at the end of the frame flush.</p>
     */
    private final List<PictureInPictureRenderState> deferredPip = new ArrayList<>();

    /** PiP dispatch table: runtime type of state → corresponding renderer — corresponds to the 26.1.2 {@code PictureInPictureRenderer} registry. */
    private final Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pipDispatchTable =
            new HashMap<>();

    /** GUI item drawing callback implementation — allows {@link OversizedItemPipRenderer} to reuse this class's item rendering logic. */
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
     * Get the global singleton.
     */
    public static GuiGraphicsExtractor getInstance() {
        return INSTANCE;
    }

    // ==================== 物品渲染 ====================

    /**
     * Render items in the GUI (without seed offset).
     */
    public void item(ItemStack stack) {
        item(stack, 0, 0);
    }

    /**
     * Renders an item in the GUI (<b>deferred to the end of the frame</b>).
     * <p>
     * Corresponds to LaterRenderer.md path 1 / 26.1.2 {@code GuiGraphics.item()}: <b>doesn't draw during the extraction phase</b>,
     * it just snapshots the current modelview matrix and collects the render state into the {@link GuiRenderState} tree,
     * and the actual GL drawing is deferred to {@link #extractDeferredElements()}.
     * <p>
     * Unlike {@code RenderCommandBuffers} which <b>defers within a scope</b> (begin→submit→endScope flushes in the same GL
     * context, no matrix snapshot needed), flushing at the <b>end of frame</b> happens after {@code drawScreen} returns,
     * at which point the GL context has switched, so the modelview matrix must be snapshotted here and restored with {@code glLoadMatrix} at the end of the frame.
     *
     * @param stack The item stack to render
     * @param x GUI slot X coordinate (pixels, only for layering/tracking)
     * @param y GUI slot Y coordinate (pixels, only for layering/tracking)
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

    /** Enchantment glint texture — consistent with the original {@code RenderItem.RES_ITEM_GLINT} */
    private static final ResourceLocation ENCHANTMENT_GLINT =
            new ResourceLocation("textures/misc/enchanted_item_glint.png");

    /**
     * Texture matrix UV scaling — aligns with 26.1.2 {@code GlintTexturingStateShard(8.0F)}.
     * Baked UVs are atlas coordinates (single sprite span ~0.03), flow pattern span is multiplied by 8
     * Equivalent in visual density to the original [0,1] UV × 0.125.
     */
    private static final float GLINT_UV_SCALE = 8.0F;

    /**
     * Single-cycle glint scroll amplitude (coordinate system before scale).
     * <p>
     * The texture matrix first {@code scale(GLINT_UV_SCALE)} then {@code translate(scroll)},
     * so the translation gets amplified by {@code GLINT_UV_SCALE}; we use {@code 1/GLINT_UV_SCALE}
     * to ensure each cycle scrolls exactly 1 texture unit (seamless loop at the end of the cycle),
     * matching the flow speed of the original 1.7.10 {@code ItemRenderer} (scale 0.125 × scroll 8.0 = 1.0/cycle).
     * Previously, just using amplitude 8.0 with scale 8 ended up making the glint 64× faster than intended (way too flickery).
     * Effective scroll per cycle = GLINT_UV_SCALE × amplitude = 1.0 texture unit,
     * matching vanilla glint flow speed (the old 8.0 amplitude ran 64× too fast).
     */
    private static final float GLINT_SCROLL_AMPLITUDE = 1.0F / GLINT_UV_SCALE;

    /**
     * Determine whether the submitted item needs to stack the enchantment glow effect.
     * <p>
     * Only applicable to item phases (GUI / hand-held / drop / display frame); block phases
     * (BLOCK_WORLD / BLOCK_GUI) have no foil semantics.
     */
    public static boolean glintApplicable(RenderSubmit s) {
        return isItemGlintPhase(s.phase) && s.stack != null && hasFoil(s.stack);
    }

    /**
     * Determines whether an item should display the enchantment glint — corresponds to 26.1.2 {@code ItemStack.hasFoil()}.
     * <p>
     * Priority:
     * <ol>
     * <li>If it has the {@link DataComponents#ENCHANTMENT_GLINT} component, toggle according to the component value</li>
     * <li>Otherwise, use {@link ItemStack#hasEffect(int)} (default implementation is {@code isItemEnchanted()},
     * but items like golden apples override it as {@code damage > 0})</li>
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
     * Render enchantment glint: replay all quad geometry of the submitted item (including solidColor sides),
     * and draw with the glint texture using two layers of scrolling texture matrices stacked.
     * <p>
     * Background: Forge disables the original GUI glint in {@code RenderItem.renderItemAndEffectIntoGUI}
     * ({@code if (false && hasEffect()}), with the comment "modders must handle themselves"),
     * and custom {@code IItemRenderer} branches for dropped or held items also skip the original glint pass.
     * So for any items whose rendering is taken over by CatFrame, the enchantment glint must be drawn by this method.
     * <p>
     * Implementation aligns with two vanilla references:
     * <ul>
     * <li><b>1.7.10 {@code ItemRenderer} held item glint</b>: redraw the same geometry,
     * {@code GL_TEXTURE} matrix two-layer scrolling animation (periods 3000ms / 4873ms, rotation -50°/10°),
     * {@code glDepthFunc(GL_EQUAL)} to overlay precisely on model fragments with written depth,
     * {@code glBlendFunc(GL_SRC_COLOR, GL_ONE)} additive blending;</li>
     * <li><b>26.1.2 {@code GlintTexturingStateShard}</b>: glint sampling directly uses the mesh atlas UV,
     * enlarged via texture matrix (scale 8.0) to get the right streak span.</li>
     * </ul>
     * <p>
     * Call timing: after {@code FeatureRenderDispatcher} has drawn the normal pass for each submitted item,
     * but before {@code applyAfterPart()} — at this point, the beforePart state of the extension chain (like display matrix) is still valid,
     * and {@link QuadWriter#writeGlintQuads} replaying geometry can get vertex coordinates identical to the normal pass,
     * allowing {@code GL_EQUAL} depth testing.
     * <p>
     * GL state is fully protected via {@code glPushAttrib} (including texture bindings),
     * so the caller doesn’t need to rebind the atlas after this method returns.
     *
     * @param s The submitted item to render (must be checked via {@link #glintApplicable} first)
     * @param t Shared Tessellator instance
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

    /** Whether it is the rendering phase of an item holding an ItemStack (GUI / Handheld / Dropped / Display Frame). */
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
