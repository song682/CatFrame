package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.core.ModelJson;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 按剔除方向分组的烘焙 quad 集合。类似于 1.21.5 的 BlockStateModelPart，
 * 把 BakedQuad 按 cullface 方向索引，加速渲染时的方向查询。
 *
 * <p>结构：
 * <ul>
 *   <li>{@link #getQuads(Direction)} — 返回按该方向剔除的 quad 列表</li>
 *   <li>{@link #getGeneralQuads()} — 返回无剔除方向（总是渲染）的 quad 列表</li>
 *   <li>{@link #getAllQuads()} — 合并所有 quad，用于物品渲染等无需剔除的场景</li>
 * </ul>
 */
public class BlockStateModelPart {
    private final Map<Direction, List<BakedQuad>> faceQuads;
    private final List<BakedQuad> generalQuads;
    private List<BakedQuad> allQuadsCache;

    /**
     * 模型级别的 display transforms（从 ModelJson.display 传播）。
     * 键为 display 场景（"gui", "firstperson_righthand", "thirdperson_righthand" 等）。
     * 由 DisplayTransformExtension 在渲染时读取并应用。
     */
    @Nullable
    private final Map<String, ModelJson.DisplayTransform> partDisplay;

    private BlockStateModelPart(Map<Direction, List<BakedQuad>> faceQuads,
                                List<BakedQuad> generalQuads) {
        this(faceQuads, generalQuads, null);
    }

    private BlockStateModelPart(Map<Direction, List<BakedQuad>> faceQuads,
                                List<BakedQuad> generalQuads,
                                @Nullable Map<String, ModelJson.DisplayTransform> partDisplay) {
        this.faceQuads = faceQuads;
        this.generalQuads = generalQuads;
        this.partDisplay = partDisplay;
    }

    // ==================== 工厂方法 ====================

    /**
     * 从 BakedQuad 列表构建 BlockStateModelPart，自动按 cullface 分组。
     */
    public static BlockStateModelPart fromQuads(List<BakedQuad> quads) {
        return fromQuads(quads, null);
    }

    public static BlockStateModelPart fromQuads(List<BakedQuad> quads,
                                                 @Nullable Map<String, ModelJson.DisplayTransform> display) {
        Map<Direction, List<BakedQuad>> faceMap = new EnumMap<>(Direction.class);
        List<BakedQuad> general = new ArrayList<>();

        if (quads != null) {
            for (BakedQuad q : quads) {
                if (q.cullface != null) {
                    faceMap.computeIfAbsent(q.cullface, k -> new ArrayList<>()).add(q);
                } else {
                    general.add(q);
                }
            }
        }

        return new BlockStateModelPart(faceMap, general, display);
    }

    /**
     * 从按方向预分组的 map 创建（用于 multipart 合并等场景）。
     */
    public static BlockStateModelPart fromFaceMap(
            Map<Direction, List<BakedQuad>> faceMap,
            List<BakedQuad> generalQuads) {
        return fromFaceMap(faceMap, generalQuads, null);
    }

    public static BlockStateModelPart fromFaceMap(
            Map<Direction, List<BakedQuad>> faceMap,
            List<BakedQuad> generalQuads,
            @Nullable Map<String, ModelJson.DisplayTransform> display) {
        return new BlockStateModelPart(
                faceMap != null ? new EnumMap<>(faceMap) : new EnumMap<>(Direction.class),
                generalQuads != null ? new ArrayList<>(generalQuads) : new ArrayList<>(),
                display
        );
    }

    /**
     * 空的 BlockStateModelPart。
     */
    public static BlockStateModelPart empty() {
        return new BlockStateModelPart(new EnumMap<>(Direction.class), new ArrayList<>(), null);
    }

    /**
     * 获取模型级别的 display transforms。
     */
    @Nullable
    public Map<String, ModelJson.DisplayTransform> getDisplay() {
        return partDisplay;
    }

    /**
     * 创建一个新的 BlockStateModelPart，以给定的 display 替换原有 display。
     * 如果 display 与当前相同则返回 this（避免不必要分配）。
     */
    public BlockStateModelPart withDisplay(@Nullable Map<String, ModelJson.DisplayTransform> display) {
        if (display == this.partDisplay) return this;
        if (display != null && display.equals(this.partDisplay)) return this;
        return new BlockStateModelPart(faceQuads, generalQuads, display);
    }

    // ==================== 查询 ====================

    /**
     * 返回给定剔除方向的所有 quad。若没有该方向的 quad 则返回空列表。
     * [W4 修复] 返回不可变视图，防止调用方意外修改内部状态。
     */
    public List<BakedQuad> getQuads(Direction side) {
        List<BakedQuad> q = faceQuads.get(side);
        return q != null ? Collections.unmodifiableList(q) : Collections.emptyList();
    }

    /**
     * 返回所有无剔除方向的 quad（总是渲染）。
     * [W4 修复] 返回不可变视图。
     */
    public List<BakedQuad> getGeneralQuads() {
        return Collections.unmodifiableList(generalQuads);
    }

    /**
     * 返回所有 quad（无论方向），用于物品渲染等无需剔除的场景。
     */
    public List<BakedQuad> getAllQuads() {
        if (allQuadsCache == null) {
            int total = generalQuads.size();
            for (List<BakedQuad> list : faceQuads.values()) {
                total += list.size();
            }
            allQuadsCache = new ArrayList<>(total);
            allQuadsCache.addAll(generalQuads);
            for (List<BakedQuad> list : faceQuads.values()) {
                allQuadsCache.addAll(list);
            }
        }
        return allQuadsCache;
    }

    /**
     * 是否有任何 quad（包括有方向和没方向的）。
     */
    public boolean isEmpty() {
        if (!generalQuads.isEmpty()) return false;
        for (List<BakedQuad> list : faceQuads.values()) {
            if (!list.isEmpty()) return false;
        }
        return true;
    }

    /**
     * 合并两个 BlockStateModelPart（用于 multipart 组合）。
     */
    public BlockStateModelPart merge(BlockStateModelPart other) {
        Map<Direction, List<BakedQuad>> mergedFace = new EnumMap<>(Direction.class);
        mergedFace.putAll(this.faceQuads);
        for (Map.Entry<Direction, List<BakedQuad>> entry : other.faceQuads.entrySet()) {
            mergedFace.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
        }
        List<BakedQuad> mergedGeneral = new ArrayList<>(this.generalQuads);
        mergedGeneral.addAll(other.generalQuads);
        // 合并 display：优先使用 this 的，如果 this 没有则使用 other 的
        Map<String, ModelJson.DisplayTransform> mergedDisplay = this.partDisplay != null
                ? this.partDisplay
                : other.partDisplay;
        return new BlockStateModelPart(mergedFace, mergedGeneral, mergedDisplay);
    }

    // ==================== 新增（对齐 26.1.2） ====================

    /**
     * 是否启用环境光遮蔽。
     * <p>
     * 遍历所有 quad 检查 ambientOcclusion 字段。
     * 如果任一 quad 的 ambientOcclusion 不为 {@code false}，则认为启用 AO。
     *
     * @return true=启用环境光遮蔽
     */
    public boolean useAmbientOcclusion() {
        // 检查有方向 quad
        for (List<BakedQuad> list : faceQuads.values()) {
            for (BakedQuad q : list) {
                if (q.ambientOcclusion != Boolean.FALSE) {
                    return true;
                }
            }
        }
        // 检查无方向 quad
        for (BakedQuad q : generalQuads) {
            if (q.ambientOcclusion != Boolean.FALSE) {
                return true;
            }
        }
        return false;
    }

    /**
     * 粒子纹理。
     * <p>
     * 返回第一个可用 quad 的 {@link IIcon}。
     * 如果模型没有 quad 则返回 null。
     *
     * @return 粒子用的 IIcon，可为 null
     */
    @Nullable
    public IIcon particleIcon() {
        // 先查有方向 quad
        for (List<BakedQuad> list : faceQuads.values()) {
            for (BakedQuad q : list) {
                if (q.icon != null) return q.icon;
            }
        }
        // 再查无方向 quad
        for (BakedQuad q : generalQuads) {
            if (q.icon != null) return q.icon;
        }
        return null;
    }
}
