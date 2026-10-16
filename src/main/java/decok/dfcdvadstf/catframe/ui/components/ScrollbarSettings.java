package decok.dfcdvadstf.catframe.ui.components;

/**
 * <p>
 * Scrollbar configuration — describes the appearance and behaviour parameters
 * of a scrollbar. Counterpart of the high-version Minecraft
 * {@code AbstractScrollArea.ScrollbarSettings} record.
 * </p>
 */
public class ScrollbarSettings {

    /** Default scrollbar width in pixels */
    public static final int DEFAULT_SCROLLBAR_WIDTH = 6;

    /** Default minimum scroller (thumb) height */
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
     *
     * @param scrollRate scroll amount per wheel tick
     * @return default settings
     */
    public static ScrollbarSettings defaultSettings(int scrollRate) {
        return new ScrollbarSettings(DEFAULT_SCROLLBAR_WIDTH, DEFAULT_SCROLLER_MIN_HEIGHT, scrollRate);
    }

    /** @return scrollbar width in pixels */
    public int scrollbarWidth() {
        return scrollbarWidth;
    }

    /** @return minimum scroller (thumb) height */
    public int scrollerMinHeight() {
        return scrollerMinHeight;
    }

    /** @return scroll amount per wheel tick */
    public int scrollRate() {
        return scrollRate;
    }
}
