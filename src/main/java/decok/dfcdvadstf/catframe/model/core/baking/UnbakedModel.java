package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.model.ItemModelGenerator;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.TextureSlots;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 未烘焙的模型定义。对标 26.1.2 的 {@code UnbakedModel}。
 * <p>
 * 表示从 JSON 解析后、尚未解析为具体 {@link BakedQuad} 的模型状态。
 * 包含纹理引用、display transforms、guiLight 等元数据。
 * 通过 {@link #bake(TextureSlots, int, int)} 方法烘焙为 {@link BlockStateModelPart}。
 *
 * <p>当前已有的 {@link ModelJson} POJO 通过 {@link ModelJsonUnbakedAdapter}
 * 适配此接口，使烘焙逻辑无需直接依赖 JSON 解析层。
 *
 * <h3>与 26.1.2 的差异</h3>
 * <ul>
 *   <li>26.1.2 区分 UnbakedGeometry 和 UnbakedModel，此处合并为单一接口以保持简洁</li>
 *   <li>未引入 ModelBakery 的复杂缓存和依赖解析，保持轻量</li>
 * </ul>
 */
public interface UnbakedModel {

    /**
     * 纹理引用映射：slot 名称 → 纹理路径（或 {@code #xxx} 引用，由 TextureSlots 解析）。
     * 对应 ModelJson.textures。
     */
    @Nullable
    Map<String, String> textureReferences();

    /**
     * Display transforms for different render contexts（gui / thirdperson_righthand / ...）。
     * 对应 ModelJson.display。
     */
    @Nullable
    Map<String, ModelJson.DisplayTransform> display();

    /**
     * GUI 光照模式：{@code "front"}（物品/平面光照）或 {@code "side"}（方块/3D 光照）。
     * 对应 ModelJson.guiLight。
     */
    @Nullable
    String guiLight();

    /**
     * 全局环境光遮蔽开关。
     * 对应 ModelJson.ambientocclusion（元素级别），此处为模型级别默认值。
     */
    @Nullable
    Boolean ambientOcclusion();

    /**
     * 模型中的 element 列表。可为 null（如 builtin/generated 由 ItemModelGenerator 动态生成）。
     * 对应 ModelJson.elements。
     */
    @Nullable
    List<ModelJson.Element> elements();

    /**
     * 是否继承自 builtin/generated 模型。
     * true 时烘焙将调用 {@link ItemModelGenerator#bakeSideFaces} 生成侧面 quad。
     */
    boolean isBuiltinGenerated();

    /**
     * 纹理尺寸 [width, height]，默认 [16, 16]。
     * 用于 UV 坐标缩放。对应 ModelJson.texture_size。
     */
    @Nullable
    int[] textureSize();

    /**
     * 烘焙入口：将未烘焙的模型转换为可用于渲染的 {@link BlockStateModelPart}。
     *
     * @param textures  已解析的纹理槽（IIcon 已就绪）
     * @param rotationX X 轴旋转角度（0/90/180/270）
     * @param rotationY Y 轴旋转角度（0/90/180/270）
     * @return 烘焙后的渲染部件
     */
    BlockStateModelPart bake(TextureSlots textures, int rotationX, int rotationY);
}
