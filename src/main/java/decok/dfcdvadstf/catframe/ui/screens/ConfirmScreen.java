package decok.dfcdvadstf.catframe.ui.screens;

import decok.dfcdvadstf.catframe.ui.Text;
import decok.dfcdvadstf.catframe.ui.components.Button;
import decok.dfcdvadstf.catframe.ui.components.MultiLineTextWidget;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;

import javax.annotation.Nullable;

/**
 * <p>
 * Confirmation screen — the CatFrame counterpart of vanilla
 * {@link net.minecraft.client.gui.GuiYesNo}. It keeps that screen's reaction
 * contract verbatim: the left button calls
 * {@link GuiYesNoCallback#confirmClicked(boolean, int)
 * confirmClicked(true, index)} on the screen that opened this one and the right
 * button calls {@code confirmClicked(false, index)}, so existing callbacks keep
 * working without an adapter and one callback can serve several questions
 * through the index.
 * </p>
 * <p>
 * The difference is the text: the question is drawn through
 * {@link MultiLineTextWidget}, which folds it with {@code StringSplitter} to the
 * space left by {@link #SIDE_MARGIN}, so a long question wraps instead of running
 * off the screen. The optional detail line follows the wrapped block — it keeps
 * vanilla's {@code y = 90} while the question fits on one line and moves down
 * when the question needs more. Button geometry is vanilla's
 * ({@code width / 2 - 155} and {@code + 160}, {@code height / 6 + 96}), clamped so
 * the pair never leaves a short screen.
 * </p>
 */
public class ConfirmScreen extends Screen {

    /** Space kept free on both sides so the question wraps inside the screen. */
    private static final int SIDE_MARGIN = 20;

    /** Y of the first question line — vanilla {@code GuiYesNo#drawScreen} uses 70. */
    private static final int MESSAGE_Y = 70;

    /** Y of the detail line while the question still fits on one line — vanilla uses 90. */
    private static final int DETAIL_Y = 90;

    /** Vertical gap between the wrapped question block and the detail line. */
    private static final int DETAIL_GAP = 2;

    /** Horizontal gap between the two buttons — vanilla steps the second one by 160 for a 150 wide button. */
    private static final int BUTTON_GAP = 10;

    /** Added to {@code height / 6} to place the buttons, as vanilla does. */
    private static final int BUTTON_Y_ANCHOR = 96;

    /** Space kept below the buttons so they stay reachable on a short screen. */
    private static final int BUTTON_BOTTOM_MARGIN = 26;

    /** The screen that opened this one; receives {@link GuiYesNoCallback#confirmClicked}. */
    protected final GuiYesNoCallback parentScreen;

    /** The question, wrapped to the screen width. */
    protected final Text message;

    /** Optional second line drawn below the question. */
    @Nullable
    protected final Text detail;

    /** Index handed back to the parent so one callback can serve several questions. */
    protected final int confirmIndex;

    /** Text shown on the button that confirms — vanilla defaults to {@code gui.yes}. */
    protected final Text confirmButtonText;

    /** Text shown on the button that cancels — vanilla defaults to {@code gui.no}. */
    protected final Text cancelButtonText;

    @Nullable
    private Button confirmButton;

    @Nullable
    private Button cancelButton;

    /** Remaining ticks both buttons stay disabled for; see {@link #disableButtons(int)}. */
    private int buttonDelayTicks;

    /**
     * Creates a confirmation screen with the vanilla button labels
     * ({@code gui.yes} / {@code gui.no}).
     *
     * @param parentScreen the screen that receives the answer
     * @param message      the question, wrapped to the screen width
     * @param detail       an optional second line ({@code null} to omit it)
     * @param confirmIndex the index handed back with the answer
     */
    public ConfirmScreen(GuiYesNoCallback parentScreen, Text message, @Nullable Text detail, int confirmIndex) {
        this(parentScreen, message, detail,
                Text.translatable("gui.yes"), Text.translatable("gui.no"), confirmIndex);
    }

    /**
     * Creates a confirmation screen with custom button labels — counterpart of the
     * six-argument vanilla {@code GuiYesNo} constructor.
     *
     * @param parentScreen      the screen that receives the answer
     * @param message           the question, wrapped to the screen width
     * @param detail            an optional second line ({@code null} to omit it)
     * @param confirmButtonText label of the confirm button ({@code null} falls back to {@code gui.yes})
     * @param cancelButtonText  label of the cancel button ({@code null} falls back to {@code gui.no})
     * @param confirmIndex      the index handed back with the answer
     */
    public ConfirmScreen(GuiYesNoCallback parentScreen, Text message, @Nullable Text detail,
                         @Nullable Text confirmButtonText, @Nullable Text cancelButtonText, int confirmIndex) {
        super((message != null) ? message : Text.literal(""));
        this.parentScreen = parentScreen;
        this.message = (message != null) ? message : Text.literal("");
        this.detail = detail;
        this.confirmButtonText = (confirmButtonText != null) ? confirmButtonText : Text.translatable("gui.yes");
        this.cancelButtonText = (cancelButtonText != null) ? cancelButtonText : Text.translatable("gui.no");
        this.confirmIndex = confirmIndex;
    }

