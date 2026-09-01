package decok.dfcdvadstf.catframe.model.render.extension;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.model.render.IModelRenderExtension;
import decok.dfcdvadstf.catframe.model.render.api.RenderContext;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时调试扩展：验证 blockstateProps / itemProps 在扩展链中的暴露（Test Plan 2 手动验证）。
 * <p>
 * 验证点：
 * <ul>
 *   <li>{@link RenderPhase#BLOCK_WORLD}：玻璃板 / 铁栏杆的 blockstateProps 非空且含
 *       north/east/south/west，红石线含 power，普通石头为 null；</li>
 *   <li>{@link RenderPhase#BLOCK_DESTROY}：破坏贴花路径 blockstateProps 随提交携带
 *       （动态方块非空）；</li>
 *   <li>{@link RenderPhase#ITEM_GUI}：带 damage 的工具有 damage / display_context 等，
 *       第三方 IItemStateProvider 路径为 null（优雅降级）。</li>
 * </ul>
 * <p>
 * 限频策略：同一种方块（或同物品同 damage）只打印一次；非 null 的方块 props 内容变化时
 * 重新打印（可观察红石 power 随位置变化）。itemProps 只按 key 读取（惰性 Map，
 * 避免 entrySet 全量遍历触发全部求值）。
 * <p>
 * 线程安全：渲染路径可在任意线程进入（Beddium 多线程区块编译），去重集合全部使用并发容器。
 * <p>
 * <b>验证完成后删除本类及 {@code ClientProxy} 中的注册行。</b>
 */
@SideOnly(Side.CLIENT)
public final class DebugPropsExtension implements IModelRenderExtension {

    private static final Logger LOGGER = LogManager.getLogger(DebugPropsExtension.class);

    /** 已打印过 null 的方块 / 物品（"phase|key"）。 */
    private static final Set<String> SEEN_NULL = ConcurrentHashMap.newKeySet();
    /** 每个方块最近一次打印的 props 内容（"phase|blockKey" -> 内容），内容变化时重打。 */
    private static final Map<String, String> LAST_PROPS = new ConcurrentHashMap<>();
    /** BLOCK_DESTROY 已打印过的位置（"blockKey|x,y,z"）。 */
    private static final Set<String> SEEN_DESTROY = ConcurrentHashMap.newKeySet();
    /** ITEM_GUI 已打印过的物品（"itemKey|damage"）。 */
    private static final Set<String> SEEN_ITEM = ConcurrentHashMap.newKeySet();

    @Override
    public void apply(RenderContext ctx) {
        switch (ctx.phase) {
            case BLOCK_WORLD:
                logBlockWorld(ctx);
                break;
            case BLOCK_DESTROY:
                logBlockDestroy(ctx);
                break;
            case ITEM_GUI:
                logItemGui(ctx);
                break;
            default:
                break;
        }
    }

    // ==================== 方块世界 ====================

    private static void logBlockWorld(RenderContext ctx) {
        if (ctx.block == null) return;
        String blockKey = blockKey(ctx.block);
        Map<String, String> props = ctx.blockstateProps;

        if (props == null || props.isEmpty()) {
            // 普通方块（无动态属性）：blockstateProps 应为 null，每种方块只打印一次
            if (SEEN_NULL.add("world|" + blockKey)) {
                LOGGER.info("[DebugProps] BLOCK_WORLD {} @ {},{},{} -> blockstateProps=null",
                        blockKey, ctx.x, ctx.y, ctx.z);
            }
            return;
        }

        // 动态方块：打印完整属性；内容变化（如红石 power 随位置变化）时重新打印
        String content = props.toString();
        String prev = LAST_PROPS.put("world|" + blockKey, content);
        if (prev == null || !prev.equals(content)) {
            LOGGER.info("[DebugProps] BLOCK_WORLD {} @ {},{},{} -> blockstateProps={}",
                    blockKey, ctx.x, ctx.y, ctx.z, content);
        }
    }

    // ==================== 破坏贴花 ====================

    private static void logBlockDestroy(RenderContext ctx) {
        if (ctx.block == null) return;
        String blockKey = blockKey(ctx.block);
        String pos = ctx.x + "," + ctx.y + "," + ctx.z;
        if (!SEEN_DESTROY.add("destroy|" + blockKey + "|" + pos)) return;

        Map<String, String> props = ctx.blockstateProps;
        LOGGER.info("[DebugProps] BLOCK_DESTROY {} @ {} -> blockstateProps={}",
                blockKey, pos, props == null ? "null" : props.toString());
    }

    // ==================== 物品 GUI ====================

    private static void logItemGui(RenderContext ctx) {
        if (ctx.stack == null || ctx.stack.getItem() == null) return;
        String itemKey = itemKey(ctx.stack);
        Map<String, Comparable<?>> props = ctx.itemProps;

        if (props == null) {
            // 第三方 IItemStateProvider 路径：itemProps 应为 null（优雅降级）
            if (SEEN_NULL.add("gui|" + itemKey)) {
                LOGGER.info("[DebugProps] ITEM_GUI {} -> itemProps=null", itemKey);
            }
            return;
        }

        // 惰性 Map：只按 key 读取（get 触发对应 provider 求值并缓存）
        String key = "gui|" + itemKey + "|damage=" + ctx.stack.getItemDamage();
        if (SEEN_ITEM.add(key)) {
            LOGGER.info("[DebugProps] ITEM_GUI {} damage={} -> damage={} max_damage={} using_item={} use_duration={} display_context={}",
                    itemKey, ctx.stack.getItemDamage(),
                    props.get("damage"), props.get("max_damage"),
                    props.get("using_item"), props.get("use_duration"),
                    props.get("display_context"));
        }
    }

    // ==================== 辅助 ====================

    private static String blockKey(Block block) {
        String name = Block.blockRegistry.getNameForObject(block);
        return name != null ? name : block.getUnlocalizedName();
    }

    private static String itemKey(ItemStack stack) {
        String name = Item.itemRegistry.getNameForObject(stack.getItem());
        return name != null ? name : stack.getUnlocalizedName();
    }
}
