package decok.dfcdvadstf.catframe.ui.screens.container.data;

/**
 * <p>
 * A simple array-backed {@link ContainerData} implementation.
 * </p>
 */
public class SimpleContainerData implements ContainerData {

    private final int[] data;

    /**
     * @param count the number of data slots
     */
    public SimpleContainerData(final int count) {
        this.data = new int[count];
    }

    @Override
    public int get(final int index) {
        return this.data[index];
    }

    @Override
    public void set(final int index, final int value) {
        this.data[index] = value;
    }

    @Override
    public int getCount() {
        return this.data.length;
    }
}
