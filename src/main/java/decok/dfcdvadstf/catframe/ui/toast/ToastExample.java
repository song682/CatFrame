package decok.dfcdvadstf.catframe.ui.toast;

import net.minecraft.client.Minecraft;

/**
 * Toast 系统快速入门示例
 * 展示如何在你的代码中使用 Toast 系统
 */
public class ToastExample {
	
	/**
	 * 示例 1: 在 GUI 中使用 Toast
	 */
	public static class ExampleGui {
		private ToastManager toastManager;
		
		public void init(Minecraft mc) {
			// 初始化 Toast 管理器
			toastManager = new ToastManager(mc);
			
			// 显示一个简单的 Toast
			toastManager.addToast(new SimpleToast("欢迎使用 CatFrame!"));
		}
		
		public void update() {
			// 每帧更新 Toast
			if (toastManager != null) {
				toastManager.update();
			}
		}
		
		public void render() {
			// 渲染 Toast
			if (toastManager != null) {
				toastManager.render();
			}
		}
		
		public void onButtonClick() {
			// 按钮点击时显示 Toast
			toastManager.addToast(SimpleToast.success("按钮已点击!"));
		}
	}
	
	/**
	 * 示例 2: 使用不同类型的 Toast
	 */
	public static class ToastTypes {
		private ToastManager manager;
		
		public ToastTypes(ToastManager manager) {
			this.manager = manager;
		}
		
		public void showExamples() {
			// 简单 Toast
			manager.addToast(new SimpleToast("简单通知"));
			
			// 带描述的 Toast
			manager.addToast(new SimpleToast("标题", "这是一段描述文字"));
			
			// 快捷方法 - 成功
			manager.addToast(SimpleToast.success("操作成功完成"));
			
			// 快捷方法 - 警告
			manager.addToast(SimpleToast.warning("请注意这个警告"));
			
			// 快捷方法 - 错误
			manager.addToast(SimpleToast.error("发生了一个错误"));
			
			// 快捷方法 - 信息
			manager.addToast(SimpleToast.info("这是一条提示信息"));
			
			// 系统 Toast (支持多行和自动换行)
			SystemToast.SystemToastId id = new SystemToast.SystemToastId();
			SystemToast.add(manager, id, 
				"系统通知", 
				"这是一条系统级通知消息,支持较长的文本内容");
		}
	}
	
	/**
	 * 示例 3: Toast 去重
	 */
	public static class ToastDeduplication {
		private ToastManager manager;
		private static final Object MY_TOAST_TOKEN = new Object();
		
		public ToastDeduplication(ToastManager manager) {
			this.manager = manager;
		}
		
		public void showToast() {
			// 检查是否已存在相同 Token 的 Toast
			SimpleToast existing = manager.getToast(SimpleToast.class, MY_TOAST_TOKEN);
			
			if (existing == null) {
				// 不存在,创建新的
				SimpleToast toast = new SimpleToast("唯一 Toast");
				// 注意: 需要重写 getToken() 方法返回 MY_TOAST_TOKEN
				manager.addToast(toast);
			} else {
				// 已存在,可以选择刷新或忽略
				System.out.println("Toast 已存在,忽略重复添加");
			}
		}
	}
	
	/**
	 * 示例 4: 自定义 Toast
	 */
	public static class CustomToastExample extends BaseToast {
		private String customMessage;
		private long displayTime = 8000; // 8秒
		
		public CustomToastExample(String message) {
			this.customMessage = message;
			this.width = 200;
			this.height = 50;
		}
		
		@Override
		public void update(ToastManager manager, long fullyVisibleForMs) {
			// 控制显示时间
			wantedVisibility = fullyVisibleForMs < displayTime ? 
				Visibility.SHOW : Visibility.HIDE;
		}
		
		@Override
		protected void renderContent(net.minecraft.client.gui.FontRenderer fontRenderer, 
		                             long fullyVisibleForMs) {
			// 自定义渲染
			fontRenderer.drawString("自定义: " + customMessage, 10, 20, 0xFFD700);
		}
		
		@Override
		public Object getToken() {
			return "custom_toast_" + customMessage;
		}
	}
	
	/**
	 * 示例 5: 强制隐藏 Toast
	 */
	public static class ForceHideExample {
		private ToastManager manager;
		private SystemToast.SystemToastId toastId = new SystemToast.SystemToastId();
		
		public ForceHideExample(ToastManager manager) {
			this.manager = manager;
		}
		
		public void showAndHide() {
			// 显示 Toast
			SystemToast.add(manager, toastId, "重要通知", "请点击查看详情");
			
			// 5秒后强制隐藏
			// (实际应该在延迟后执行)
			SystemToast toast = manager.getToast(SystemToast.class, toastId);
			if (toast != null) {
				toast.forceHide();
			}
		}
	}
	
	/**
	 * 示例 6: 清空所有 Toast
	 */
	public static class ClearExample {
		private ToastManager manager;
		
		public ClearExample(ToastManager manager) {
			this.manager = manager;
		}
		
		public void onWorldChange() {
			// 切换世界时清空所有 Toast
			manager.clear();
		}
	}
	
	/**
	 * 示例 7: 在实际 Mod 中集成
	 */
	public static class ModIntegration {
		// 在你的 Mod 主类中创建全局 Toast 管理器
		private static ToastManager toastManager;
		
		public static void init(Minecraft mc) {
			toastManager = new ToastManager(mc);
		}
		
		public static ToastManager getToastManager() {
			return toastManager;
		}
		
		// 在你的事件处理器中更新和渲染
		public static void onGameLoop() {
			if (toastManager != null) {
				toastManager.update();
			}
		}
		
		public static void onRenderOverlay() {
			if (toastManager != null) {
				toastManager.render();
			}
		}
		
		// 便捷方法
		public static void showSuccess(String message) {
			if (toastManager != null) {
				toastManager.addToast(SimpleToast.success(message));
			}
		}
		
		public static void showError(String message) {
			if (toastManager != null) {
				toastManager.addToast(SimpleToast.error(message));
			}
		}
	}
}
