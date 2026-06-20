package decok.dfcdvadstf.catframe.ui.components.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.util.*;

/**
 * <p>
 * Toast 管理器<br>
 * 负责管理 Toast 的队列、显示、动画和渲染.
 * 参考高版本 ToastManager 设计.
 * </p>
 * <p>
 * Toast manager — manages Toast queuing, display, animation, and rendering.
 * </p>
 */
public class ToastManager {
    /** Max simultaneous slot count / 最大同时显示的槽位数 */
    private static final int MAX_SLOT_COUNT = 5;

    /** Slide-in/out animation duration (ms) / 滑入/滑出动画持续时间(毫秒) */
    private static final long ANIMATION_DURATION_MS = 600L;

    private final Minecraft mc;

    /** Visible Toast instances / 可见的 Toast 实例列表 */
    private final List<ToastInstance> visibleToasts = new ArrayList<>();

    /** Queued Toast instances / 等待队列中的 Toast */
    private final Deque<Toast> queuedToasts = new ArrayDeque<>();

    /** Occupied slot tracker / 已占用的槽位标记 */
    private final BitSet occupiedSlots = new BitSet(MAX_SLOT_COUNT);

    public ToastManager(Minecraft mc) {
        this.mc = mc;
    }

    /**
     * Update all Toast states.
     * <p>更新所有 Toast 状态。</p>
     */
    public void update() {
        visibleToasts.removeIf(instance -> {
            Toast.Visibility previousVisibility = instance.visibility;
            instance.update();

            // Sound effect on visibility change (if needed)
            if (instance.visibility != previousVisibility) {
                // TODO: add sound effect
            }

            if (instance.hasFinishedRendering) {
                occupiedSlots.clear(instance.firstSlotIndex,
                    Math.min(instance.firstSlotIndex + instance.occupiedSlotCount, MAX_SLOT_COUNT));
                return true;
            }
            return false;
        });

        // Move from queue to visible list when slots free up
        if (!queuedToasts.isEmpty() && getFreeSlotCount() > 0) {
            queuedToasts.removeIf(toast -> {
                int occupiedSlotCount = toast.occupiedSlotCount();
                int firstSlotIndex = findFreeSlotsIndex(occupiedSlotCount);

                if (firstSlotIndex == -1) {
                    return false;
                }

                ToastInstance instance = new ToastInstance(toast, firstSlotIndex, occupiedSlotCount);
                visibleToasts.add(instance);
                occupiedSlots.set(firstSlotIndex,
                    Math.min(firstSlotIndex + occupiedSlotCount, MAX_SLOT_COUNT));
                return true;
            });
        }
    }

    /**
     * Render all visible Toasts.
     * <p>渲染所有可见的 Toast。</p>
     */
    public void render() {
        if (mc.gameSettings.hideGUI) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int screenWidth = resolution.getScaledWidth();
        FontRenderer fontRenderer = mc.fontRenderer;

        // Store mouse coordinates for Component render calls
        int mouseX = org.lwjgl.input.Mouse.getX() * screenWidth / mc.displayWidth;
        int mouseY = resolution.getScaledHeight() - org.lwjgl.input.Mouse.getY() * resolution.getScaledHeight() / mc.displayHeight - 1;

        for (ToastInstance instance : visibleToasts) {
            instance.render(screenWidth, fontRenderer, mouseX, mouseY);
        }
    }

    /**
     * Add a Toast to the queue.
     * <p>添加 Toast 到队列。</p>
     */
    public void addToast(Toast toast) {
        queuedToasts.add(toast);
    }

    /**
     * Find a Toast of the given type with the given token.
     * <p>查找指定类型的 Toast。</p>
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
     * <p>清空所有 Toast。</p>
     */
    public void clear() {
        occupiedSlots.clear();
        visibleToasts.clear();
        queuedToasts.clear();
    }

    /**
     * Find consecutive free slots.
     * <p>查找连续的空闲槽位。</p>
     *
     * @return starting slot index, or -1 if none found
     */
    private int findFreeSlotsIndex(int requiredCount) {
        if (getFreeSlotCount() >= requiredCount) {
            int consecutiveFreeSlotCount = 0;
            for (int i = 0; i < MAX_SLOT_COUNT; i++) {
                if (occupiedSlots.get(i)) {
                    consecutiveFreeSlotCount = 0;
                } else if (++consecutiveFreeSlotCount == requiredCount) {
                    return i + 1 - consecutiveFreeSlotCount;
                }
            }
        }
        return -1;
    }

    /**
     * Get the number of free slots.
     * <p>获取空闲槽位数量。</p>
     */
    private int getFreeSlotCount() {
        return MAX_SLOT_COUNT - occupiedSlots.cardinality();
    }

    /**
     * Toast instance wrapper — manages animation and state for a single Toast.
     * <p>Toast 实例包装类 — 管理单个 Toast 的动画和状态。</p>
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

        public void render(int screenWidth, FontRenderer fontRenderer, int mouseX, int mouseY) {
            if (hasFinishedRendering) {
                return;
            }

            GL11.glPushMatrix();
            GL11.glTranslatef(
                toast.xPos(screenWidth, visiblePortion),
                toast.yPos(firstSlotIndex),
                0.0F
            );

            // Set the toast position for Component rendering
            toast.setX(0);
            toast.setY(0);
            toast.render(mouseX, mouseY, 0);

            GL11.glPopMatrix();
        }
    }
}
