package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.CatFrame;

/**
 * 第三方物品属性注册 facade —— 外部模组接入 items/ 决策树属性体系的唯一正式入口。
 * <p>
 * Third-party item property registration facade — the official entry point for
 * external mods to plug custom properties into the items/ decision tree system.
 *
 * <h3>为什么不直接调 {@link ItemPropertyRegistry}？ / Why not call the registry directly?</h3>
 * <ul>
 *   <li>强制 {@code modid:name} 命名空间，杜绝裸名被自动挂上 {@code minecraft:} 别名、
 *       冒充原版命名空间。
 *       <br>Enforces the {@code modid:name} namespace so bare names never get the
 *       automatic {@code minecraft:} alias and impersonate the vanilla namespace.</li>
 *   <li>注册前先物化默认属性表（{@link ItemPropertyRegistry#registerDefaults()}），
 *       保证外部覆写不会被懒触发的默认注册静默冲掉。
 *       <br>Materializes the default property table before registering, so external
 *       overrides are never silently clobbered by the lazily-triggered defaults.</li>
 * </ul>
 *
 * <h3>用法 / Usage</h3>
 * <pre>{@code
 * // preInit / init 任意阶段（客户端）：
 * CatItemProperties.register("somemod", "charge_level", (stack, phase) -> {
 *     NBTTagCompound tag = stack != null ? stack.getTagCompound() : null;
 *     return tag != null ? tag.getInteger("Charge") : 0;
 * });
 * // items/*.json 中即可用 "property": "somemod:charge_level" 引用
 * }</pre>
 *
 * <h3>软依赖 / Soft dependency</h3>
 * 方法签名只含 {@link String} 与 CatFrame 自有的 {@link ItemPropertyProvider}，
 * 便于外部模组以 {@code Loader.isModLoaded("catframe")} + 反射做可选接入。
 * <br>Signatures only expose {@link String} and CatFrame's own
 * {@link ItemPropertyProvider}, keeping reflection-based optional integration easy.
 */
public final class CatItemProperties {

    private CatItemProperties() {}

    /**
     * 注册一个带命名空间的第三方属性，最终 key 为 {@code modid:name}。
     * <p>
     * Registers a namespaced third-party property under the key {@code modid:name}.
     *
     * @param modid    模组 id（非空，不含 {@code :}） / mod id (non-empty, no {@code :})
     * @param name     属性名（非空，不含 {@code :}） / property name (non-empty, no {@code :})
     * @param provider 属性计算逻辑 / the property computation logic
     * @throws IllegalArgumentException 参数为空或含 {@code :} 时 / on empty args or embedded {@code :}
     */
    public static void register(String modid, String name, ItemPropertyProvider provider) {
        validatePart(modid, "modid");
        validatePart(name, "name");
        if (provider == null) {
            throw new IllegalArgumentException("CatItemProperties: provider must not be null");
        }
        // 先物化默认表，确保外部注册永远排在默认注册之后
        // Materialize defaults first so external entries always land after them
        ItemPropertyRegistry.registerDefaults();
        ItemPropertyRegistry.register(modid + ":" + name, provider);
    }

    /**
     * 覆写一个已存在的属性（含默认属性，如 {@code damage}）。
     * <p>
     * 与 {@link #register(String, String, ItemPropertyProvider)} 不同，此方法接受完整属性名
     * （裸名或带命名空间均可），且要求目标属性已注册——避免拼写错误静默创建新属性。
     * 裸名覆写会同步刷新 {@code minecraft:} 别名（由 {@link ItemPropertyRegistry#register} 保证）。
     * <p>
     * Overrides an existing property (including defaults such as {@code damage}).
     * Unlike {@code register}, this takes the full property name (bare or namespaced)
     * and requires the target to already exist — a typo won't silently create a new
     * property. Bare-name overrides refresh the {@code minecraft:} alias as well.
     *
     * @param propertyName 目标属性全名 / full name of the target property
     * @param provider     新的属性计算逻辑 / the replacement computation logic
     * @throws IllegalArgumentException 目标属性未注册或参数非法时 / if the target is unknown or args are invalid
     */
    public static void override(String propertyName, ItemPropertyProvider provider) {
        if (propertyName == null || propertyName.isEmpty()) {
            throw new IllegalArgumentException("CatItemProperties: propertyName must not be empty");
        }
        if (provider == null) {
            throw new IllegalArgumentException("CatItemProperties: provider must not be null");
        }
        // 先物化默认表，否则默认属性尚未注册、且覆写会被后到的默认注册冲掉
        // Materialize defaults first: the target may not exist yet, and a later
        // lazy default registration would clobber the override
        ItemPropertyRegistry.registerDefaults();
        if (ItemPropertyRegistry.get(propertyName) == null) {
            throw new IllegalArgumentException(
                    "CatItemProperties: cannot override unknown property '" + propertyName + "'");
        }
        CatFrame.logger.info("CatItemProperties: property '{}' overridden by external provider", propertyName);
        ItemPropertyRegistry.register(propertyName, provider);
    }

    /**
     * 校验命名空间片段：非空且不含 {@code :}。
     * <p>Validates a namespace fragment: non-empty and without {@code :}.
     */
    private static void validatePart(String value, String label) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("CatItemProperties: " + label + " must not be empty");
        }
        if (value.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "CatItemProperties: " + label + " must not contain ':' (got '" + value + "')");
        }
    }
}
