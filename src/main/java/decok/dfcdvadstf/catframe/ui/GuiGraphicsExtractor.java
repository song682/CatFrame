package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.core.component.DataComponents;
import decok.dfcdvadstf.catframe.core.component.predicates.ItemStackComponents;
import decok.dfcdvadstf.catframe.core.tooltip.ItemTooltipImages;
import decok.dfcdvadstf.catframe.core.tooltip.TooltipComponent;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.render.pipeline.QuadWriter;
import decok.dfcdvadstf.catframe.model.render.pipeline.RenderSubmit;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;
import decok.dfcdvadstf.catframe.ui.render.GuiRenderState;
import decok.dfcdvadstf.catframe.ui.render.pip.*;
import decok.dfcdvadstf.catframe.ui.screens.Screen;
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
 * This class draws items <b>immediately</b> at the call site via {@link IItemStateProvider#render},
 * since the GL modelview matrix is valid at the call point (no matrix snapshot needed)</li>
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
 * gui.item(stack, x, y); // Immediate: draws the model right at the call site
 * gui.extractDeferredElements(); // End of frame (drawScreen RETURN) flush PiP entities and tooltips
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
    public final GuiRenderState renderState;

    /** Delayed tooltip — corresponds to 26.1.2 {@code GuiGraphics.deferredTooltip} */
    @Nullable
    private Runnable deferredTooltip;
    @Nullable
    private Style hoveredTextStyle;
    @Nullable
    private Style clickableTextStyle;

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

    public int getHeight() {
        return mc.currentScreen.height;
    }

    public int getWidth() {
        return mc.currentScreen.width;
    }

    public GuiGraphicsExtractor() {
        this.mc = Minecraft.getMinecraft();
        this.renderState = new GuiRenderState();

        // Register PiP renderer dispatch table (construction-time constants, not cleared between frames)
        registerPip(new OversizedItemPipRenderer(itemDrawer));
        registerPip(new EntityPipRenderer());
        UiTextureAtlasManager textureAtlasManager = new UiTextureAtlasManager();

    }

    /** Register a PiP renderer using its handled state class as the dispatch key. */
    private void registerPip(PictureInPictureRenderer<?> renderer) {
        pipDispatchTable.put(renderer.getStateClass(), renderer);
    }

    /**
     * Get the global singleton.
     */
    public static GuiGraphicsExtractor getInstance() {
        return INSTANCE;
    }

    // ==================== Item rendering ====================

    /**
     * Render items in the GUI (without seed offset).
     */
    public void item(ItemStack stack) {
        item(stack, 0, 0);
    }

    /**
     * Renders an item in the GUI <b>immediately</b> at the call site.
     * <p>
     * Corresponds to 26.1.2 {@code GuiGraphics.item()}, but unlike the high-version which extracts
     * render state into {@code GuiItemRenderState} for deferred pipeline consumption, this method
     * draws the model directly using the current GL modelview matrix (which is valid at the call point,
     * set up by the caller for the slot position). No matrix snapshot is needed.
     * <p>
     * Only items registered with CatFrame models (via {@link ModelRegistry#hasItemModel}) go through
     * this path; other items are handled by the vanilla rendering pipeline.
     *
     * @param stack The item stack to render
     * @param x GUI slot X coordinate (pixels)
     * @param y GUI slot Y coordinate (pixels)
     */
    public void item(ItemStack stack, int x, int y) {
        if (stack == null || stack.getItem() == null) return;

        // Only items with registered CatFrame models go through this path;
        // everything else is handled by the vanilla rendering pipeline.
        if (!ModelRegistry.hasItemModel(stack.getItem())) return;

        // Immediate rendering: the GL modelview matrix is valid at the call site,
        // so we draw directly — no matrix snapshot needed (pose = null).
        drawItemModel(stack, null);
    }

    /**
     * GUI item model drawing core — used by the immediate {@link #item(ItemStack, int, int)} path
     * and the PiP oversized channel (via {@link ItemGuiDrawer}).
     * <ol>
     *   <li>{@code glPushAttrib} saves GL state + sets up item rendering environment</li>
     *   <li>If {@code pose} is non-null, restores the modelview matrix from the snapshot
     *       (needed for deferred PiP rendering where the GL context has changed since capture)</li>
     *   <li>Delegates to {@link IItemStateProvider#render} for model rendering
     *       (enchantment glint is driven by the pipeline's {@code FeatureRenderDispatcher}
     *       calling {@link #renderEnchantmentGlint(RenderSubmit, Tessellator)})</li>
     * </ol>
     *
     * @param stack the item stack to render
     * @param pose  modelview matrix snapshot for deferred PiP rendering, or {@code null} for immediate rendering
     */
    private void drawItemModel(ItemStack stack, @Nullable float[] pose) {
        if (stack == null || stack.getItem() == null) return;

        IItemStateProvider model = ModelRegistry.getRegisteredItemModel(stack.getItem());

        // Save full GL state — no need to manually restore individual bits
        GL11.glPushAttrib(GL_SAVE_MASK);
        setupItemRenderState();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            // Restore modelview matrix from snapshot (deferred PiP path only;
            // immediate path passes null — the caller's matrix is already active)
            if (pose != null) {
                MATRIX_BUFFER.clear();
                MATRIX_BUFFER.put(pose);
                MATRIX_BUFFER.flip();
                GL11.glLoadMatrix(MATRIX_BUFFER);
            }

            // No pre-transform cancellation needed in GUI context
            // (Forge INVENTORY path has no Forge pre-transform)
            // Enchantment glint is driven by FeatureRenderDispatcher → renderEnchantmentGlint
            model.render(stack, RenderPhase.ITEM_GUI, null);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    /**
     * {@link ItemGuiDrawer} internal implementation — delegates to {@link #drawItemModel},
     * allowing the PiP package's {@link OversizedItemPipRenderer} to reuse item rendering logic
     * without leaking {@code draw()} into this class's public API.
     */
    private final class ItemGuiDrawerImpl implements ItemGuiDrawer {
        @Override
        public void draw(ItemStack stack, @Nullable float[] pose, boolean allowOversized) {
            drawItemModel(stack, pose);
        }
    }

    /**
     * Capture current modelview matrix — for end-of-frame deferred rendering to restore
     * the GL transform at the call site.
     */
    private static float[] captureModelViewMatrix() {
        MATRIX_BUFFER.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX_BUFFER);
        float[] m = new float[16];
        MATRIX_BUFFER.get(m);
        return m;
    }

    // ==================== PiP submission (GUI entities) ====================

    /**
     * Render an entity in GUI (<b>deferred to end of frame</b>, via dedicated PiP channel).
     *
     * @param entity entity to render
     * @param scale  scale factor
     * @param lookX  entity look horizontal offset (mirrors {@code GuiInventory.drawEntityOnScreen} mouseX)
     * @param lookY  entity look vertical offset
     * @param x      GUI slot X coordinate (pixels)
     * @param y      GUI slot Y coordinate (pixels)
     */
    public void entity(EntityLivingBase entity, int scale, float lookX, float lookY, int x, int y) {
        if (entity == null) return;
        float[] pose = captureModelViewMatrix();
        deferredPip.add(new GuiEntityRenderState(
                entity, scale, lookX, lookY, pose, new ScreenRectangle(x, y, 16, 16), null));
    }

    // ==================== GL state management ====================

    /**
     * Set up GL state required for item rendering.
     * <p>
     * Mirrors 26.1.2 {@code RenderPipelines.GUI_ITEM} implicit GL state management.
     * Depth test stays enabled so model writes to depth buffer for
     * {@link #renderEnchantmentGlint(RenderSubmit, Tessellator)}'s
     * {@code GL_EQUAL} depth test to precisely overlay glint.
     * Outer {@code glPushAttrib/glPopAttrib} means no need to manually save original state here.
     */
    private void setupItemRenderState() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    // ==================== Enchantment glint (single pass, driven per submit by pipeline) ====================

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
     * (like BLOCK_WORLD) have no foil semantics.
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
     * Render enchantment glint: replay the textured quad geometry of the submitted item (solidColor side quads
     * are skipped, keeping glint inside the opaque texel contour), and draw with the glint texture using two
     * layers of scrolling texture matrices stacked.
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
// ENABLE_BIT: depth/blend/lighting/alpha test bits; DEPTH_BUFFER_BIT: depthFunc + depthMask;
            // COLOR_BUFFER_BIT: blendFunc + alphaFunc; TEXTURE_BIT: texture binding; CURRENT_BIT: current color
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT);
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(ENCHANTMENT_GLINT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

            // GL_EQUAL: only overlay on model fragments written in normal pass, glint precisely fits model contour
            GL11.glDepthFunc(GL11.GL_EQUAL);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            // Additive blend — matches original ItemRenderer held glint (OpenGlHelper.glBlendFunc(768, 1, ...))
            GL11.glBlendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);

            // Two-layer scroll animation — periods / rotation aligned with 1.7.10 ItemRenderer held glint;
            // Scroll amplitude see GLINT_SCROLL_AMPLITUDE (1 texture unit per cycle, matches vanilla flow speed)
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
                && phase != RenderPhase.BLOCK_WORLD;
    }

    // ==================== State access ====================

    /**
     * Gets the layered state collector.
     */
    public GuiRenderState getRenderState() {
        return renderState;
    }

    // ==================== Deferred Tooltip (mirrors 26.1.2 GuiGraphics.setTooltipForNextFrame) ====================

    /**
     * Set a simple text tooltip.
     */
    public void setTooltipForNextFrame(String text, int x, int y) {
        List<String> lines = new ArrayList<>();
        lines.add(text);
        setTooltipForNextFrame(lines, x, y);
    }

    /**
     * Set a multi-line text tooltip (using default positioner).
     */
    public void setTooltipForNextFrame(List<String> lines, int x, int y) {
        setTooltipForNextFrame(mc.fontRenderer, lines, DefaultTooltipPositioner.INSTANCE, x, y, false);
    }

    /**
     * Set a multi-line text tooltip (specifying positioner).
     */
    public void setTooltipForNextFrame(FontRenderer font, List<String> lines,
                                       ClientTooltipPositioner positioner,
                                       int xo, int yo, boolean replaceExisting) {
        setTooltipForNextFrame(font, lines, Optional.empty(), positioner, xo, yo, replaceExisting, null);
    }

    /**
     * Full-parameter tooltip setup.
     * <p>Mirrors 26.1.2 {@code setTooltipForNextFrame(Font, List, Optional, ClientTooltipPositioner, int, int, boolean, Identifier)}.</p>
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
        // Structured component inserted at line 2 position (line 1 if no text lines) — mirrors 26.1.2
        component.ifPresent(image -> components.add(components.isEmpty() ? 0 : 1, ClientTooltipComponent.create(image)));
        setTooltipForNextFrameInternal(font, components, xo, yo, positioner, style, replaceExisting);
    }

    /**
     * Set a multi-line text tooltip (with structured component, using default positioner).
     * <p>Mirrors 26.1.2 {@code setTooltipForNextFrame(Font, List, Optional, int, int)}.</p>
     */
    public void setTooltipForNextFrame(FontRenderer font, List<String> lines,
                                       Optional<TooltipComponent> component,
                                       int xo, int yo) {
        setTooltipForNextFrame(font, lines, component, xo, yo, null);
    }

    /**
     * Set a multi-line text tooltip (with structured component and style, using default positioner).
     * <p>Mirrors 26.1.2 {@code setTooltipForNextFrame(Font, List, Optional, int, int, Identifier)}.</p>
     */
    public void setTooltipForNextFrame(FontRenderer font, List<String> lines,
                                       Optional<TooltipComponent> component,
                                       int xo, int yo, @Nullable ResourceLocation style) {
        List<ClientTooltipComponent> components = new ArrayList<>();
        for (String line : lines) {
            components.add(ClientTooltipComponent.create(line));
        }
        // Structured component inserted at line 2 position (line 1 if no text lines) — mirrors 26.1.2
        component.ifPresent(image -> components.add(components.isEmpty() ? 0 : 1, ClientTooltipComponent.create(image)));
        setTooltipForNextFrameInternal(font, components, xo, yo, DefaultTooltipPositioner.INSTANCE, style, false);
    }

    /**
     * Set an item tooltip (using default positioner).
     * <p>Mirrors 26.1.2 {@code setTooltipForNextFrame(Font, ItemStack, int, int)}:
     * Text from {@link Screen#getTooltipFromItem}, image components from
     * {@link ItemTooltipImages}, style from {@link DataComponents#TOOLTIP_STYLE}.</p>
     */
    public void setTooltipForNextFrame(FontRenderer font, ItemStack stack, int xo, int yo) {
        String styleId = ItemStackComponents.get(stack).get(DataComponents.TOOLTIP_STYLE);
        setTooltipForNextFrame(font, Screen.getTooltipFromItem(this.mc, stack), ItemTooltipImages.get(stack),
                xo, yo, styleId != null ? new ResourceLocation(styleId) : null);
    }

    /**
     * Internal unified entry — mirrors 26.1.2 {@code setTooltipForNextFrameInternal()}.
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
     * Actually render tooltip — mirrors 26.1.2 {@code GuiGraphics.tooltip()}.
     * <p>
     * Calculate size → position → render background → render text → render images.
     */
    public void tooltip(
            FontRenderer font,
            List<ClientTooltipComponent> lines,
            int xo, int yo,
            ClientTooltipPositioner positioner,
            @Nullable ResourceLocation style
    ) {
        if (lines.isEmpty()) return;

        // Calculate tooltip size (same algorithm as 26.1.2)
        int textWidth = 0;
        int tempHeight = lines.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent line : lines) {
            int lineWidth = line.getWidth(font);
            if (lineWidth > textWidth) textWidth = lineWidth;
            tempHeight += line.getHeight(font);
        }

        int w = textWidth;
        int h = tempHeight;

        // Get screen dimensions
        int screenWidth, screenHeight;
        if (mc.currentScreen != null) {
            screenWidth = mc.currentScreen.width;
            screenHeight = mc.currentScreen.height;
        } else {
            ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            screenWidth = res.getScaledWidth();
            screenHeight = res.getScaledHeight();
        }

        // Position
        int[] pos = positioner.positionTooltip(screenWidth, screenHeight, xo, yo, w, h);
        int x = pos[0];
        int y = pos[1];

        // Save OpenGL state and render
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Render background (with style support)
        TooltipRenderUtil.renderTooltipBackground(x, y, textWidth, tempHeight, style);

        // Render text lines
        int localY = y;
        for (int i = 0; i < lines.size(); i++) {
            ClientTooltipComponent line = lines.get(i);
            line.renderText(font, x, localY);
            localY += line.getHeight(font) + (i == 0 ? 2 : 0);
        }

        // Render image components
        localY = y;
        for (int i = 0; i < lines.size(); i++) {
            ClientTooltipComponent line = lines.get(i);
            line.renderImage(font, x, localY, w, h);
            localY += line.getHeight(font) + (i == 0 ? 2 : 0);
        }

        GL11.glPopAttrib();
    }

    // ==================== Deferred elements flush ====================

    /**
     * Flush deferred elements at end of frame — corresponds to 26.1.2
     * {@code GuiGraphics.extractDeferredElements()}.
     * <p>
     * Called at the end of screen rendering, drives the remaining deferred paths:
     * <ol>
     *   <li><b>PiP</b>: renders 3D content (entities) that require deferred drawing.</li>
     *   <li><b>Tooltip</b>: rendered last in a new stratum, ensuring it's always on top.</li>
     * </ol>
     * Note: item models are rendered immediately by {@link #item(ItemStack, int, int)},
     * so no item flush is needed here.
     */
    public void extractDeferredElements() {
        // PiP: render 3D content (entities) that still requires deferred drawing
        for (PictureInPictureRenderState state : deferredPip) {
            dispatchPip(state);
        }

        // Tooltip: always on top — rendered last in a new stratum
        if (this.deferredTooltip != null) {
            this.renderState.nextStratum();
            this.deferredTooltip.run();
            this.deferredTooltip = null;
        }
    }

    /**
     * Dispatch to PiP renderer based on runtime type of state.
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
     * Reset state at frame start.
     */
    public void resetForNewFrame() {
        this.renderState.reset();
        this.deferredPip.clear();
        this.deferredTooltip = null;
    }
}
