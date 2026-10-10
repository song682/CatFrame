package decok.dfcdvadstf.catframe.datapack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Parsed {@code pack.mcmeta} of one datapack (worklist A2).
 * <p>
 * The on-disk shape mirrors the familiar {@code {"pack": {"pack_format": n,
 * "description": ...}}} document. {@link #CURRENT_PACK_FORMAT} is this module's
 * 1.7.10 baseline format number; any numeric value found on disk is accepted
 * and only reported, never gated on — packs written by modern tooling carry
 * modern format numbers, and rejecting them would fail for no benefit while the
 * payloads themselves are parsed strictly.
 * </p>
 * <p>
 * {@code description} may be a plain string or a chat component object
 * ({@code {"text": ...}} / {@code {"translate": ...}}); the display string is
 * extracted best-effort without resolving translations.
 * </p>
 * <p>
 * Corrupt metadata must never crash the game: {@link #parse} returns
 * {@link #INVALID} for anything unusable, and the repository turns that into a
 * report entry and skips the pack.
 * </p>
 */
public final class PackMetadata {

    /** 1.7.10 datapack baseline format number used when the file omits one. */
    public static final int CURRENT_PACK_FORMAT = 1;

    /** Singleton returned for corrupt/unusable metadata. */
    public static final PackMetadata INVALID = new PackMetadata(CURRENT_PACK_FORMAT, "", false);

    private final int packFormat;
    private final String description;
    private final boolean valid;

    private PackMetadata(int packFormat, String description, boolean valid) {
        this.packFormat = packFormat;
        this.description = description;
        this.valid = valid;
    }

    /**
     * Parses a {@code pack.mcmeta} root object.
     *
     * @param meta parsed document root, may be {@code null}
     * @return parsed metadata, or {@link #INVALID} when the {@code pack} section
     *         is missing or not an object
     */
    public static PackMetadata parse(JsonObject meta) {
        if (meta == null) {
            return INVALID;
        }
        JsonObject pack = DataPackJson.getObject(meta, "pack");
        if (pack == null) {
            return INVALID;
        }
        int format = DataPackJson.getInt(pack, "pack_format", CURRENT_PACK_FORMAT);
        String description = descriptionToString(pack.get("description"));
        return new PackMetadata(format, description, true);
    }

    private static String descriptionToString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            try {
                return element.getAsString();
            } catch (RuntimeException e) {
                return "";
            }
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String text = DataPackJson.getString(object, "text", null);
            if (text != null) {
                return text;
            }
            String translate = DataPackJson.getString(object, "translate", null);
            if (translate != null) {
                return translate;
            }
        }
        return element.toString();
    }

    public int getPackFormat() {
        return packFormat;
    }

    /** Display description; empty string when absent or unserializable. */
    public String getDescription() {
        return description;
    }

    public boolean isValid() {
        return valid;
    }
}
