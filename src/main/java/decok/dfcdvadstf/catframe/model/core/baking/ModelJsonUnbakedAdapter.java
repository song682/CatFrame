package decok.dfcdvadstf.catframe.model.core.baking;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.ItemModelGenerator;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.TextureSlots;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link ModelJson} 包装为 {@link UnbakedModel} 接口的适配器。
 * <p>
 * 当前 {@link VanillaModelManager.ModelBaking#bakeModel} 中的元素烘焙逻辑
 * 被移入此适配器的 {@link #bake(TextureSlots, int, int)} 方法中。
 * <p>
 * 这样实现了：
 * <ul>
 *   <li>烘焙逻辑与 VMM 解耦——VMM 只需委托给 {@link ModelBaker}</li>
 *   <li>Unbaked 抽象层——未来可加入其他 UnbakedModel 实现（如程序化生成的几何体）</li>
 *   <li>测试性——可在不初始化 Minecraft 环境的情况下测试烘焙逻辑</li>
 * </ul>
 */
public class ModelJsonUnbakedAdapter implements UnbakedModel {

    private final ModelJson json;
    private final String modelPath;

    public ModelJsonUnbakedAdapter(ModelJson json, String modelPath) {
        this.json = json;
        this.modelPath = modelPath;
    }

    // ==================== UnbakedModel 接口 ====================

    @Nullable
    @Override
    public Map<String, String> textureReferences() {
        return json.textures;
    }

    @Nullable
    @Override
    public Map<String, ModelJson.DisplayTransform> display() {
        return json.display;
    }

    @Nullable
    @Override
    public String guiLight() {
        return json.guiLight;
    }

    @Nullable
    @Override
    public Boolean ambientOcclusion() {
        return null; // 模型级别的环境光遮蔽暂未使用
    }

    @Nullable
    @Override
    public List<ModelJson.Element> elements() {
        return json.elements;
    }

    @Override
    public boolean isBuiltinGenerated() {
        return json.builtinGenerated;
    }

    @Nullable
    @Override
    public int[] textureSize() {
        return json.texture_size;
    }

    // ==================== 烘焙入口 ====================

    /**
     * 烘焙此模型为 {@link BlockStateModelPart}。
     * <p>
     * 逻辑来源于当前 {@code VanillaModelManager.ModelBaking.bakeModel()} 第 1204-1299 行，
     * 移入此处以实现 Unbaked→Baked 的阶段分离。
     *
     * @param textures  已解析的纹理槽（TextureSlots 已处理好 IIcon 查找）
     * @param rotationX X 轴旋转角度（0/90/180/270）
     * @param rotationY Y 轴旋转角度（0/90/180/270）
     * @return 烘焙后的渲染部件
     */
    @Override
    public BlockStateModelPart bake(TextureSlots textures, int rotationX, int rotationY) {
        // 1. 获取 texture_size
        int[] texSize = json.texture_size;

        // 2. 构建 iconMap（向后兼容 BlockJsonModelBake.bakeElement 的 Map<String, IIcon> 参数）
        Map<String, IIcon> iconMap = textures.toIconMap();

        // 3. 检查 elements
        if (json.elements == null || json.elements.isEmpty()) {
            CatFrame.logger.warn("[ModelJsonUnbakedAdapter] bake: model '{}' has no elements", modelPath);
            return BlockStateModelPart.empty();
        }

        // 4. 遍历 elements 烘焙为 BakedQuad
        List<BakedQuad> quads = new ArrayList<>();
        for (ModelJson.Element element : json.elements) {
            List<BakedQuad> elementQuads = JsonModelBake.bakeElement(element, iconMap, texSize);
            if (elementQuads != null) {
                quads.addAll(elementQuads);
            }
        }

        // 5. 为 builtin/generated 模型生成侧面 quad（像素级边缘挤出着色）
        if (json.builtinGenerated && !iconMap.isEmpty()) {
            int totalSideQuads = 0;
            for (Map.Entry<String, IIcon> texEntry : iconMap.entrySet()) {
                // 从纹理键名推导 tintIndex（layer0→0, layer1→1, ... 其他→-1）
                int layerTint = -1;
                String texKey = texEntry.getKey();
                if (texKey.startsWith("layer")) {
                    try {
                        layerTint = Integer.parseInt(texKey.substring(5));
                    } catch (NumberFormatException ignored) {
                    }
                }
                List<BakedQuad> sideQuads = ItemModelGenerator.bakeSideFaces(texEntry.getValue(), layerTint);
                if (sideQuads != null) {
                    quads.addAll(sideQuads);
                    totalSideQuads += sideQuads.size();
                }
            }
            if (totalSideQuads > 0) {
                CatFrame.logger.debug("[ModelJsonUnbakedAdapter] bake: generated {} side quads for '{}'",
                        totalSideQuads, modelPath);
            }
        }

        // 6. 传播模型级别的 guiLight 到所有 quad
        if (json.guiLight != null) {
            for (BakedQuad q : quads) {
                q.guiLight = json.guiLight;
            }
        }

        // 7. display transforms 已通过 BlockStateModelPart 的 partDisplay 字段传播，
        //    不再需要逐个设置到 BakedQuad 上

        // 8. 应用旋转（深拷贝，不污染基础缓存）
        //    顺序必须与 Minecraft blockstate 一致：先绕 X 轴、再绕 Y 轴。
        //    3D 旋转不可交换，若先 Y 后 X 会导致 axis=x 原木被摆成 Z 轴、
        //    以及 x:180 顶部楼梯朝向错误（180° 翻转会对 Y 旋转做共轭取反）。
        if (rotationX != 0) {
            quads = JsonModelBake.applyXRotation(quads, rotationX);
        }
        if (rotationY != 0) {
            quads = JsonModelBake.applyYRotation(quads, rotationY);
        }

        CatFrame.logger.debug("[ModelJsonUnbakedAdapter] bake: '{}' | elements={} | quads={} | rotX={} rotY={}",
                modelPath, json.elements.size(), quads.size(), rotationX, rotationY);

        // 9. 包装为 BlockStateModelPart（携带 display transforms）
        return BlockStateModelPart.fromQuads(quads, json.display);
    }
}
