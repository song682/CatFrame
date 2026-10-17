package decok.dfcdvadstf.catframe.model.render.extension;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.block.Block;

/**
 * Built-in extension that performs face culling based on the {@code "cullface"} field of each
 * face in JSON models.
 *
 * <h3>How it works</h3>
 * The direction specified by {@code cullface} represents <b>the direction in which to test for
 * occlusion</b> (which adjacent block direction to check), not necessarily the facing direction
 * of that face. When {@link JsonModelBake.BakedQuad#cullface} is not null, this extension calls
 * vanilla occlusion test {@link Block#shouldSideBeRendered(net.minecraft.world.IBlockAccess, int, int, int, int)}:
 * <ul>
 *   <li>returns {@code false} (occluded by adjacent block in that direction) → cull this face;</li>
 *   <li>returns {@code true} (not occluded) → keep this face.</li>
 * </ul>
 *
 * <p>Why not just use {@link Block#isOpaqueCube()}? Vanilla delegates the occlusion decision to
 * the <b>block being rendered itself</b> via {@code shouldSideBeRendered}, which correctly handles
 * cases like glass↔glass, leaves, ice, etc. where non-opaque blocks still need mutual culling.
 * A typical example is glass blocks: internal faces between adjacent glass are culled. Relying
 * solely on {@code isOpaqueCube()} would miss these cases and render internal faces.</p>
 *
 * <p>Note: only faces that explicitly declare {@code "cullface"} in the JSON model trigger
 * culling; undeclared faces are unaffected.</p>
 *
 * <h3>Typical use cases</h3>
 * Transparent connected blocks such as glass panes and iron bars — faces adjacent to solid
 * blocks do not need to be rendered.
 *
 * <h3>JSON example</h3>
 * <pre>{@code
 * "south": { "uv": [7,0,9,16], "texture": "#edge", "cullface": "south" }
 * }</pre>
 *
 * <p>This extension is automatically registered at the head of the chain by
 * {@link ModelRenderRegistry} (built-in extension); mods do not need to register it.</p>
 */
@SideOnly(Side.CLIENT)
public final class FaceCullExtension implements IModelRenderExtension {

    public FaceCullExtension() {
    }

    @Override
    public void apply(RenderContext ctx) {
        // Only effective during block world rendering
        if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
        if (ctx.world == null || ctx.block == null) return;

        Direction cullDir = ctx.quad.cullface;
        if (cullDir == null) return;

        // cullface specifies the direction to test for occlusion: compute adjacent block coords in that direction
        // Use Direction.getStepX/Y/Z for correct offset
        int nx = ctx.x + cullDir.getStepX();
        int ny = ctx.y + cullDir.getStepY();
        int nz = ctx.z + cullDir.getStepZ();

        // Use vanilla occlusion test: the block being rendered decides whether the face in that direction should render.
        // Side index aligns with Direction.get3DDataValue() (DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5),
        // matching vanilla RenderBlocks.shouldSideBeRendered semantics. Returns false if occluded → cull face; true → keep.
        boolean shouldRender = ctx.block.shouldSideBeRendered(
                ctx.world, nx, ny, nz, cullDir.get3DDataValue());

        if (!shouldRender) {
            ctx.skip = true;
        }
    }
}
