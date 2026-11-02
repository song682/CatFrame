package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.overlay.Overlay;
import decok.dfcdvadstf.catframe.ui.overlay.ScreenAnchor;
import decok.dfcdvadstf.catframe.ui.screens.Screen;

public class PopUpScreen extends Screen implements Overlay {
    protected PopUpScreen(Text title) {
        super(title);
    }

    @Override
    public ScreenAnchor getAnchor() {
        return null;
    }

    @Override
    public int getOffsetX() {
        return 0;
    }

    @Override
    public int getOffsetY() {
        return 0;
    }
}
