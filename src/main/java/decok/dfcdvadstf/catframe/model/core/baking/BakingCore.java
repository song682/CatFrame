package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.*;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.ModelResolver;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * 烘焙纯函数。无状态、无静态字段写入，天然线程安全。
 * <p>
 * 从 {@link VMMModelBaking#bakeModel} 和 {@link ModelBaker} 提取的核心烘焙逻辑：
 * <pre>
 *   modelPath → ModelResolver.resolve() → ModelBaker.bake() → BlockStateModelPart
 * </pre>
 * <p>
 * 本类不维护任何缓存，所有缓存职责由 {@link BakedModelCache} 承担。
 * 这使得本类可以被多线程安全地并发调用（如 Akka BakeWorker 或渲染线程懒烘焙）。
 */
public class BakingCore {

    private BakingCore() {}

    /**
     * 烘焙单个模型为 {@link BlockStateModelPart}（使用当前 stitch 周期的 iconMap）。
     * <p>
     * 纯函数语义：相同的 (modelPath, rotX, rotY, iconMap) 输入始终产出语义等价的结果。
     * 不写入任何静态缓存或全局状态。
     *
     * @param modelPath 模型路径（如 "block/stone"、"builtin/generated"）
     * @param rotX      X 轴旋转角度（0/90/180/270）
     * @param rotY      Y 轴旋转角度（0/90/180/270）
     * @return 烘焙后的模型部件，失败返回 null
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, int rotX, int rotY) {
        return bake(modelPath, rotX, rotY, VanillaTextureTracker.textureIcons);
    }

    /**
     * 烘焙单个模型为 {@link BlockStateModelPart}，显式指定 iconMap。
     * <p>
     * 对标高版本 {@code MaterialBaker} 的实例化闭包模式：每次烘焙使用调用方提供的
     * iconMap，避免多 stitch 周期之间的竞态覆盖。
     *
     * @param modelPath 模型路径
     * @param rotX      X 轴旋转角度
     * @param rotY      Y 轴旋转角度
     * @param iconMap   当前 stitch 周期的 IIcon 映射
     * @return 烘焙后的模型部件，失败返回 null
     */
    @Nullable
    public static BlockStateModelPart bake(String modelPath, int rotX, int rotY,
                                            @Nullable Map<String, IIcon> iconMap) {
        if (modelPath == null) return null;

        // 1. 解析 JSON（ModelResolver 内部有缓存，线程安全由 ConcurrentHashMap 保证）
        ModelJson resolved = ModelResolver.resolve(modelPath);
        if (resolved == null) {
            CatFrame.logger.warn("[BakingCore] ModelResolver.resolve('{}') returned null", modelPath);
            return null;
        }

        // 2. 委托给 ModelBaker 执行实际烘焙（传递 iconMap，而非读全局静态）
        BlockStateModelPart part = ModelBaker.bake(resolved, rotX, rotY, modelPath, iconMap);
        if (part == null) {
            CatFrame.logger.debug("[BakingCore] bake returned null for '{}' @ {}@{}", modelPath, rotX, rotY);
        }
        return part;
    }
}
