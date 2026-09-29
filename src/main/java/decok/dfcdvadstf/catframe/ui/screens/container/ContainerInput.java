package decok.dfcdvadstf.catframe.ui.screens.container;

/**
 * <p>
 * Container click action type — semantic replacement for the magic-number
 * {@code mode} parameter of vanilla {@code Container.slotClick}.<br>
 * ID values are intentionally aligned with the vanilla {@code mode} integers
 * so that bridging is a simple cast.
 * </p>
 */
public enum ContainerInput {

    /** Pick up / place items. */
    PICKUP(0),

    /** Shift-click transfer. */
    QUICK_MOVE(1),

    /** Swap with hotbar slot. */
    SWAP(2),

    /** Creative-mode clone. */
    CLONE(3),

    /** Drop item. */
    THROW(4),

    /** Drag-distribute across slots. */
    QUICK_CRAFT(5),

    /** Collect all matching items. */
    PICKUP_ALL(6);

    private static final ContainerInput[] BY_ID = values();

    private final int id;

    ContainerInput(final int id) {
        this.id = id;
    }

    /**
     * @return the vanilla {@code mode} integer this input maps to
     */
    public int id() {
        return this.id;
    }

    /**
     * Resolve a vanilla {@code mode} integer to the corresponding enum constant.
     *
     * @param id the vanilla mode integer
     * @return the matching {@code ContainerInput}, or {@link #PICKUP} if out of range
     */
    public static ContainerInput byId(final int id) {
        if (id >= 0 && id < BY_ID.length) {
            return BY_ID[id];
        }
        return PICKUP;
    }
}
