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
 * (see {@link BuiltinPackDescriptor}), so packs can be registered by any mod
 * at any point, including the earliest load stages.
 * <p>
 * Registration is keyed by pack id and repeatable: registering the same id
 * again replaces the previous descriptor. The enabled state of a pack is
 * restored by name from {@code options.txt} at two points: the vanilla
 * repository constructor (which only reaches packs registered before it runs)
 * and the startup bootstrap that runs on the vanilla resource refresh
 * following preInit (which reaches every pack registered up to postInit — so
 * ordinary mods get the full behaviour by registering during preInit). A pack
 * registered even later, while the game is already running, shows up the next
 * time the repository rebuilds its entry list (opening the resource pack
 * screen); its enabled state cannot be restored automatically, because such a
 * registration only ever runs after the bootstrap.
 */
public final class BuiltinPackRegistry {

    private static final Logger LOGGER = LogManager.getLogger("CatFrame/BuiltinPacks");

    private static final Map<String, BuiltinPackDescriptor> PACKS = new LinkedHashMap<>();

    private BuiltinPackRegistry() {}

    /**
     * Registers (or replaces) a built-in pack. Safe to call from any thread,
     * at any point (see the class documentation for when the enabled state is
     * restored).
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
