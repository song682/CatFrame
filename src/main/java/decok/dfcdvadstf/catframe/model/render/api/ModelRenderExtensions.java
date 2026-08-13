package decok.dfcdvadstf.catframe.model.render.api;

import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;

/**
 * 模型渲染扩展注册门面 —— 外部模组注册 {@link IModelRenderExtension} 的<b>唯一对外入口</b>。
 * <p>
 * 委托给内部 {@link ModelRenderRegistry}（该类的注册面标注为内部实现）。
 * 注册契约与 v0.5+ 一致：优先级越小越先执行（默认 {@code 0}，内建扩展基址
 * {@code -1000} 位于链头），同优先级按注册顺序稳定排列，重复注册 = 更新优先级。
 * <p>
 * Facade for registering render extensions; the single external entry point
 * delegating to the internal registry.
 */
public final class ModelRenderExtensions {

    private ModelRenderExtensions() {
    }

    /**
     * 注册一个自定义渲染扩展（默认优先级 0，追加到同组链尾）。模组应在客户端 init 阶段调用。
     * 同一实例重复注册 = 以最后一次调用的优先级重新定位。
     */
    public static void register(IModelRenderExtension ext) {
        ModelRenderRegistry.register(ext);
    }

    /**
     * 以显式优先级注册渲染扩展。优先级越小越先执行，同优先级按注册顺序稳定排列。
     * 同一实例重复注册 = 更新优先级并重新定位。
     *
     * @param ext      渲染扩展（null 忽略）
     * @param priority 优先级（负值可插入内建扩展之前）
     */
    public static void register(IModelRenderExtension ext, int priority) {
        ModelRenderRegistry.register(ext, priority);
    }

    /**
     * 取消注册一个扩展。
     */
    public static void unregister(IModelRenderExtension ext) {
        ModelRenderRegistry.unregister(ext);
    }

    /**
     * 当前已注册的扩展数（含内建）。
     */
    public static int size() {
        return ModelRenderRegistry.size();
    }
}
