package decok.dfcdvadstf.catframe.ui;

import decok.dfcdvadstf.catframe.ui.components.AbstractComponent;
import decok.dfcdvadstf.catframe.ui.components.CyclingButton;

/**
 * <p>
 * Legacy compatibility wrapper — delegates to {@link CyclingButton}.<br>
 * Existing code can migrate transparently through this class.
 * </p>
 * <p><strong>NOTE (BREAKING CHANGE):</strong> This class no longer extends the {@code GuiButton}, <br>
 * instead it extends {@link AbstractComponent}. Return from Builder {@code GuiCyclableButton.Builder} <br>
 * changed to {@code CyclingButton.Builder}, the {@code build()} signature removed {@code id} parameter.
 * Legacy API users n</p>
 *
 * @param <T> the type of values to cycle through
 * @deprecated  Use {@link CyclingButton} instead
 */
@Deprecated
public class GuiCyclableButton<T> extends AbstractComponent {

    private final CyclingButton<T> delegate;

    // ──── Delegate constructors via Builder ────

    private GuiCyclableButton(CyclingButton<T> delegate) {
        super(delegate.getX(), delegate.getY(), delegate.getWidth(), delegate.getHeight());
        this.delegate = delegate;
    }

    /**
     * Creates a new Builder for a GuiCyclableButton with the given value-to-text function.
     */
    public static <T> CyclingButton.Builder<T> builder(CyclingButton.ValueToText<T> valueToText) {
        return CyclingButton.builder(valueToText);
    }

    /**
     * Convenience builder for Boolean on/off cycling.
     */
    public static CyclingButton.Builder<Boolean> onOffBuilder() {
        return CyclingButton.onOffBuilder();
    }

    // ──── Wrapped constructor (old style, uses Builder internally) ────

    GuiCyclableButton(int id, int x, int y, int width, int height,
                      String label, int index, T value,
                      CyclingButton.Values<T> values,
                      CyclingButton.ValueToText<T> valueToText,
                      CyclingButton.UpdateCallback<T> callback) {
        super(x, y, width, height);
        this.delegate = CyclingButton.<T>builder(valueToText)
            .values(values)
            .initially(value)
            .label(label)
            .build(x, y, width, height, callback);
    }

    // ──── Delegated methods ────

    public void mouseScrolled(int delta) {
        delegate.mouseScrolled(delta);
    }

    public T getValue() {
        return delegate.getValue();
    }

    public void setValue(T value) {
        delegate.setValue(value);
    }

    public void updateText() {
        // CyclingButton autoupdates; this is handled internally
    }

    public CyclingButton<T> getDelegate() {
        return delegate;
    }

    // ──── Component overrides ────

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        delegate.render(mouseX, mouseY, partialTicks);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        delegate.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public int getX() {
        return delegate.getX();
    }

    @Override
    public void setX(int x) {
        delegate.setX(x);
    }

    @Override
    public int getY() {
        return delegate.getY();
    }

    @Override
    public void setY(int y) {
        delegate.setY(y);
    }

    @Override
    public int getWidth() {
        return delegate.getWidth();
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public boolean isVisible() {
        return delegate.isVisible();
    }

    @Override
    public void setVisible(boolean visible) {
        delegate.setVisible(visible);
    }

    @Override
    public boolean isActive() {
        return delegate.isActive();
    }

    @Override
    public void setActive(boolean active) {
        delegate.setActive(active);
    }
}
