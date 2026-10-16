package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.Text;

public class SimpleButton extends Button {

    protected SimpleButton(int x, int y, int width, int height, Text message, OnPress onPress, boolean useVanillaTexture) {
        super(x, y, width, height, message, onPress, true);
    }

}
