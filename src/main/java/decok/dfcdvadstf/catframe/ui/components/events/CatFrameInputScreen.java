package decok.dfcdvadstf.catframe.ui.components.events;

import javax.annotation.Nullable;

/**
 * <p>
 * CatFrame 输入屏幕接口 —— 由宿主 {@code GuiScreen} 子类实现以「接入」CatFrame 的
 * 拆分键盘事件（keyPressed/keyReleased/charTyped）与 Tab 焦点导航。<br>
 * {@link decok.dfcdvadstf.catframe.mixin.middle.MixinGuiScreen} 会检测本接口，读取 LWJGL2
 * 键盘事件并自动路由到 {@link #getEventRoot()} 返回的根组件。
 * </p>
 * <p>
 * CatFrame input screen marker — implemented by a host {@code GuiScreen} subclass to opt
 * into CatFrame's split keyboard events (keyPressed/keyReleased/charTyped) and Tab focus
 * navigation. {@link decok.dfcdvadstf.catframe.mixin.middle.MixinGuiScreen} detects this
 * interface, reads the LWJGL2 keyboard event and auto-routes it to the component returned
 * by {@link #getEventRoot()}.
 * </p>
 * <p>
 * 使用契约 / Contract:<br>
 * 实现本接口的屏幕<strong>不应</strong>再在自己的 {@code keyTyped} 中手动向根组件转发按键，
 * 否则会造成重复分发（本机制已通过 {@code root.keyTyped} 做了兼容桥接）。<br>
 * A screen implementing this interface <strong>must not</strong> also forward keys to the
 * root manually inside its own {@code keyTyped}, otherwise events are dispatched twice
 * (this mechanism already bridges legacy {@code keyTyped} via {@code root.keyTyped}).
 * </p>
 */
public interface CatFrameInputScreen {

    /**
     * @return the root component/container that should receive keyboard input, or
     *         {@code null} to skip CatFrame dispatch this frame
     *         / 应接收键盘输入的根组件/容器；返回 {@code null} 则本帧跳过 CatFrame 分发
     */
    @Nullable
    GuiScreenEvent getEventRoot();

    /**
     * Whether this screen dispatches CatFrame split keyboard events on its own — e.g. by
     * overriding {@code GuiScreen.handleKeyboardInput()} and calling {@code ScreenKeyboardInput}
     * directly. When {@code true}, {@link decok.dfcdvadstf.catframe.mixin.middle.MixinGuiScreen}
     * <strong>must not</strong> dispatch for this screen, otherwise every key would be delivered
     * twice (once by the screen, once by the mixin).
     * <p>
     * Defaults to {@code false}: a foreign host {@code GuiScreen} (vanilla / another mod) that
     * merely implements this interface cannot self-dispatch, so the mixin drives it. The built-in
     * {@link decok.dfcdvadstf.catframe.ui.screens.Screen} base overrides this to {@code true}.
     * </p>
     * <p>
     * 本屏幕是否自行派发 CatFrame 拆分键盘事件——例如覆写 {@code GuiScreen.handleKeyboardInput()}
     * 并直接调用 {@code ScreenKeyboardInput}。返回 {@code true} 时，
     * {@link decok.dfcdvadstf.catframe.mixin.middle.MixinGuiScreen} <strong>不得</strong>再为本屏幕派发，
     * 否则每个按键会被投递两次（屏幕一次、mixin 一次）。<br>
     * 默认 {@code false}：仅实现本接口的外部宿主 {@code GuiScreen}（原版 / 他模组）无法自派发，
     * 由 mixin 代为驱动；内建 {@link decok.dfcdvadstf.catframe.ui.screens.Screen} 基类覆写为 {@code true}。
     * </p>
     *
     * @return {@code true} to opt out of mixin dispatch (self-dispatching screen)
     *         / 返回 {@code true} 表示自派发、放弃 mixin 派发
     */
    default boolean handlesKeyboardDispatchInternally() {
        return false;
    }
}
