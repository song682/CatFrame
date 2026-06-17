package decok.dfcdvadstf.catframe.ui.layouts;

public interface ILayout {
    int getX();

    void setX(int x);

    int getY();

    void setY(int y);

    int getWidth();

    int getHeight();

    default void setPosition(final int x, final int y) {
        this.setX(x);
        this.setY(y);
    }
}