    /**
     * Convenience constructor taking plain strings for the question and the detail
     * line — the closest match to how vanilla {@code GuiYesNo} is normally opened.
     *
     * @param parentScreen the screen that receives the answer
     * @param message      the question
     * @param detail       an optional second line ({@code null} to omit it)
     * @param confirmIndex the index handed back with the answer
     */
    public ConfirmScreen(GuiYesNoCallback parentScreen, String message, @Nullable String detail, int confirmIndex) {
        this(parentScreen, Text.literal(message),
                (detail != null) ? Text.literal(detail) : null, confirmIndex);
    }

    /**
     * Builds the text blocks and the button pair. Called by vanilla on first show and
     * on every resize, so the wrap width always matches the current screen width.
     */
    @Override
    protected void init() {
        int textWidth = Math.max(1, this.width - SIDE_MARGIN * 2);

        MultiLineTextWidget messageWidget = new MultiLineTextWidget(this.message);
        messageWidget.setMaxWidth(textWidth);
        messageWidget.setCentered(true);
        // getWidth() reflects the folded block, so the whole block can be centred.
        messageWidget.setX((this.width - messageWidget.getWidth()) / 2);
        messageWidget.setY(MESSAGE_Y);
        addRenderableOnly(messageWidget);

        if (this.detail != null && !this.detail.getString().isEmpty()) {
            MultiLineTextWidget detailWidget = new MultiLineTextWidget(this.detail);
            detailWidget.setMaxWidth(textWidth);
            detailWidget.setCentered(true);
            detailWidget.setX((this.width - detailWidget.getWidth()) / 2);
            // Keep vanilla's 90 until the wrapped question grows past it.
            detailWidget.setY(Math.max(DETAIL_Y, MESSAGE_Y + messageWidget.getHeight() + DETAIL_GAP));
            addRenderableOnly(detailWidget);
        }

        int buttonsX = this.width / 2 - (Button.DEFAULT_WIDTH * 2 + BUTTON_GAP) / 2;
        int buttonsY = Math.min(this.height / 6 + BUTTON_Y_ANCHOR, this.height - BUTTON_BOTTOM_MARGIN);

        this.confirmButton = this.addRenderableWidget(Button.builder(this.confirmButtonText,
                        button -> this.confirm(true))
                .pos(buttonsX, buttonsY)
                .width(Button.DEFAULT_WIDTH)
                .build());
        this.cancelButton = this.addRenderableWidget(Button.builder(this.cancelButtonText,
                        button -> this.confirm(false))
                .pos(buttonsX + Button.DEFAULT_WIDTH + BUTTON_GAP, buttonsY)
                .width(Button.DEFAULT_WIDTH)
                .build());

        if (this.buttonDelayTicks > 0) {
            this.setButtonsActive(false);
        }
    }

    /**
     * Hands the answer to the parent with the {@code GuiYesNo} signature. The parent
     * may be absent when this screen is only displayed for its layout, in which case
     * nothing is called.
     */
    private void confirm(boolean result) {
        if (this.parentScreen != null) {
            this.parentScreen.confirmClicked(result, this.confirmIndex);
        }
    }

    /**
     * Disables both buttons for the given number of ticks and re-enables them
     * afterwards — counterpart of vanilla {@code GuiYesNo#func_146350_a(int)}, which
     * stops the click that opened the screen from confirming it immediately.
     *
     * @param ticks how many ticks to stay disabled ({@code 0} re-enables at once)
     */
    public void disableButtons(int ticks) {
        this.buttonDelayTicks = Math.max(0, ticks);
        this.setButtonsActive(this.buttonDelayTicks == 0);
    }

    /** @return whether {@link #disableButtons(int)} is still counting down */
    public boolean areButtonsDelayed() {
        return this.buttonDelayTicks > 0;
    }

    @Override
    public void tick() {
        if (this.buttonDelayTicks > 0 && --this.buttonDelayTicks == 0) {
            this.setButtonsActive(true);
        }
    }

    private void setButtonsActive(boolean active) {
        if (this.confirmButton != null) {
            this.confirmButton.setActive(active);
        }
        if (this.cancelButton != null) {
            this.cancelButton.setActive(active);
        }
    }

    /**
     * Closes back onto the screen that opened this one rather than onto the game.
     * Vanilla {@code GuiYesNo} leaves the decision to its parent and falls through to
     * {@code GuiScreen}'s close; a modal confirmation is opened on top of another
     * screen here, so the parent is restored instead.
     */
    @Override
    public void onClose() {
        if (this.parentScreen instanceof GuiScreen && this.parentScreen != this) {
            this.mc.displayGuiScreen((GuiScreen) this.parentScreen);
            return;
        }
        super.onClose();
    }
}
