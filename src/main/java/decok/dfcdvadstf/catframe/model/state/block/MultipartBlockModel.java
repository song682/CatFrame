package decok.dfcdvadstf.catframe.model.state.block;

import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.ParticleIconResolver;
import decok.dfcdvadstf.catframe.model.core.baking.AtlasGuard;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake;
import decok.dfcdvadstf.catframe.model.core.baking.ModelBaker;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Multipart 条件组合模型。根据属性条件评估每个 MultipartCase，
 * 合并所有匹配的部件。
 *
 * <p>对应 1.21.5 的 Multipart BlockStateModel 实现。
 *
 * <p>[S7] 本类为后期开发预留：门（doors）、玻璃（glass）、栅栏（fences/walls）等
 * 需要 multipart 组合逻辑的方块。当前主 VMM 中的 multipart 路径采用内联实现，
 * 待相关方块类型合并后将切换到本类。
 */
public class MultipartBlockModel implements BlockStateModel {
    private final List<MultipartEntry> entries;

    public MultipartBlockModel(List<MultipartEntry> entries) {
        this.entries = entries;
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        Map<Direction, List<JsonModelBake.BakedQuad>> mergedFace
                = new EnumMap<>(Direction.class);
        List<JsonModelBake.BakedQuad> mergedGeneral = new ArrayList<>();

        for (MultipartEntry entry : entries) {
            boolean applies = (entry.when == null) || entry.when.matches(metadata);
            if (applies) {
                BlockStateModelPart part = AtlasGuard.gate(ModelBaker.bake(entry.modelPath), entry.modelPath);
                if (part != null) {
                    mergedGeneral.addAll(part.getGeneralQuads());
                    for (Direction dir : Direction.values()) {
                        mergedFace.computeIfAbsent(dir, k -> new ArrayList<>())
                                .addAll(part.getQuads(dir));
                    }
                }
            }
        }

        return BlockStateModelPart.fromFaceMap(mergedFace, mergedGeneral);
    }

    @Override
    public boolean isFullModel() {
        return false; // Multipart 通常是叠加层
    }

    // ==================== 粒子纹理解析（模型驱动） ====================

    /**
     * 粒子纹理解析：取首个 entry 的模型（对标 26.1.2 首 selector 语义，与条件匹配无关），
     * 返回其显式 particle 槽（缺失为 null，由 ParticleIconResolver 映射 missingno）。
     * 本类为后期预留（当前生产路径不经过），保持与其它实现同构的语义即可。
     */
    @Override
    public IIcon particleIcon(int metadata) {
        if (entries.isEmpty()) return null;
        MultipartEntry first = entries.get(0);
        if (first.modelPath == null) return null;
        BlockStateModelPart part = AtlasGuard.gate(ModelBaker.bake(first.modelPath), first.modelPath);
        return ParticleIconResolver.fromPart(part);
    }

    /**
     * Multipart 条件。支持基于 metadata 范围的条件判断。
     */
    @FunctionalInterface
    public interface MultipartCondition {
        boolean matches(int metadata);
    }

    /**
     * 一个 multipart 条件条目。
     */
    public static class MultipartEntry {
        /**
         * 条件（null=始终应用）
         */
        public final MultipartCondition when;
        /**
         * 模型路径
         */
        public final String modelPath;

        public MultipartEntry(MultipartCondition when, String modelPath) {
            this.when = when;
            this.modelPath = modelPath;
        }
    }
}
