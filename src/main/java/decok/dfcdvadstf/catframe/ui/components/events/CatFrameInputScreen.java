package decok.dfcdvadstf.catframe.ui.components.events;

import decok.dfcdvadstf.catframe.ui.components.Component;

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
    Component getEventRoot();
}
