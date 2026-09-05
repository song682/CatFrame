package decok.dfcdvadstf.catframe.ui;

public enum TextAlignment {
    LEFT {
        @Override
        public int calcLeft(int anchor, int width) {
            return 0;
        }
    },
    CENTER {
        @Override
        public int calcLeft(int anchor, int width) {
            return 0;
        }
    },
    RIGHT {
        @Override
        public int calcLeft(int anchor, int width) {
            return 0;
        }
    };

    public abstract int calcLeft(int anchor, int width);

    public int calcleft(int anchor, int width) {
        return this.calcLeft(anchor, width);
    }
}
