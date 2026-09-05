package decok.dfcdvadstf.catframe.ui.screens.container.data;

/**
 * <p>
 * A simple array-backed {@link ContainerData} implementation.
 * </p>
 * <p>
 * 基于数组的简单 {@link ContainerData} 实现。
 * </p>
 */
public class SimpleContainerData implements ContainerData {

    private final int[] data;

    /**
     * @param count the number of data slots / 数据槽数量
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
