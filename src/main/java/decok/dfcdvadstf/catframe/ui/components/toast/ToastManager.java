package decok.dfcdvadstf.catframe.ui.components.toast;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.*;

/**
 * <p>
 * Toast manager — manages Toast queuing, display, animation, and rendering.
 * Modelled on the modern ToastManager design.
 * </p>
 */
public class ToastManager {
    /** Max simultaneous slot count per corner */
    private static final int MAX_SLOT_COUNT = 5;

    /** Slide-in/out animation duration (ms) */
    private static final long ANIMATION_DURATION_MS = 600L;

    private final Minecraft mc;

    /** Visible Toast instances */
    private final List<ToastInstance> visibleToasts = new ArrayList<>();

    /** Queued Toast instances */
    private final Deque<Toast> queuedToasts = new ArrayDeque<>();

    /**
     * Occupied slot tracker, one independent pool per corner.
     */
    private final Map<ToastCorner, BitSet> occupiedSlots = new EnumMap<>(ToastCorner.class);

    public ToastManager(Minecraft mc) {
        this.mc = mc;
        for (ToastCorner corner : ToastCorner.values()) {
            occupiedSlots.put(corner, new BitSet(MAX_SLOT_COUNT));
        }
    }

    /**
     * Update all Toast states.
     */
    public void update() {
        visibleToasts.removeIf(instance -> {
            Toast.Visibility previousVisibility = instance.visibility;
            instance.update();

            // Play slide-in/out sound on visibility change. Routed through the
            // SoundHandler (GUI master channel), which needs no world or player —
            // safe on the HUD, inside GUIs, and on the main menu alike.
            if (instance.visibility != previousVisibility) {
                playVisibilitySound(instance.toast, instance.visibility);
            }

            if (instance.hasFinishedRendering) {
                occupiedSlots.get(instance.toast.getCorner()).clear(instance.firstSlotIndex,
                    Math.min(instance.firstSlotIndex + instance.occupiedSlotCount, MAX_SLOT_COUNT));
                return true;
            }
            return false;
        });

        // Move from queue to visible list when slots free up (per the toast's own corner)
        if (!queuedToasts.isEmpty()) {
            queuedToasts.removeIf(toast -> {
                BitSet slots = occupiedSlots.get(toast.getCorner());
                int occupiedSlotCount = toast.occupiedSlotCount();
                int firstSlotIndex = findFreeSlotsIndex(slots, occupiedSlotCount);

                if (firstSlotIndex == -1) {
                    return false;
                }

                ToastInstance instance = new ToastInstance(toast, firstSlotIndex, occupiedSlotCount);
                visibleToasts.add(instance);
                slots.set(firstSlotIndex,
                    Math.min(firstSlotIndex + occupiedSlotCount, MAX_SLOT_COUNT));
                return true;
            });
        }
    }

    /**
     * Render all visible Toasts.
     */
    public void render() {
        // Match vanilla: F1 hides toasts only when no screen is open on top.
        if (mc.gameSettings.hideGUI && mc.currentScreen == null) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();
        FontRenderer fontRenderer = mc.fontRenderer;

        // Store mouse coordinates for Component render calls
        int mouseX = org.lwjgl.input.Mouse.getX() * screenWidth / mc.displayWidth;
        int mouseY = screenHeight - org.lwjgl.input.Mouse.getY() * screenHeight / mc.displayHeight - 1;

        for (ToastInstance instance : visibleToasts) {
            instance.render(screenWidth, screenHeight, fontRenderer, mouseX, mouseY);
        }
    }

    /**
     * Add a Toast to the queue.
     */
    public void addToast(Toast toast) {
        queuedToasts.add(toast);
    }

    /**
     * Find a Toast of the given type with the given token.
     */
    @SuppressWarnings("unchecked")
    public <T extends Toast> T getToast(Class<? extends T> clazz, Object token) {
        // Search visible list
        for (ToastInstance instance : visibleToasts) {
            if (clazz.isAssignableFrom(instance.toast.getClass()) &&
                instance.toast.getToken().equals(token)) {
                return (T) instance.toast;
            }
        }

        // Search queue
        for (Toast toast : queuedToasts) {
            if (clazz.isAssignableFrom(toast.getClass()) &&
                toast.getToken().equals(token)) {
                return (T) toast;
            }
        }

        return null;
    }

