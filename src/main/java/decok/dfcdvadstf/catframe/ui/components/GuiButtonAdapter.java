package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * <p>
 * 原版 GuiButton 适配器 —— 将原版 {@link GuiButton} 包装为 {@link GuiEventListener}。<br>
 * 用于 Tab 系统中兼容已有原版按钮的过渡。
 * </p>
 * <p>
 * Vanilla GuiButton adapter — wraps the vanilla {@link GuiButton} as a {@link GuiEventListener}.<br>
 * Used for transitional compatibility in the Tab system.
 * </p>
 */
public class GuiButtonAdapter extends AbstractComponent {

    private final GuiButton delegate;

    public GuiButtonAdapter(GuiButton delegate) {
        super(delegate.xPosition, delegate.yPosition, delegate.width, delegate.height);
        this.delegate = delegate;
    }

    /**
     * Returns the wrapped vanilla button.
     * <p>返回被包装的原版按钮。</p>
     */
    public GuiButton getDelegate() {
        return delegate;
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        delegate.xPosition = x;
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        delegate.yPosition = y;
    }

    /**
     * 委托原版 {@link GuiButton#drawButton} 完成绘制 —— 可见性检查已由
     * {@link #extractRenderState} 在调用前处理。<br>
     * Delegates drawing to vanilla {@link GuiButton#drawButton} — the visibility
     * check is already handled by {@link #extractRenderState}.
     */
    @Override
    protected void renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        delegate.visible = true;
        delegate.drawButton(Minecraft.getMinecraft(), mouseX, mouseY);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (active && visible && delegate.mousePressed(Minecraft.getMinecraft(), mouseX, mouseY)) {
            Minecraft.getMinecraft().getSoundHandler().playSound(
                net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(
                    new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
        }
    }
}
