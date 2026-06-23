package decok.dfcdvadstf.catframe.exception;

import decok.dfcdvadstf.catframe.ui.tab.TabRegistry;

/**
 * <p>
 * 当 {@link TabRegistry} 注册时声明的 tabId 与 Tab 实例构造器内部的 tabId 不一致时抛出。<br>
 * Thrown when the tabId declared during {@link TabRegistry} registration
 * does not match the tabId baked into the Tab instance's constructor.
 * </p>
 *
 * <p>
 * 例如：<br>
 * Example:
 * <pre>{@code
 *   // Registered with tabId=103
 *   TabRegistry.registerTab("mybar", MyTab::new, 103, "mymod.tab.custom");
 *
 *   // But MyTab constructor uses tabId=999 → TabUncorrespondException
 *   public MyTab() { super(999, "wrong.key"); }
 * }</pre>
 * </p>
 */
public class TabUncorrespondException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final String barId;
    private final int expectedTabId;
    private final int actualTabId;
    private final String expectedNameKey;

    public TabUncorrespondException(String barId, int expectedTabId, int actualTabId, String expectedNameKey) {
        super(buildMessage(barId, expectedTabId, actualTabId, expectedNameKey));
        this.barId = barId;
        this.expectedTabId = expectedTabId;
        this.actualTabId = actualTabId;
        this.expectedNameKey = expectedNameKey;
    }

    private static String buildMessage(String barId, int expected, int actual, String nameKey) {
        return "Tab ID mismatch in bar '" + barId + "': "
                + "TabRegistry entry declares tabId=" + expected + " (nameKey=\"" + nameKey + "\"), "
                + "but the Tab instance returned tabId=" + actual + " from getTabId(). "
                + "Ensure the Tab constructor uses the same tabId as the registration call.";
    }

    public String getBarId() {
        return barId;
    }

    public int getExpectedTabId() {
        return expectedTabId;
    }

    public int getActualTabId() {
        return actualTabId;
    }

    public String getExpectedNameKey() {
        return expectedNameKey;
    }
}
