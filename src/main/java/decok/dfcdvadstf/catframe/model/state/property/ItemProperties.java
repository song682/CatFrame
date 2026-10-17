package decok.dfcdvadstf.catframe.model.state.property;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.state.item.ItemStateNode;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.Map;

/**
 * Predefined item property constants + runtime property set construction utilities.
 * <p>
 * Reuses the block-side {@link Property} system to define type-safe constants for item properties.
 * Every frame at runtime, a {@code Map<String, Comparable<?>>} property set is built from the
 * ItemStack + RenderPhase, for evaluation by the {@link ItemStateNode} decision tree.
 *
 * <h3>Block Property vs Item Property</h3>
 * <ul>
 *   <li>Block Property: world position → fixed property value → blockstate JSON variants/multipart</li>
 *   <li>Item Property: ItemStack → computed live every frame → items/ JSON decision tree</li>
 * </ul>
 *
 * <h3>Predefined properties</h3>
 * <ul>
 *   <li>{@link #USING_ITEM} — whether the item is being used (drawing a bow, eating, etc.)</li>
 *   <li>{@link #DAMAGE} — item damage value</li>
 *   <li>{@link #MAX_DAMAGE} — item max damage value (durability)</li>
 *   <li>{@link #USE_DURATION} — use duration (ticks)</li>
 *   <li>{@link #DISPLAY_CONTEXT} — current render phase (for display context model switching)</li>
 * </ul>
 */
public class ItemProperties {

    // ==================== Predefined item properties ====================

    /** Whether the item is being used (drawing a bow, eating, etc.). Only valid during hand render phases. */
    public static final BooleanProperty USING_ITEM = BooleanProperty.create("using_item");

    /** Item damage value (metadata). */
    public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 32767);

    /** Item max damage value (durability). */
    public static final IntegerProperty MAX_DAMAGE = IntegerProperty.create("max_damage", 0, 32767);

    /** Elapsed use ticks (consistent with vanilla 1.21.4+ use_duration semantics). Used by the range_dispatch decision tree node. */
    public static final IntegerProperty USE_DURATION = IntegerProperty.create("use_duration", 0, 72000);

    /**
     * Current render phase. Used for display context model switching (such as the spyglass/Bluey doll).
     * <p>
     * The value is a {@link RenderPhase} enum; JSON matches by enum name (e.g. "ITEM_HAND_FIRST_PERSON").
     */
    public static final EnumProperty<RenderPhase> DISPLAY_CONTEXT =
            EnumProperty.create("display_context", RenderPhase.class);

    // ==================== Runtime property set construction ====================

    /**
     * Builds the runtime property set from an ItemStack + RenderPhase.
     * <p>
     * Returns a lazy Map ({@link LazyPropertyMap}) that only calls the provider to compute a value
     * when the decision tree actually accesses the property. Most decision trees access only 1-3
     * properties per frame, avoiding unnecessary NBT reads and player state queries.
     *
     * @param stack the ItemStack currently being rendered
     * @param phase the current render phase
     * @return the lazy property set (property name → value)
     */
    public static Map<String, Comparable<?>> buildProperties(ItemStack stack, RenderPhase phase) {
        // Ensure the default properties are registered
        ItemPropertyRegistry.registerDefaults();
        return new LazyPropertyMap(ItemPropertyRegistry.getAll(), stack, phase);
    }

    /**
     * Writes a property value into the runtime property set, providing both the namespaceless and
     * the {@code minecraft:}-prefixed key.
     */
    private static <T extends Comparable<T>> void putProperty(Map<String, Comparable<?>> props,
                                                               Property<T> property, T value) {
        String name = property.getName();
        props.put(name, value);
        if (!name.contains(":")) {
            props.put("minecraft:" + name, value);
        }
    }

    // ==================== Internal utilities ====================

    /**
     * Determines whether this is a hand render phase (requires checking the using state).
     */
    private static boolean isHandPhase(RenderPhase phase) {
        return phase == RenderPhase.ITEM_HAND_FIRST_PERSON
                || phase == RenderPhase.ITEM_HAND_THIRD_PERSON;
    }

    /**
     * Safely gets the client player.
     */
    private static EntityPlayer getPlayerSafe() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? mc.thePlayer : null;
    }

    /**
     * Computes the raw elapsed use ticks of the player's item-in-use.
     * <p>
     * In 1.7.10 {@code EntityPlayer.itemInUseCount} is the remaining use ticks (counting down),
     * so elapsed ticks = maxItemUseDuration - itemInUseCount;
     * normalization is performed by the JSON-side {@code scale}.
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