    /**
     * Clear all Toasts.
     */
    public void clear() {
        for (BitSet slots : occupiedSlots.values()) {
            slots.clear();
        }
        visibleToasts.clear();
        queuedToasts.clear();
    }

    /**
     * Find consecutive free slots in the given corner's slot pool.
     *
     * @return starting slot index, or -1 if none found
     */
    private static int findFreeSlotsIndex(BitSet slots, int requiredCount) {
        if (MAX_SLOT_COUNT - slots.cardinality() >= requiredCount) {
            int consecutiveFreeSlotCount = 0;
            for (int i = 0; i < MAX_SLOT_COUNT; i++) {
                if (slots.get(i)) {
                    consecutiveFreeSlotCount = 0;
                } else if (++consecutiveFreeSlotCount == requiredCount) {
                    return i + 1 - consecutiveFreeSlotCount;
                }
            }
        }
        return -1;
    }

    /**
     * Play the Toast's slide-in/out sound for the given visibility state.
     * Uses {@code SoundHandler} + {@link PositionedSoundRecord} (same path as GUI button
     * clicks), so it never touches {@code thePlayer} and cannot NPE outside a world.
     */
    private void playVisibilitySound(Toast toast, Toast.Visibility visibility) {
        ResourceLocation sound = visibility == Toast.Visibility.SHOW
                ? toast.getShowSound()
                : toast.getHideSound();
        if (sound != null && mc.getSoundHandler() != null) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(sound, 1.0F));
        }
    }

    /**
     * Toast instance wrapper — manages animation and state for a single Toast.
     */
    private class ToastInstance {
        private final Toast toast;
        private final int firstSlotIndex;
        private final int occupiedSlotCount;

        private long animationStartTime = -1L;
        private long becameFullyVisibleAt = -1L;
        private Toast.Visibility visibility = Toast.Visibility.HIDE;
        private long fullyVisibleFor = 0L;
        private float visiblePortion = 0.0F;
        protected boolean hasFinishedRendering = false;

        public ToastInstance(Toast toast, int firstSlotIndex, int occupiedSlotCount) {
            this.toast = toast;
            this.firstSlotIndex = firstSlotIndex;
            this.occupiedSlotCount = occupiedSlotCount;
        }

        public void update() {
            long now = System.currentTimeMillis();

            if (animationStartTime == -1L) {
                animationStartTime = now;
                visibility = Toast.Visibility.SHOW;
            }

            if (visibility == Toast.Visibility.SHOW &&
                now - animationStartTime <= ANIMATION_DURATION_MS) {
                becameFullyVisibleAt = now;
            }

            fullyVisibleFor = now - becameFullyVisibleAt;
            calculateVisiblePortion(now);

            toast.update(ToastManager.this, fullyVisibleFor);

            Toast.Visibility wantedVisibility = toast.getWantedVisibility();
            if (wantedVisibility != visibility) {
                animationStartTime = now - (long) ((1.0F - visiblePortion) * ANIMATION_DURATION_MS);
                visibility = wantedVisibility;
            }

            boolean wasAlreadyFinished = hasFinishedRendering;
            hasFinishedRendering = visibility == Toast.Visibility.HIDE &&
                now - animationStartTime > ANIMATION_DURATION_MS;

            if (hasFinishedRendering && !wasAlreadyFinished) {
                toast.onFinishedRendering();
            }
        }

        private void calculateVisiblePortion(long now) {
            float animationProgress = Math.min(Math.max(
                (float) (now - animationStartTime) / ANIMATION_DURATION_MS, 0.0F), 1.0F);
            animationProgress *= animationProgress;

            if (visibility == Toast.Visibility.HIDE) {
                visiblePortion = 1.0F - animationProgress;
            } else {
                visiblePortion = animationProgress;
            }
        }

        public void render(int screenWidth, int screenHeight, FontRenderer fontRenderer, int mouseX, int mouseY) {
            if (hasFinishedRendering) {
                return;
            }

            GL11.glPushMatrix();
            GL11.glTranslatef(
                toast.xPos(screenWidth, visiblePortion),
                toast.yPos(screenHeight, firstSlotIndex),
                0.0F
            );

            // Set the toast position for Component rendering
            toast.setX(0);
            toast.setY(0);
            // Driven through the unified Renderable entry, consistent with the
            // component render pipeline.
            toast.extractRenderState(GuiGraphicsExtractor.getInstance(), mouseX, mouseY, 0);

            GL11.glPopMatrix();
        }
    }
}
