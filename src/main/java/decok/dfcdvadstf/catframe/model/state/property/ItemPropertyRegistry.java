package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 物品属性静态注册表。
 * <p>
 * 所有属性提供者通过 {@link #register(String, ItemPropertyProvider)} 注册，
 * 同时写入裸名和 {@code minecraft:} 前缀两个 key。
 * <p>
 * {@link #registerDefaults()} 在模组初始化时调用一次，注册 wiki 规范中定义的全部 33+ 属性。
 * 未实现的属性提供安全的占位值（false / 0 / ""），不会导致决策树崩溃。
 */
public class ItemPropertyRegistry {

    private static final Map<String, ItemPropertyProvider> REGISTRY = new HashMap<>();
    private static boolean defaultsRegistered = false;

    /**
     * 注册一个属性提供者。同时写入裸名和 {@code minecraft:} 前缀。
     *
     * @param name     属性裸名（不含命名空间）
     * @param provider 属性计算逻辑
     */
    public static void register(String name, ItemPropertyProvider provider) {
        REGISTRY.put(name, provider);
        if (!name.contains(":")) {
            REGISTRY.put("minecraft:" + name, provider);
        }
    }

    /**
     * 获取已注册的属性提供者。
     *
     * @param name 属性名（支持裸名或 minecraft: 前缀）
     * @return 提供者，未注册返回 null
     */
    public static ItemPropertyProvider get(String name) {
        return REGISTRY.get(name);
    }

    /**
     * @return 所有已注册的属性名（不可变视图）
     */
    public static Map<String, ItemPropertyProvider> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    /**
     * 注册 wiki 规范中定义的全部属性。
     * 幂等——多次调用安全。
     */
    public static synchronized void registerDefaults() {
        if (defaultsRegistered) return;
        defaultsRegistered = true;

        // ==================== 布尔属性 (13) ====================

        register("using_item", (stack, phase) -> {
            if (stack == null) return Boolean.FALSE;
            EntityPlayer player = getPlayerSafe();
            if (player != null && player.isUsingItem()) {
                ItemStack using = player.getItemInUse();
                return using != null && using == stack;
            }
            return Boolean.FALSE;
        });

        register("broken", (stack, phase) -> {
            if (stack == null) return Boolean.FALSE;
            int dmg = stack.getItemDamage();
            int max = stack.getMaxDamage();
            return max > 0 && dmg >= max;
        });

        register("damaged", (stack, phase) ->
                stack != null && stack.getItemDamage() > 0 ? Boolean.TRUE : Boolean.FALSE);

        register("selected", (stack, phase) -> {
            if (stack == null) return Boolean.FALSE;
            EntityPlayer player = getPlayerSafe();
            if (player == null) return Boolean.FALSE;
            ItemStack held = player.inventory.getCurrentItem();
            return held != null && held == stack;
        });

        register("carried", (stack, phase) -> {
            if (stack == null) return Boolean.FALSE;
            EntityPlayer player = getPlayerSafe();
            if (player == null) return Boolean.FALSE;
            ItemStack carried = player.inventory.getItemStack();
            return carried != null && carried == stack;
        });

        // bundle/has_selected_item — 1.7.10 无 bundle，占位返回 false
        register("bundle/has_selected_item", (stack, phase) -> Boolean.FALSE);

        // custom_model_data 布尔形式：检查 NBT flags[index]（默认 index=0）
        register("custom_model_data_bool", (stack, phase) -> {
            if (stack == null) return Boolean.FALSE;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || !tag.hasKey("CustomModelData", 10)) return Boolean.FALSE;
            NBTTagCompound cmd = tag.getCompoundTag("CustomModelData");
            if (!cmd.hasKey("flags", 11)) return Boolean.FALSE;
            int[] flags = cmd.getIntArray("flags");
            return flags.length > 0 && flags[0] != 0;
        });

        // component — 占位，返回 false
        register("component", (stack, phase) -> Boolean.FALSE);

        // has_component — 占位，返回 false
        register("has_component", (stack, phase) -> Boolean.FALSE);

        register("extended_view", (stack, phase) -> {
            // Shift + GUI 渲染
            EntityPlayer player = getPlayerSafe();
            if (player == null) return Boolean.FALSE;
            return player.isSneaking() && phase == RenderPhase.ITEM_GUI;
        });

        register("fishing_rod/cast", (stack, phase) -> {
            if (stack == null) return Boolean.FALSE;
            EntityPlayer player = getPlayerSafe();
            if (player == null) return Boolean.FALSE;
            // 1.7.10: 检查玩家是否有鱼钩实体
            return player.fishEntity != null;
        });

        // keybind_down — 需要指定键位，占位返回 false
        register("keybind_down", (stack, phase) -> Boolean.FALSE);

        register("view_entity", (stack, phase) -> {
            if (stack == null) return Boolean.FALSE;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.renderViewEntity == null) return Boolean.FALSE;
            EntityPlayer player = getPlayerSafe();
            return player != null && mc.renderViewEntity == player;
        });

        // ==================== 数值属性 (10) ====================

        register("damage", (stack, phase) ->
                stack != null ? stack.getItemDamage() : 0);

        register("count", (stack, phase) ->
                stack != null ? stack.stackSize : 0);

        register("use_duration", (stack, phase) -> {
            if (stack == null) return 0;
            EntityPlayer player = getPlayerSafe();
            if (player == null || !player.isUsingItem()) return 0;
            ItemStack using = player.getItemInUse();
            if (using == null || using != stack) return 0;
            return computeUseDuration(player);
        });

        register("use_cycle", (stack, phase) -> {
            if (stack == null) return 0;
            EntityPlayer player = getPlayerSafe();
            if (player == null || !player.isUsingItem()) return 0;
            ItemStack using = player.getItemInUse();
            if (using == null || using != stack) return 0;
            int remaining = player.getItemInUseCount();
            int period = 20; // 默认周期 20 tick
            return remaining % period;
        });

        register("cooldown", (stack, phase) -> {
            // 1.7.10 没有原生冷却系统，占位返回 0
            return 0;
        });

        register("time", (stack, phase) -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.theWorld == null) return 0;
            return (int) (mc.theWorld.getWorldTime() % 24000L);
        });

        register("compass", (stack, phase) -> {
            // 罗盘朝向角度 0-1，简化实现
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return 0;
            float yaw = mc.thePlayer.rotationYaw % 360.0f;
            if (yaw < 0) yaw += 360.0f;
            return (int) (yaw / 360.0f * 10000);
        });

        // bundle/fullness — 占位，返回 0
        register("bundle/fullness", (stack, phase) -> 0);

        // crossbow/pull — 占位，返回 0
        register("crossbow/pull", (stack, phase) -> 0);

        // custom_model_data 数值形式：读取 NBT floats[index]
        register("custom_model_data_float", (stack, phase) -> {
            if (stack == null) return 0.0f;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || !tag.hasKey("CustomModelData", 10)) return 0.0f;
            NBTTagCompound cmd = tag.getCompoundTag("CustomModelData");
            if (!cmd.hasKey("floats", 9)) return 0.0f;
            // floats 存储为 NBTTagList of TAG_Float
            NBTTagList list = cmd.getTagList("floats", 5);
            return list.tagCount() > 0 ? list.func_150308_e(0) : 0.0f;
        });

        // ==================== 枚举属性 (10) ====================

        register("display_context", (stack, phase) ->
                phase != null ? phase.name() : "UNKNOWN");

        // custom_model_data 字符串形式：读取 NBT strings[index]
        register("custom_model_data_string", (stack, phase) -> {
            if (stack == null) return "";
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || !tag.hasKey("CustomModelData", 10)) return "";
            NBTTagCompound cmd = tag.getCompoundTag("CustomModelData");
            if (!cmd.hasKey("strings", 9)) return "";
            NBTTagList list = cmd.getTagList("strings", 8);
            return list.tagCount() > 0 ? list.getStringTagAt(0) : "";
        });

        register("main_hand", (stack, phase) -> {
            // 1.7.10 默认右手
            return "right";
        });

        register("context_dimension", (stack, phase) -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.theWorld == null) return 0;
            return mc.theWorld.provider.dimensionId;
        });

        register("context_entity_type", (stack, phase) -> {
            EntityPlayer player = getPlayerSafe();
            if (player == null) return "unknown";
            return player.getCommandSenderName();
        });

        // block_state — 占位，返回空字符串
        register("block_state", (stack, phase) -> "");

        // charge_type — 弩装载物类型，1.7.10 无弩，占位
        register("charge_type", (stack, phase) -> "none");

        // trim_material — 盔甲纹饰，占位
        register("trim_material", (stack, phase) -> "");

        register("local_time", (stack, phase) -> {
            long time = System.currentTimeMillis() % 86400000L;
            int hours = (int) (time / 3600000L);
            int minutes = (int) ((time % 3600000L) / 60000L);
            return String.format("%02d:%02d", hours, minutes);
        });

        // max_damage — 补充数值属性
        register("max_damage", (stack, phase) ->
                stack != null ? stack.getMaxDamage() : 0);
    }

    // ==================== 内部工具 ====================

    private static EntityPlayer getPlayerSafe() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? mc.thePlayer : null;
    }

    /**
     * 计算玩家当前物品的使用进度（归一化到 0-10000）。
     */
    private static int computeUseDuration(EntityPlayer player) {
        ItemStack usingItem = player.getItemInUse();
        if (usingItem == null) return 0;
        int maxDuration = usingItem.getMaxItemUseDuration();
        if (maxDuration <= 0) return 0;
        int remaining = player.getItemInUseCount();
        int elapsed = Math.max(0, maxDuration - remaining);
        return Math.min(10000, (int) ((long) elapsed * 10000L / maxDuration));
    }
}
