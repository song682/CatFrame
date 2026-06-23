package decok.dfcdvadstf.catframe.ui;

/**
 * <p>
 *     A simple String array for flowing loading.
 * </p>
 */
public class LoadingDotsText {
    private static final String[] FRAMES = new String[]{"o O o", "o o O", "O o o"};

    /**
     * Get {@code timeMs} Millisecond flowing paramators
     * @param timeMs
     * @return
     */
    public static String get(final long timeMs) {
        int index = (int) (timeMs / 200L % (long) FRAMES.length);
        return FRAMES[index];
    }
}
