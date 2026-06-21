package decok.dfcdvadstf.catframe.ui.layouts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GridLayout — arranges children in a grid with row/column spanning support.
 * <p>
 * Children can be placed at specific row/column positions and can span multiple
 * rows or columns.  Use {@link #addChild(ILayout, int, int)} for simple placement,
 * or {@link #addChild(ILayout, int, int, int, int, LayoutSettings)} for spanning.
 * </p>
 *
 * <p>
 * For simple sequential filling, use {@link #createRowHelper(int)}.
 * </p>
 *
 * <p>
 * GridLayout —— 支持行/列跨越的网格布局。
 * 子元素可以放置在指定的行/列位置，并跨越多个行或列。
 * 对于简单的顺序填充，使用 {@link #createRowHelper(int)}。
 * </p>
 */
public class GridLayout extends AbstractLayout {

    private final List<ChildContainer> children = new ArrayList<>();
    private final LayoutSettings defaultCellSettings = LayoutSettings.defaults();
    private int rowSpacing = 0;
    private int columnSpacing = 0;
    private int nextAutoCol = 0;

    public GridLayout() {
        this(0, 0);
    }

    public GridLayout(int x, int y) {
        super();
        this.x = x;
        this.y = y;
    }

    // ──── Spacing ────

    public GridLayout columnSpacing(int columnSpacing) {
        this.columnSpacing = columnSpacing;
        return this;
    }

    public GridLayout rowSpacing(int rowSpacing) {
        this.rowSpacing = rowSpacing;
        return this;
    }

    public GridLayout spacing(int spacing) {
        return columnSpacing(spacing).rowSpacing(spacing);
    }

    // ──── Cell settings ────

    public LayoutSettings newCellSettings() {
        return this.defaultCellSettings.copy();
    }

    public LayoutSettings defaultCellSetting() {
        return this.defaultCellSettings;
    }

    // ──── Add children (positional) ────

    public <T extends ILayout> T addChild(T child, int row, int column) {
        return addChild(child, row, column, newCellSettings());
    }

    public <T extends ILayout> T addChild(T child, int row, int column, LayoutSettings cellSettings) {
        return addChild(child, row, column, 1, 1, cellSettings);
    }

    public <T extends ILayout> T addChild(T child, int row, int column, Consumer<LayoutSettings> configurator) {
        return addChild(child, row, column, 1, 1, makeSettings(configurator));
    }

    public <T extends ILayout> T addChild(T child, int row, int column, int rows, int columns) {
        return addChild(child, row, column, rows, columns, newCellSettings());
    }

    public <T extends ILayout> T addChild(
            T child, int row, int column, int rows, int columns, LayoutSettings cellSettings
    ) {
        if (rows < 1) throw new IllegalArgumentException("Occupied rows must be at least 1");
        if (columns < 1) throw new IllegalArgumentException("Occupied columns must be at least 1");
        this.children.add(new ChildContainer(child, row, column, rows, columns, cellSettings));
        super.add(child); // adds to AbstractLayout's child list + triggers recalculate
        return child;
    }

    public <T extends ILayout> T addChild(
            T child, int row, int column, int rows, int columns, Consumer<LayoutSettings> configurator
    ) {
        return addChild(child, row, column, rows, columns, makeSettings(configurator));
    }

    // ──── Add children (sequential, backward compatible) ────

    /**
     * Add a child sequentially (appends to the next available cell).
     * <p>顺序添加子元素（追加到下一个可用单元格）。</p>
     */
    public <T extends ILayout> T addChild(T child) {
        return addChild(child, newCellSettings());
    }

    /**
     * Add a child sequentially with the given cell settings.
     * <p>顺序添加子元素并指定单元格设置。</p>
     */
    public <T extends ILayout> T addChild(T child, LayoutSettings settings) {
        int col = this.nextAutoCol++;
        return addChild(child, 0, col, settings);
    }

    @Override
    public Layout add(ILayout child) {
        addChild(child);
        return this;
    }

    @Override
    public Layout add(ILayout child, LayoutSettings settings) {
        addChild(child, settings);
        return this;
    }

    @Override
    public Layout remove(ILayout child) {
        this.children.removeIf(w -> w.child == child);
        return super.remove(child);
    }

    @Override
    public void clear() {
        this.children.clear();
        this.nextAutoCol = 0;
        super.clear();
    }

    // ──── Visit ────

    @Override
    public void visitChildren(Consumer<ILayout> visitor) {
        this.children.forEach(w -> visitor.accept(w.child));
    }

    // ──── Recalculate ────

    @Override
    public void recalculate() {
        LAYOUT_LOG.debug("[GridLayout] recalculate: pos=({},{}), children={}",
                x, y, children.size());
        if (this.children.isEmpty()) {
            width = 0;
            height = 0;
            return;
        }

        int maxRow = 0;
        int maxColumn = 0;
        for (ChildContainer child : this.children) {
            maxRow = Math.max(child.getLastOccupiedRow(), maxRow);
            maxColumn = Math.max(child.getLastOccupiedColumn(), maxColumn);
        }

        int[] maxColumnWidths = new int[maxColumn + 1];
        int[] maxRowHeights = new int[maxRow + 1];

        for (ChildContainer child : this.children) {
            int childHeight = child.getHeight() - (child.occupiedRows - 1) * this.rowSpacing;
            int heightPerRow = child.occupiedRows > 0 ? ceilDiv(childHeight, child.occupiedRows) : 0;
            for (int r = child.row; r <= child.getLastOccupiedRow(); r++) {
                maxRowHeights[r] = Math.max(maxRowHeights[r], heightPerRow);
            }

            int childWidth = child.getWidth() - (child.occupiedColumns - 1) * this.columnSpacing;
            int widthPerCol = child.occupiedColumns > 0 ? ceilDiv(childWidth, child.occupiedColumns) : 0;
            for (int c = child.column; c <= child.getLastOccupiedColumn(); c++) {
                maxColumnWidths[c] = Math.max(maxColumnWidths[c], widthPerCol);
            }
        }


        int[] columnXOffsets = new int[maxColumn + 1];
        int[] rowYOffsets = new int[maxRow + 1];
        columnXOffsets[0] = 0;
        for (int c = 1; c <= maxColumn; c++) {
            columnXOffsets[c] = columnXOffsets[c - 1] + maxColumnWidths[c - 1] + this.columnSpacing;
        }

        rowYOffsets[0] = 0;
        for (int r = 1; r <= maxRow; r++) {
            rowYOffsets[r] = rowYOffsets[r - 1] + maxRowHeights[r - 1] + this.rowSpacing;
        }

        for (ChildContainer child : this.children) {
            int availableWidth = 0;
            for (int c = child.column; c <= child.getLastOccupiedColumn(); c++) {
                availableWidth += maxColumnWidths[c];
            }
            availableWidth += this.columnSpacing * (child.occupiedColumns - 1);
            child.setX(this.x + columnXOffsets[child.column], availableWidth);

            int availableHeight = 0;
            for (int r = child.row; r <= child.getLastOccupiedRow(); r++) {
                availableHeight += maxRowHeights[r];
            }
            availableHeight += this.rowSpacing * (child.occupiedRows - 1);
            child.setY(this.y + rowYOffsets[child.row], availableHeight);
        }

        this.width = columnXOffsets[maxColumn] + maxColumnWidths[maxColumn];
        this.height = rowYOffsets[maxRow] + maxRowHeights[maxRow];
    }

    // ──── RowHelper ────

    /**
     * Creates a helper that fills cells sequentially left-to-right, top-to-bottom.
     * <p>创建一个助手，按从左到右、从上到下的顺序填充单元格。</p>
     */
    public RowHelper createRowHelper(int columns) {
        return new RowHelper(columns);
    }

    // ──── Internal helpers ────

    private LayoutSettings makeSettings(Consumer<LayoutSettings> configurator) {
        LayoutSettings settings = newCellSettings();
        configurator.accept(settings);
        return settings;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    // ──── ChildContainer ────

    private static class ChildContainer extends AbstractChildWrapper {
        private final int row;
        private final int column;
        private final int occupiedRows;
        private final int occupiedColumns;

        private ChildContainer(
                ILayout widget, int row, int column, int occupiedRows, int occupiedColumns, LayoutSettings cellSettings
        ) {
            super(widget, cellSettings);
            this.row = row;
            this.column = column;
            this.occupiedRows = occupiedRows;
            this.occupiedColumns = occupiedColumns;
        }

        public int getLastOccupiedRow() {
            return this.row + this.occupiedRows - 1;
        }

        public int getLastOccupiedColumn() {
            return this.column + this.occupiedColumns - 1;
        }
    }

    // ──── RowHelper ────

    public final class RowHelper {
        private final int columns;
        private int index;

        private RowHelper(int columns) {
            this.columns = columns;
        }

        public <T extends ILayout> T addChild(T widget) {
            return addChild(widget, 1);
        }

        public <T extends ILayout> T addChild(T widget, int columnWidth) {
            return addChild(widget, columnWidth, defaultCellSetting());
        }

        public <T extends ILayout> T addChild(T widget, LayoutSettings layoutSettings) {
            return addChild(widget, 1, layoutSettings);
        }

        public <T extends ILayout> T addChild(T widget, int columnWidth, LayoutSettings layoutSettings) {
            int row = this.index / this.columns;
            int columnBegin = this.index % this.columns;
            if (columnBegin + columnWidth > this.columns) {
                row++;
                columnBegin = 0;
                this.index = roundUp(this.index, this.columns);
            }
            this.index += columnWidth;
            return GridLayout.this.addChild(widget, row, columnBegin, 1, columnWidth, layoutSettings);
        }

        public GridLayout getGrid() {
            return GridLayout.this;
        }

        public LayoutSettings newCellSettings() {
            return GridLayout.this.newCellSettings();
        }

        public LayoutSettings defaultCellSetting() {
            return GridLayout.this.defaultCellSetting();
        }

        private int roundUp(int value, int multiple) {
            return ((value + multiple - 1) / multiple) * multiple;
        }
    }
}
