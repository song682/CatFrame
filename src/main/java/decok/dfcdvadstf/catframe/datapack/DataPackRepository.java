package decok.dfcdvadstf.catframe.datapack;

import com.google.gson.JsonObject;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Datapack repository: discovery, enable/disable state and multi-pack overlay
 * lookup (worklist A1/A3/A4).
 * <p>
 * Sources, in ascending priority (later sources override earlier ones on pack-id
 * conflicts, and their overlays win):
 * <ol>
 * <li>{@code builtin} — mod jars and reachable classpath archives that embed a
 *     {@code datapacks/<name>/pack.mcmeta} subtree (this includes the CatFrame
 *     jar itself);</li>
 * <li>{@code config} — {@code config/catframe/datapacks/<name>/} (the
 *     TagLoader-style user-editable location);</li>
 * <li>{@code game} — {@code <gameDir>/datapacks/<name>/};</li>
 * <li>{@code world} — {@code <saveDir>/datapacks/<name>/} (per-save,
 *     singleplayer-aware; set by the manager at server start).</li>
 * </ol>
 * A pack is only recognized when its {@code pack.mcmeta} exists and parses
 * (corrupt metadata is reported and the pack skipped — never a crash).
 * </p>
 * <p>
 * Overlay lookup ({@link #listResources(String)}) returns entries of all
 * enabled packs ordered from lowest to highest priority. It exposes both merge
 * semantics (tags: every contribution counts, handled inside the tag loader)
 * and override semantics (recipes: same pack-relative path, later entry wins,
 * handled by the caller taking the last one per path).
 * </p>
 */
public final class DataPackRepository {

    private static final Logger LOGGER = LogManager.getLogger(DataPackRepository.class);

    public static final String SOURCE_BUILTIN = "builtin";
    public static final String SOURCE_CONFIG = "config";
    public static final String SOURCE_GAME = "game";
    public static final String SOURCE_WORLD = "world";

    public static final int TIER_BUILTIN = 0;
    public static final int TIER_CONFIG = 10;
    public static final int TIER_GAME = 20;
    public static final int TIER_WORLD = 30;

    private static final String DATAPACKS_DIR = "datapacks";
    private static final String DATAPACKS_PREFIX = "datapacks/";
    private static final String MCMETA_NAME = "pack.mcmeta";
    private static final String STATE_SUBDIR = "catframe";
    private static final String STATE_FILE = "datapack-state.json";

    private final File configDir;
    private final File gameDir;
    private final DataPackState state;

    /** {@code <save>/datapacks}, or {@code null} when no world is active. */
    private File worldDatapacksDir;

    private final List<DataPack> packs = new ArrayList<>();
    private final Map<String, DataPack> packsById = new HashMap<>();

    public DataPackRepository(File configDir, File gameDir) {
        this.configDir = configDir;
        this.gameDir = gameDir;
        File stateFile = configDir == null
                ? null
                : new File(new File(configDir, STATE_SUBDIR), STATE_FILE);
        this.state = DataPackState.load(stateFile);
        LOGGER.info("Data pack repository created (config={}, game={})", configDir, gameDir);
    }

    /**
     * Points the repository at the current save's datapack directory (or
     * {@code null} when no world is active). Takes effect on the next
     * {@link #discover(DataPackErrors)}.
     */
    public void setWorldDir(File worldDir) {
        this.worldDatapacksDir = worldDir == null ? null : new File(worldDir, DATAPACKS_DIR);
    }

    public DataPackState getState() {
        return state;
    }

    /**
     * Rescans every source and rebuilds the pack list, applying persisted
     * enable/disable state. Safe to call repeatedly; the list is replaced.
     */
    public void discover(DataPackErrors errors) {
        packs.clear();
        packsById.clear();

        discoverClasspath(errors);
        discoverDirectoryTree(configDatapacksDir(), SOURCE_CONFIG, TIER_CONFIG, errors);
        discoverDirectoryTree(gameDir == null ? null : new File(gameDir, DATAPACKS_DIR), SOURCE_GAME, TIER_GAME, errors);
        discoverDirectoryTree(worldDatapacksDir, SOURCE_WORLD, TIER_WORLD, errors);

        for (DataPack pack : packs) {
            pack.setEnabled(!state.isDisabled(pack.getId()));
        }
        LOGGER.info("Discovered {} data pack(s), {} enabled", packs.size(), getEnabledPacksByPriority().size());
    }

    private File configDatapacksDir() {
        return configDir == null ? null : new File(new File(configDir, STATE_SUBDIR), DATAPACKS_DIR);
    }

    // ==================== Discovery ====================

    private void discoverClasspath(DataPackErrors errors) {
        for (File candidate : reachableArchives()) {
            try {
                if (candidate.isDirectory()) {
                    discoverDirectoryTree(new File(candidate, DATAPACKS_DIR), SOURCE_BUILTIN, TIER_BUILTIN, errors);
                } else if (candidate.isFile()) {
                    discoverArchive(candidate, errors);
                }
            } catch (RuntimeException e) {
                errors.warning(null, candidate.getName(), "source scan failed: " + e);
            }
        }
    }

    /** Directory source: every immediate subdirectory with a valid {@code pack.mcmeta} is a pack. */
    private void discoverDirectoryTree(File dir, String sourceId, int tier, DataPackErrors errors) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!child.isDirectory() || !new File(child, MCMETA_NAME).isFile()) {
                continue;
            }
            PackContent content = PackContents.directory(child);
            PackMetadata metadata = readMetadata(content, child.getName(), errors);
            if (metadata == null || !metadata.isValid()) {
                continue;
            }
            addPack(new DataPack(child.getName(), sourceId, tier, content, metadata), errors);
        }
    }

    /** Archive source: every {@code datapacks/<name>/pack.mcmeta} entry is a pack root. */
    private void discoverArchive(File archive, DataPackErrors errors) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(archive);
            Map<String, String> roots = new LinkedHashMap<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(DATAPACKS_PREFIX) || !name.endsWith(MCMETA_NAME)) {
                    continue;
                }
                String rest = name.substring(DATAPACKS_PREFIX.length());
                // only exactly "<name>/pack.mcmeta" (no nesting)
                if (rest.isEmpty() || rest.indexOf('/') != rest.lastIndexOf('/')) {
                    continue;
                }
                String packName = rest.substring(0, rest.length() - MCMETA_NAME.length() - 1);
                if (!packName.isEmpty()) {
                    roots.put(packName, DATAPACKS_PREFIX + packName + "/");
                }
            }
            for (Map.Entry<String, String> root : roots.entrySet()) {
                PackContent content = PackContents.archive(archive, root.getValue());
                PackMetadata metadata = readMetadata(content, root.getKey(), errors);
                if (metadata == null || !metadata.isValid()) {
                    continue;
                }
                addPack(new DataPack(root.getKey(), SOURCE_BUILTIN, TIER_BUILTIN, content, metadata), errors);
            }
        } catch (IOException ignored) {
            // not a readable zip — common on mixed classpaths, skip silently
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                    // best-effort close
                }
            }
        }
    }

    private PackMetadata readMetadata(PackContent content, String packId, DataPackErrors errors) {
        String raw = content.read(MCMETA_NAME);
        JsonObject json = DataPackJson.parseObject(raw, packId, MCMETA_NAME, errors);
        if (json == null) {
            return null;
        }
        PackMetadata metadata = PackMetadata.parse(json);
        if (!metadata.isValid()) {
            errors.error(packId, MCMETA_NAME, "missing or invalid 'pack' section");
        }
        return metadata;
    }

    /**
     * Registers a discovered pack. Pack ids are global: a higher-priority
     * source silently replaces a lower-priority pack of the same id (reported
     * as info-level warning); an equal-or-lower priority duplicate is ignored.
     * The first pack of equal priority wins, keeping discovery order stable.
     */
    private void addPack(DataPack pack, DataPackErrors errors) {
        DataPack existing = packsById.get(pack.getId());
        if (existing == null) {
            packsById.put(pack.getId(), pack);
            packs.add(pack);
            return;
        }
        if (pack.getTier() > existing.getTier()) {
            packs.set(packs.indexOf(existing), pack);
            packsById.put(pack.getId(), pack);
            errors.warning(pack.getId(), null,
                    "pack from source '" + pack.getSourceId() + "' overrides the one from '" + existing.getSourceId() + "'");
        } else {
            errors.warning(pack.getId(), null,
                    "duplicate pack id ignored (keeps source '" + existing.getSourceId() + "')");
        }
    }

    /**
     * Collects candidate archives: the full classpath (dev/IDE scenarios),
     * the CodeSource of this class (launcher scenario for the CatFrame jar),
     * and every mod container source (launcher scenario for mod jars that are
     * dynamically loaded and absent from the classpath).
     */
    private Set<File> reachableArchives() {
        Set<File> files = new LinkedHashSet<>();
        String classpath = System.getProperty("java.class.path");
        if (classpath != null) {
            for (String part : classpath.split(Pattern.quote(File.pathSeparator))) {
                if (!part.isEmpty()) {
                    File file = new File(part);
                    if (file.exists()) {
                        addCanonical(files, file);
                    }
                }
            }
        }
        addCodeSource(DataPackRepository.class, files);
        try {
            for (ModContainer mod : Loader.instance().getModList()) {
                if (mod != null && mod.getSource() != null) {
                    addCanonical(files, mod.getSource());
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Could not enumerate mod sources for datapack discovery", e);
        }
        return files;
    }

    private static void addCodeSource(Class<?> cls, Set<File> files) {
        try {
            URL url = cls.getProtectionDomain().getCodeSource().getLocation();
            if (url != null && "file".equals(url.getProtocol())) {
                File file = new File(url.toURI());
                if (file.exists()) {
                    addCanonical(files, file);
                }
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // CodeSource unresolvable -> skip (non-fatal)
        }
    }

    private static void addCanonical(Set<File> files, File file) {
        try {
            files.add(file.getCanonicalFile());
        } catch (IOException e) {
            files.add(file.getAbsoluteFile());
        }
    }

    // ==================== State ====================

    /**
     * Enables or disables a pack and persists the change.
     *
     * @return {@code false} when no pack with the given id was discovered
     */
    public boolean setEnabled(String packId, boolean enabled) {
        DataPack pack = packsById.get(packId);
        if (pack == null) {
            return false;
        }
        pack.setEnabled(enabled);
        state.setDisabled(packId, !enabled);
        state.save();
        return true;
    }

    // ==================== Overlay lookup (A4) ====================

    /** All discovered packs, including disabled ones, in discovery order. */
    public List<DataPack> getPacks() {
        return Collections.unmodifiableList(packs);
    }

    /** Enabled packs sorted from lowest to highest priority (apply order). */
    public List<DataPack> getEnabledPacksByPriority() {
        List<DataPack> out = new ArrayList<>();
        for (DataPack pack : packs) {
            if (pack.isEnabled()) {
                out.add(pack);
            }
        }
        Collections.sort(out, new java.util.Comparator<DataPack>() {
            @Override
            public int compare(DataPack a, DataPack b) {
                return Integer.compare(a.getTier(), b.getTier());
            }
        });
        return out;
    }

    /**
     * Resolves a pack-relative prefix across all enabled packs, lowest priority
     * first. Entries of the same path may appear multiple times — merge/override
     * choices belong to the caller.
     */
    public List<PackResource> listResources(String prefix) {
        List<PackResource> out = new ArrayList<>();
        for (DataPack pack : getEnabledPacksByPriority()) {
            for (String path : pack.getContent().list(prefix)) {
                out.add(new PackResource(pack, path));
            }
        }
        return out;
    }

    /**
     * Enumerates all namespaces that appear under some pack's {@code data/}
     * directory, in overlay order.
     */
    public List<String> listNamespaces() {
        Set<String> namespaces = new LinkedHashSet<>();
        for (DataPack pack : getEnabledPacksByPriority()) {
            for (String path : pack.getContent().list("data/")) {
                int slash = path.indexOf('/', "data/".length());
                if (slash > "data/".length()) {
                    namespaces.add(path.substring("data/".length(), slash));
                }
            }
        }
        return new ArrayList<>(namespaces);
    }

    /** One resolved file of one pack. */
    public static final class PackResource {

        private final DataPack pack;
        private final String path;

        public PackResource(DataPack pack, String path) {
            this.pack = pack;
            this.path = path;
        }

        public DataPack getPack() {
            return pack;
        }

        /** Pack-relative path. */
        public String getPath() {
            return path;
        }

        /** File content, or {@code null} when unreadable. */
        public String read() {
            return pack.getContent().read(path);
        }

        @Override
        public String toString() {
            return pack.getId() + "/" + path;
        }
    }
}
