package decok.dfcdvadstf.catframe.resources.builtin;

import decok.dfcdvadstf.catframe.mixin.CatFrameEarlyMixinPlugin;

/**
 * Immutable description of a built-in resource pack: the pack identity plus the
 * translation keys used by the resource pack GUI and by synthesized pack
 * metadata.
 * <p>
 * This class deliberately touches no Minecraft class, because instances are
 * created during the FML coremod stage (see {@link CatFrameEarlyMixinPlugin}) — the only
 * code that runs before {@code Minecraft} builds its
 * {@code ResourcePackRepository}.
 * <p>
 * 内置资源包的不可变描述：包标识，以及资源包 GUI 与合成 metadata 使用的翻译键。
 * 本类刻意不引用任何 Minecraft 类——实例在 FML coremod 阶段（见
 * {@link CatFrameEarlyMixinPlugin}）创建，那是早于 {@code Minecraft} 构造
 * {@code ResourcePackRepository} 的唯一执行时机。
 */
public final class BuiltinPackDescriptor {

    /** Prefix of the stable repository name, see {@link #getPackName()}. */
    private static final String PACK_NAME_PREFIX = "builtin/";

    private final String id;
    private final String nameKey;
    private final String descriptionKey;

    /**
     * @param id             globally unique pack id (lowercase letters, digits
     *                       and underscores); it names the classpath folder
     *                       {@code builtin_packs/<id>} and is embedded in the
     *                       stable repository name
     * @param nameKey        translation key for the pack name shown in the GUI
     * @param descriptionKey translation key used when the pack ships no
     *                       {@code pack.mcmeta}
     */
    public BuiltinPackDescriptor(String id, String nameKey, String descriptionKey) {
        if (id == null || !id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid built-in pack id: " + id);
        }
        if (nameKey == null || nameKey.isEmpty()) {
            throw new IllegalArgumentException("Missing name key for built-in pack: " + id);
        }
        if (descriptionKey == null || descriptionKey.isEmpty()) {
            throw new IllegalArgumentException("Missing description key for built-in pack: " + id);
        }
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
    }

    public String getId() {
        return this.id;
    }

    public String getNameKey() {
        return this.nameKey;
    }

    public String getDescriptionKey() {
        return this.descriptionKey;
    }

    /**
     * Stable repository name of the pack. It is the persistence key written to
     * {@code options.txt} and it participates in repository entry equality
     * ({@code Entry.equals()} compares {@code toString()} which embeds the file
     * name), so it must <em>never</em> be localized.
     * <p>
     * 包的稳定仓库名。它既作为持久化键写入 {@code options.txt}，也参与仓库条目相等性
     * （{@code Entry.equals()} 比较 {@code toString()}，其中包含文件名），
     * 因此<em>绝不能</em>本地化。
     */
    public String getPackName() {
        return PACK_NAME_PREFIX + this.id;
    }
}
