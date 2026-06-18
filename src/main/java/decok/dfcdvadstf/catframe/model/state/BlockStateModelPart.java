package decok.dfcdvadstf.catframe.model.state;

import decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad;
import net.minecraft.util.EnumFacing;

import java.util.*;

/**
 * 按剔除方向分组的烘焙 quad 集合。类似于 1.21.5 的 BlockStateModelPart，
 * 把 BakedQuad 按 cullface 方向索引，加速渲染时的方向查询。
 *
 * <p>结构：
 * <ul>
 *   <li>{@link #getQuads(EnumFacing)} — 返回按该方向剔除的 quad 列表</li>
 *   <li>{@link #getGeneralQuads()} — 返回无剔除方向（总是渲染）的 quad 列表</li>
 *   <li>{@link #getAllQuads()} — 合并所有 quad，用于物品渲染等无需剔除的场景</li>
 * </ul>
 */
public class BlockStateModelPart {
    private final Map<EnumFacing, List<BakedQuad>> faceQuads;
    private final List<BakedQuad> generalQuads;
    private List<BakedQuad> allQuadsCache;

    private BlockStateModelPart(Map<EnumFacing, List<BakedQuad>> faceQuads,
                                List<BakedQuad> generalQuads) {
        this.faceQuads = faceQuads;
        this.generalQuads = generalQuads;
    }

    // ==================== 工厂方法 ====================

    /**
     * 从 BakedQuad 列表构建 BlockStateModelPart，自动按 cullface 分组。
     */
    public static BlockStateModelPart fromQuads(List<BakedQuad> quads) {
        Map<EnumFacing, List<BakedQuad>> faceMap = new EnumMap<>(EnumFacing.class);
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

        return new BlockStateModelPart(faceMap, general);
    }

    /**
     * 从按方向预分组的 map 创建（用于 multipart 合并等场景）。
     */
    public static BlockStateModelPart fromFaceMap(
            Map<EnumFacing, List<BakedQuad>> faceMap,
            List<BakedQuad> generalQuads) {
        return new BlockStateModelPart(
                faceMap != null ? new EnumMap<>(faceMap) : new EnumMap<>(EnumFacing.class),
                generalQuads != null ? new ArrayList<>(generalQuads) : new ArrayList<>()
        );
    }

    /**
     * 空的 BlockStateModelPart。
     */
    public static BlockStateModelPart empty() {
        return new BlockStateModelPart(new EnumMap<>(EnumFacing.class), new ArrayList<>());
    }

    // ==================== 查询 ====================

    /**
     * 返回给定剔除方向的所有 quad。若没有该方向的 quad 则返回空列表。
     * [W4 修复] 返回不可变视图，防止调用方意外修改内部状态。
     */
    public List<BakedQuad> getQuads(EnumFacing side) {
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
        Map<EnumFacing, List<BakedQuad>> mergedFace = new EnumMap<>(EnumFacing.class);
        mergedFace.putAll(this.faceQuads);
        for (Map.Entry<EnumFacing, List<BakedQuad>> entry : other.faceQuads.entrySet()) {
            mergedFace.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
        }
        List<BakedQuad> mergedGeneral = new ArrayList<>(this.generalQuads);
        mergedGeneral.addAll(other.generalQuads);
        return new BlockStateModelPart(mergedFace, mergedGeneral);
    }
}
