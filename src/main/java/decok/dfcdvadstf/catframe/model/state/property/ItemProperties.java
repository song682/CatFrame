package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * 预定义的物品属性常量 + 运行时属性集构建工具。
 * <p>
 * 复用方块侧的 {@link Property} 体系定义物品属性的类型安全常量。
 * 运行时每帧从 ItemStack + RenderPhase 构建 {@code Map<String, Comparable<?>>} 属性集，
 * 供 {@link ItemStateNode} 决策树求值。
 *
 * <h3>方块 Property vs 物品 Property</h3>
 * <ul>
 *   <li>方块 Property：世界位置 → 固定属性值 → blockstate JSON variants/multipart</li>
 *   <li>物品 Property：ItemStack → 每帧实时计算 → items/ JSON 决策树</li>
 * </ul>
 *
 * <h3>预定义属性</h3>
 * <ul>
 *   <li>{@link #USING_ITEM} — 是否正在使用物品（拉弓、吃东西等）</li>
 *   <li>{@link #DAMAGE} — 物品 damage 值</li>
 *   <li>{@link #MAX_DAMAGE} — 物品最大 damage 值（耐久度）</li>
 *   <li>{@link #USE_DURATION} — 使用持续时间（tick）</li>
 *   <li>{@link #DISPLAY_CONTEXT} — 当前渲染阶段（用于 display context 模型切换）</li>
 * </ul>
 */
public class ItemProperties {

    // ==================== 预定义物品属性 ====================

    /** 是否正在使用物品（拉弓、吃东西等）。仅在手持渲染阶段有效。 */
    public static final BooleanProperty USING_ITEM = BooleanProperty.create("using_item");

    /** 物品 damage 值（metadata）。 */
    public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 32767);

    /** 物品最大 damage 值（耐久度）。 */
    public static final IntegerProperty MAX_DAMAGE = IntegerProperty.create("max_damage", 0, 32767);

    /** 使用持续时间（归一化进度 0-10000，其中 10000 = 完全使用）。用于 range_dispatch 决策树节点。 */
    public static final IntegerProperty USE_DURATION = IntegerProperty.create("use_duration", 0, 10000);

    /**
     * 当前渲染阶段。用于 display context 模型切换（如望远镜/Bluey 玩偶）。
     * <p>
     * 值为 {@link RenderPhase} 枚举，JSON 中用枚举名称匹配（如 "ITEM_HAND_FIRST_PERSON"）。
     */
    public static final EnumProperty<RenderPhase> DISPLAY_CONTEXT =
            EnumProperty.create("display_context", RenderPhase.class);

    // ==================== 运行时属性集构建 ====================

    /**
     * 从 ItemStack + RenderPhase 构建运行时属性集。
     * <p>
     * 返回惰性 Map（{@link LazyPropertyMap}），只在决策树实际访问属性时才调用 provider 计算值。
     * 大多数决策树每帧只访问 1-3 个属性，避免了无谓的 NBT 读取和玩家状态查询。
     *
     * @param stack 当前渲染的 ItemStack
     * @param phase 当前渲染阶段
     * @return 惰性属性集（property name → value）
     */
    public static Map<String, Comparable<?>> buildProperties(ItemStack stack, RenderPhase phase) {
        // 确保默认属性已注册
        ItemPropertyRegistry.registerDefaults();
        return new LazyPropertyMap(ItemPropertyRegistry.getAll(), stack, phase);
    }

    /**
     * 将属性值写入运行时属性集，同时提供无命名空间与 {@code minecraft:} 前缀两种 key。
     */
    private static <T extends Comparable<T>> void putProperty(Map<String, Comparable<?>> props,
                                                               Property<T> property, T value) {
        String name = property.getName();
        props.put(name, value);
        if (!name.contains(":")) {
            props.put("minecraft:" + name, value);
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 判断是否为手持渲染阶段（需要检测 using 状态）。
     */
    private static boolean isHandPhase(RenderPhase phase) {
        return phase == RenderPhase.ITEM_HAND_FIRST_PERSON
                || phase == RenderPhase.ITEM_HAND_THIRD_PERSON;
    }

    /**
     * 安全获取客户端玩家。
     */
    private static EntityPlayer getPlayerSafe() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? mc.thePlayer : null;
    }

    /**
     * 计算玩家当前物品的使用进度（归一化到 0-10000）。
     * <p>
     * 1.7.10 中 {@code EntityPlayer.itemInUseCount} 是剩余使用 tick（递减），
     * 实际进度 = (maxItemUseDuration - itemInUseCount) / maxItemUseDuration。
     * 乘以 10000 转换为整数范围，便于 range_dispatch 节点使用。
     */
    private static int computeUseDuration(EntityPlayer player) {
        ItemStack usingItem = player.getItemInUse();
        if (usingItem == null) return 0;
        int maxDuration = usingItem.getMaxItemUseDuration();
        if (maxDuration <= 0) return 0;
        int remaining = player.getItemInUseCount();
        int elapsed = Math.max(0, maxDuration - remaining);
        // 归一化到 0-10000
        return Math.min(10000, (int) ((long) elapsed * 10000L / maxDuration));
    }
}
