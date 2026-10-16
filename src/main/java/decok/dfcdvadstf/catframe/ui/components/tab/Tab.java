package decok.dfcdvadstf.catframe.ui.components.tab;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import java.util.function.Consumer;

/**
 * <p>
 * Tab page contract — describes a tab's title, narration text, children
 * enumeration and layout. Counterpart of the high-version {@code Tab},
 * reduced to its four methods.
 * </p>
 */
public interface Tab {

    /**
     * @return the title shown on the tab button
     */
    Text getTabTitle();

    /**
     * @return extra narration text for accessibility
     */
    Text getTabExtraNarration();

    /**
     * Visit all child components of this tab — used by {@link TabManager} to
     * add / remove them when the tab becomes active / inactive.
     *
     * @param childrenConsumer consumer applied to every child
     */
    void visitChildren(Consumer<GuiEventListener> childrenConsumer);

    /**
     * Lay out this tab within the given screen rectangle.
     *
     * @param screenRectangle the tab content area
     */
    void doLayout(ScreenRectangle screenRectangle);
}
