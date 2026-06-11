package decok.dfcdvadstf.catframe.ui.layouts;

/**
 * GridLayout — arranges children in a fixed-column grid.
 * <p>
 * Children fill left-to-right, then top-to-bottom. Each cell can optionally
 * have an explicit size; otherwise cells are sized to fit the largest child.
 * Children are centred within their cell.
 * </p>
 *
 * <p>
 * GridLayout —— 将子元素按固定列数排布成网格。
 * 从左到右、从上到下填充。每个格子可以指定固定尺寸，
 * 否则根据最大子元素自适应。子元素在格子内居中。
 * </p>
 */
public class GridLayout extends AbstractLayout {

    private int columns = 2;
    private int cellWidth;
    private int cellHeight;

    /**
     * Creates a GridLayout with 2 columns and auto-sized cells. / 创建 2 列自适应网格。
     */
    public GridLayout() {
    }

    /**
     * Creates a GridLayout with the given column count. / 创建指定列数的网格。
     */
    public GridLayout(int columns) {
        this.columns = Math.max(1, columns);
    }

    /**
     * Creates a GridLayout with fixed cell size. / 创建固定格子尺寸的网格。
     */
    public GridLayout(int columns, int cellWidth, int cellHeight) {
        this.columns = Math.max(1, columns);
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
    }

    // ──── Properties ────

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = Math.max(1, columns);
        recalculate();
    }

    public int getCellWidth() {
        return cellWidth;
    }

    public int getCellHeight() {
        return cellHeight;
    }

    public void setCellSize(int width, int height) {
        this.cellWidth = width;
        this.cellHeight = height;
        recalculate();
    }

    // ──── Recalculate ────

    @Override
    public void recalculate() {
        if (children.isEmpty()) {
            width = 0;
            height = 0;
            return;
        }

        int rows = (int) Math.ceil((double) children.size() / columns);

        // Determine effective cell size — explicit or from largest child
        int maxChildW = 0;
        int maxChildH = 0;
        for (ILayout child : children) {
            if (maxChildW < child.getWidth()) maxChildW = child.getWidth();
            if (maxChildH < child.getHeight()) maxChildH = child.getHeight();
        }

        int effW = cellWidth > 0 ? cellWidth : maxChildW;
        int effH = cellHeight > 0 ? cellHeight : maxChildH;

        width = columns * effW + (columns - 1) * spacing + padding * 2;
        height = rows * effH + (rows - 1) * spacing + padding * 2;

        for (int i = 0; i < children.size(); i++) {
            ILayout child = children.get(i);
            int col = i % columns;
            int row = i / columns;

            int cx = x + padding + col * (effW + spacing);
            int cy = y + padding + row * (effH + spacing);

            // Centre the child within its cell
            cx += (effW - child.getWidth()) / 2;
            cy += (effH - child.getHeight()) / 2;

            child.setPosition(cx, cy);
        }
    }
}
