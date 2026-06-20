package decok.dfcdvadstf.catframe.ui.toast;

import net.minecraft.client.gui.FontRenderer;

/**
 * 高版本风格的 Toast 接口
 * 参考 Minecraft 高版本 Toast 系统设计
 * 适配 1.7.10 Forge 环境
 */
public interface Toast {
	/**
	 * 默认 Toast 宽度
	 */
	int DEFAULT_WIDTH = 160;
	
	/**
	 * 默认槽位高度
	 */
	int SLOT_HEIGHT = 32;
	
	/**
	 * 获取期望的可见性状态
	 */
	Visibility getWantedVisibility();
	
	/**
	 * 更新 Toast 状态
	 * @param manager Toast 管理器
	 * @param fullyVisibleForMs 完全可见的持续时间(毫秒)
	 */
	void update(ToastManager manager, long fullyVisibleForMs);
	
	/**
	 * 渲染 Toast
	 * @param fontRenderer 字体渲染器
	 * @param fullyVisibleForMs 完全可见的持续时间(毫秒)
	 */
	void render(FontRenderer fontRenderer, long fullyVisibleForMs);
	
	/**
	 * 获取唯一标识符(用于去重)
	 * @return 标识符对象
	 */
	default Object getToken() {
		return this;
	}
	
	/**
	 * 获取 Toast 宽度
	 */
	default int width() {
		return DEFAULT_WIDTH;
	}
	
	/**
	 * 获取 Toast 高度
	 */
	default int height() {
		return SLOT_HEIGHT;
	}
	
	/**
	 * 获取占用的槽位数量
	 */
	default int occupiedSlotCount() {
		return (int) Math.ceil((double) height() / SLOT_HEIGHT);
	}
	
	/**
	 * 计算 X 坐标(考虑滑入动画)
	 * @param screenWidth 屏幕宽度
	 * @param visiblePortion 可见比例(0.0-1.0)
	 */
	default float xPos(int screenWidth, float visiblePortion) {
		return screenWidth - width() * visiblePortion;
	}
	
	/**
	 * 计算 Y 坐标
	 * @param firstSlotIndex 起始槽位索引
	 */
	default float yPos(int firstSlotIndex) {
		return firstSlotIndex * height();
	}
	
	/**
	 * 渲染完成时回调
	 */
	default void onFinishedRendering() {
	}
	
	/**
	 * Toast 可见性枚举
	 */
	enum Visibility {
		SHOW,
		HIDE;
	}
}
