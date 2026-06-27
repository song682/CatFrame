package decok.dfcdvadstf.catframe.ui.components;

/**
 * <p>
 * 滚动条配置 —— 描述滚动条的外观和行为参数。<br>
 * 对标高版本 Minecraft 的 {@code AbstractScrollArea.ScrollbarSettings} record。
 * </p>
 * <p>
 * Scrollbar configuration — describes the appearance and behaviour parameters
 * of a scrollbar. Counterpart of the high-version Minecraft
 * {@code AbstractScrollArea.ScrollbarSettings} record.
 * </p>
 */
public class ScrollbarSettings {

    /** Default scrollbar width in pixels / 默认滚动条宽度 */
    public static final int DEFAULT_SCROLLBAR_WIDTH = 6;

    /** Default minimum scroller (thumb) height / 默认滑块最小高度 */
    public static final int DEFAULT_SCROLLER_MIN_HEIGHT = 32;

    private final int scrollbarWidth;
    private final int scrollerMinHeight;
    private final int scrollRate;

    public ScrollbarSettings(int scrollbarWidth, int scrollerMinHeight, int scrollRate) {
        this.scrollbarWidth = scrollbarWidth;
        this.scrollerMinHeight = scrollerMinHeight;
        this.scrollRate = scrollRate;
    }

    /**
     * Create a default settings instance with the given scroll rate.
     * <p>使用给定的滚动量创建默认配置。</p>
     *
     * @param scrollRate scroll amount per wheel tick / 每次滚轮的滚动量
     * @return default settings / 默认配置
     */
    public static ScrollbarSettings defaultSettings(int scrollRate) {
        return new ScrollbarSettings(DEFAULT_SCROLLBAR_WIDTH, DEFAULT_SCROLLER_MIN_HEIGHT, scrollRate);
    }

    /** @return scrollbar width in pixels / 滚动条宽度 */
    public int scrollbarWidth() {
        return scrollbarWidth;
    }

    /** @return minimum scroller (thumb) height / 滑块最小高度 */
    public int scrollerMinHeight() {
        return scrollerMinHeight;
    }

    /** @return scroll amount per wheel tick / 每次滚轮的滚动量 */
    public int scrollRate() {
        return scrollRate;
    }
}
