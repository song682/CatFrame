package decok.dfcdvadstf.catframe.ui.screens.container.data;

/**
 * <p>
 * Integer-valued data slots for container synchronisation — used for furnace
 * progress, enchanting-table seed, beacon levels, etc.<br>
 * Counterpart of the high-version Minecraft {@code ContainerData} interface.
 * </p>
 */
public interface ContainerData {

    /**
     * @param index the data-slot index
     * @return the current value
     */
    int get(int index);

    /**
     * Set the value of a data slot.
     *
     * @param index the data-slot index
     * @param value the new value
     */
    void set(int index, int value);

    /**
     * @return the number of data slots
     */
    int getCount();
}
