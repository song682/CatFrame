package decok.dfcdvadstf.catframe.ui;

public class LoadingDotsText {
    private static final String[] FRAMES = new String[]{"o O o", "o o O", "O o o"};

    public static String get(final long timeMs) {
        int index = (int) (timeMs / 200L % (long) FRAMES.length);
        return FRAMES[index];
    }
}
