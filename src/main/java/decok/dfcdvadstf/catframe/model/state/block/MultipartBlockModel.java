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
 * Multipart conditional composition model. Evaluates each MultipartCase against
 * property conditions and merges all matching parts.
 *
 * <p>Corresponds to the 1.21.5 Multipart BlockStateModel implementation.
 *
 * <p>[S7] Reserved for later development: blocks that need multipart composition
 * logic such as doors, glass, and fences/walls. The multipart path in the main VMM
 * currently uses an inline implementation and will switch to this class once the
 * relevant block types are consolidated.
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
        return false; // Multipart is usually an overlay layer
    }

    // ==================== Particle icon resolution (model-driven) ====================

    /**
     * Particle icon resolution: takes the first entry's model (26.1.2 first-selector
     * semantics, unrelated to condition matching) and returns its explicit particle slot
     * (null when missing, mapped to missingno by ParticleIconResolver). This class is a
     * later-development placeholder (not on the current production path); it only needs to
     * keep semantics isomorphic to the other implementations.
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
     * Multipart condition. Supports condition evaluation based on metadata.
     */
    @FunctionalInterface
    public interface MultipartCondition {
        boolean matches(int metadata);
    }

    /**
     * A multipart condition entry.
     */
    public static class MultipartEntry {
        /**
         * Condition (null = always applies)
         */
        public final MultipartCondition when;
        /**
         * Model path
         */
        public final String modelPath;

        public MultipartEntry(MultipartCondition when, String modelPath) {
            this.when = when;
            this.modelPath = modelPath;
        }
    }
}
