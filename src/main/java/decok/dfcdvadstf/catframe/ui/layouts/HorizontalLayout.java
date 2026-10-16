package decok.dfcdvadstf.catframe.ui.layouts;

/**
 * HorizontalLayout — a linear layout that arranges children left to right.
 * <p>
 * Convenience wrapper around {@link LinearLayout} preset with
 * {@link LinearLayout.Axis#HORIZONTAL} and
 * {@link LinearLayout.Alignment#CENTER} (vertical centering by default).
 * </p>
 */
public class HorizontalLayout extends LinearLayout {

    /**
     * Creates a horizontal layout with centre alignment on the Y axis.
     */
    public HorizontalLayout() {
        super(Axis.HORIZONTAL, Alignment.CENTER);
    }

    /**
     * Creates a horizontal layout with the given perpendicular alignment.
     */
    public HorizontalLayout(Alignment alignment) {
        super(Axis.HORIZONTAL, alignment);
    }
}
