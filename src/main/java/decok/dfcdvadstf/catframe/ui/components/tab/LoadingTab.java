package decok.dfcdvadstf.catframe.ui.components.tab;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.LoadingDotsWidgets;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import decok.dfcdvadstf.catframe.ui.layouts.FrameLayout;
import decok.dfcdvadstf.catframe.ui.layouts.LinearLayout;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import java.util.function.Consumer;

/**
 * <p>
 * Loading tab — displays a loading placeholder until content is ready. Its only
 * child is a {@link LoadingDotsWidgets} (centred title plus flowing dots).
 * Counterpart of the high-version {@code LoadingTab}.
 * </p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * new LoadingTab(
 *     Text.translatable("catframe:tab.loading"),
 *     Text.translatable("catframe:tab.loading.status")
 * );
 * }</pre>
 */
public class LoadingTab implements Tab {

    private final Text title;
    private final Text loadingTitle;
    protected final LinearLayout layout = new LinearLayout(LinearLayout.Axis.VERTICAL, LinearLayout.Alignment.CENTER);

    /**
     * @param title        tab button title
     * @param loadingTitle title shown on the loading placeholder (also used as
     *                     extra narration)
     */
    public LoadingTab(Text title, Text loadingTitle) {
        this.title = title;
        this.loadingTitle = loadingTitle;
        LoadingDotsWidgets loadingDotsWidgets = new LoadingDotsWidgets(loadingTitle);
        this.layout.defaultChildLayoutSetting().alignVerticallyMiddle().alignHorizontallyCenter();
        this.layout.addChild(loadingDotsWidgets, settings -> settings.paddingBottom(30));
    }

    @Override
    public Text getTabTitle() {
        return this.title;
    }

    @Override
    public Text getTabExtraNarration() {
        return this.loadingTitle;
    }

    @Override
    public void visitChildren(Consumer<GuiEventListener> childrenConsumer) {
        this.layout.visitChildren(child -> {
            if (child instanceof GuiEventListener) {
                childrenConsumer.accept((GuiEventListener) child);
            }
        });
    }

    @Override
    public void doLayout(ScreenRectangle screenRectangle) {
        this.layout.arrangeElements();
        FrameLayout.alignInRectangle(this.layout, screenRectangle.x, screenRectangle.y,
                screenRectangle.width, screenRectangle.height, 0.5F, 0.5F);
    }
}
