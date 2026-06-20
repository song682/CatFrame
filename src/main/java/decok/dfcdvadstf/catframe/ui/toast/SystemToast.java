package decok.dfcdvadstf.catframe.ui.toast;

import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统 Toast
 * 用于显示系统通知、错误信息等
 * 参考高版本 SystemToast 设计
 */
public class SystemToast extends BaseToast {
	/**
	 * Toast ID(用于去重)
	 */
	private final SystemToastId id;
	
	/**
	 * 标题
	 */
	private String title;
	
	/**
	 * 消息行列表
	 */
	private List<String> messageLines;
	
	/**
	 * 上次更改时间
	 */
	private long lastChanged;
	
	/**
	 * 是否已更改
	 */
	private boolean changed;
	
	/**
	 * 是否强制隐藏
	 */
	private boolean forceHide;
	
	/**
	 * 构造函数
	 * @param id Toast ID
	 * @param title 标题
	 * @param message 消息(可为 null)
	 */
	public SystemToast(SystemToastId id, String title, String message) {
		this.id = id;
		this.title = title;
		this.messageLines = splitText(message != null ? message : "", 180);
		this.width = Math.max(160, 30 + Math.max(
			mc.fontRenderer.getStringWidth(title),
			message == null ? 0 : mc.fontRenderer.getStringWidth(message)
		));
		this.height = 20 + Math.max(this.messageLines.size(), 1) * 12;
	}
	
	/**
	 * 分割文本为多行
	 */
	private List<String> splitText(String text, int maxWidth) {
		List<String> lines = new ArrayList<>();
		FontRenderer font = mc.fontRenderer;
		
		if (text.isEmpty()) {
			return lines;
		}
		
		String[] words = text.split(" ");
		StringBuilder currentLine = new StringBuilder();
		
		for (String word : words) {
			String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
			if (font.getStringWidth(testLine) > maxWidth) {
				if (currentLine.length() > 0) {
					lines.add(currentLine.toString());
					currentLine = new StringBuilder(word);
				} else {
					// 单个词就超宽,直接添加
					lines.add(word);
				}
			} else {
				if (currentLine.length() > 0) {
					currentLine.append(" ");
				}
				currentLine.append(word);
			}
		}
		
		if (currentLine.length() > 0) {
			lines.add(currentLine.toString());
		}
		
		return lines;
	}
	
	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		if (changed) {
			lastChanged = fullyVisibleForMs;
			changed = false;
		}
		
		long timeSinceUpdate = fullyVisibleForMs - lastChanged;
		wantedVisibility = !forceHide && timeSinceUpdate < id.displayTime ? 
			Visibility.SHOW : Visibility.HIDE;
	}
	
	@Override
	protected void renderContent(FontRenderer fontRenderer, long fullyVisibleForMs) {
		// 渲染标题
		int titleX = 18;
		int titleY = messageLines.isEmpty() ? 12 : 7;
		fontRenderer.drawString(title, titleX, titleY, 0xFFFFFF);
		
		// 渲染消息行
		for (int i = 0; i < messageLines.size(); i++) {
			int lineY = 18 + i * 12;
			fontRenderer.drawString(messageLines.get(i), titleX, lineY, 0x555555);
		}
	}
	
	@Override
	public Object getToken() {
		return id;
	}
	
	/**
	 * 强制隐藏此 Toast
	 */
	public void forceHide() {
		this.forceHide = true;
	}
	
	/**
	 * 重置 Toast 内容
	 */
	public void reset(String title, String message) {
		this.title = title;
		this.messageLines = splitText(message != null ? message : "", 180);
		this.changed = true;
	}
	
	/**
	 * 便捷方法:添加 Toast
	 */
	public static void add(ToastManager toastManager, SystemToastId id, String title, String message) {
		toastManager.addToast(new SystemToast(id, title, message));
	}
	
	/**
	 * 便捷方法:添加或更新 Toast
	 */
	public static void addOrUpdate(ToastManager toastManager, SystemToastId id, String title, String message) {
		SystemToast toast = toastManager.getToast(SystemToast.class, id);
		if (toast == null) {
			add(toastManager, id, title, message);
		} else {
			toast.reset(title, message);
		}
	}
	
	/**
	 * Toast ID 类
	 */
	public static class SystemToastId {
		/**
		 * 默认显示时间
		 */
		private final long displayTime;
		
		public SystemToastId(long displayTime) {
			this.displayTime = displayTime;
		}
		
		public SystemToastId() {
			this(5000L);
		}
	}
}
