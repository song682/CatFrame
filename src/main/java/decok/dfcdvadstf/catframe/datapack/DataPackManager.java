package decok.dfcdvadstf.catframe.datapack;

import decok.dfcdvadstf.catframe.datapack.content.RecipesContentLoader;
import decok.dfcdvadstf.catframe.datapack.content.TagsContentLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Datapack lifecycle orchestration (worklist A5).
 * <p>
 * One pipeline — discovery, load, validate, apply — triggered from the server
 * start event and from the {@code /datapack} command. Runtime is
 * server-authoritative (decision E3): the pipeline runs on the server thread
 * only; there is no client-side datapack state this round.
 * </p>
 * <p>
 * Reapply rules: the first {@link #bootstrap} always applies. Later bootstraps
 * reapply only when the active world directory changed (singleplayer world
 * switch), because the discovered world packs and their overlays would differ.
 * {@link #reload()} and {@link #setEnabled} always reapply. Every pass produces
 * one {@link DataPackErrors} report, logged as a whole and exposed to the
 * command layer.
 * </p>
 * <p>
 * After every pass the syncable content snapshots are broadcast to online
 * clients (worklist C5, see {@link DataPackSync}); failures there are logged
 * and never affect the apply result.
 * </p>
 */
public final class DataPackManager {

    private static final Logger LOGGER = LogManager.getLogger(DataPackManager.class);

    /** Loader order is significant: tags before recipes. */
    private static final List<DataContentLoader> LOADERS = new ArrayList<>();

    static {
        LOADERS.add(new TagsContentLoader());
        LOADERS.add(new RecipesContentLoader());
    }

    private static DataPackRepository repository;
    private static String lastWorldDirPath;
    private static DataPackErrors lastErrors = new DataPackErrors();

    private DataPackManager() {
    }

    /**
     * Entry point from the server start event. Discovers and applies on first
     * call; reapplies when the world changed. Failures are logged and never
     * block world loading.
     *
     * @param configDir FML config directory ({@code <gameDir>/config})
     * @param gameDir   game directory (parent of the config dir)
     * @param worldDir  per-save directory, or {@code null} when unavailable
     */
    public static synchronized void bootstrap(File configDir, File gameDir, File worldDir) {
        try {
            String worldPath = worldDir == null ? null : worldDir.getAbsolutePath();
            if (repository == null) {
                repository = new DataPackRepository(configDir, gameDir);
                repository.setWorldDir(worldDir);
                apply();
                lastWorldDirPath = worldPath;
                return;
            }
            if (!equalsSafe(lastWorldDirPath, worldPath)) {
                LOGGER.info("World directory changed ({} -> {}); reapplying data packs", lastWorldDirPath, worldPath);
                repository.setWorldDir(worldDir);
                apply();
                lastWorldDirPath = worldPath;
            }
        } catch (RuntimeException e) {
            LOGGER.error("Datapack bootstrap failed; continuing without data packs", e);
        }
    }

    /** Rescans all sources and reapplies. Requires a prior successful bootstrap. */
    public static synchronized void reload() {
        if (repository == null) {
            LOGGER.warn("Datapack reload requested before bootstrap; ignoring");
            return;
        }
        apply();
    }

    /**
     * Enables or disables a pack, persists the change and reapplies so it takes
     * effect immediately.
     *
     * @return {@code false} when the pack id is unknown
     */
    public static synchronized boolean setEnabled(String packId, boolean enabled) {
        if (repository == null || !repository.setEnabled(packId, enabled)) {
            return false;
        }
        apply();
        return true;
    }

    private static void apply() {
        DataPackErrors errors = new DataPackErrors();
        try {
            repository.discover(errors);
        } catch (RuntimeException e) {
            errors.error(null, null, "discovery failed: " + e);
        }
        for (DataContentLoader loader : LOADERS) {
            try {
                loader.unload(errors);
            } catch (RuntimeException e) {
                errors.error(null, loader.getId(), "unload failed: " + e);
            }
        }
        for (DataContentLoader loader : LOADERS) {
            try {
                loader.load(repository, errors);
            } catch (RuntimeException e) {
                errors.error(null, loader.getId(), "load failed: " + e);
            }
        }
        lastErrors = errors;
        errors.logAll();
        try {
            DataPackSync.broadcast();
        } catch (RuntimeException e) {
            LOGGER.error("Datapack sync broadcast failed", e);
        }
    }

    /** The repository, or {@code null} before the first bootstrap. */
    public static DataPackRepository getRepository() {
        return repository;
    }

    /** Problem report of the most recent apply pass. */
    public static DataPackErrors getLastErrors() {
        return lastErrors;
    }

    private static boolean equalsSafe(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
