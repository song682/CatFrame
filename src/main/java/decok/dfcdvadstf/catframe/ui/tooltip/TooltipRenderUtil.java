package decok.dfcdvadstf.catframe.ui.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.util.ResourceLocation;

/**
 * 工具提示背景渲染工具——使用九宫格纹理绘制 tooltip 背景与边框。</p>
 * <p>
 * 对应 26.1.2 {@code net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil}。
 * 使用两层纹理：
 * <ul>
 *   <li>{@link #BACKGROUND_TEXTURE background} — 背景填充层</li>
 *   <li>{@link #FRAME_TEXTURE frame} — 边框层</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class TooltipRenderUtil {

    /** tooltip 内边距（文字到背景边缘） */
    public static final int PADDING = 4;
    public static final int PADDING_LEFT = 4;
    public static final int PADDING_RIGHT = 4;
    public static final int PADDING_TOP = 4;
    public static final int PADDING_BOTTOM = 4;

    /** 背景填充纹理（中心平铺） */
    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("catframe", "textures/gui/tooltips/background.png");
    /** 边框纹理（中心拉伸） */
    private static final ResourceLocation FRAME_TEXTURE = new ResourceLocation("catframe", "textures/gui/tooltips/frame.png");

    /**
     * 渲染 tooltip 背景。
     * <p>先绘制背景填充层，再叠加边框层。</p>
     *
     * @param x tooltip 文字区域左上 X
     * @param y tooltip 文字区域左上 Y
     * @param w 文字区域宽度
     * @param h 文字区域高度
     */
    public static void renderTooltipBackground(int x, int y, int w, int h) {
        int bgX = x - PADDING;
        int bgY = y - PADDING;
        int bgW = w + PADDING + PADDING;
        int bgH = h + PADDING + PADDING;

        // 第一层：背景填充（center 不平铺）
        TextureStretching.drawAutoNinePatch(BACKGROUND_TEXTURE, bgX, bgY, bgW, bgH, 100, 100, PADDING);

        // 第二层：边框（center 拉伸覆盖背景）
        TextureStretching.drawAutoNinePatch(FRAME_TEXTURE, bgX, bgY, bgW, bgH, 100, 100, PADDING);
    }
}
