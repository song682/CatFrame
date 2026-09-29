package decok.dfcdvadstf.catframe.ui.overlay;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.components.Renderable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.util.*;

/**
 * <p>
 * Overlay Manager — manages registration, positioning, auto-stacking, and
 * rendering
 * of screen overlays. Inspired by the {@code ToastManager} slot architecture.
 * </p>
 *
 * <h3>Auto-stacking</h3>
 * <p>
 * Multiple overlays at the same anchor are sorted by {@link Overlay#getStackPriority()},
 * auto-stacked along the Y axis. TOP anchors stack downwards, BOTTOM anchors stack upwards.
 * </p>
 * </p>
 */
public class OverlayManager {

    /** Singleton instance */
    public static final OverlayManager INSTANCE = new OverlayManager();

    /** Spacing between stacked overlays */
    private static final int STACK_SPACING = 2;

    private final Map<ScreenAnchor, List<Overlay>> overlays = new EnumMap<>(ScreenAnchor.class);

    private OverlayManager() {
        for (ScreenAnchor anchor : ScreenAnchor.values()) {
            overlays.put(anchor, new ArrayList<>());
        }
    }

    // ──── Registration ────

    /**
     * Register an overlay.
     */
    public void register(Overlay overlay) {
        if (overlay == null)
            return;
        requirePauseContextValid(overlay);
        List<Overlay> list = overlays.get(overlay.getAnchor());
        if (!list.contains(overlay)) {
            list.add(overlay);
            sortAnchorList(list);
        }
    }

    /**
     * Unregister an overlay.
     */
    public void unregister(Overlay overlay) {
        if (overlay == null)
            return;
        overlays.get(overlay.getAnchor()).remove(overlay);
    }

    /**
     * Remove all overlays.
     */
    public void clearAll() {
        for (List<Overlay> list : overlays.values()) {
            list.clear();
        }
    }

    /**
     * Remove all overlays at the given anchor.
     */
    public void clearAnchor(ScreenAnchor anchor) {
        overlays.get(anchor).clear();
    }

    // ──── Query ────

    /**
     * Get all registered overlays at the given anchor.
     */
    public List<Overlay> getOverlays(ScreenAnchor anchor) {
        return Collections.unmodifiableList(overlays.get(anchor));
    }

    /**
     * Get total count of all registered overlays.
     */
    public int getOverlayCount() {
        int count = 0;
        for (List<Overlay> list : overlays.values()) {
            count += list.size();
        }
        return count;
    }

    /**
     * Whether any registered, visible SCREEN-context overlay currently requests a
     * game pause.
     * Host screens can OR this into their {@code doesGuiPauseGame()} so an overlay
     * can pause the
     * single-player world just like the vanilla screen mechanism.
     */
    public boolean isPausingGame() {
        for (List<Overlay> list : overlays.values()) {
            for (Overlay overlay : list) {
                if (overlay.isVisible() && overlay.isPausingGame()
                        && overlay.getContext() != OverlayContext.HUD) {
                    return true;
                }
            }
        }
        return false;
    }

    // ──── Update ────

    /**
     * Update all visible overlays. Call once per tick.
     */
    public void updateAll() {
        for (List<Overlay> list : overlays.values()) {
            for (Overlay overlay : list) {
                if (overlay.isVisible()) {
                    overlay.update();
                }
            }
        }
    }

    // ──── Rendering ────

    /**
     * Render all visible SCREEN-context overlays. Call from the screen's drawScreen
     * method.
     *
     * @param mouseX       mouse X
     * @param mouseY       mouse Y
     * @param partialTicks partial tick
     */
    public void renderAll(int mouseX, int mouseY, float partialTicks) {
        renderTarget(false, mouseX, mouseY, partialTicks);
    }

