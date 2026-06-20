package decok.dfcdvadstf.catframe.ui.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.util.*;

/**
 * Toast 管理器
 * 负责管理 Toast 的队列、显示、动画和渲染
 * 参考高版本 ToastManager 设计
 */
public class ToastManager {
	/**
	 * 最大同时显示的槽位数
	 */
	private static final int MAX_SLOT_COUNT = 5;
	
	/**
	 * 滑入/滑出动画持续时间(毫秒)
	 */
	private static final long ANIMATION_DURATION_MS = 600L;
	
	private final Minecraft mc;
	
	/**
	 * 可见的 Toast 实例列表
	 */
	private final List<ToastInstance> visibleToasts = new ArrayList<>();
	
	/**
	 * 等待队列中的 Toast
	 */
	private final Deque<Toast> queuedToasts = new ArrayDeque<>();
	
	/**
	 * 已占用的槽位标记
	 */
	private final BitSet occupiedSlots = new BitSet(MAX_SLOT_COUNT);
	
	public ToastManager(Minecraft mc) {
		this.mc = mc;
	}
	
	/**
	 * 更新所有 Toast 状态
	 */
	public void update() {
		// 更新可见的 Toast
		visibleToasts.removeIf(instance -> {
			Toast.Visibility previousVisibility = instance.visibility;
			instance.update();
			
			// 播放可见性变化音效(如果需要)
			if (instance.visibility != previousVisibility) {
				// TODO: 可以添加音效
			}
			
			// 如果渲染完成,释放槽位
			if (instance.hasFinishedRendering) {
				occupiedSlots.clear(instance.firstSlotIndex, 
					Math.min(instance.firstSlotIndex + instance.occupiedSlotCount, MAX_SLOT_COUNT));
				return true;
			}
			return false;
		});
		
		// 从队列中添加新的 Toast 到可见列表
		if (!queuedToasts.isEmpty() && getFreeSlotCount() > 0) {
			queuedToasts.removeIf(toast -> {
				int occupiedSlotCount = toast.occupiedSlotCount();
				int firstSlotIndex = findFreeSlotsIndex(occupiedSlotCount);
				
				if (firstSlotIndex == -1) {
					return false; // 没有足够的连续槽位
				}
				
				// 创建 Toast 实例并添加到可见列表
				ToastInstance instance = new ToastInstance(toast, firstSlotIndex, occupiedSlotCount);
				visibleToasts.add(instance);
				occupiedSlots.set(firstSlotIndex, 
					Math.min(firstSlotIndex + occupiedSlotCount, MAX_SLOT_COUNT));
				return true;
			});
		}
	}
	
	/**
	 * 渲染所有可见的 Toast
	 */
	public void render() {
		if (mc.gameSettings.hideGUI) {
			return;
		}
		
		ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
		int screenWidth = resolution.getScaledWidth();
		FontRenderer fontRenderer = mc.fontRenderer;
		
		// 从右到左渲染
		for (ToastInstance instance : visibleToasts) {
			instance.render(screenWidth, fontRenderer);
		}
	}
	
	/**
	 * 添加 Toast 到队列
	 */
	public void addToast(Toast toast) {
		queuedToasts.add(toast);
	}
	
	/**
	 * 查找指定类型的 Toast
	 */
	@SuppressWarnings("unchecked")
	public <T extends Toast> T getToast(Class<? extends T> clazz, Object token) {
		// 在可见列表中查找
		for (ToastInstance instance : visibleToasts) {
			if (clazz.isAssignableFrom(instance.toast.getClass()) && 
				instance.toast.getToken().equals(token)) {
				return (T) instance.toast;
			}
		}
		
		// 在队列中查找
		for (Toast toast : queuedToasts) {
			if (clazz.isAssignableFrom(toast.getClass()) && 
				toast.getToken().equals(token)) {
				return (T) toast;
			}
		}
		
		return null;
	}
	
	/**
	 * 清空所有 Toast
	 */
	public void clear() {
		occupiedSlots.clear();
		visibleToasts.clear();
		queuedToasts.clear();
	}
	
	/**
	 * 查找连续的空闲槽位
	 * @return 起始槽位索引,如果没有找到返回 -1
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
	 * 获取空闲槽位数量
	 */
	private int getFreeSlotCount() {
		return MAX_SLOT_COUNT - occupiedSlots.cardinality();
	}
	
	/**
	 * Toast 实例包装类
	 * 管理单个 Toast 的动画和状态
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
		
		/**
		 * 更新 Toast 实例状态
		 */
		public void update() {
			long now = System.currentTimeMillis();
			
			// 初始化动画
			if (animationStartTime == -1L) {
				animationStartTime = now;
				visibility = Toast.Visibility.SHOW;
			}
			
			// 记录完全可见的时间点
			if (visibility == Toast.Visibility.SHOW && 
				now - animationStartTime <= ANIMATION_DURATION_MS) {
				becameFullyVisibleAt = now;
			}
			
			// 计算完全可见的持续时间
			fullyVisibleFor = now - becameFullyVisibleAt;
			
			// 计算可见比例(用于动画)
			calculateVisiblePortion(now);
			
			// 更新 Toast 本身
			toast.update(ToastManager.this, fullyVisibleFor);
			
			// 处理可见性变化
			Toast.Visibility wantedVisibility = toast.getWantedVisibility();
			if (wantedVisibility != visibility) {
				// 平滑过渡动画
				animationStartTime = now - (long)((1.0F - visiblePortion) * ANIMATION_DURATION_MS);
				visibility = wantedVisibility;
			}
			
			// 检查是否完成渲染
			boolean wasAlreadyFinished = hasFinishedRendering;
			hasFinishedRendering = visibility == Toast.Visibility.HIDE && 
				now - animationStartTime > ANIMATION_DURATION_MS;
			
			if (hasFinishedRendering && !wasAlreadyFinished) {
				toast.onFinishedRendering();
			}
		}
		
		/**
		 * 计算当前可见比例
		 */
		private void calculateVisiblePortion(long now) {
			float animationProgress = Math.min(Math.max(
				(float)(now - animationStartTime) / ANIMATION_DURATION_MS, 0.0F), 1.0F);
			animationProgress *= animationProgress; // 缓动效果
			
			if (visibility == Toast.Visibility.HIDE) {
				visiblePortion = 1.0F - animationProgress;
			} else {
				visiblePortion = animationProgress;
			}
		}
		
		/**
		 * 渲染此 Toast 实例
		 */
		public void render(int screenWidth, FontRenderer fontRenderer) {
			if (hasFinishedRendering) {
				return;
			}
			
			GL11.glPushMatrix();
			GL11.glTranslatef(
				toast.xPos(screenWidth, visiblePortion),
				toast.yPos(firstSlotIndex),
				0.0F
			);
			
			toast.render(fontRenderer, fullyVisibleFor);
			
			GL11.glPopMatrix();
		}
	}
}
