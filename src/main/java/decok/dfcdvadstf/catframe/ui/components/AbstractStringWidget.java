package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Style;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class AbstractStringWidget extends AbstractComponent {
    @Nullable
    private Consumer<Style> componentClick = null;

}
