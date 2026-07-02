package decok.dfcdvadstf.catframe.model;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.core.baking.ModelBaker;
import decok.dfcdvadstf.catframe.model.state.BlockStateModel;
import decok.dfcdvadstf.catframe.model.state.BlockStateModelPart;
import decok.dfcdvadstf.catframe.model.state.BlockstateJson;
import decok.dfcdvadstf.catframe.model.state.IMetadataBlockstateRedirect;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.*;

/**
 * 运行时 multipart 模型，用于 {@link BlockPane} 子类方块的连接状态渲染。
 * <p>
 * 1.7.10 中 {@link BlockPane} 的连接状态（north/east/south/west）是运行时由
 * {@link BlockPane#canPaneConnectTo} 动态判断的，不编码在 metadata 中。
 * 本类在 {@link #collectParts} 时从世界邻居获取连接，加载/使用 multipart
 * blockstate、评估条件并合并匹配的模型部件。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li><b>redirect 模式</b>（如染色玻璃板）：metadata = 颜色索引，
 *       通过 {@link IMetadataBlockstateRedirect} 映射到 per-color blockstate</li>
 *   <li><b>直接模式</b>（如无色玻璃板）：直接使用传入的 blockstate JSON，
 *       metadata 值不影响 blockstate 选择</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class PaneMultipartRedirectModel implements BlockStateModel {

    private final Block block;
    @javax.annotation.Nullable
    private final IMetadataBlockstateRedirect redirect;
    @javax.annotation.Nullable
    private final BlockstateJson directBs;
    @javax.annotation.Nullable
    private final String namespace;
    private final boolean autoOcclusion;

    /**
     * Redirect 模式构造器（用于染色玻璃板等）。
     *
     * @param block         方块实例（用于 {@link BlockPane#canPaneConnectTo}）
     * @param redirect      颜色索引 → blockstate 文件名映射
     * @param namespace     blockstate 所在命名空间
     * @param autoOcclusion 是否启用环境光遮蔽
     */
    public PaneMultipartRedirectModel(Block block, IMetadataBlockstateRedirect redirect,
                                      String namespace, boolean autoOcclusion) {
        this.block = block;
        this.redirect = redirect;
        this.directBs = null;
        this.namespace = namespace;
        this.autoOcclusion = autoOcclusion;
    }

    /**
     * 直接模式构造器（用于无色玻璃板等）。
     *
     * @param block         方块实例（用于 {@link BlockPane#canPaneConnectTo}）
     * @param directBs      blockstate JSON（直接从 loadedBlockstates 传入）
     * @param autoOcclusion 是否启用环境光遮蔽
     */
    public PaneMultipartRedirectModel(Block block, BlockstateJson directBs, boolean autoOcclusion) {
        this.block = block;
        this.redirect = null;
        this.directBs = directBs;
        this.namespace = null;
        this.autoOcclusion = autoOcclusion;
    }

    @Override
    public BlockStateModelPart collectParts(IBlockAccess world, int x, int y, int z, int metadata) {
        // 1. 获取 multipart blockstate JSON
        BlockstateJson targetBs;
        if (redirect != null) {
            // Redirect 模式：通过 redirect 获取 per-color blockstate 名称
            String targetName = redirect.redirect(metadata);
            if (targetName == null) return BlockStateModelPart.empty();

            // 优先使用 init 时的缓存
            String cacheKey = namespace + ":" + targetName;
            targetBs = VMMDataLoader.cachedRedirectBlockstates.get(cacheKey);
            if (targetBs == null) {
                targetBs = VMMDataLoader.loadSingleBlockstate(namespace, targetName);
            }
        } else {
            // 直接模式：使用传入的 blockstate JSON
            targetBs = directBs;
        }
        if (targetBs == null || targetBs.multipart == null) return BlockStateModelPart.empty();

        // 3. 运行时确定连接状态（BlockPane.canPaneConnectTo）
        Map<String, String> props = buildConnectionProps(world, x, y, z);

        // 4. 评估 multipart 条件，合并匹配的部件
        CatFrame.logger.info("[PaneMP] collectParts block={} pos=({},{},{}) meta={} props={} targetBs.multipart={}",
                Block.blockRegistry.getNameForObject(block), x, y, z, metadata, props,
                targetBs.multipart != null ? targetBs.multipart.size() : 0);

        Map<EnumFacing, List<BlockJsonModelBake.BakedQuad>> mergedFace
                = new EnumMap<>(EnumFacing.class);
        List<BlockJsonModelBake.BakedQuad> mergedGeneral = new ArrayList<>();

        for (BlockstateJson.MultipartCase mpc : targetBs.multipart) {
            boolean applies = (mpc.when == null) || mpc.when.matches(props);
            CatFrame.logger.info("[PaneMP]   case: when={} apply.model={} applies={}",
                    mpc.when != null ? mpc.when.conditions : "null",
                    mpc.apply != null ? mpc.apply.model : "null", applies);
            if (applies && mpc.apply != null && mpc.apply.model != null) {
                BlockStateModelPart part = ModelBaker.bake(mpc.apply.model, mpc.apply.x, mpc.apply.y);
                CatFrame.logger.info("[PaneMP]   bake('{}', x={}, y={}) -> part={}", mpc.apply.model, mpc.apply.x, mpc.apply.y, part);
                if (part != null) {
                    for (EnumFacing dir : EnumFacing.values()) {
                        mergedFace.computeIfAbsent(dir, k -> new ArrayList<>())
                                .addAll(part.getQuads(dir));
                    }
                    mergedGeneral.addAll(part.getGeneralQuads());
                }
            }
        }

        if (mergedGeneral.isEmpty() && mergedFace.isEmpty()) {
            return BlockStateModelPart.empty();
        }
        return BlockStateModelPart.fromFaceMap(mergedFace, mergedGeneral);
    }

    @Override
    public boolean isFullModel() {
        return false;
    }

    /**
     * 构建连接属性映射：从世界邻居获取 north/east/south/west。
     */
    private Map<String, String> buildConnectionProps(IBlockAccess world, int x, int y, int z) {
        Map<String, String> props = new HashMap<>();
        if (world == null || !(block instanceof BlockPane)) {
            props.put("north", "false");
            props.put("east", "false");
            props.put("south", "false");
            props.put("west", "false");
            return props;
        }
        BlockPane pane = (BlockPane) block;
        // 传入邻居坐标 + 方向（vanilla 用法：坐标是邻居位置，ForgeDirection 望向当前）
        props.put("north", pane.canPaneConnectTo(world, x, y, z - 1, ForgeDirection.NORTH) ? "true" : "false");
        props.put("east",  pane.canPaneConnectTo(world, x + 1, y, z, ForgeDirection.EAST)  ? "true" : "false");
        props.put("south", pane.canPaneConnectTo(world, x, y, z + 1, ForgeDirection.SOUTH) ? "true" : "false");
        props.put("west",  pane.canPaneConnectTo(world, x - 1, y, z, ForgeDirection.WEST)  ? "true" : "false");
        return props;
    }
}
