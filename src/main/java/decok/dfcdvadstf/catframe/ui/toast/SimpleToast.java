package decok.dfcdvadstf.catframe.ui.toast;

import net.minecraft.client.gui.FontRenderer;

/**
 * 简单 Toast
 * 用于显示简单的单行或双行通知
 */
public class SimpleToast extends BaseToast {
	/**
	 * 显示时间(毫秒)
	 */
	private final long displayTime;
	
	/**
	 * 标题
	 */
	private String title;
	
	/**
	 * 描述(可选)
	 */
	private String description;
	
	/**
	 * 图标资源路径(可选)
	 */
	private String iconPath;
	
	/**
	 * 构造函数 - 仅标题
	 * @param title 标题文本
	 */
	public SimpleToast(String title) {
		this(title, null, DEFAULT_DISPLAY_TIME);
	}
	
	/**
	 * 构造函数 - 标题和描述
	 * @param title 标题文本
	 * @param description 描述文本
	 */
	public SimpleToast(String title, String description) {
		this(title, description, DEFAULT_DISPLAY_TIME);
	}
	
	/**
	 * 构造函数 - 完整参数
	 * @param title 标题文本
	 * @param description 描述文本
	 * @param displayTime 显示时间(毫秒)
	 */
	public SimpleToast(String title, String description, long displayTime) {
		this.title = title;
		this.description = description;
		this.displayTime = displayTime;
		
		// 根据内容调整高度
		if (description != null && !description.isEmpty()) {
			this.height = 48; // 双行高度
		} else {
			this.height = 32; // 单行高度
		}
		
		// 根据文本长度调整宽度
		int titleWidth = mc.fontRenderer.getStringWidth(title);
		int descWidth = description != null ? mc.fontRenderer.getStringWidth(description) : 0;
		this.width = Math.max(DEFAULT_WIDTH, Math.max(titleWidth, descWidth) + 40);
	}
	
	/**
	 * 设置图标
	 * @param iconPath 图标资源路径
	 */
	public void setIcon(String iconPath) {
		this.iconPath = iconPath;
	}
	
	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		wantedVisibility = fullyVisibleForMs < displayTime ? 
			Visibility.SHOW : Visibility.HIDE;
	}
	
	@Override
	protected void renderContent(FontRenderer fontRenderer, long fullyVisibleForMs) {
		int x = 18;
		
		// 渲染标题
		int titleY = (description != null && !description.isEmpty()) ? 8 : 12;
		fontRenderer.drawString(title, x, titleY, 0xFFFFFF);
		
		// 渲染描述
		if (description != null && !description.isEmpty()) {
			fontRenderer.drawString(description, x, 26, 0xAAAAAA);
		}
		
		// TODO: 如果有图标,渲染图标
		// if (iconPath != null) {
		//     renderIcon(iconPath);
		// }
	}
	
	/**
	 * 便捷方法:显示成功提示
	 */
	public static SimpleToast success(String message) {
		return new SimpleToast("§a✓ " + message);
	}
	
	/**
	 * 便捷方法:显示警告提示
	 */
	public static SimpleToast warning(String message) {
		return new SimpleToast("§e⚠ " + message);
	}
	
	/**
	 * 便捷方法:显示错误提示
	 */
	public static SimpleToast error(String message) {
		return new SimpleToast("§c✗ " + message);
	}
	
	/**
	 * 便捷方法:显示信息提示
	 */
	public static SimpleToast info(String message) {
		return new SimpleToast("§bℹ " + message);
	}
}
