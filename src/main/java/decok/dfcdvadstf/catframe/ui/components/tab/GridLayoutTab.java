package decok.dfcdvadstf.catframe.ui.components.tab;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import decok.dfcdvadstf.catframe.ui.layouts.FrameLayout;
import decok.dfcdvadstf.catframe.ui.layouts.GridLayout;
import decok.dfcdvadstf.catframe.ui.navigation.ScreenRectangle;

import java.util.function.Consumer;

/**
 * <p>
 * 基于 {@link GridLayout} 的标签页 —— 子元素按网格排列，整个布局水平居中、
 * 垂直约 1/6 处（{@code 0.16666667F}）对齐。<br>
 * 对标 26.1.2 {@code net.minecraft.client.gui.components.tabs.GridLayoutTab}。
 * </p>
 * <p>
 * A Tab backed by a {@link GridLayout} — children are arranged in a grid and the
 * whole layout is centred horizontally and aligned at ~1/6 from the top.
 * Counterpart of the high-version {@code GridLayoutTab}.
 * </p>
 *
 * <p>Usage / 用法:</p>
 * <pre>{@code
 * public class MyTab extends GridLayoutTab {
 *     public MyTab() {
 *         super(Text.translatable("mymod.tab.mytab"));
 *         layout.addChild(new SomeWidget(), 0, 0);
 *         layout.addChild(new SomeWidget(), 0, 1);
 *     }
 * }
 * }</pre>
 */
public class GridLayoutTab implements Tab {

    private final Text title;
    protected final GridLayout layout = new GridLayout();

    public GridLayoutTab(Text title) {
        this.title = title;
    }

    @Override
    public Text getTabTitle() {
        return this.title;
    }

    @Override
    public Text getTabExtraNarration() {
        return Text.literal("");
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
                screenRectangle.width, screenRectangle.height, 0.5F, 0.16666667F);
    }
}
