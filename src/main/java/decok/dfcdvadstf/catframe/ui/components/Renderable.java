package decok.dfcdvadstf.catframe.ui.components;

import decok.dfcdvadstf.catframe.ui.GuiGraphicsExtractor;

public interface Renderable {
    void extractRenderState(final GuiGraphicsExtractor graphicss, int mouseX, int mouseY, float partialTicks);
}
