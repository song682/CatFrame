package decok.dfcdvadstf.catframe.compact.ime;

import decok.dfcdvadstf.catframe.compact.CompactBase;
import decok.dfcdvadstf.catframe.ui.components.AbstractEditBox;
import decok.dfcdvadstf.catframe.ui.components.AbstractTextAreaWidget;
import decok.dfcdvadstf.catframe.ui.components.events.ContainerEventHandler;
import decok.dfcdvadstf.catframe.ui.components.events.GuiEventListener;
import decok.dfcdvadstf.catframe.ui.overlay.Overlay;
import decok.dfcdvadstf.catframe.ui.overlay.OverlayManager;
import decok.dfcdvadstf.catframe.ui.overlay.ScreenAnchor;
import decok.dfcdvadstf.imeinput.api.IMECommitTarget;
import decok.dfcdvadstf.imeinput.api.IMEInputAPI;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

/**
 * <p>
 * IME 输入回传兼容层 —— 把 CatFrame 自绘文本框体系（{@link AbstractTextAreaWidget}
 * 及其子类，如 {@link AbstractEditBox} 与 {@code MultilineEditBox}）接入
 * IMEInputBackport 的输入管线：任一可见 Overlay 的组件树中出现聚焦的文本区域时
 * 激活 IME，提交文本整体插入到光标处（与手动粘贴同一路径，过滤/长度限制/刷新
 * 回调全部生效），并为候选窗提供文本框锚点。
 * </p>
 * <p>
 * IME Input Backport compat — hooks CatFrame's self-drawn text-area family
 * ({@link AbstractTextAreaWidget} and subclasses such as
 * {@link AbstractEditBox}
 * and {@code MultilineEditBox}) into the IMEInputBackport input pipeline: a
 * focused text area inside any visible Overlay activates the IME, committed
 * text is inserted at the caret as a whole (the same path as a manual paste —
 * filtering, length limits and the refresh callbacks all apply), and the
 * active text area anchors the candidate window.
 * </p>
 * <p>
 * 线程模型：所有方法由 IMEInputBackport 在客户端 tick（主线程）调用，与
 * {@code IMEInputAPI} 的注册约定一致；本类在 ClientProxy.preInit 注册一次，
 * 客户端生命周期内无需注销。
 * </p>
 */
public final class IMECompact implements IMECommitTarget {

    /** 单例：注册进 {@link IMEInputAPI} 的实例 */
    private static final IMECompact INSTANCE = new IMECompact();

    private IMECompact() {
    }

    /**
     * IMEInputBackport 已加载时注册为 IME 提交目标（客户端初始化调用一次）。
     * Registers as an IME commit target when IMEInputBackport is loaded (call
     * once at client init).
     */
    public static void register() {
        if (isLoaded()) {
            IMEInputAPI.register(INSTANCE);
        }
    }

    /**
     * IMEInputBackport 是否已加载（modid = {@code ime_input_backport}）。
     * Whether IMEInputBackport is loaded (modid {@code ime_input_backport}).
     *
     * @return true = 已加载
     */
    public static boolean isLoaded() {
        return CompactBase.isIMEBackportInstalled();
    }

    @Override
    public boolean shouldReceiveInput() {
        // 任一可见 Overlay 的组件树中存在聚焦的文本区域即接收输入；
        // AbstractEditBox 与 MultilineEditBox 均落在 AbstractTextAreaWidget 之下。
        // Any visible Overlay whose component tree holds a focused text area
        // claims IME input; both AbstractEditBox and MultilineEditBox fall
        // under AbstractTextAreaWidget.
        return findFocusedArea() != null;
    }

    @Override
    public void commitText(String text) {
        // 整体插入到聚焦文本框（等同 Ctrl+V 粘贴路径），刷新逻辑只触发一次。
        // 现查焦点而非复用 shouldReceiveInput 的结果，防止同一 tick 内焦点
        // 切换后把文本写进已失焦的控件。
        // Bulk-insert into the focused text area (same path as Ctrl+V paste),
        // refreshing exactly once. The focus is resolved anew instead of reusing
        // shouldReceiveInput's result, so a focus switch within the same tick
        // cannot route the text into a control that just lost focus.
        AbstractTextAreaWidget area = findFocusedArea();
        if (area != null) {
            area.insertText(text);
        }
    }

    @Override
    public int getCaretX() {
        AbstractTextAreaWidget area = findFocusedArea();
        if (area == null) {
            return -1;
        }
        int x = area.getX() + area.innerPadding();
        if (area instanceof AbstractEditBox) {
            // 单行框：跟随光标（光标前文本宽度）；多行框退化到文本起点。
            // Single-line box: follow the caret (text-prefix width); multi-line
            // boxes fall back to the text start.
            AbstractEditBox box = (AbstractEditBox) area;
            String text = box.getText();
            int cursor = Math.min(box.getCursorPosition(), text.length());
            x += Minecraft.getMinecraft().fontRenderer.getStringWidth(text.substring(0, cursor));
        }
        return x;
    }

    @Override
    public int getCaretY() {
        // 文本框底沿（光标下沿），对齐 IMEHandler 对 vanilla 字段的默认回退。
        // Bottom edge of the text area (below the caret), mirroring the
        // IMEHandler fallback for vanilla fields.
        AbstractTextAreaWidget area = findFocusedArea();
        return area != null ? area.getY() + area.getHeight() : -1;
    }

    /**
     * 在 OverlayManager 的可见 Overlay 中沿焦点链查找聚焦的文本区域。
     * 组件坐标是布局解析后的屏幕绝对坐标（GUI 逻辑坐标），可直接用于锚点。
     * 包可见：{@link IgIMECompact} 复用同一套查找逻辑。
     * Finds the focused text area by walking the focus chain of every visible
     * Overlay registered in OverlayManager. Component coordinates are
     * layout-resolved screen absolutes (GUI units), usable as anchors directly.
     * Package-visible: {@link IgIMECompact} reuses the same lookup.
     *
     * @return 聚焦的文本区域；无则 null
     */
    @Nullable
    static AbstractTextAreaWidget findFocusedArea() {
        OverlayManager manager = OverlayManager.INSTANCE;
        for (ScreenAnchor anchor : ScreenAnchor.values()) {
            for (Overlay overlay : manager.getOverlays(anchor)) {
                if (!overlay.isVisible()) {
                    continue;
                }
                AbstractTextAreaWidget area = findFocusedIn(overlay);
                if (area != null) {
                    return area;
                }
            }
        }
        return null;
    }

    /**
     * 沿组件焦点链递归查找聚焦的文本区域。
     * Recursively walks the component focus chain for a focused text area.
     *
     * @param node 组件 / component
     * @return 聚焦的文本区域；无则 null
     */
    @Nullable
    private static AbstractTextAreaWidget findFocusedIn(GuiEventListener node) {
        if (node instanceof AbstractTextAreaWidget) {
            AbstractTextAreaWidget area = (AbstractTextAreaWidget) node;
            return area.isActive() && area.isVisible() && area.isFocused() ? area : null;
        }
        if (node instanceof ContainerEventHandler) {
            GuiEventListener focused = ((ContainerEventHandler) node).getFocused();
            if (focused != null) {
                return findFocusedIn(focused);
            }
        }
        return null;
    }
}
