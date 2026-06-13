package decok.dfcdvadstf.catframe.model.render.extension;

import decok.dfcdvadstf.catframe.model.ModelJson;
import org.lwjgl.opengl.GL11;

/**
 * 内建渲染扩展：处理 JSON model 中的 {@code display} transforms。
 *
 * <p>根据渲染阶段（gui / firstperson_righthand / thirdperson_righthand）为物品/方块
 * 应用对应的 GL 变换（平移 → 旋转 ZYX → 缩放），顺序与 Minecraft 原版一致。
 *
 * <p>本扩展提供静态方法 {@link #applyTransform(ModelJson.DisplayTransform)} 和
 * {@link #phaseToDisplayKey(String)}，供 {@link decok.dfcdvadstf.catframe.model.render.UniformRenderPipeline}
 * 在渲染时调用。管线负责 display transform 的 GL 矩阵生命周期（push/pop），
 * 本扩展负责变换计算逻辑。
 */
public final class DisplayTransformExtension {

    private DisplayTransformExtension() {
    }

    /**
     * 应用 JSON model 的 display transform（平移 → 旋转 ZYX → 缩放）。
     * 顺序与 Minecraft 原版一致。
     *
     * @param dt display transform 数据
     */
    public static void applyTransform(ModelJson.DisplayTransform dt) {
        if (dt == null) return;
        if (dt.translation != null && dt.translation.length >= 3) {
            GL11.glTranslatef(dt.translation[0] / 100f, dt.translation[1] / 100f, dt.translation[2] / 100f);
        }
        if (dt.rotation != null && dt.rotation.length >= 3) {
            GL11.glRotatef(dt.rotation[0], 1, 0, 0);
            GL11.glRotatef(dt.rotation[1], 0, 1, 0);
            GL11.glRotatef(dt.rotation[2], 0, 0, 1);
        }
        if (dt.scale != null && dt.scale.length >= 3) {
            GL11.glScalef(dt.scale[0], dt.scale[1], dt.scale[2]);
        }
    }

    /**
     * 将渲染阶段名称映射到 JSON model 的 display 键名。
     *
     * @param phaseName 渲染阶段名称（如 "ITEM_GUI", "ITEM_HAND_FIRST_PERSON"）
     * @return display 键名（如 "gui", "firstperson_righthand"），若无对应返回 null
     */
    public static String phaseToDisplayKey(String phaseName) {
        if (phaseName == null) return null;
        switch (phaseName) {
            case "ITEM_GUI":
            case "BLOCK_GUI":
                return "gui";
            case "ITEM_HAND_FIRST_PERSON":
                return "firstperson_righthand";
            case "ITEM_HAND_THIRD_PERSON":
                return "thirdperson_righthand";
            case "ITEM_HAND":
                return "firstperson_righthand";
            default:
                return null;
        }
    }
}
