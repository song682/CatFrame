package decok.dfcdvadstf.catframe.model.core;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake;
import decok.dfcdvadstf.catframe.resources.atlas.CatAtlasManager;
import decok.dfcdvadstf.catframe.resources.atlas.CatSprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 纹理槽系统。对标 26.1.2 的 {@code TextureSlots}。
 * <p>
 * 负责将模型的纹理引用（如 {@code "layer0"} → {@code "minecraft:block/stone"}）
 * 解析为具体的 {@link IIcon} 引用，支持：
 * <ul>
 *   <li>{@code #xxx} 引用链解析（当前在 {@link ModelResolver#resolveTextureVariables(ModelJson)} 中内联完成）</li>
 *   <li>Parent 模型的纹理覆盖（子模型覆盖父模型的同名 slot）</li>
 *   <li>从已知 {@link IIcon} 映射直接查询</li>
 * </ul>
 *
 * <h3>与 26.1.2 的差异</h3>
 * <ul>
 *   <li>26.1.2 使用 Material/Material.Reference 做类型安全引用，此处简化为 String → IIcon</li>
 *   <li>未引入 TextureSlots.Data/Resolver 分层，直接提供静态工厂方法</li>
 * </ul>
 */
public class TextureSlots {

    private final Map<String, IIcon> resolvedIcons;

    private TextureSlots(Map<String, IIcon> resolvedIcons) {
        this.resolvedIcons = resolvedIcons;
    }

    // ==================== 工厂方法 ====================

    /**
     * 从 {@link ModelJson} 的 textures map 构建 TextureSlots。
     * <p>
     * 遍历 model.textures 中的每个条目，跳过 {@code #xxx} 引用（这些引用已在
     * {@link ModelResolver#resolveTextureVariables(ModelJson)} 中解析为实际路径），
     * 然后从 globalIconMap 或 MC 的纹理图集中查找对应的 {@link IIcon}。
     *
     * @param model         已解析的模型（textures 中的 # 引用已被 ModelResolver 展开）
     * @param globalIconMap 全局 IIcon 映射（来自 VMM.textureIcons），可为 null
     * @param parentOverride 父模型的 TextureSlots，子模型可覆盖其值，可为 null
     * @return 构建好的 TextureSlots
     */
    public static TextureSlots fromModel(ModelJson model,
                                          @Nullable Map<String, IIcon> globalIconMap,
                                          @Nullable TextureSlots parentOverride) {
        Map<String, IIcon> result = new LinkedHashMap<>();

        // 1. 先继承 parent 的纹理
        if (parentOverride != null) {
            result.putAll(parentOverride.resolvedIcons);
        }

        // 2. 子模型的纹理覆盖父模型
        if (model.textures != null) {
            for (Map.Entry<String, String> entry : model.textures.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // 跳过 # 引用（已在 ModelResolver.resolveTextureVariables 中展开）
                if (value.startsWith("#")) {
                    CatFrame.logger.debug("[TextureSlots] skipping unresolved reference: {} -> {}", key, value);
                    continue;
                }

                // 查找 IIcon：findIcon 对非空引用恒返回有效 icon（缺失时 = missingno），
                // null 仅可能来自空路径引用（防御性跳过）
                IIcon icon = findIcon(value, globalIconMap);
                if (icon != null) {
                    result.put(key, icon);
                } else {
                    CatFrame.logger.warn("[TextureSlots] texture error: empty reference for '{}', slot skipped", key);
                }
            }
        }

        return new TextureSlots(result);
    }

    /**
     * 从预先构建好的 Map 构建 TextureSlots（用于向后兼容）。
     */
    public static TextureSlots fromIconMap(Map<String, IIcon> iconMap) {
        return new TextureSlots(new LinkedHashMap<>(iconMap));
    }

    /**
     * 空的 TextureSlots。
     */
    public static final TextureSlots EMPTY = new TextureSlots(Collections.emptyMap());

    // ==================== 查询 ====================

    /**
     * 判定 icon 是否属于 blocks 纹理图集（对标 26.1.2
     * {@code sprite.atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)}）。
     * <p>
     * 1.7.10 的 {@link TextureAtlasSprite} 不携带图集信息，判定分三级：
     * <ol>
     *   <li>blocks 图集实例同一性 → blocks；</li>
     *   <li>items 图集实例同一性 → items；</li>
     *   <li>两个原版图集都不含该实例时，按路径前缀归类（前瞻兼容：
     *       {@code items/} / {@code item/} → items，其余含 {@code blocks/} /
     *       {@code block/} → blocks），为未来自定义缝合系统预留。</li>
     * </ol>
     * icon 为 null 或非 atlas sprite 时兜底返回 true（与世界路径恒绑 blocks 图集一致）。
     *
     * @param icon 待判定的 icon，可为 null
     * @return true=blocks 图集（含兜底），false=items 图集
     */
    public static boolean isBlockAtlas(@Nullable IIcon icon) {
        // 0. 自定义图集 sprite：按所属图集 id 归类（设计文档预留的 custom stitching 钩子），
        //    必须先于 instanceof TextureAtlasSprite 检查 —— 否则 CatSprite 恒被判为 blocks。
        // Custom-atlas sprites are classified by their owning atlas id.
        if (icon instanceof CatSprite) {
            return CatAtlasManager.BLOCK_ATLAS_ID.equals(((CatSprite) icon).getAtlasId());
        }
        if (!(icon instanceof TextureAtlasSprite)) return true;
        try {
            String name = icon.getIconName();
            // 1. blocks 图集实例同一性：同名 sprite 是同一个对象 → 来自 blocks 图集
            if (Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name) == icon) {
                return true;
            }
            // 2. items 图集实例同一性
            TextureMap itemsMap = (TextureMap) Minecraft.getMinecraft().getTextureManager()
                    .getTexture(TextureMap.locationItemsTexture);
            if (itemsMap != null && itemsMap.getAtlasSprite(name) == icon) {
                return false;
            }
            // 3. 都不含该实例（未来自定义缝合的 sprite）：按路径前缀归类
            return !hasItemsPrefix(name);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 判定纹理名（可含 namespace）是否以 items 图集的文件夹前缀开头
     * （{@code items/} 与现代单数 {@code item/}）。
     */
    private static boolean hasItemsPrefix(@Nullable String name) {
        if (name == null) return false;
        int colon = name.indexOf(':');
        String path = colon >= 0 ? name.substring(colon + 1) : name;
        return path.startsWith("items/") || path.startsWith("item/");
    }

    /**
     * 获取指定 slot 的 {@link IIcon}。
     *
     * @param slot 纹理槽名称（如 {@code "layer0"}、{@code "particle"}）
     * @return IIcon，未找到时返回 null
     */
    @Nullable
    public IIcon getIcon(String slot) {
        return resolvedIcons.get(slot);
    }

    /**
     * 获取所有已解析的纹理路径集合。
     */
    public Set<String> getTexturePaths() {
        return resolvedIcons.keySet();
    }

    /**
     * 获取底层 icon 映射的不可变视图。
     */
    public Map<String, IIcon> getIconMap() {
        return Collections.unmodifiableMap(resolvedIcons);
    }

    /**
     * 转换为 {@link JsonModelBake#bakeElement} 所需的 {@code Map<String, IIcon>} 格式。
     * 用于向后兼容。
     */
    public Map<String, IIcon> toIconMap() {
        return new HashMap<>(resolvedIcons);
    }

    /**
     * 此 TextureSlots 是否为空（无任何纹理）。
     */
    public boolean isEmpty() {
        return resolvedIcons.isEmpty();
    }

    // ==================== 内部辅助 ====================

    /**
     * 根据纹理路径查找 IIcon。
     * <p>
     * [渲染三域架构] 原版后端语义（唯一路径）：textureIcons 直持 vanilla IIcon，
     * 全部渲染路径（物品作用域 / 世界 chunk 批次）均绑原版图集，UV 空间全局一致 ——
     * 查表接受任意非空 icon；<b>查表未命中 → 直接返回 missingno（紫黑格）</b>，
     * 不返回 null、不透明、不做任何兼容纠正。
     * Vanilla backend: globalIconMap holds vanilla IIcons and every render path
     * binds the vanilla atlases, so any non-null icon is accepted; misses resolve
     * to missingno (purple-black square).
     */
    @Nullable
    private static IIcon findIcon(String texturePath, @Nullable Map<String, IIcon> globalIconMap) {
        if (texturePath == null || texturePath.isEmpty()) return null;

        // 接受 globalIconMap 中的任意非空 icon（vanilla IIcon，原版图集 UV；
        // 所有渲染路径同绑原版图集，无错配风险）。
        // Accept any non-null icon from the map (vanilla atlas UV space).
        IIcon icon = globalIconMap != null ? globalIconMap.get(texturePath) : null;
        if (icon != null) {
            return icon;
        }

        // 最终兜底：missingno（原版空间紫黑格）。
        // Final fallback: missingno (vanilla-space purple-black square).
        CatFrame.logger.warn("[TextureSlots] texture error: '{}' not found in texture table, using missingno", texturePath);
        return CatAtlasManager.getMissingIcon(texturePath);
    }

    // ==================== Object ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextureSlots)) return false;
        TextureSlots that = (TextureSlots) o;
        return resolvedIcons.equals(that.resolvedIcons);
    }

    @Override
    public int hashCode() {
        return resolvedIcons.hashCode();
    }

    @Override
    public String toString() {
        return "TextureSlots{" + resolvedIcons.keySet() + "}";
    }
}
