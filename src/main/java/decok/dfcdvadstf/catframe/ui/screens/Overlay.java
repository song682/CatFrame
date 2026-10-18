package decok.dfcdvadstf.catframe.ui.screens;

import decok.dfcdvadstf.catframe.ui.components.Renderable;

public abstract class Overlay implements Renderable {
    public boolean isPauseScreen() {
        return true;
    }

    public void tick(){}
}
