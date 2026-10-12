package decok.dfcdvadstf.catframe.datapack.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import decok.dfcdvadstf.catframe.datapack.DataPackErrors;
import decok.dfcdvadstf.catframe.datapack.DataPackJson;
import net.minecraft.util.ResourceLocation;

/**
 * One tint declaration of the datapack pipeline (worklist C3) — the first
 * CatFrame-native content type, fully owned by this codebase.
 * <p>
 * JSON shape (one declaration per {@code data/<ns>/tints/**.json} file):
 * <pre>{@code
 * {
 *   "block": "minecraft:leaves",       // or "item": "<id>" — exactly one of the two
 *   "tintindex": 0,                    // optional; absent = every tintindex of the model
 *   "type": "minecraft:constant",      // required
 *   "value": 7987546                   // type-specific, see below
 * }
 * }</pre>
 * Type vocabulary:
 * <ul>
 * <li>{@code minecraft:constant} — {@code "value"}: an int (decimal) or a hex
 *     string ({@code "#RRGGBB"} / {@code "RRGGBB"}).</li>
 * <li>{@code minecraft:biome} — {@code "variant"}: {@code "foliage"} or
 *     {@code "grass"}. Water is deliberately not offered: 1.7.10 has no
 *     per-biome water colour read path ({@code BiomeGenBase.waterColorMultiplier}
 *     has no render-side consumer in this version).</li>
 * </ul>
 * Parsing never throws; problems are reported through {@link DataPackErrors}
 * with the declaration skipped. {@link #toJson()} rebuilds the canonical form,
 * which is what the server ships to clients over the sync channel (C5), so both
 * ends run the same validation.
 */
public final class TintDeclaration {

    /** Which registry the {@link #target} id refers to. */
    public enum Kind {
        BLOCK,
        ITEM
    }

    /** Built-in declaration types (the dispatch table worklist C3 is proving). */
    public enum TintType {
        CONSTANT("minecraft:constant"),
        BIOME("minecraft:biome");

        private final String id;

        TintType(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        /** Resolves a JSON {@code "type"} string, or {@code null} when unknown. */
        public static TintType byId(String id) {
            for (TintType type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    private final Kind kind;
    private final ResourceLocation target;
    private final int tintIndex;
    private final TintType type;
    private final int color;
    private final String variant;

    private TintDeclaration(Kind kind, ResourceLocation target, int tintIndex, TintType type,
                            int color, String variant) {
        this.kind = kind;
        this.target = target;
        this.tintIndex = tintIndex;
        this.type = type;
        this.color = color;
        this.variant = variant;
    }

    // ==================== Parsing ====================

    /**
     * Parses one declaration object, reporting every problem instead of
     * throwing.
     *
     * @param json   the declaration object (one pack file)
     * @param packId producing pack (for the report, nullable)
     * @param path   pack-relative file path (for the report, nullable)
     * @param errors consolidated report sink
     * @return the parsed declaration, or {@code null} when invalid
     */
    public static TintDeclaration parse(JsonObject json, String packId, String path, DataPackErrors errors) {
        boolean hasBlock = json.has("block");
        boolean hasItem = json.has("item");
        if (hasBlock == hasItem) {
            errors.error(packId, path, "declaration must carry exactly one of 'block' or 'item'");
            return null;
        }
        Kind kind = hasBlock ? Kind.BLOCK : Kind.ITEM;
        ResourceLocation target = parseTarget(DataPackJson.getString(json, hasBlock ? "block" : "item", null),
                packId, path, errors);
        if (target == null) {
            return null;
        }

        int tintIndex = -1;
        if (json.has("tintindex")) {
            JsonElement element = json.get("tintindex");
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                errors.error(packId, path, "'tintindex' must be an integer");
                return null;
            }
            tintIndex = element.getAsInt();
            if (tintIndex < -1) {
                errors.error(packId, path, "'tintindex' must be >= 0 (or omitted for all indices)");
                return null;
            }
        }

        String typeId = DataPackJson.getString(json, "type", null);
        TintType type = typeId == null ? null : TintType.byId(typeId);
        if (type == null) {
            errors.error(packId, path, typeId == null
                    ? "missing required field 'type'"
                    : "unknown tint type '" + typeId + "'");
            return null;
        }

        int color = 0;
        String variant = null;
        switch (type) {
            case CONSTANT:
                Integer parsed = parseColor(json.get("value"));
                if (parsed == null) {
                    errors.error(packId, path,
                            "type 'minecraft:constant' requires 'value' as an int or \"#RRGGBB\" (0x000000-0xFFFFFF)");
                    return null;
                }
                color = parsed;
                break;
            case BIOME:
                variant = DataPackJson.getString(json, "variant", null);
                if (!"foliage".equals(variant) && !"grass".equals(variant)) {
                    errors.error(packId, path,
                            "type 'minecraft:biome' requires 'variant' to be one of: foliage, grass");
                    return null;
                }
                break;
            default:
                errors.error(packId, path, "unhandled tint type '" + type.getId() + "'");
                return null;
        }

        return new TintDeclaration(kind, target, tintIndex, type, color, variant);
    }

    private static ResourceLocation parseTarget(String raw, String packId, String path, DataPackErrors errors) {
        if (raw == null || raw.trim().isEmpty()) {
            errors.error(packId, path, "target id must not be empty");
            return null;
        }
        try {
            // 1.7.10 ResourceLocation: a bare name defaults to the "minecraft" namespace.
            return new ResourceLocation(raw.trim());
        } catch (RuntimeException e) {
            errors.error(packId, path, "invalid target id '" + raw + "': " + e.getMessage());
            return null;
        }
    }

    /** Accepts a decimal JSON number or a hex string, bounded to 24-bit RGB. */
    private static Integer parseColor(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        try {
            long value;
            if (primitive.isNumber()) {
                value = primitive.getAsLong();
            } else if (primitive.isString()) {
                String text = primitive.getAsString().trim();
                if (text.startsWith("#")) {
                    text = text.substring(1);
                }
                if (text.isEmpty() || text.length() > 6) {
                    return null;
                }
                value = Long.parseLong(text, 16);
            } else {
                return null;
            }
            if (value < 0 || value > 0xFFFFFF) {
                return null;
            }
            return (int) value;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ==================== Serialization ====================

    /**
     * Rebuilds the canonical declaration object. This exact form travels over
     * the sync channel, so the client parses its payload through the same
     * {@link #parse} path the server used.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(kind == Kind.BLOCK ? "block" : "item", target.toString());
        if (tintIndex >= 0) {
            json.addProperty("tintindex", tintIndex);
        }
        json.addProperty("type", type.getId());
        switch (type) {
            case CONSTANT:
                json.addProperty("value", color);
                break;
            case BIOME:
                json.addProperty("variant", variant);
                break;
            default:
                break;
        }
        return json;
    }

    // ==================== Getters ====================

    public Kind getKind() {
        return kind;
    }

    /** Normalized target id ({@code "ns:path"}; bare names get {@code minecraft:}). */
    public ResourceLocation getTarget() {
        return target;
    }

    /** Normalized target id as a string, ready for registry lookup. */
    public String getTargetId() {
        return target.toString();
    }

    /** Specific tintindex, or {@code -1} meaning every index of the target model. */
    public int getTintIndex() {
        return tintIndex;
    }

    public TintType getType() {
        return type;
    }

    /** RGB value for {@link TintType#CONSTANT}; 0 otherwise. */
    public int getColor() {
        return color;
    }

    /** Variant for {@link TintType#BIOME} ({@code foliage}/{@code grass}); {@code null} otherwise. */
    public String getVariant() {
        return variant;
    }
}
