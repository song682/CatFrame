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
 * again replaces the previous descriptor. A pack registered while the game is
 * already running shows up the next time the repository rebuilds its entry
 * list (opening the resource pack screen); its persisted enabled state is
 * restored on the next launch only when the registration runs before the
 * repository is first built (the FML coremod stage), since the vanilla
 * repository constructor performs its name-matching restore at that point.
 * <p>
 * 内置资源包注册表。刻意不引用任何 Minecraft 类（见 {@link BuiltinPackDescriptor}），
 * 因此既可在 FML coremod 阶段注册，也可由任意模组在之后任意时刻注册。
 * 注册以包 id 为键、可重复：同 id 再次注册将替换旧描述。游戏运行中注册的包会在
 * 仓库下次重建条目列表时出现（打开资源包界面）；其持久化的启用状态仅当注册发生在
 * 仓库首次构建之前（FML coremod 阶段）时，才会在下次启动时被恢复——原版仓库
 * 构造器正是在那个时点执行按名匹配。
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
