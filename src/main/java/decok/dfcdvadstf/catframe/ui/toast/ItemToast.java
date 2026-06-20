package decok.dfcdvadstf.catframe.ui.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 物品 Toast 示例
 * 展示如何创建带有物品图标的 Toast
 */
public class ItemToast extends BaseToast {
	private final ItemStack itemStack;
	private final String title;
	private final String description;
	private final long displayTime;
	
	public ItemToast(ItemStack itemStack, String title, String description) {
		this(itemStack, title, description, DEFAULT_DISPLAY_TIME);
	}
	
	public ItemToast(ItemStack itemStack, String title, String description, long displayTime) {
		this.itemStack = itemStack;
		this.title = title;
		this.description = description;
		this.displayTime = displayTime;
		
		this.width = 180;
		this.height = 48;
	}
	
	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		wantedVisibility = fullyVisibleForMs < displayTime ? 
			Visibility.SHOW : Visibility.HIDE;
	}
	
	@Override
	protected void renderContent(FontRenderer fontRenderer, long fullyVisibleForMs) {
		// 渲染物品图标 (32x32)
		GL11.glPushMatrix();
		GL11.glScalef(2.0F, 2.0F, 1.0F);
		// TODO: 使用 RenderHelper 渲染物品
		// mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, 4, 4);
		GL11.glPopMatrix();
		
		// 渲染标题
		fontRenderer.drawString(title, 42, 10, 0xFFFFFF);
		
		// 渲染描述
		if (description != null) {
			fontRenderer.drawString(description, 42, 24, 0xAAAAAA);
		}
	}
}
