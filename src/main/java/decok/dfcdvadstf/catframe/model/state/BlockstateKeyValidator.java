package decok.dfcdvadstf.catframe.model.state;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.state.property.Property;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Blockstate variant key validator (aligned with vanilla 1.8+ semantics).
 *
 * <p>A variant key is composed of {@code property=value} check groups joined by commas
 * (e.g. {@code "facing=north,half=upper"}). Each group is validated against the block's
 * typed {@link BlockStateDefinition}:
 * <ul>
 *   <li>The property must be one the block actually has, otherwise the game logs
 *       {@code Unknown blockstate property: '<property>'}.</li>
 *   <li>The value must be a valid value of that property, otherwise the game logs
 *       {@code Unknown value: '<value>' for blockstate property: '<property>' [<values>]}.</li>
 * </ul>
 *
 * <p>If either the property or the value is invalid, the whole blockstate key and its
 * model mapping definition become invalid: the entry is replaced by the
 * {@code builtin/missing} model (purple-black MissingNo, see
 * {@link decok.dfcdvadstf.catframe.model.core.BuiltinMissingModel}).
 *
 * <p>Legacy keys of this project ({@code "normal"}, {@code "meta=N"}, pure number keys and
 * keys without {@code '='}) are not property-based and are skipped.
 *
 * <p>This class also rejects x/y rotation angles that are not multiples of 90 degrees
 * (see {@link #validateRotations}), mirroring vanilla's behaviour of refusing invalid
 * rotations instead of baking silently wrong geometry.
 *
 * <p>Client main thread only (model registration / render dispatch); no synchronization.
 */
@SideOnly(Side.CLIENT)
public final class BlockstateKeyValidator {

    private BlockstateKeyValidator() {
    }

    /**
     * BlockstateJson instances already validated — identity-based, so each JSON is only
     * validated (and mutated) once no matter how many call sites reach it.
     */
    private static final Set<BlockstateJson> VALIDATED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * BlockstateJson instances already rotation-checked — independent identity set, since
     * rotation validation is definition-independent and has different call sites than
     * {@link #validate}.
     */
    private static final Set<BlockstateJson> ROTATION_VALIDATED =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    /**
     * Validate all variant keys of a blockstate against the block's typed state definition.
     * Invalid keys keep their slot but their model mapping is replaced by
     * {@code builtin/missing}, so they render as the MissingNo model.
     *
     * @param bs    the loaded blockstate JSON (no-op if null or has no variants)
     * @param def   the typed state definition to validate against (no-op if null)
     * @param owner human-readable owner for log context (e.g. block registry name)
     */
    public static void validate(@Nullable BlockstateJson bs,
                                @Nullable BlockStateDefinition<?> def,
                                String owner) {
        if (bs == null || def == null || bs.variants == null || bs.variants.isEmpty()) return;
        if (!VALIDATED.add(bs)) return;

        // Rotation angles are definition-independent; piggyback on the same hub call
        // sites so every validated blockstate also gets its rotations checked.
        validateRotations(bs, owner);

        List<String> invalidKeys = null;
        for (String key : bs.variants.keySet()) {
            if (isLegacyKey(key)) continue;
            if (!isValidKey(key, def)) {
                if (invalidKeys == null) invalidKeys = new ArrayList<>();
                invalidKeys.add(key);
            }
        }

        if (invalidKeys != null) {
            for (String key : invalidKeys) {
                // Whole key + model mapping invalidated → builtin/missing (MissingNo)
                bs.variants.put(key, missingEntry());
                CatFrame.logger.warn(
                        "Invalid blockstate variant key '{}' in {}; its model mapping falls back to builtin/missing",
                        key, owner);
            }
        }
    }

    /**
     * Validate x/y rotation angles of every variant and multipart apply entry.
     * Aligned with vanilla: blockstate rotations only allow 90-degree increments
     * (0 / 90 / 180 / 270). Angles outside that set are rejected explicitly instead
     * of silently baking wrong geometry — faces would leave axis alignment and
     * face / cullface / AO recomputation would produce incorrect results.
     * Arbitrary-angle rotation is a mod-exclusive feature reserved for the
     * compatibility layer, not available in vanilla blockstate definitions.
     * Invalid variants fall back to {@code builtin/missing} (MissingNo); invalid
     * multipart apply entries are dropped entirely.
     *
     * <p>Definition-independent: can be called on any loaded blockstate, including ones
     * that never reach {@link #validate}.
     *
     * @param bs    the loaded blockstate JSON (no-op if null)
     * @param owner human-readable owner for log context (e.g. blockstate resource id)
     */
    public static void validateRotations(@Nullable BlockstateJson bs, String owner) {
        if (bs == null) return;
        if (!ROTATION_VALIDATED.add(bs)) return;

        if (bs.variants != null) {
            List<String> invalidKeys = null;
            for (Map.Entry<String, BlockstateJson.VariantEntry> e : bs.variants.entrySet()) {
                BlockstateJson.VariantEntry entry = e.getValue();
                if (entry == null) continue;
                boolean bad = false;
                if (entry.isArray()) {
                    for (BlockstateJson.Variant v : entry.list) {
                        if (!hasValidRotations(v)) bad = true;
                    }
                } else if (entry.single != null && !hasValidRotations(entry.single)) {
                    bad = true;
                }
                if (bad) {
                    if (invalidKeys == null) invalidKeys = new ArrayList<>();
                    invalidKeys.add(e.getKey());
                }
            }
            if (invalidKeys != null) {
                for (String key : invalidKeys) {
                    // Whole variant invalidated → builtin/missing (MissingNo)
                    bs.variants.put(key, missingEntry());
                    CatFrame.logger.warn(
                            "Invalid rotation angle (not a multiple of 90) in variant '{}' of {}; "
                                    + "its model mapping falls back to builtin/missing",
                            key, owner);
                }
            }
        }

        if (bs.multipart != null) {
            Iterator<BlockstateJson.MultipartCase> it = bs.multipart.iterator();
            while (it.hasNext()) {
                BlockstateJson.MultipartCase mpc = it.next();
                if (mpc.apply != null) {
                    boolean bad = false;
                    if (mpc.apply.isArray()) {
                        for (BlockstateJson.Variant v : mpc.apply.list) {
                            if (!hasValidRotations(v)) bad = true;
                        }
                    } else if (mpc.apply.single != null && !hasValidRotations(mpc.apply.single)) {
                        bad = true;
                    }
                    if (bad) {
                        it.remove();
                        CatFrame.logger.warn(
                                "Invalid rotation angle (not a multiple of 90) in a multipart apply entry of {}; "
                                        + "the entry is dropped",
                                owner);
                    }
                }
            }
        }
    }

    /**
     * A variant's rotations are valid iff x, y and z are multiples of 90 degrees.
     */
    private static boolean hasValidRotations(BlockstateJson.Variant v) {
        return v != null && isQuarterTurn(v.x) && isQuarterTurn(v.y) && isQuarterTurn(v.z);
    }

    /** Multiple-of-90 check for float angles (±0.01° tolerance). */
    private static boolean isQuarterTurn(float deg) {
        float remainder = Math.abs(deg % 90f);
        return remainder < 0.01f || Math.abs(remainder - 90f) < 0.01f;
    }

    // ==================== Internal: key parsing and validation ====================

    /**
     * Legacy / non-property keys skipped by validation: {@code "normal"}, empty keys,
     * {@code "meta=N"} and any key without {@code '='} (pure number keys, "inventory"…).
     */
    private static boolean isLegacyKey(String key) {
        if (key == null || key.isEmpty() || "normal".equals(key)) return true;
        if (key.indexOf('=') < 0) return true;
        if (key.startsWith("meta=")) {
            try {
                Integer.parseInt(key.substring(5));
                return true;
            } catch (NumberFormatException ignored) {
                // "meta=<non-numeric>" is not this project's meta convention, validate as a normal property group
            }
        }
        return false;
    }

    /**
     * Validate every {@code property=value} check group in one variant key.
     * Warnings are emitted per invalid group; the key is invalid if any group fails.
     */
    private static boolean isValidKey(String key, BlockStateDefinition<?> def) {
        boolean valid = true;
        for (String group : key.split(",")) {
            int eq = group.indexOf('=');
            if (eq < 0) {
                // Malformed group without '=' → cannot name a property, treat as unknown
                CatFrame.logger.warn("Unknown blockstate property: '{}'", group);
                valid = false;
                continue;
            }
            String propName = group.substring(0, eq).trim();
            String valueName = group.substring(eq + 1).trim();

            Property<?> prop = findProperty(def, propName);
            if (prop == null) {
                CatFrame.logger.warn("Unknown blockstate property: '{}'", propName);
                valid = false;
                continue;
            }
            if (!prop.getNames().contains(valueName)) {
                CatFrame.logger.warn("Unknown value: '{}' for blockstate property: '{}' {}",
                        valueName, propName, prop.getNames());
                valid = false;
            }
        }
        return valid;
    }

    /** Find a property by name in the definition, or null. */
    @Nullable
    private static Property<?> findProperty(BlockStateDefinition<?> def, String name) {
        for (Property<?> prop : def.getProperties()) {
            if (prop.getName().equals(name)) return prop;
        }
        return null;
    }

    /**
     * Build a replacement entry pointing at the {@code builtin/missing} model.
     */
    private static BlockstateJson.VariantEntry missingEntry() {
        BlockstateJson.Variant variant = new BlockstateJson.Variant();
        variant.model = "builtin/missing";
        BlockstateJson.VariantEntry entry = new BlockstateJson.VariantEntry();
        entry.single = variant;
        return entry;
    }
}
