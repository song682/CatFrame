package decok.dfcdvadstf.catframe.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Enable/disable state of discovered datapacks, persisted as a small JSON file
 * (worklist A3, decision E2: a JSON beside the packs in the config directory —
 * {@code config/catframe/datapack-state.json}).
 * <p>
 * Only disabled packs are stored; anything not listed is enabled. This keeps
 * the file stable across pack discoveries and makes the default (all packs
 * active) recoverable by deleting the file. Missing or corrupt state files
 * degrade to "everything enabled" and never block startup.
 * </p>
 */
public final class DataPackState {

    private static final Logger LOGGER = LogManager.getLogger(DataPackState.class);

    private static final String KEY_DISABLED = "disabled";

    private final File file;
    private final Set<String> disabled = new LinkedHashSet<>();

    private DataPackState(File file) {
        this.file = file;
    }

    /**
     * Loads the state file; any failure degrades to an empty (all-enabled)
     * state.
     */
    public static DataPackState load(File file) {
        DataPackState state = new DataPackState(file);
        if (file == null || !file.isFile()) {
            return state;
        }
        InputStream stream = null;
        try {
            stream = new FileInputStream(file);
            JsonElement element = DataPackJson.gson().fromJson(
                    new java.io.InputStreamReader(stream, PackContents.UTF_8), JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                return state;
            }
            JsonArray array = DataPackJson.getArray(element.getAsJsonObject(), KEY_DISABLED);
            if (array != null) {
                for (JsonElement entry : array) {
                    if (entry != null && entry.isJsonPrimitive()) {
                        state.disabled.add(entry.getAsString());
                    }
                }
            }
        } catch (RuntimeException | java.io.IOException e) {
            LOGGER.warn("Could not read datapack state from {}; assuming everything enabled", file.getPath(), e);
        } finally {
            close(stream);
        }
        return state;
    }

    public boolean isDisabled(String packId) {
        return disabled.contains(packId);
    }

    public void setDisabled(String packId, boolean isDisabled) {
        if (isDisabled) {
            disabled.add(packId);
        } else {
            disabled.remove(packId);
        }
    }

    /** Writes the state file; IO failures are logged, never thrown. */
    public void save() {
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            LOGGER.warn("Could not create datapack state directory {}", parent.getPath());
            return;
        }
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (String id : disabled) {
            array.add(new JsonPrimitive(id));
        }
        root.add(KEY_DISABLED, array);
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), PackContents.UTF_8);
            writer.write(DataPackJson.gson().toJson(root));
        } catch (java.io.IOException e) {
            LOGGER.warn("Could not save datapack state to {}", file.getPath(), e);
        } finally {
            close(writer);
        }
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (java.io.IOException ignored) {
                // best-effort close
            }
        }
    }
}
