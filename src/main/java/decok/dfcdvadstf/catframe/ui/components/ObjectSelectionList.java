package decok.dfcdvadstf.catframe.ui.components;

/**
 * <p>
 * Object selection list — simplified selection list where each entry is a
 * simple selectable item. Counterpart of the high-version Minecraft
 * {@code ObjectSelectionList}.
 * </p>
 *
 * @param <E> the entry type
 */
public abstract class ObjectSelectionList<E extends ObjectSelectionList.Entry<E>> extends AbstractSelectionList<E> {

    public ObjectSelectionList(int width, int height, int y, int itemHeight) {
        super(width, height, y, itemHeight);
    }

    /**
     * <p>
     * Entry for an object selection list — a simple entry that can be
     * selected by mouse click.
     * </p>
     *
     * @param <E> the entry type
     */
    public abstract static class Entry<E extends Entry<E>> extends AbstractSelectionList.Entry<E> {

        @Override
        public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            // Entries in ObjectSelectionList are always clickable
        }
    }
}
