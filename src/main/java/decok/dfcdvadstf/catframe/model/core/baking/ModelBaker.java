package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.core.TextureSlots;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
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
 *   <li>在 {@link TextureSlots#fromModel} 和 {@link ModelJsonUnbakedAdapter#bake} 之间协调</li>
 * </ul>
 *
 * <p>缓存职责已由 {@link BakedModelCache} 承担（StampedLock + LRU 懒烘焙）。
 * 本类不再维护内部缓存，仅作为烘焙管线门面。
 */
public class ModelBaker {

    private ModelBaker() {}

    // ==================== 烘焙入口（含便捷重载） ====================

    /**
     * 便捷重载：从模型路径烘焙，无旋转（使用当前 stitch 周期的 iconMap）。
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath) {
        return bake(modelPath, 0, 0);
    }

    /**
     * 便捷重载：从模型路径烘焙，仅 Y 轴旋转（使用当前 stitch 周期的 iconMap）。
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, int rotationY) {
        return bake(modelPath, 0, rotationY);
    }

    /**
     * 从模型路径烘焙为 {@link BlockStateModelPart}（使用当前 stitch 周期的 iconMap）。
     * <p>
     * 完整管线：{@code modelPath → ModelResolver.resolve() → ModelJsonUnbakedAdapter → TextureSlots → bake()}
     * <p>
     * 注意：本方法不维护缓存。缓存由 {@link BakedModelCache} 统一管理。
     *
     * @param modelPath 模型路径（如 {@code "block/stone"}、{@code "builtin/generated"}）
     * @param rotationX X 轴旋转角度（0/90/180/270）
     * @param rotationY Y 轴旋转角度（0/90/180/270）
     * @return 烘焙后的渲染部件，失败返回 null
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, int rotationX, int rotationY) {
        return bake(modelPath, rotationX, rotationY, VanillaTextureTracker.textureIcons);
    }

    /**
     * 从模型路径烘焙为 {@link BlockStateModelPart}，显式指定 iconMap。
     * <p>
     * 对标高版本 {@code MaterialBaker} 的实例化闭包模式：每次烘焙使用调用方提供的
     * iconMap（而非全局静态字段），避免多 stitch 周期之间的竞态覆盖。
     *
     * @param modelPath 模型路径
     * @param rotationX X 轴旋转角度
     * @param rotationY Y 轴旋转角度
     * @param iconMap   当前 stitch 周期的 IIcon 映射
     * @return 烘焙后的渲染部件，失败返回 null
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, int rotationX, int rotationY,
                                            @Nullable Map<String, IIcon> iconMap) {
        if (modelPath == null) return null;

        // 1. 解析 JSON
        ModelJson resolved = ModelResolver.resolve(modelPath);
        if (resolved == null) {
            CatFrame.logger.warn("[ModelBaker] ModelResolver.resolve('{}') returned null", modelPath);
            return null;
        }

        // 2. 委托给已解析的重载
        return bake(resolved, rotationX, rotationY, modelPath, iconMap);
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
        return bake(resolved, rotationX, rotationY, "adapter", VanillaTextureTracker.textureIcons);
    }

    /**
     * 从已解析的 {@link ModelJson} 烘焙，带模型路径标签（使用当前 stitch 周期的 iconMap）。
     *
     * @param resolved  已解析的模型
     * @param rotationX X 轴旋转角度
     * @param rotationY Y 轴旋转角度
     * @param modelPath 模型路径（用于日志和标识）
     * @return 烘焙后的渲染部件
     */
    @Nullable
    public static BlockStateModelPart bake(ModelJson resolved, int rotationX, int rotationY, String modelPath) {
        return bake(resolved, rotationX, rotationY, modelPath, VanillaTextureTracker.textureIcons);
    }

    /**
     * 从已解析的 {@link ModelJson} 烘焙，显式指定 iconMap。
     * <p>
     * 对标高版本 {@code MaterialBaker} 的实例化闭包模式。
     *
     * @param resolved  已解析的模型
     * @param rotationX X 轴旋转角度
     * @param rotationY Y 轴旋转角度
     * @param modelPath 模型路径（用于日志和标识）
     * @param iconMap   当前 stitch 周期的 IIcon 映射
     * @return 烘焙后的渲染部件
     */
    @Nullable
    public static BlockStateModelPart bake(ModelJson resolved, int rotationX, int rotationY,
                                            String modelPath, @Nullable Map<String, IIcon> iconMap) {
        if (resolved == null) return null;
        if (resolved.elements == null || resolved.elements.isEmpty()) {
            if (!resolved.builtinGenerated) {
                CatFrame.logger.warn("[ModelBaker] model '{}' has no elements and is not builtinGenerated", modelPath);
                return null;
            }
        }

        // 1. 构建 UnbakedModel 适配器
        UnbakedModel unbaked = new ModelJsonUnbakedAdapter(resolved, modelPath);

        // 2. 构建 TextureSlots（使用调用方提供的 iconMap，而非全局静态）
        TextureSlots textures = TextureSlots.fromModel(resolved, iconMap, null);

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
     * 清除烘焙缓存。
     * @deprecated 缓存已由 {@link BakedModelCache} 统一管理，本方法为 no-op。
     */
    @Deprecated
    public static void clearCache() {
        // No-op: 缓存已由 BakedModelCache 统一管理
    }
}
