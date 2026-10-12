package decok.dfcdvadstf.catframe.datapack.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.datapack.DataPackErrors;
import decok.dfcdvadstf.catframe.datapack.content.TintDeclaration;
import decok.dfcdvadstf.catframe.model.render.extension.tint.IBlockTintProvider;
import decok.dfcdvadstf.catframe.model.render.extension.tint.IItemTintProvider;
import decok.dfcdvadstf.catframe.model.render.extension.tint.TintRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.ColorizerFoliage;
import net.minecraft.world.ColorizerGrass;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.biome.BiomeGenBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side applier for synced tint declarations (worklist C3+C5).
 * <p>
 * Every packet replaces the whole datapack tint layer: previously installed
 * datapack providers are undone first (each one restoring whatever provider it
 * had shadowed, so mod-registered tints survive), then the new snapshot is
 * installed into {@link TintRegistry}. Declarations are grouped per target;
 * within one target a later declaration with the same tintindex wins, which
 * makes pack priority (low → high) resolve as "higher pack overrides".
 * </p>
 * <p>
 * Threading: packets arrive on the netty thread while the render thread reads
 * {@link TintRegistry}; both directions are safe — providers are immutable maps
 * and the registry itself is copy-on-write. This class' own state is only ever
 * touched from the netty thread (packet handling and channel close are
 * serialised on the same channel), guarded by {@code synchronized} anyway.
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class ClientTintSync implements ClientDataSync.Applier {

    private static final Logger LOGGER = LogManager.getLogger(ClientTintSync.class);

    /** Providers shadowed by the current datapack layer; {@code null} value = none existed. */
    private static final Map<Block, IBlockTintProvider> BLOCK_UNDO = new HashMap<>();
    private static final Map<Item, IItemTintProvider> ITEM_UNDO = new HashMap<>();

    @Override
    public synchronized void apply(String payloadJson) {
        List<TintDeclaration> declarations = parsePayload(payloadJson);
        restorePrevious();
        install(declarations);
        LOGGER.info("Applied {} synced tint declaration(s)", declarations.size());
    }

    @Override
    public synchronized void clear() {
        restorePrevious();
    }

    // ==================== Undo / install ====================

    private void restorePrevious() {
        for (Map.Entry<Block, IBlockTintProvider> entry : BLOCK_UNDO.entrySet()) {
            if (entry.getValue() == null) {
                TintRegistry.unregisterBlockTint(entry.getKey());
            } else {
                TintRegistry.registerBlockTint(entry.getKey(), entry.getValue());
            }
        }
        BLOCK_UNDO.clear();
        for (Map.Entry<Item, IItemTintProvider> entry : ITEM_UNDO.entrySet()) {
            if (entry.getValue() == null) {
                TintRegistry.unregisterItemTint(entry.getKey());
            } else {
                TintRegistry.registerItemTint(entry.getKey(), entry.getValue());
            }
        }
        ITEM_UNDO.clear();
    }

    private void install(List<TintDeclaration> declarations) {
        Map<Block, Map<Integer, TintDeclaration>> blockRules = new LinkedHashMap<>();
        Map<Item, Map<Integer, TintDeclaration>> itemRules = new LinkedHashMap<>();

        for (TintDeclaration declaration : declarations) {
            switch (declaration.getKind()) {
                case BLOCK: {
                    Block block = resolveBlock(declaration.getTargetId());
                    if (block == null) {
                        LOGGER.warn("Skipping tint declaration for unknown block '{}'", declaration.getTargetId());
                        break;
                    }
                    putRule(blockRules, block, declaration);
                    break;
                }
                case ITEM: {
                    Item item = resolveItem(declaration.getTargetId());
                    if (item == null) {
                        LOGGER.warn("Skipping tint declaration for unknown item '{}'", declaration.getTargetId());
                        break;
                    }
                    putRule(itemRules, item, declaration);
                    break;
                }
                default:
                    break;
            }
        }

        for (Map.Entry<Block, Map<Integer, TintDeclaration>> entry : blockRules.entrySet()) {
            Block block = entry.getKey();
            BLOCK_UNDO.put(block, TintRegistry.getBlockProvider(block));
            TintRegistry.registerBlockTint(block, new BlockTint(entry.getValue()));
        }
        for (Map.Entry<Item, Map<Integer, TintDeclaration>> entry : itemRules.entrySet()) {
            Item item = entry.getKey();
            ITEM_UNDO.put(item, TintRegistry.getItemProvider(item));
            TintRegistry.registerItemTint(item, new ItemTint(entry.getValue()));
        }
    }

    /** Groups by (target, tintindex); a later declaration for the same key wins. */
    private static <T> void putRule(Map<T, Map<Integer, TintDeclaration>> rules, T target, TintDeclaration declaration) {
        Map<Integer, TintDeclaration> byIndex = rules.get(target);
        if (byIndex == null) {
            byIndex = new LinkedHashMap<>();
            rules.put(target, byIndex);
        }
        byIndex.put(declaration.getTintIndex(), declaration);
    }

    private static Block resolveBlock(String id) {
        try {
            return Block.getBlockFromName(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Item resolveItem(String id) {
        try {
            Object resolved = Item.itemRegistry.getObject(id);
            return resolved instanceof Item ? (Item) resolved : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ==================== Payload parsing ====================

    private static List<TintDeclaration> parsePayload(String payloadJson) {
        List<TintDeclaration> declarations = new ArrayList<>();
        if (payloadJson == null || payloadJson.isEmpty()) {
            // Empty payload = empty snapshot: clear this layer (never a parse error).
            return declarations;
        }
        DataPackErrors errors = new DataPackErrors();
        try {
            JsonElement root = new JsonParser().parse(payloadJson);
            if (root == null || !root.isJsonArray()) {
                errors.error(null, "tints/sync", "payload root is not a JSON array");
            } else {
                for (JsonElement element : root.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        errors.error(null, "tints/sync", "entry is not a JSON object");
                        continue;
                    }
                    TintDeclaration declaration =
                            TintDeclaration.parse(element.getAsJsonObject(), null, "tints/sync", errors);
                    if (declaration != null) {
                        declarations.add(declaration);
                    }
                }
            }
        } catch (RuntimeException e) {
            errors.error(null, "tints/sync", "malformed sync payload: " + e.getMessage());
        }
        if (errors.size() > 0) {
            errors.logAll();
        }
        return declarations;
    }

    // ==================== Providers ====================

    /** Shared evaluator — the client-side half of the type dispatch table. */
    private static int evaluate(TintDeclaration declaration, boolean hasWorld, IBlockAccess world,
                                int x, int y, int z) {
        switch (declaration.getType()) {
            case CONSTANT:
                return declaration.getColor() & 0xFFFFFF;
            case BIOME: {
                boolean grass = "grass".equals(declaration.getVariant());
                if (hasWorld && world != null) {
                    BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
                    return (grass ? biome.getBiomeGrassColor(x, y, z)
                            : biome.getBiomeFoliageColor(x, y, z)) & 0xFFFFFF;
                }
                // No world context (item rendering / GUI): same fallbacks the
                // vanilla item forms use — BlockTallGrass(ColorizerGrass 0.5, 1.0)
                // and the leaves item (ColorizerFoliage basic).
                return (grass ? ColorizerGrass.getGrassColor(0.5D, 1.0D)
                        : ColorizerFoliage.getFoliageColorBasic()) & 0xFFFFFF;
            }
            default:
                return 0xFFFFFF;
        }
    }

    private static TintDeclaration ruleFor(Map<Integer, TintDeclaration> rules, int tintIndex) {
        TintDeclaration declaration = rules.get(tintIndex);
        if (declaration == null) {
            declaration = rules.get(-1);
        }
        return declaration;
    }

    /** Block target: one provider per block, rules keyed by tintindex ({@code -1} = default). */
    private static final class BlockTint implements IBlockTintProvider {

        private final Map<Integer, TintDeclaration> rules;

        BlockTint(Map<Integer, TintDeclaration> rules) {
            this.rules = rules;
        }

        @Override
        public int getTint(IBlockAccess world, int x, int y, int z, Block block, int tintIndex) {
            TintDeclaration declaration = ruleFor(rules, tintIndex);
            if (declaration == null) {
                // No datapack rule for this face — fall back exactly like the
                // no-provider path of TintRegistry.getBlockTint.
                return block != null && world != null ? block.colorMultiplier(world, x, y, z) & 0xFFFFFF : 0xFFFFFF;
            }
            return evaluate(declaration, true, world, x, y, z);
        }
    }

    /** Item target: one provider per item, rules keyed by tintindex ({@code -1} = default). */
    private static final class ItemTint implements IItemTintProvider {

        private final Map<Integer, TintDeclaration> rules;

        ItemTint(Map<Integer, TintDeclaration> rules) {
            this.rules = rules;
        }

        @Override
        public int getTint(ItemStack stack, int tintIndex) {
            TintDeclaration declaration = ruleFor(rules, tintIndex);
            if (declaration == null) {
                // Fall back exactly like the no-provider path of TintRegistry.getItemTint.
                if (stack != null && stack.getItem() instanceof ItemBlock) {
                    Block block = ((ItemBlock) stack.getItem()).field_150939_a;
                    if (block != null) {
                        return block.getRenderColor(stack.getItemDamage()) & 0xFFFFFF;
                    }
                }
                return 0xFFFFFF;
            }
            return evaluate(declaration, false, null, 0, 0, 0);
        }
    }
}
