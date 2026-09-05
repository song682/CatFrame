package decok.dfcdvadstf.catframe.ui.screens.container.data;

/**
 * <p>
 * Integer-valued data slots for container synchronisation — used for furnace
 * progress, enchanting-table seed, beacon levels, etc.<br>
 * Counterpart of the high-version Minecraft {@code ContainerData} interface.
 * </p>
 * <p>
 * 整数型数据槽，用于容器同步——熔炉进度、附魔台种子、信标等级等。<br>
 * 对标高版本 Minecraft 的 {@code ContainerData} 接口。
 * </p>
 */
public interface ContainerData {

    /**
     * @param index the data-slot index / 数据槽索引
     * @return the current value / 当前值
     */
    int get(int index);

    /**
     * Set the value of a data slot.
     * <p>设置数据槽的值。</p>
     *
     * @param index the data-slot index / 数据槽索引
     * @param value the new value / 新值
     */
    void set(int index, int value);

    /**
     * @return the number of data slots / 数据槽数量
     */
    int getCount();
}
