package decok.dfcdvadstf.catframe.resources.builtin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of built-in resource packs. Deliberately free of Minecraft classes
 * (see {@link BuiltinPackDescriptor}), so packs can be registered during the
 * FML coremod stage and by any mod at any later point.
 * <p>
 * Registration is keyed by pack id and repeatable: registering the same id
 * again replaces the previous descriptor. The enabled state of a pack is
 * restored by name from {@code options.txt} at two points: the vanilla
 * repository constructor (which only reaches packs registered before it runs,
 * the FML coremod stage) and the startup bootstrap that runs on the vanilla
 * resource refresh following preInit (which reaches every pack registered up
 * to postInit — so ordinary mods get the full behaviour by registering during
 * preInit). A pack registered even later, while the game is already running,
 * shows up the next time the repository rebuilds its entry list (opening the
 * resource pack screen); its enabled state cannot be restored automatically,
 * because such a registration only ever runs after the bootstrap.
 * <p>
 * 内置资源包注册表。刻意不引用任何 Minecraft 类（见 {@link BuiltinPackDescriptor}），
 * 因此既可在 FML coremod 阶段注册，也可由任意模组在之后任意时刻注册。
 * 注册以包 id 为键、可重复：同 id 再次注册将替换旧描述。包的启用状态会在两个
 * 时点按名从 {@code options.txt} 恢复：原版仓库构造器（只能覆盖在其运行之前
 * ——即 FML coremod 阶段——注册的包）与 preInit 之后那次原版资源刷新上的启动
 * 引导（可覆盖直到 postInit 为止注册的所有包，因此普通模组在 preInit 注册即可
 * 获得完整行为）。更晚注册（游戏已在运行）的包会在仓库下次重建条目列表时出现
 * （打开资源包界面），但其启用状态无法自动恢复——这类注册只会运行于引导之后。
 */
public final class BuiltinPackRegistry {

    private static final Logger LOGGER = LogManager.getLogger("CatFrame/BuiltinPacks");

    private static final Map<String, BuiltinPackDescriptor> PACKS = new LinkedHashMap<>();

    private BuiltinPackRegistry() {}

    /**
     * Registers (or replaces) a built-in pack. Safe to call from the coremod
     * stage and from the game thread.
     */
    public static synchronized void register(BuiltinPackDescriptor descriptor) {
        BuiltinPackDescriptor previous = PACKS.put(descriptor.getId(), descriptor);
        if (previous != null) {
            LOGGER.warn("Built-in resource pack '{}' was registered twice; the previous registration is replaced",
                    descriptor.getId());
        } else {
            LOGGER.info("Registered built-in resource pack '{}'", descriptor.getId());
        }
    }

    /**
     * Snapshot of all registered descriptors, in registration order.
     */
    public static synchronized Collection<BuiltinPackDescriptor> getDescriptors() {
        return Collections.unmodifiableCollection(new ArrayList<>(PACKS.values()));
    }

    /**
     * @return the descriptor registered for {@code id}, or {@code null} when
     *         the id is unknown
     */
    public static synchronized BuiltinPackDescriptor getDescriptor(String id) {
        return id == null ? null : PACKS.get(id);
    }
}
