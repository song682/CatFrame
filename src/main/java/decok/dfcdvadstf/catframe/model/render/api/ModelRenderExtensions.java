package decok.dfcdvadstf.catframe.model.render.api;

import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;

/**
 * Model render extension registration facade — the <b>single external entry point</b>
 * through which external mods register an {@link IModelRenderExtension}.
 * <p>
 * Delegates to the internal {@link ModelRenderRegistry} (that class's registration
 * surface is marked as an internal implementation). The registration contract matches
 * v0.5+: a lower priority executes first (default {@code 0}; the built-in extension
 * base {@code -1000} sits at the head of the chain), equal priorities are ordered
 * stably by registration order, and re-registering the same instance = updating its
 * priority.
 */
public final class ModelRenderExtensions {

    private ModelRenderExtensions() {
    }

    /**
     * Registers a custom render extension (default priority 0, appended to the tail of
     * its group). Mods should call this during the client init stage. Re-registering
     * the same instance = relocating it with the priority of the last call.
     */
    public static void register(IModelRenderExtension ext) {
        ModelRenderRegistry.register(ext);
    }

    /**
     * Registers a render extension with an explicit priority. A lower priority executes
     * first; equal priorities are ordered stably by registration order. Re-registering
     * the same instance = updating its priority and relocating it.
     *
     * @param ext      render extension (null is ignored)
     * @param priority priority (a negative value can insert before built-in extensions)
     */
    public static void register(IModelRenderExtension ext, int priority) {
        ModelRenderRegistry.register(ext, priority);
    }

    /**
     * Unregisters an extension.
     */
    public static void unregister(IModelRenderExtension ext) {
        ModelRenderRegistry.unregister(ext);
    }

    /**
     * Number of currently registered extensions (including built-in ones).
     */
    public static int size() {
        return ModelRenderRegistry.size();
    }
}
