package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 物品属性静态注册表 — 属性注册的唯一入口。
 * <p>
 * 内部属性通过 {@link #register(String, ItemPropertyProvider)} 注册（裸名 + {@code minecraft:} 双 key）；
 * 第三方模组通过 {@link #register(String, String, ItemPropertyProvider)} 注册（强制 {@code modid:name} 命名空间）。
 * <p>
 * {@link #registerDefaults()} 在模组初始化时调用一次，注册 wiki 规范中定义的全部 33+ 属性。
 * 未实现的属性提供安全的占位值（false / 0 / ""），不会导致决策树崩溃。
 * <p>
 * Item property static registry — the single entry point for property registration.
 * <br>Internal properties use {@link #register(String, ItemPropertyProvider)} (bare name +
 * {@code minecraft:} dual key); third-party mods use
 * {@link #register(String, String, ItemPropertyProvider)} (enforced {@code modid:name} namespace).
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
        // 冲突检测：盲写覆盖会让"谁后加载谁赢"难以排查，替换已有 provider 时打条警告
        // Conflict detection: blind puts make "last loader wins" hard to debug,
        // so warn whenever an existing provider gets replaced
        ItemPropertyProvider previous = REGISTRY.put(name, provider);
        if (previous != null && previous != provider) {
            CatFrame.logger.warn("ItemPropertyRegistry: property '{}' provider replaced", name);
        }
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
            // 1.7.10: 检查玩家是否有鱼钩实体；同时校验 isDead，
            // 防止鱼钩自灭路径（超距/切换手持）未清空字段导致的"幽灵抛竿"状态
            // 1.7.10: check the player's hook entity; also guard isDead because some
            // hook self-removal paths (out of range / item switch) kill the entity
            // without clearing the field, leaving a stale "ghost cast" state
            return player.fishEntity != null && !player.fishEntity.isDead;
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

        // catframe:potion_splash — CatFrame 扩展属性：1.7.10 的喷溅药水不是独立物品，
        // 而是同一 ItemPotion 上 damage 值的 16384 位（ItemPotion.isSplash）。
        // 高版本用独立的 splash_potion 物品区分，这里以布尔属性形式暴露给
        // items/ 决策树做喝的瓶 / 喷溅瓶模型分支。
        // CatFrame extension: 1.7.10 splash potions are the 16384 damage bit on the
        // same ItemPotion (ItemPotion.isSplash) rather than a separate item like in
        // modern versions; exposed as a boolean so the items/ decision tree can
        // branch between the drinkable and splash bottle models.
        register("catframe:potion_splash", (stack, phase) ->
                stack != null && stack.getItem() instanceof ItemPotion
                        && ItemPotion.isSplash(stack.getItemDamage())
                        ? Boolean.TRUE : Boolean.FALSE);

        // ==================== 数值属性 (10) ====================

        register("damage", (stack, phase) ->
                stack != null ? stack.getItemDamage() : 0);

        // catframe:meta — CatFrame 扩展属性：1.7.10 的 metadata（子类型 ID）。
        // 与 minecraft:damage 数值相同，但语义上专用于 meta 子类型精确匹配
        // （wool/log/dye 等未扁平化物品），让 minecraft:damage 回归 wiki
        // 正统的耐久语义（range_dispatch）。
        // CatFrame extension: the 1.7.10 metadata (subtype id). Same value as
        // minecraft:damage, but semantically dedicated to exact meta subtype
        // matching (unflattened items like wool/log/dye) so minecraft:damage
        // keeps its wiki durability semantics (range_dispatch).
        register("catframe:meta", (stack, phase) ->
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

    // ==================== 第三方注册 API（原 CatItemProperties facade） ====================

    /**
     * 注册一个带命名空间的第三方属性，最终 key 为 {@code modid:name}。
     * <p>
     * Registers a namespaced third-party property under the key {@code modid:name}.
     *
     * @param modid    模组 id（非空，不含 {@code :}） / mod id (non-empty, no {@code :})
     * @param name     属性名（非空，不含 {@code :}） / property name (non-empty, no {@code :})
     * @param provider 属性计算逻辑 / the property computation logic
     * @throws IllegalArgumentException 参数为空或含 {@code :} 时 / on empty args or embedded {@code :}
     */
    public static void register(String modid, String name, ItemPropertyProvider provider) {
        validatePart(modid, "modid");
        validatePart(name, "name");
        if (provider == null) {
            throw new IllegalArgumentException("ItemPropertyRegistry: provider must not be null");
        }
        // 先物化默认表，确保外部注册永远排在默认注册之后
        // Materialize defaults first so external entries always land after them
        registerDefaults();
        register(modid + ":" + name, provider);
    }

    /**
     * 覆写一个已存在的属性（含默认属性，如 {@code damage}）。
     * <p>
     * 与 {@link #register(String, String, ItemPropertyProvider)} 不同，此方法接受完整属性名
     * （裸名或带命名空间均可），且要求目标属性已注册——避免拼写错误静默创建新属性。
     * 裸名覆写会同步刷新 {@code minecraft:} 别名（由 {@link #register(String, ItemPropertyProvider)} 保证）。
     * <p>
     * Overrides an existing property (including defaults such as {@code damage}).
     * Unlike {@code register}, this takes the full property name (bare or namespaced)
     * and requires the target to already exist — a typo won't silently create a new
     * property. Bare-name overrides refresh the {@code minecraft:} alias as well.
     *
     * @param propertyName 目标属性全名 / full name of the target property
     * @param provider     新的属性计算逻辑 / the replacement computation logic
     * @throws IllegalArgumentException 目标属性未注册或参数非法时 / if the target is unknown or args are invalid
     */
    public static void override(String propertyName, ItemPropertyProvider provider) {
        if (propertyName == null || propertyName.isEmpty()) {
            throw new IllegalArgumentException("ItemPropertyRegistry: propertyName must not be empty");
        }
        if (provider == null) {
            throw new IllegalArgumentException("ItemPropertyRegistry: provider must not be null");
        }
        // 先物化默认表，否则默认属性尚未注册、且覆写会被后到的默认注册冲掉
        // Materialize defaults first: the target may not exist yet, and a later
        // lazy default registration would clobber the override
        registerDefaults();
        if (REGISTRY.get(propertyName) == null) {
            throw new IllegalArgumentException(
                    "ItemPropertyRegistry: cannot override unknown property '" + propertyName + "'");
        }
        CatFrame.logger.info("ItemPropertyRegistry: property '{}' overridden by external provider", propertyName);
        register(propertyName, provider);
    }

    /**
     * 校验命名空间片段：非空且不含 {@code :}。
     * <p>Validates a namespace fragment: non-empty and without {@code :}.
     */
    private static void validatePart(String value, String label) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("ItemPropertyRegistry: " + label + " must not be empty");
        }
        if (value.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "ItemPropertyRegistry: " + label + " must not contain ':' (got '" + value + "')");
        }
    }

    // ==================== 内部工具 ====================

    private static EntityPlayer getPlayerSafe() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? mc.thePlayer : null;
    }

    /**
     * 计算玩家当前物品的已使用 tick 数（原始 elapsed ticks）。
     * <p>
     * Computes the raw elapsed use ticks of the player's item-in-use, matching
     * the modern (1.21.4+) {@code use_duration} property semantics; normalization
     * is left to the JSON-side {@code scale} (bow: 0.05, full draw = 20 ticks).
     * <p>
     * 与 1.21.4+ 原版 {@code use_duration} 语义一致，归一化由 JSON 侧
     * {@code scale} 完成（弓：scale 0.05，满弓 20 tick → 1.0）。
     * 1.7.10 中 {@code itemInUseCount} 从 maxItemUseDuration 递减，
     * 故 elapsed = maxDuration - remaining。
     */
    private static int computeUseDuration(EntityPlayer player) {
        ItemStack usingItem = player.getItemInUse();
        if (usingItem == null) return 0;
        int maxDuration = usingItem.getMaxItemUseDuration();
        if (maxDuration <= 0) return 0;
        int remaining = player.getItemInUseCount();
        return Math.max(0, maxDuration - remaining);
    }
}
