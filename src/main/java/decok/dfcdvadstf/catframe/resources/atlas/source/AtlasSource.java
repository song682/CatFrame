package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.resources.IResourceManager;

import java.util.List;

/**
 * 定义驱动源 SPI（对标 26.1.2 {@code SpriteSource}）。
 * <p>
 * 一个源把「扫描/裁剪/染色」结果产出为 {@link SpriteRef} 列表；sources 按定义
 * JSON 中的声明顺序执行，重复 sprite id 后者胜（收集器告警）。
 * {@link #removesCollected()} 为 true 的源（filter）不产出，而是对收集器已收集
 * 的 id 应用 {@link #shouldRemove(String)} 移除语义。
 *
 * <p>Source SPI for atlas definition files: produces sprite refs in JSON order;
 * filter sources remove already-collected ids instead of producing.
 */
@SideOnly(Side.CLIENT)
public interface AtlasSource {

    /** 产出本源的 sprite 引用列表（filter 源返回空列表）。 */
    List<SpriteRef> list(IResourceManager manager);

    /** 是否为过滤源（true 时 list 无产出，收集器对其已收集集合应用 shouldRemove）。 */
    boolean removesCollected();

    /** 过滤判定（仅 removesCollected() 为 true 的源调用）：spriteId 是否应被移除。 */
    boolean shouldRemove(String spriteId);

    /** 源类型名（诊断日志，如 {@code minecraft:directory}）。 */
    String type();
}
