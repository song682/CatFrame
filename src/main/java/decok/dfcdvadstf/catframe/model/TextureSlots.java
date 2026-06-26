package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.CatFrame;
import net.minecraft.client.Minecraft;
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
 *   <li>{@code #xxx} 引用链解析（当前在 {@link ModelResolver#resolveTextureVariables} 中内联完成）</li>
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
     * {@link ModelResolver#resolveTextureVariables} 中解析为实际路径），
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

                // 查找 IIcon
                IIcon icon = findIcon(value, globalIconMap);
                if (icon != null) {
                    result.put(key, icon);
                } else {
                    CatFrame.logger.warn("[TextureSlots] icon not found for '{}': {} (fallback to missingno)", key, value);
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
     * 转换为 {@link BlockJsonModelBake#bakeElement} 所需的 {@code Map<String, IIcon>} 格式。
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
     * 搜索顺序：globalIconMap → blocks 纹理图集 → items 纹理图集。
     */
    @Nullable
    private static IIcon findIcon(String texturePath, @Nullable Map<String, IIcon> globalIconMap) {
        if (texturePath == null || texturePath.isEmpty()) return null;

        // 1. 检查全局映射
        if (globalIconMap != null) {
            IIcon icon = globalIconMap.get(texturePath);
            if (icon != null) return icon;
        }

        // 2. 尝试 blocks 纹理图集
        try {
            String iconName = resolveTextureName(texturePath);
            TextureMap blocksMap = Minecraft.getMinecraft().getTextureMapBlocks();
            IIcon icon = blocksMap.getAtlasSprite(iconName);
            if (icon != null && icon.getIconName() != null && !"missingno".equals(icon.getIconName())) {
                return icon;
            }

            // 3. 尝试 items 纹理图集
            TextureMap itemsMap = (TextureMap) Minecraft.getMinecraft().getTextureManager()
                    .getTexture(TextureMap.locationItemsTexture);
            if (itemsMap != null) {
                icon = itemsMap.getAtlasSprite(texturePath);
                if (icon == null) {
                    icon = itemsMap.getAtlasSprite(iconName);
                }
                if (icon != null && icon.getIconName() != null && !"missingno".equals(icon.getIconName())) {
                    return icon;
                }
            }
        } catch (Exception e) {
            CatFrame.logger.debug("[TextureSlots] error resolving icon for '{}': {}", texturePath, e.getMessage());
        }

        return null;
    }

    /**
     * 将纹理路径解析为图集可识别的名称。
     * 去除 "blocks/"、"items/"、"block/"、"item/" 前缀，保留 namespace。
     */
    private static String resolveTextureName(String texturePath) {
        if (texturePath == null) return null;
        String namespace = "";
        String pathPart = texturePath;

        if (texturePath.contains(":")) {
            namespace = texturePath.substring(0, texturePath.indexOf(':') + 1);
            pathPart = texturePath.substring(texturePath.indexOf(':') + 1);
        }

        if (pathPart.startsWith("blocks/")) {
            pathPart = pathPart.substring("blocks/".length());
        } else if (pathPart.startsWith("items/")) {
            pathPart = pathPart.substring("items/".length());
        } else if (pathPart.startsWith("block/")) {
            pathPart = pathPart.substring("block/".length());
        } else if (pathPart.startsWith("item/")) {
            pathPart = pathPart.substring("item/".length());
        }

        return namespace + pathPart;
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
