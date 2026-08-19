package decok.dfcdvadstf.catframe.model.state;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.state.property.Property;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Blockstate variant key validator (aligned with vanilla 1.8+ semantics).
 * 方块状态 variant 键校验器（对齐原版 1.8+ 语义）。
 *
 * <p>A variant key is composed of {@code property=value} check groups joined by commas
 * (e.g. {@code "facing=north,half=upper"}). Each group is validated against the block's
 * typed {@link CatStateDefinition}:
 * <br>variant 键由逗号连接的 {@code 属性=属性值} 检查属性组构成（如
 * {@code "facing=north,half=upper"}），每组都要对照方块的 typed
 * {@link CatStateDefinition} 校验：
 * <ul>
 *   <li>The property must be one the block actually has, otherwise the game logs
 *       {@code Unknown blockstate property: '<property>'}.
 *       <br>方块属性必须是这个方块含有的一个方块属性，否则在日志中警告。</li>
 *   <li>The value must be a valid value of that property, otherwise the game logs
 *       {@code Unknown value: '<value>' for blockstate property: '<property>' [<values>]}.
 *       <br>属性值必须是这个方块属性的一个有效属性值，否则在日志中警告。</li>
 * </ul>
 *
 * <p>If either the property or the value is invalid, the whole blockstate key and its
 * model mapping definition become invalid: the entry is replaced by the
 * {@code builtin/missing} model (purple-black MissingNo, see
 * {@link decok.dfcdvadstf.catframe.model.core.BuiltinMissingModel}).
 * <br>方块属性或者属性值只要有一个无效，整个方块状态键和对应的模型映射定义都将无效：
 * 该条目被替换为 {@code builtin/missing} 无效模型（紫黑 MissingNo）。
 *
 * <p>Legacy keys of this project ({@code "normal"}, {@code "meta=N"}, pure number keys and
 * keys without {@code '='}) are not property-based and are skipped.
 * <br>本项目的旧式键（{@code "normal"}、{@code "meta=N"}、纯数字键及不含 {@code '='} 的键）
 * 不属于属性检查组，跳过校验。
 *
 * <p>This class also rejects x/y rotation angles that are not multiples of 90 degrees
 * (see {@link #validateRotations}), mirroring vanilla's behaviour of refusing invalid
 * rotations instead of baking silently wrong geometry.
 * <br>本类还拒绝非 90 度倍数的 x/y 旋转角度（见 {@link #validateRotations}），
 * 对齐原版拒绝非法旋转的行为，而不是静默烘焙出错误几何。
 *
 * <p>Client main thread only (model registration / render dispatch); no synchronization.
 * <br>仅在客户端主线程调用（模型注册 / 渲染分发），不做同步。
 */
@SideOnly(Side.CLIENT)
public final class BlockstateKeyValidator {

    private BlockstateKeyValidator() {
    }

    /**
     * BlockstateJson instances already validated — identity-based, so each JSON is only
     * validated (and mutated) once no matter how many call sites reach it.
     * 已校验过的 BlockstateJson 实例——按引用去重，无论多少调用点触达都只校验（并改写）一次。
     */
    private static final Set<BlockstateJson> VALIDATED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * BlockstateJson instances already rotation-checked — independent identity set, since
     * rotation validation is definition-independent and has different call sites than
     * {@link #validate}.
     * 已做过旋转角度校验的 BlockstateJson 实例——独立的引用去重集合，
     * 因为旋转校验不依赖状态定义，调用点与 {@link #validate} 不同。
     */
    private static final Set<BlockstateJson> ROTATION_VALIDATED =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    /**
     * Validate all variant keys of a blockstate against the block's typed state definition.
     * Invalid keys keep their slot but their model mapping is replaced by
     * {@code builtin/missing}, so they render as the MissingNo model.
     * <br>用方块的 typed 状态定义校验 blockstate 的所有 variant 键；无效键保留槽位，
     * 但其模型映射被替换为 {@code builtin/missing}，渲染表现为 MissingNo 无效模型。
     *
     * @param bs    the loaded blockstate JSON (no-op if null or has no variants)
     *              已加载的 blockstate JSON（为 null 或无 variants 时不做任何事）
     * @param def   the typed state definition to validate against (no-op if null)
     *              用于校验的 typed 状态定义（为 null 时不做任何事）
     * @param owner human-readable owner for log context (e.g. block registry name)
     *              日志上下文用的归属描述（如方块注册名）
     */
    public static void validate(@Nullable BlockstateJson bs,
                                @Nullable CatStateDefinition<?> def,
                                String owner) {
        if (bs == null || def == null || bs.variants == null || bs.variants.isEmpty()) return;
        if (!VALIDATED.add(bs)) return;

        // Rotation angles are definition-independent; piggyback on the same hub call
        // sites so every validated blockstate also gets its rotations checked.
        // 旋转角度校验不依赖状态定义；搭载同一批中枢调用点，
        // 保证每个被校验的 blockstate 同时完成角度校验。
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
                // 整个状态键与模型映射作废 → builtin/missing（MissingNo）
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
     * Invalid variants fall back to {@code builtin/missing} (MissingNo); invalid
     * multipart apply entries are dropped entirely.
     * <br>校验所有 variant 与 multipart apply 条目的 x/y 旋转角度。对齐原版：
     * blockstate 旋转只允许 90 度增量（0 / 90 / 180 / 270）。超出该集合的角度
     * 会被显式拒绝，而不是静默烘焙出错误几何——旋转非 90° 倍数后面不再轴对齐，
     * face / cullface / AO 的重算会给出错误结果。非法 variant 回退
     * {@code builtin/missing}（MissingNo）；非法 multipart apply 条目整体作废。
     *
     * <p>Definition-independent: can be called on any loaded blockstate, including ones
     * that never reach {@link #validate}.
     * <br>不依赖状态定义：可对任何已加载的 blockstate 调用，
     * 包括从不经过 {@link #validate} 的 blockstate。
     *
     * @param bs    the loaded blockstate JSON (no-op if null)
     *              已加载的 blockstate JSON（为 null 时不做任何事）
     * @param owner human-readable owner for log context (e.g. blockstate resource id)
     *              日志上下文用的归属描述（如 blockstate 资源标识）
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
                    // 整个 variant 作废 → builtin/missing（MissingNo）
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
                if (mpc.apply != null && !hasValidRotations(mpc.apply)) {
                    it.remove();
                    CatFrame.logger.warn(
                            "Invalid rotation angle (not a multiple of 90) in a multipart apply entry of {}; "
                                    + "the entry is dropped",
                            owner);
                }
            }
        }
    }

    /**
     * A variant's rotations are valid iff both x and y are multiples of 90 degrees.
     * variant 的旋转合法当且仅当 x 与 y 均为 90 度的整数倍。
     */
    private static boolean hasValidRotations(BlockstateJson.Variant v) {
        return v != null && isQuarterTurn(v.x) && isQuarterTurn(v.y);
    }

    /** Multiple-of-90 check; negative values are handled by the modulo. 90 度倍数判定（模运算兼容负值）。 */
    private static boolean isQuarterTurn(int deg) {
        return deg % 90 == 0;
    }

    // ==================== 内部：键解析与校验 ====================

    /**
     * Legacy / non-property keys skipped by validation: {@code "normal"}, empty keys,
     * {@code "meta=N"} and any key without {@code '='} (pure number keys, "inventory"…).
     * 校验跳过的旧式 / 非属性键：{@code "normal"}、空键、{@code "meta=N"} 以及任何不含
     * {@code '='} 的键（纯数字键、"inventory" 等）。
     */
    private static boolean isLegacyKey(String key) {
        if (key == null || key.isEmpty() || "normal".equals(key)) return true;
        if (key.indexOf('=') < 0) return true;
        if (key.startsWith("meta=")) {
            try {
                Integer.parseInt(key.substring(5));
                return true;
            } catch (NumberFormatException ignored) {
                // "meta=<非数字>" 不是本项目的 meta 约定，按属性组正常校验
            }
        }
        return false;
    }

    /**
     * Validate every {@code property=value} check group in one variant key.
     * Warnings are emitted per invalid group; the key is invalid if any group fails.
     * 校验单个 variant 键中的每个 {@code 属性=属性值} 检查属性组；每个无效组各自告警，
     * 任一组失败则整个键无效。
     */
    private static boolean isValidKey(String key, CatStateDefinition<?> def) {
        boolean valid = true;
        for (String group : key.split(",")) {
            int eq = group.indexOf('=');
            if (eq < 0) {
                // Malformed group without '=' → cannot name a property, treat as unknown
                // 缺 '=' 的畸形组 → 无法构成属性名，按未知属性处理
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

    /** Find a property by name in the definition, or null. 按名称在定义中查找属性，找不到返回 null。 */
    @Nullable
    private static Property<?> findProperty(CatStateDefinition<?> def, String name) {
        for (Property<?> prop : def.getProperties()) {
            if (prop.getName().equals(name)) return prop;
        }
        return null;
    }

    /**
     * Build a replacement entry pointing at the {@code builtin/missing} model.
     * 构建指向 {@code builtin/missing} 无效模型的替换条目。
     */
    private static BlockstateJson.VariantEntry missingEntry() {
        BlockstateJson.Variant variant = new BlockstateJson.Variant();
        variant.model = "builtin/missing";
        BlockstateJson.VariantEntry entry = new BlockstateJson.VariantEntry();
        entry.single = variant;
        return entry;
    }
}
