package decok.dfcdvadstf.catframe.ui.components.tooltip;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.Tags;
import decok.dfcdvadstf.catframe.ui.util.TextureStretching;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 工具提示背景渲染工具——使用九宫格纹理绘制 tooltip 背景与边框。
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

    /** 鼠标偏移量（对标 26.1.2 MOUSE_OFFSET） */
    public static final int MOUSE_OFFSET = 12;

    /** tooltip 内边距（文字到背景边缘）— 对标 26.1.2 PADDING = 3 */
    public static final int PADDING = 3;
    public static final int PADDING_LEFT = 3;
    public static final int PADDING_RIGHT = 3;
    public static final int PADDING_TOP = 3;
    public static final int PADDING_BOTTOM = 3;

    /** 外边距（背景到纹理边缘）— 对标 26.1.2 MARGIN = 9 */
    private static final int MARGIN = 9;

    /** 默认背景填充纹理（中心平铺） */
    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(Tags.MODID, "textures/gui/tooltips/background.png");
    /** 默认边框纹理（中心拉伸） */
    private static final ResourceLocation FRAME_TEXTURE = new ResourceLocation(Tags.MODID, "textures/gui/tooltips/frame.png");

    /**
     * 渲染 tooltip 背景（无自定义样式）。
     *
     * @param x tooltip 文字区域左上 X
     * @param y tooltip 文字区域左上 Y
     * @param w 文字区域宽度
     * @param h 文字区域高度
     */
    public static void renderTooltipBackground(int x, int y, int w, int h) {
        renderTooltipBackground(x, y, w, h, null);
    }

    /**
     * 渲染 tooltip 背景（支持自定义样式）。
     * <p>先绘制背景填充层，再叠加边框层。</p>
     * <p>对标 26.1.2 {@code TooltipRenderUtil.extractTooltipBackground()}：
     * 背景区域 = 文字区域向外扩展 PADDING + MARGIN。</p>
     *
     * @param x     tooltip 文字区域左上 X
     * @param y     tooltip 文字区域左上 Y
     * @param w     文字区域宽度
     * @param h     文字区域高度
     * @param style 自定义样式 ID（可为 null，null 时使用默认纹理）
     */
    public static void renderTooltipBackground(int x, int y, int w, int h, @Nullable ResourceLocation style) {
        // 对标 26.1.2: x0 = x - PADDING - MARGIN, paddedWidth = w + PADDING*2 + MARGIN*2
        int bgX = x - PADDING - MARGIN;
        int bgY = y - PADDING - MARGIN;
        int bgW = w + PADDING_LEFT + PADDING_RIGHT + MARGIN * 2;
        int bgH = h + PADDING_TOP + PADDING_BOTTOM + MARGIN * 2;

        ResourceLocation bgTexture = getBackgroundTexture(style);
        ResourceLocation frameTexture = getFrameTexture(style);

        // 第一层：背景填充
        TextureStretching.drawAutoNinePatch(bgTexture, bgX, bgY, bgW, bgH, 100, 100, PADDING + MARGIN);

        // 第二层：边框
        TextureStretching.drawAutoNinePatch(frameTexture, bgX, bgY, bgW, bgH, 100, 100, PADDING + MARGIN);
    }

    /**
     * 获取背景纹理——支持自定义 style 路径。
     * <p>对标 26.1.2 {@code getBackgroundSprite(style)}。</p>
     */
    private static ResourceLocation getBackgroundTexture(@Nullable ResourceLocation style) {
        if (style == null) return BACKGROUND_TEXTURE;
        return new ResourceLocation(style.getResourceDomain(),
                "textures/gui/tooltips/" + style.getResourcePath() + "_background.png");
    }

    /**
     * 获取边框纹理——支持自定义 style 路径。
     * <p>对标 26.1.2 {@code getFrameSprite(style)}。</p>
     */
    private static ResourceLocation getFrameTexture(@Nullable ResourceLocation style) {
        if (style == null) return FRAME_TEXTURE;
        return new ResourceLocation(style.getResourceDomain(),
                "textures/gui/tooltips/" + style.getResourcePath() + "_frame.png");
    }
}
