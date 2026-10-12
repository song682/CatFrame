package decok.dfcdvadstf.catframe.datapack.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import decok.dfcdvadstf.catframe.datapack.DataContentLoader;
import decok.dfcdvadstf.catframe.datapack.DataPackErrors;
import decok.dfcdvadstf.catframe.datapack.DataPackJson;
import decok.dfcdvadstf.catframe.datapack.DataPackRepository;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Tint declarations content loader (worklist C3).
 * <p>
 * Reads every enabled pack's {@code data/<ns>/tints/**.json} into one ordered
 * snapshot. Each valid declaration is checked against the 1.7.10 registries
 * (a declaration for an unknown block/item is rejected here, before it ever
 * reaches a client). The snapshot is what the C5 sync channel ships to clients;
 * {@link #buildPayload()} serialises it in the canonical declaration form so
 * both ends run the same parse path.
 * </p>
 * <p>
 * Order is significant: packs are visited from lowest to highest priority, so
 * a higher-priority pack's declaration for the same (target, tintindex) pair
 * comes later and wins on the client side.
 * </p>
 */
public class TintsContentLoader implements DataContentLoader {

    private static final Logger LOGGER = LogManager.getLogger(TintsContentLoader.class);

    public static final String ID = "tints";

    /** Snapshot of the most recent load pass, in pack priority order. */
    private static final List<TintDeclaration> DECLARATIONS = new ArrayList<>();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void unload(DataPackErrors errors) {
        synchronized (DECLARATIONS) {
            DECLARATIONS.clear();
        }
    }

    @Override
    public void load(DataPackRepository repository, DataPackErrors errors) {
        int count = 0;
        synchronized (DECLARATIONS) {
            for (String namespace : repository.listNamespaces()) {
                count += loadDirectory(repository, errors, namespace);
            }
        }
        LOGGER.info("Loaded {} tint declaration(s) from data packs", count);
    }

    private int loadDirectory(DataPackRepository repository, DataPackErrors errors, String namespace) {
        String prefix = "data/" + namespace + "/tints/";
        int count = 0;
        for (DataPackRepository.PackResource resource : repository.listResources(prefix)) {
            String path = resource.getPath();
            String relative = path.substring(prefix.length());
            if (!relative.endsWith(".json")) {
                continue;
            }
            JsonObject json = DataPackJson.parseObject(resource.read(), resource.getPack().getId(), path, errors);
            if (json == null) {
                continue;
            }
            TintDeclaration declaration = TintDeclaration.parse(json, resource.getPack().getId(), path, errors);
            if (declaration == null) {
                continue;
            }
            if (!targetExists(declaration)) {
                errors.error(resource.getPack().getId(), path,
                        "unknown " + (declaration.getKind() == TintDeclaration.Kind.BLOCK ? "block" : "item")
                                + " '" + declaration.getTargetId() + "'");
                continue;
            }
            DECLARATIONS.add(declaration);
            count++;
        }
        return count;
    }

    /** Resolves the declaration target against the 1.7.10 registries (server-side check). */
    private static boolean targetExists(TintDeclaration declaration) {
        try {
            if (declaration.getKind() == TintDeclaration.Kind.BLOCK) {
                return Block.getBlockFromName(declaration.getTargetId()) != null;
            }
            return Item.itemRegistry.getObject(declaration.getTargetId()) != null;
        } catch (RuntimeException e) {
            // Malformed id characters can make the registry throw — treat as unknown.
            return false;
        }
    }

    // ==================== Sync snapshot (worklist C5) ====================

    /**
     * Serialises the current snapshot as a JSON array in canonical declaration
     * form — the payload of {@code PacketDataPackSync} for loader id
     * {@code "tints"}.
     */
    public static String buildPayload() {
        JsonArray array = new JsonArray();
        synchronized (DECLARATIONS) {
            for (TintDeclaration declaration : DECLARATIONS) {
                array.add(declaration.toJson());
            }
        }
        return DataPackJson.gson().toJson(array);
    }

    /** Number of declarations in the current snapshot. */
    public static int size() {
        synchronized (DECLARATIONS) {
            return DECLARATIONS.size();
        }
    }
}
