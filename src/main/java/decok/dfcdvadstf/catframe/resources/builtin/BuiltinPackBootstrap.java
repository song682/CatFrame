package decok.dfcdvadstf.catframe.resources.builtin;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * One-shot startup bootstrap of the built-in resource packs, invoked from the
 * head of every vanilla {@code Minecraft.refreshResources()} call.
 * <p>
 * FML refreshes the resources exactly twice during startup: once in the
 * {@code finally} block of {@code beginMinecraftLoading} (after
 * {@code loadMods}, still in {@link LoaderState#PREINITIALIZATION}) and once in
 * {@code finishMinecraftLoading} right after {@code initializeMods} returned.
 * The second call is the first one made with the loader in
 * {@link LoaderState#INITIALIZATION} or beyond — after every preInit and
 * postInit handler has run — so it is the earliest stable point at which packs
 * registered by ordinary mods are complete. It is therefore used as the
 * bootstrap point: the repository is rebuilt (re-injecting the registered
 * built-in entries) and the enabled state is restored from
 * {@code GameSettings.resourcePacks} by name — the same restore the vanilla
 * repository constructor performs for packs that already existed when it ran.
 * Because the refresh itself reads the enabled entries afterwards, a pack
 * restored here is live for that very launch.
 * <p>
 * 内置资源包的启动引导（一次性），由原版 {@code Minecraft.refreshResources()}
 * 的 HEAD 调用。FML 在启动期间恰好刷新两次资源：一次在
 * {@code beginMinecraftLoading} 的 finally 块中（loadMods 之后、仍处于
 * {@code PREINITIALIZATION}），一次在 {@code finishMinecraftLoading} 中
 * {@code initializeMods} 返回之后。第二次是加载器达到
 * {@code INITIALIZATION} 之后的首次调用——此时所有 preInit/postInit 处理器
 * 都已执行完毕——因此它是普通模组注册完成的第一个稳定时点。本引导就落在这里：
 * 重建仓库（重新注入已注册的内置条目）并按名从
 * {@code GameSettings.resourcePacks} 恢复启用状态——与包在仓库首次构建前
 * 就已存在时原版构造器所做的恢复相同。由于原版刷新随后读取的正是"已启用条目"，
 * 在此恢复的包当次启动即生效。
 */
public final class BuiltinPackBootstrap {

    private static final Logger LOGGER = LogManager.getLogger("CatFrame/BuiltinPacks");

    /** Set once the startup window has passed; later refreshes are left untouched. */
    private static boolean bootstrapped;

    private BuiltinPackBootstrap() {}

    /**
     * Runs the bootstrap the first time a refresh happens with the loader in
     * {@link LoaderState#INITIALIZATION} or beyond. Called from the head of
     * {@code Minecraft.refreshResources()} on the client thread.
     */
    public static void onRefreshResources(Minecraft minecraft) {
        if (bootstrapped || !Loader.instance().hasReachedState(LoaderState.INITIALIZATION)) {
            return;
        }
        // Close the window with the first refresh that follows preInit: anything
        // registered later is a runtime registration and keeps the plain rebuild
        // semantics (see BuiltinPackRegistry) instead of triggering a late restore.
        bootstrapped = true;
        if (BuiltinPackRegistry.getDescriptors().isEmpty()) {
            return;
        }
        ResourcePackRepository repository = minecraft.getResourcePackRepository();
        try {
            repository.updateRepositoryEntriesAll();
            restoreSelectedFromOptions(repository, minecraft.gameSettings.resourcePacks);
        } catch (Throwable throwable) {
            LOGGER.error("Failed to bootstrap the built-in resource packs at startup", throwable);
        }
    }

    /**
     * Rebuilds the enabled-entry list from the persisted pack names, mirroring
     * the vanilla repository constructor's name-matching loop (the equivalent
     * of {@code Options.loadSelectedResourcePacks} in modern versions). Runs
     * after the repository rebuild, so entries are taken from the fresh list.
     */
    private static void restoreSelectedFromOptions(ResourcePackRepository repository, List selectedNames) {
        List available = repository.getRepositoryEntriesAll();
        List<ResourcePackRepository.Entry> selected = new ArrayList<>(selectedNames.size());
        for (Object selectedName : selectedNames) {
            String name = (String) selectedName;
            for (Object candidate : available) {
                ResourcePackRepository.Entry entry = (ResourcePackRepository.Entry) candidate;
                if (entry.getResourcePackName().equals(name)) {
                    selected.add(entry);
                    break;
                }
            }
        }
        repository.func_148527_a(selected);
    }
}
