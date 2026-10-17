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
 * Item property static registry — the single entry point for property registration.
 * <p>
 * Internal properties are registered via {@link #register(String, ItemPropertyProvider)}
 * (bare name + {@code minecraft:} dual key); third-party mods register via
 * {@link #register(String, String, ItemPropertyProvider)} (enforced {@code modid:name} namespace).
 * <p>
 * {@link #registerDefaults()} is called once during mod initialization and registers all 33+
 * properties defined in the wiki spec. Unimplemented properties provide safe placeholder values
 * (false / 0 / ""), so the decision tree never crashes.
 */
public class ItemPropertyRegistry {

    private static final Map<String, ItemPropertyProvider> REGISTRY = new HashMap<>();
    private static boolean defaultsRegistered = false;

    /**
     * Registers a property provider. Writes both the bare name and the {@code minecraft:} prefix.
     *
     * @param name     the bare property name (without namespace)
     * @param provider the property computation logic
     */
    public static void register(String name, ItemPropertyProvider provider) {
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
     * Gets the registered property provider.
     *
     * @param name the property name (supports bare name or minecraft: prefix)
     * @return the provider, or null if not registered
     */
    public static ItemPropertyProvider get(String name) {
        return REGISTRY.get(name);
    }

    /**
     * @return all registered property names (unmodifiable view)
     */
    public static Map<String, ItemPropertyProvider> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    /**
     * Registers all properties defined in the wiki spec.
     * Idempotent — safe to call multiple times.
     */
    public static synchronized void registerDefaults() {
        if (defaultsRegistered) return;
        defaultsRegistered = true;

        // ==================== Boolean properties (13) ====================

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

        // custom_model_data boolean form: checks NBT flags[index] (default index=0)
        register("custom_model_data_bool", (stack, phase) -> {
            if (stack == null) return Boolean.FALSE;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || !tag.hasKey("CustomModelData", 10)) return Boolean.FALSE;
            NBTTagCompound cmd = tag.getCompoundTag("CustomModelData");
            if (!cmd.hasKey("flags", 11)) return Boolean.FALSE;
            int[] flags = cmd.getIntArray("flags");
            return flags.length > 0 && flags[0] != 0;
        });

        // component — placeholder, returns false
        register("component", (stack, phase) -> Boolean.FALSE);

        // has_component — placeholder, returns false
        register("has_component", (stack, phase) -> Boolean.FALSE);

        register("extended_view", (stack, phase) -> {
            // Shift + GUI rendering
            EntityPlayer player = getPlayerSafe();
            if (player == null) return Boolean.FALSE;
            return player.isSneaking() && phase == RenderPhase.ITEM_GUI;
        });

        register("fishing_rod/cast", (stack, phase) -> {
            if (stack == null) return Boolean.FALSE;
            EntityPlayer player = getPlayerSafe();
            if (player == null) return Boolean.FALSE;
            // 1.7.10: check the player's hook entity; also guard isDead because some
            // hook self-removal paths (out of range / item switch) kill the entity
            // without clearing the field, leaving a stale "ghost cast" state
            return player.fishEntity != null && !player.fishEntity.isDead;
        });

        // keybind_down — a key binding must be specified; placeholder returns false
        register("keybind_down", (stack, phase) -> Boolean.FALSE);

        register("view_entity", (stack, phase) -> {
            if (stack == null) return Boolean.FALSE;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.renderViewEntity == null) return Boolean.FALSE;
            EntityPlayer player = getPlayerSafe();
            return player != null && mc.renderViewEntity == player;
        });

        // CatFrame extension: 1.7.10 splash potions are the 16384 damage bit on the
        // same ItemPotion (ItemPotion.isSplash) rather than a separate item like in
        // modern versions; exposed as a boolean so the items/ decision tree can
        // branch between the drinkable and splash bottle models.
        register("catframe:potion_splash", (stack, phase) ->
                stack != null && stack.getItem() instanceof ItemPotion
                        && ItemPotion.isSplash(stack.getItemDamage())
                        ? Boolean.TRUE : Boolean.FALSE);

        // ==================== Numeric properties (10) ====================

        register("damage", (stack, phase) ->
                stack != null ? stack.getItemDamage() : 0);

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
            int period = 20; // default period: 20 ticks
            return remaining % period;
        });

        register("cooldown", (stack, phase) -> {
            // 1.7.10 has no native cooldown system; placeholder returns 0
            return 0;
        });

        register("time", (stack, phase) -> {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.theWorld == null) return 0;
            return (int) (mc.theWorld.getWorldTime() % 24000L);
        });

        register("compass", (stack, phase) -> {
            // Compass heading angle 0-1, simplified implementation
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.thePlayer == null) return 0;
            float yaw = mc.thePlayer.rotationYaw % 360.0f;
            if (yaw < 0) yaw += 360.0f;
            return (int) (yaw / 360.0f * 10000);
        });

        // crossbow/pull — placeholder, returns 0
        register("crossbow/pull", (stack, phase) -> 0);

        // custom_model_data numeric form: reads NBT floats[index]
        register("custom_model_data_float", (stack, phase) -> {
            if (stack == null) return 0.0f;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null || !tag.hasKey("CustomModelData", 10)) return 0.0f;
            NBTTagCompound cmd = tag.getCompoundTag("CustomModelData");
            if (!cmd.hasKey("floats", 9)) return 0.0f;
            // floats are stored as an NBTTagList of TAG_Float
            NBTTagList list = cmd.getTagList("floats", 5);
            return list.tagCount() > 0 ? list.func_150308_e(0) : 0.0f;
        });

        // ==================== Enum properties (10) ====================

        register("display_context", (stack, phase) ->
                phase != null ? phase.name() : "UNKNOWN");

        // custom_model_data string form: reads NBT strings[index]
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
            // 1.7.10 defaults to right hand
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

        // block_state — placeholder, returns an empty string
        register("block_state", (stack, phase) -> "");

        // charge_type — crossbow projectile type; 1.7.10 has no crossbow, placeholder
        register("charge_type", (stack, phase) -> "none");

        // trim_material — armor trim material, placeholder
        register("trim_material", (stack, phase) -> "");

        register("local_time", (stack, phase) -> {
            long time = System.currentTimeMillis() % 86400000L;
            int hours = (int) (time / 3600000L);
            int minutes = (int) ((time % 3600000L) / 60000L);
            return String.format("%02d:%02d", hours, minutes);
        });

        // max_damage — supplementary numeric property
        register("max_damage", (stack, phase) ->
                stack != null ? stack.getMaxDamage() : 0);
    }

    // ==================== Third-party registration API (formerly the CatItemProperties facade) ====================

    /**
     * Registers a namespaced third-party property under the key {@code modid:name}.
     *
     * @param modid    mod id (non-empty, no {@code :})
     * @param name     property name (non-empty, no {@code :})
     * @param provider the property computation logic
     * @throws IllegalArgumentException on empty args or embedded {@code :}
     */
    public static void register(String modid, String name, ItemPropertyProvider provider) {
        validatePart(modid, "modid");
        validatePart(name, "name");
        if (provider == null) {
            throw new IllegalArgumentException("ItemPropertyRegistry: provider must not be null");
        }
        // Materialize defaults first so external entries always land after them
        registerDefaults();
        register(modid + ":" + name, provider);
    }

    /**
     * Overrides an existing property (including defaults such as {@code damage}).
     * <p>
     * Unlike {@code register}, this takes the full property name (bare or namespaced)
     * and requires the target to already exist — a typo won't silently create a new
     * property. Bare-name overrides refresh the {@code minecraft:} alias as well.
     *
     * @param propertyName full name of the target property
     * @param provider     the replacement computation logic
     * @throws IllegalArgumentException if the target is unknown or args are invalid
     */
    public static void override(String propertyName, ItemPropertyProvider provider) {
        if (propertyName == null || propertyName.isEmpty()) {
            throw new IllegalArgumentException("ItemPropertyRegistry: propertyName must not be empty");
        }
        if (provider == null) {
            throw new IllegalArgumentException("ItemPropertyRegistry: provider must not be null");
        }
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
     * Validates a namespace fragment: non-empty and without {@code :}.
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

    // ==================== Internal utilities ====================

    private static EntityPlayer getPlayerSafe() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? mc.thePlayer : null;
    }

    /**
     * Computes the raw elapsed use ticks of the player's item-in-use, matching the vanilla
     * 1.21.4+ {@code use_duration} semantics; normalization is left to the JSON-side
     * {@code scale} (bow: 0.05, full draw = 20 ticks).
     * <p>
     * In 1.7.10 {@code itemInUseCount} counts down from maxItemUseDuration,
     * so elapsed = maxDuration - remaining.
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
