package decok.dfcdvadstf.catframe.model.render.extension;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.ModelRenderRegistry;
import decok.dfcdvadstf.catframe.model.render.RenderContext;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.block.Block;

/**
 * 内建渲染扩展：根据 JSON 模型中 face 的 {@code "cullface"} 字段执行面剔除。
 *
 * <h3>工作方式</h3>
 * {@code cullface} 填写的方向代表<b>检测遮挡的方向</b>（去检测哪个方向的相邻方块），
 * 与该 face 自身朝向没有必然关系。当 {@link JsonModelBake.BakedQuad#cullface}
 * 不为 null 时，本扩展调用原版遮挡判定
 * {@link Block#shouldSideBeRendered(net.minecraft.world.IBlockAccess, int, int, int, int)}：
 * <ul>
 *   <li>该方法返回 {@code false}（即该方向被相邻方块遮挡）→ 剔除本面；</li>
 *   <li>返回 {@code true}（未被遮挡）→ 保留本面。</li>
 * </ul>
 *
 * <p>为何不直接用 {@link Block#isOpaqueCube()}：原版把
 * “是否被遮挡” 的判定下放给<b>被渲染方块自身</b>的
 * {@code shouldSideBeRendered}，从而正确处理玻璃↔玻璃、树叶、冰等
 * 非不透明但仍需互相剔除的情形。典型例子即视频中的玻璃立方体——
 * 相邻玻璃之间的内部面会被剔除。仅判断 {@code isOpaqueCube()} 会漏掉这些情况，
 * 导致内部面全部渲染。</p>
 *
 * <p>注意：只有 JSON 模型中显式声明了 {@code "cullface"} 的 face 才会触发剔除，
 * 未声明的 face 不受影响。</p>
 *
 * <h3>典型使用场景</h3>
 * 玻璃板（glass_pane）、铁栏杆（iron_bars）等透明连接方块 ——
 * 与实心方块相邻的面不需要绘制。
 *
 * <h3>JSON 示例</h3>
 * <pre>{@code
 * "south": { "uv": [7,0,9,16], "texture": "#edge", "cullface": "south" }
 * }</pre>
 *
 * <p>本扩展由 {@link ModelRenderRegistry} 在链头自动注册（内建扩展），
 * 模组无需手动注册。</p>
 */
@SideOnly(Side.CLIENT)
public final class FaceCullExtension implements IModelRenderExtension {

    public FaceCullExtension() {
    }

    @Override
    public void apply(RenderContext ctx) {
        // 仅在世界渲染阶段生效
        if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
        if (ctx.world == null || ctx.block == null) return;

        Direction cullDir = ctx.quad.cullface;
        if (cullDir == null) return;

        // cullface 是「检测遮挡的方向」：计算该方向上的相邻方块坐标
        // 注意：使用 Direction 的 getStepX/Y/Z 确保正确偏移
        int nx = ctx.x + cullDir.getStepX();
        int ny = ctx.y + cullDir.getStepY();
        int nz = ctx.z + cullDir.getStepZ();

        // 使用原版遮挡判定：由被渲染方块自身决定该方向的面是否应绘制。
        // side 索引与 Direction.get3DDataValue() 对齐
        // （DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5），
        // 与原版 RenderBlocks 传入 shouldSideBeRendered 的语义一致。
        // 返回 false 表示该方向被遮挡 → 剔除本面；返回 true 表示保留。
        boolean shouldRender = ctx.block.shouldSideBeRendered(
                ctx.world, nx, ny, nz, cullDir.get3DDataValue());

        if (!shouldRender) {
            ctx.skip = true;
        }
    }
}
