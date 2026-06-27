package decok.dfcdvadstf.catframe.ui.components;

public interface TabOrderedElement {
    default int getTabOrderGroup() {
        return 0;
    }
}