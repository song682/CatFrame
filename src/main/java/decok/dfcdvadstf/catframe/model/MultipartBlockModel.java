package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import net.minecraft.util.EnumFacing;
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
 */
public class MultipartBlockModel implements BlockStateModel {
    private final List<MultipartEntry> entries;

    public MultipartBlockModel(List<MultipartEntry> entries) {
        this.entries = entries;
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        Map<EnumFacing, List<BlockJsonModelBake.BakedQuad>> mergedFace
                = new EnumMap<>(EnumFacing.class);
        List<BlockJsonModelBake.BakedQuad> mergedGeneral = new ArrayList<>();

        for (MultipartEntry entry : entries) {
            boolean applies = (entry.when == null) || entry.when.matches(metadata);
            if (applies) {
                BlockStateModelPart part = VanillaModelManager.ModelRegistration.bakeModelPart(entry.modelPath);
                if (part != null) {
                    mergedGeneral.addAll(part.getGeneralQuads());
                    for (EnumFacing dir : EnumFacing.values()) {
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
