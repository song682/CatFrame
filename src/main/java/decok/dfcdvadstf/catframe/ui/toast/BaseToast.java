package decok.dfcdvadstf.catframe.ui.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Toast 基础实现类
 * 提供通用的背景渲染和基础功能
 */
public abstract class BaseToast implements Toast {
	/**
	 * 默认显示时间(毫秒)
	 */
	protected static final long DEFAULT_DISPLAY_TIME = 5000L;
	
	/**
	 * 背景颜色
	 */
	protected static final int BACKGROUND_COLOR = 0xCC000000; // 半透明黑色
	
	/**
	 * 边框颜色
	 */
	protected static final int BORDER_COLOR = 0xFF555555;
	
	/**
	 * Toast 管理器
	 */
	protected final Minecraft mc = Minecraft.getMinecraft();
	
	/**
	 * 当前期望的可见性
	 */
	protected Visibility wantedVisibility = Visibility.HIDE;
	
	/**
	 * Toast 宽度
	 */
	protected int width = DEFAULT_WIDTH;
	
	/**
	 * Toast 高度
	 */
	protected int height = SLOT_HEIGHT;
	
	@Override
	public Visibility getWantedVisibility() {
		return wantedVisibility;
	}
	
	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		// 默认实现:子类应该重写此方法
	}
	
	@Override
	public void render(FontRenderer fontRenderer, long fullyVisibleForMs) {
		// 渲染背景
		renderBackground();
		
		// 渲染内容(由子类实现)
		renderContent(fontRenderer, fullyVisibleForMs);
	}
	
	/**
	 * 渲染 Toast 背景
	 */
	protected void renderBackground() {
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		
		// 绘制背景矩形
		drawRect(0, 0, width, height, BACKGROUND_COLOR);
		
		// 绘制边框
		drawBorder();
		
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}
	
	/**
	 * 绘制边框
	 */
	protected void drawBorder() {
		int borderColor = BORDER_COLOR;
		
		// 上边
		drawRect(0, 0, width, 1, borderColor);
		// 下边
		drawRect(0, height - 1, width, height, borderColor);
		// 左边
		drawRect(0, 0, 1, height, borderColor);
		// 右边
		drawRect(width - 1, 0, width, height, borderColor);
	}
	
	/**
	 * 绘制矩形
	 */
	protected void drawRect(int left, int top, int right, int bottom, int color) {
		if (left < right) {
			int temp = left;
			left = right;
			right = temp;
		}
		if (top < bottom) {
			int temp = top;
			top = bottom;
			bottom = temp;
		}
		
		float alpha = (float)(color >> 24 & 255) / 255.0F;
		float red = (float)(color >> 16 & 255) / 255.0F;
		float green = (float)(color >> 8 & 255) / 255.0F;
		float blue = (float)(color & 255) / 255.0F;
		
		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();
		tessellator.setColorRGBA_F(red, green, blue, alpha);
		tessellator.addVertex(left, bottom, 0.0D);
		tessellator.addVertex(right, bottom, 0.0D);
		tessellator.addVertex(right, top, 0.0D);
		tessellator.addVertex(left, top, 0.0D);
		tessellator.draw();
	}
	
	/**
	 * 渲染 Toast 内容(由子类实现)
	 * @param fontRenderer 字体渲染器
	 * @param fullyVisibleForMs 完全可见的持续时间
	 */
	protected abstract void renderContent(FontRenderer fontRenderer, long fullyVisibleForMs);
	
	@Override
	public int width() {
		return width;
	}
	
	@Override
	public int height() {
		return height;
	}
}
