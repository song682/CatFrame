package decok.dfcdvadstf.catframe.model.lazy;

import decok.dfcdvadstf.catframe.model.BakedModelCache;
import decok.dfcdvadstf.catframe.model.ModelManagerDataLoader;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake.BakedQuad;
import decok.dfcdvadstf.catframe.model.state.*;
import net.minecraft.world.IBlockAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 懒烘焙 blockstate redirect 模型。
 * <p>
 * 处理 {@link IMetadataBlockstateRedirect}：将每个 metadata 重定向到不同的 blockstate，
 * 使用原方块的 {@link IMetadataMapper} 进行 multipart 匹配。
 * <p>
 * 替代旧系统中 {@code bakeBlockstateForBlock()} 的 redirect 处理路径。
 * 目标 blockstate 按需加载并缓存，模型烘焙委托给 {@link BakedModelCache}。
 */
public class LazyRedirectModel implements BlockStateModel {
    private final IMetadataBlockstateRedirect redirect;
    private final IMetadataMapper mapper;
    private final String namespace;
    /** metadata → 解析后的 multipart 条目缓存 */
    private final Map<Integer, List<ResolvedMultipart>> resolvedCache = new HashMap<>();

    public LazyRedirectModel(IMetadataBlockstateRedirect redirect,
                              IMetadataMapper mapper, String namespace) {
        this.redirect = redirect;
        this.mapper = mapper;
        this.namespace = namespace;
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        List<ResolvedMultipart> resolved = resolvedCache.get(metadata);
        if (resolved == null) {
            resolved = resolveMultipart(metadata);
            resolvedCache.put(metadata, resolved);
        }

        if (resolved.isEmpty()) return BlockStateModelPart.empty();

        List<BakedQuad> allQuads = new ArrayList<>();
        for (ResolvedMultipart rm : resolved) {
            String cacheKey = BakedModelCache.buildKey(rm.modelPath, rm.rotX, rm.rotY);
            BlockStateModelPart part = BakedModelCache.INSTANCE.get(cacheKey);
            if (part != null && !part.isEmpty()) {
                allQuads.addAll(part.getAllQuads());
            }
        }

        return allQuads.isEmpty() ? BlockStateModelPart.empty() : BlockStateModelPart.fromQuads(allQuads);
    }

    private List<ResolvedMultipart> resolveMultipart(int metadata) {
        String targetName = redirect.redirect(metadata);
        if (targetName == null) return java.util.Collections.emptyList();

        // 查找目标 blockstate
        BlockstateJson targetBs = findTargetBlockstate(targetName);
        if (targetBs == null) return java.util.Collections.emptyList();

        if (targetBs.multipart == null || mapper == null) {
            return java.util.Collections.emptyList();
        }

        Map<String, String> props = mapper.map(metadata);
        List<ResolvedMultipart> result = new ArrayList<>();

        for (BlockstateJson.MultipartCase mpc : targetBs.multipart) {
            boolean applies = (mpc.when == null) || mpc.when.matches(props);
            if (applies && mpc.apply != null && mpc.apply.model != null) {
                result.add(new ResolvedMultipart(mpc.apply.model, mpc.apply.x, mpc.apply.y));
            }
        }

        return result;
    }

    private BlockstateJson findTargetBlockstate(String targetName) {
        // 先查 loadedBlockstates
        Map<String, BlockstateJson> nsMap = ModelManagerDataLoader.loadedBlockstates.get(namespace);
        BlockstateJson targetBs = (nsMap != null) ? nsMap.get(targetName) : null;
        if (targetBs != null) return targetBs;

        // 尝试动态加载
        targetBs = ModelManagerDataLoader.loadSingleBlockstate(namespace, targetName);
        if (targetBs != null) {
            // 缓存到 loadedBlockstates 供后续使用
            if (nsMap == null) {
                nsMap = new java.util.HashMap<>();
                ModelManagerDataLoader.loadedBlockstates.put(namespace, nsMap);
            }
            nsMap.put(targetName, targetBs);
        }
        return targetBs;
    }

    private static class ResolvedMultipart {
        final String modelPath;
        final int rotX, rotY;

        ResolvedMultipart(String modelPath, int rotX, int rotY) {
            this.modelPath = modelPath;
            this.rotX = rotX;
            this.rotY = rotY;
        }
    }
}
