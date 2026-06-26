package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 烘焙门面。对标 26.1.2 的 {@code ModelBaker}。
 * <p>
 * 封装从模型路径到烘焙产物的完整管线：
 * {@code modelPath → ModelResolver.resolve() → UnbakedModel → TextureSlots → bake()}
 * <p>
 * <h3>职责</h3>
 * <ul>
 *   <li>提供三种粒度的烘焙入口（路径/已解析JSON/UnbakedModel）</li>
 *   <li>维护烘焙缓存（{@code modelPath@rotX@rotY → BlockStateModelPart}）</li>
 *   <li>在 {@link TextureSlots#fromModel} 和 {@link ModelJsonUnbakedAdapter#bake} 之间协调</li>
 * </ul>
 *
 * <p>VMM 的 {@code ModelBaking.bakeModel()} 将委托给此类，
 * 实现模型加载逻辑与 VMM 的解耦。
 */
public class ModelBaker {

    /** 烘焙缓存：key = "modelPath@rotX@rotY" */
    private static final Map<String, BlockStateModelPart> bakeCache = new HashMap<>();

    /** 全局纹理池（由 VMM 在初始化时设置，来自 VMM.textureIcons） */
    @Nullable
    private static Map<String, IIcon> globalIconMap = null;

    private ModelBaker() {}

    // ==================== 配置 ====================

    /**
     * 设置全局纹理池映射。由 {@link VanillaModelManager} 在初始化时调用。
     * 烘焙时将优先从此映射查找 IIcon，找不到时回退到 Minecraft 纹理图集查询。
     */
    public static void setGlobalIconMap(@Nullable Map<String, IIcon> iconMap) {
        globalIconMap = iconMap;
    }

    /**
     * 获取当前全局纹理池映射。
     */
    @Nullable
    public static Map<String, IIcon> getGlobalIconMap() {
        return globalIconMap;
    }

    // ==================== 烘焙入口（含便捷重载） ====================

    /**
     * 便捷重载：从模型路径烘焙，无旋转。
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath) {
        return bake(modelPath, 0, 0);
    }

    /**
     * 便捷重载：从模型路径烘焙，仅 Y 轴旋转。
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, int rotationY) {
        return bake(modelPath, 0, rotationY);
    }

    /**
     * 从模型路径烘焙为 {@link BlockStateModelPart}（带缓存）。
     * <p>
     * 完整管线：{@code modelPath → ModelResolver.resolve() → ModelJsonUnbakedAdapter → TextureSlots → bake()}
     *
     * @param modelPath 模型路径（如 {@code "block/stone"}、{@code "builtin/generated"}）
     * @param rotationX X 轴旋转角度（0/90/180/270）
     * @param rotationY Y 轴旋转角度（0/90/180/270）
     * @return 烘焙后的渲染部件，失败返回 null
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, int rotationX, int rotationY) {
        if (modelPath == null) return null;

        String cacheKey = modelPath + "@" + rotationX + "@" + rotationY;
        BlockStateModelPart cached = bakeCache.get(cacheKey);
        if (cached != null) {
            CatFrame.logger.debug("[ModelBaker] cache HIT: {}", cacheKey);
            return cached;
        }

        // 1. 解析 JSON
        ModelJson resolved = ModelResolver.resolve(modelPath);
        if (resolved == null) {
            CatFrame.logger.warn("[ModelBaker] ModelResolver.resolve('{}') returned null", modelPath);
            return null;
        }

        // 2. 委托给已解析的重载
        BlockStateModelPart part = bake(resolved, rotationX, rotationY, modelPath);
        if (part != null) {
            bakeCache.put(cacheKey, part);
            CatFrame.logger.debug("[ModelBaker] cache MISS: {} | quads={}", cacheKey,
                    part.isEmpty() ? 0 : part.getAllQuads().size());
        }
        return part;
    }

    /**
     * 从已解析的 {@link ModelJson} 直接烘焙（绕过 JSON 解析和缓存）。
     *
     * @param resolved  已解析的模型
     * @param rotationX X 轴旋转角度
     * @param rotationY Y 轴旋转角度
     * @return 烘焙后的渲染部件
     */
    @Nullable
    public static BlockStateModelPart bake(ModelJson resolved, int rotationX, int rotationY) {
        return bake(resolved, rotationX, rotationY, "adapter");
    }

    /**
     * 从已解析的 {@link ModelJson} 烘焙，带模型路径标签。
     *
     * @param resolved  已解析的模型
     * @param rotationX X 轴旋转角度
     * @param rotationY Y 轴旋转角度
     * @param modelPath 模型路径（用于日志和标识）
     * @return 烘焙后的渲染部件
     */
    @Nullable
    public static BlockStateModelPart bake(ModelJson resolved, int rotationX, int rotationY, String modelPath) {
        if (resolved == null) return null;
        if (resolved.elements == null || resolved.elements.isEmpty()) {
            if (!resolved.builtinGenerated) {
                CatFrame.logger.warn("[ModelBaker] model '{}' has no elements and is not builtinGenerated", modelPath);
                return null;
            }
        }

        // 1. 构建 UnbakedModel 适配器
        UnbakedModel unbaked = new ModelJsonUnbakedAdapter(resolved, modelPath);

        // 2. 构建 TextureSlots
        TextureSlots textures = TextureSlots.fromModel(resolved, globalIconMap, null);

        // 3. 烘焙
        return unbaked.bake(textures, rotationX, rotationY);
    }

    /**
     * 从 {@link UnbakedModel} 烘焙（最底层入口，适合测试或程序化模型）。
     *
     * @param model     UnbakedModel 实例
     * @param textures  已解析的纹理槽
     * @param rotationX X 轴旋转角度
     * @param rotationY Y 轴旋转角度
     * @return 烘焙后的渲染部件
     */
    @Nullable
    public static BlockStateModelPart bake(UnbakedModel model, TextureSlots textures,
                                            int rotationX, int rotationY) {
        if (model == null) return null;
        return model.bake(textures, rotationX, rotationY);
    }

    // ==================== 缓存管理 ====================

    /**
     * 清除所有烘焙缓存。在资源重载时调用。
     */
    public static void clearCache() {
        bakeCache.clear();
        CatFrame.logger.info("[ModelBaker] bake cache cleared");
    }

    /**
     * 获取缓存条目数。
     */
    public static int getCacheSize() {
        return bakeCache.size();
    }
}
