package decok.dfcdvadstf.catframe.model.render;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.util.EnumFacing;

/**
 * 内建渲染扩展：根据 JSON 模型中 face 的 {@code "cullface"} 字段执行面剔除。
 *
 * <h3>工作方式</h3>
 * 当 {@link decok.dfcdvadstf.catframe.model.BlockJsonModelBake.BakedQuad#cullface}
 * 不为 null 时，检查该方向上的相邻方块：
 * <ul>
 *   <li>若相邻方块是完整不透明方块（{@link Block#isOpaqueCube()}），则剔除本面；</li>
 *   <li>若相邻方块是空气 / 透明方块，则保留本面。</li>
 * </ul>
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

  public FaceCullExtension() {}

  @Override
  public void apply(RenderContext ctx) {
    // 仅在世界渲染阶段生效
    if (ctx.phase != RenderPhase.BLOCK_WORLD) return;
    if (ctx.world == null || ctx.block == null) return;

    EnumFacing cullDir = ctx.quad.cullface;
    if (cullDir == null) return;

    // 手动计算相邻方块坐标
    // 注意：1.7.10 中 EnumFacing.getFrontOffsetX() 的东西偏移与实际相反
    // (EAST=-1, WEST=+1)，而面法线指向正常方向，故手动 switch 确保正确
    int nx = ctx.x, ny = ctx.y, nz = ctx.z;
    switch (cullDir) {
      case DOWN:  ny--; break;
      case UP:    ny++; break;
      case NORTH: nz--; break;
      case SOUTH: nz++; break;
      case WEST:  nx--; break;
      case EAST:  nx++; break;
      default:    return;
    }

    Block neighbor = ctx.world.getBlock(nx, ny, nz);
    if (neighbor == null) return;

    boolean opaque = neighbor.isOpaqueCube();

    // 相邻方块为完整不透明方块时，剔除本面
    if (opaque) {
      ctx.skip = true;
    }
  }
}