    /**
     * Render all visible HUD-context overlays. Call from the in-game HUD render
     * path,
     * e.g. Forge's {@code RenderGameOverlayEvent.Post}.
     * <p>
     * HUD has no meaningful mouse coordinates, pass {@code (-1, -1)}.
     * </p>
     *
     * @param partialTicks partial tick
     */
    public void renderHud(float partialTicks) {
        renderTarget(true, -1, -1, partialTicks);

        // Restore GL state so overlay rendering never leaks tint/blend onto the vanilla
        // HUD.
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * Shared rendering routine for a given target (HUD vs screen). Resolves anchor
     * position,
     * applies auto-stacking, temporarily assigns coordinates, and renders each
     * matching overlay.
     */
    private void renderTarget(boolean hud, int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();
        boolean screenOpen = mc.currentScreen != null;

        for (Map.Entry<ScreenAnchor, List<Overlay>> entry : overlays.entrySet()) {
            ScreenAnchor anchor = entry.getKey();
            List<Overlay> list = entry.getValue();
            if (list.isEmpty())
                continue;

            int stackOffsetY = 0;
            for (Overlay overlay : list) {
                if (!overlay.isVisible() || !matchesTarget(overlay, hud, screenOpen))
                    continue;

                // A HUD overlay must never pause the world; pausing only makes sense on an open
                // GUI (SCREEN). Fail fast so this design contract can't be violated at runtime.
                if (hud && overlay.isPausingGame()) {
                    throw new IllegalStateException(
                            "Overlay " + overlay.getClass().getName()
                                    + " requests a game pause but is being rendered in the HUD context; "
                                    + "isPausingGame() is only valid for SCREEN overlays.");
                }

                int resolvedX = anchor.resolveX(screenWidth, overlay.getWidth(), overlay.getOffsetX());
                int resolvedY = anchor.resolveY(screenHeight, overlay.getHeight(), overlay.getOffsetY());

                // Apply stacking offset
                if (anchor.stacksDownward()) {
                    resolvedY += stackOffsetY;
                } else {
                    resolvedY -= stackOffsetY;
                }

                // Temporarily set position for rendering
                int oldX = overlay.getX();
                int oldY = overlay.getY();
                overlay.setX(resolvedX);
                overlay.setY(resolvedY);

                if (overlay instanceof Renderable) {
                    ((Renderable) overlay).extractRenderState(GuiGraphicsExtractor.getInstance(),
                            mouseX, mouseY, partialTicks);
                }

                // Restore original position
                overlay.setX(oldX);
                overlay.setY(oldY);

                // Accumulate stack offset
                stackOffsetY += overlay.getHeight() + STACK_SPACING;
            }
        }
    }

    /**
     * Whether the overlay should render for the given target.
     * <p>
     * A {@link OverlayContext#BOTH} overlay is rendered by exactly one pass per
     * frame:
     * while a screen is open, the screen pass ({@code renderAll}) owns it and the
     * HUD pass
     * skips it, so it is never drawn twice in the same frame.
     * </p>
     */
    private static boolean matchesTarget(Overlay overlay, boolean hud, boolean screenOpen) {
        OverlayContext ctx = overlay.getContext();
        if (hud) {
            return ctx == OverlayContext.HUD || (ctx == OverlayContext.BOTH && !screenOpen);
        }
        return ctx == OverlayContext.SCREEN || ctx == OverlayContext.BOTH;
    }

    // ──── Input forwarding ────

    /**
     * Forward mouse click to overlays. Returns true if a blocking overlay consumed
     * it.
     */
    public boolean handleMouseClick(int mouseX, int mouseY, int mouseButton) {
        for (List<Overlay> list : overlays.values()) {
            for (Overlay overlay : list) {
                if (overlay.isVisible() && overlay.isBlocking()
                        && overlay.isMouseOver(mouseX, mouseY)) {
                    overlay.mouseClicked(mouseX, mouseY, mouseButton);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Forward key press to overlays. Returns true if a blocking overlay consumed
     * it.
     */
    public boolean handleKeyPress(char typedChar, int keyCode) {
        for (List<Overlay> list : overlays.values()) {
            for (Overlay overlay : list) {
                if (overlay.isVisible() && overlay.isBlocking()) {
                    overlay.keyTyped(typedChar, keyCode);
                    return true;
                }
            }
        }
        return false;
    }

    // ──── Internal ────

    /**
     * Enforce that only SCREEN-context overlays may request a game pause. A HUD (or
     * BOTH) overlay
     * that returns {@code true} from {@link Overlay#isPausingGame()} is a
     * programming error.
     */
    private static void requirePauseContextValid(Overlay overlay) {
        if (overlay.isPausingGame() && overlay.getContext() != OverlayContext.SCREEN) {
            throw new IllegalStateException(
                    "Overlay " + overlay.getClass().getName()
                            + " requests a game pause but its context is " + overlay.getContext()
                            + "; isPausingGame() is only valid for SCREEN overlays.");
        }
    }

    private void sortAnchorList(List<Overlay> list) {
        Collections.sort(list, new Comparator<Overlay>() {
            @Override
            public int compare(Overlay a, Overlay b) {
                return Integer.compare(a.getStackPriority(), b.getStackPriority());
            }
        });
    }
}
