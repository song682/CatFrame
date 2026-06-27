package decok.dfcdvadstf.catframe.ui.components;

/**
 * <p>
 * 对象选择列表 —— 简化版选择列表，每个条目是一个简单可选条目。<br>
 * 对标高版本 Minecraft 的 {@code ObjectSelectionList}。
 * </p>
 * <p>
 * Object selection list — simplified selection list where each entry is a
 * simple selectable item. Counterpart of the high-version Minecraft
 * {@code ObjectSelectionList}.
 * </p>
 *
 * @param <E> the entry type / 条目类型
 */
public abstract class ObjectSelectionList<E extends ObjectSelectionList.Entry<E>> extends AbstractSelectionList<E> {

    public ObjectSelectionList(int width, int height, int y, int itemHeight) {
        super(width, height, y, itemHeight);
    }

    /**
     * <p>
     * 对象选择列表条目 —— 可被鼠标点击选中的简单条目。<br>
     * Entry for an object selection list — a simple entry that can be
     * selected by mouse click.
     * </p>
     *
     * @param <E> the entry type / 条目类型
     */
    public abstract static class Entry<E extends Entry<E>> extends AbstractSelectionList.Entry<E> {

        @Override
        public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            // Entries in ObjectSelectionList are always clickable
        }
    }
}
