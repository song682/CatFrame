package decok.dfcdvadstf.catframe.model.render.pipeline;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * State holder coordinating {@code force_translucent} world-block routing with the
 * vanilla 1.7.10 Forge dual-pass chunk tessellation (pass 0 = solid, pass 1 = alpha,
 * the alpha pass drawn by {@code EntityRenderer} with GL_BLEND enabled).
 * <p>
 * A block whose baked CatFrame part contains at least one force-translucent quad is
 * "marked"; {@code MixinBlock} then hijacks the <b>base</b> {@code Block.getRenderBlockPass()}
 * to 1 so the WorldRenderer pass loop also compiles an alpha-pass mesh for it. Blocks that
 * override {@code getRenderBlockPass}/{@code canRenderInPass} themselves (native translucent
 * blocks such as glass) dispatch to their own class implementation before the base mixin
 * body ever runs and therefore keep their original semantics untouched.
 * <p>
 * Sequence pairing: {@code WorldRenderer.updateRenderer} queries, per block position,
 * {@code getRenderBlockPass()} → {@code canRenderInPass(pass)} → {@code renderBlockByRenderType()}
 * with no interleaving on the same thread, so a marker recording "the base
 * {@code getRenderBlockPass} mixin just fired for this block instance" uniquely identifies
 * a CatFrame-hijacked tessellation position for the two later steps to read.
 */
@SideOnly(Side.CLIENT)
public final class WorldTranslucentPasses {

    private WorldTranslucentPasses() {
    }

    /** Blocks whose BLOCK_WORLD part has contained force-translucent quads at least once. */
    private static final Set<Block> forcedBlocks =
            Collections.newSetFromMap(new ConcurrentHashMap<Block, Boolean>());

    /**
     * Sequence-pair marker: set only from the base {@code Block.getRenderBlockPass} mixin body
     * (reachable exactly when the block class itself declares no override). Volatile so a
     * deferred rebuild on another thread cannot observe a torn reference.
     */
    private static volatile Block lastBasePassHijack;

    /** Tessellation pass just reported through {@code canRenderInPass} for the paired block. */
    private static volatile int lastTessellationPass = -1;

    /**
     * Per-thread chunk-tessellation scope, maintained by {@code MixinWorldRenderer} around
     * {@code WorldRenderer.updateRenderer}. The pass split must never touch submissions made
     * outside a chunk build (selection outline, digging overlays, background compilers).
     */
    private static final ThreadLocal<Boolean> TESSELLATING = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    /** Coalesces first-time force discoveries of one tick into a single deferred renderer rebuild. */
    private static volatile boolean rebuildPending;
    private static volatile boolean tickerRegistered;

    // ==================== Marking (submission side) ====================

    /**
     * Records that {@code block} renders force-translucent elements. On first discovery the
     * visible chunk renderers are rebuilt once at the next client tick end, because the pass
     * loop of the currently building section may already have passed the alpha-pass gate for
     * earlier positions of this block type.
     */
    public static void mark(Block block) {
        if (block != null && forcedBlocks.add(block)) {
            rebuildPending = true;
            scheduleRebuildIfNeeded();
        }
    }

    /** Whether this block's baked model contains force-translucent quads (hijack gate). */
    public static boolean isForced(Block block) {
        return block != null && forcedBlocks.contains(block);
    }

    // ==================== Sequence pairing (tessellation side) ====================

    /** Called by the base getRenderBlockPass mixin right before it returns the hijacked 1. */
    public static void noteBasePassHijack(Block block) {
        lastBasePassHijack = block;
    }

    /** Called by the canRenderInPass mixin when the paired hijack marker matches this block. */
    public static void noteTessellationPass(int pass) {
        lastTessellationPass = pass;
    }

    // ==================== Pass split query (submission filter) ====================

    /**
     * Whether the current BLOCK_WORLD submission for {@code block} must be split by tessellation
     * pass: inside a chunk build on this thread <b>and</b> the base getRenderBlockPass of exactly
     * this block instance was just hijacked (sequence pairing). Native translucent blocks and
     * all non-tessellation draw contexts return false (whole-part submission, unchanged behavior).
     */
    public static boolean shouldSplitByPass(Block block) {
        return Boolean.TRUE.equals(TESSELLATING.get()) && lastBasePassHijack == block;
    }

    /** The pass value reported by canRenderInPass for the paired position: 0 solid, 1 alpha, -1 unknown. */
    public static int lastTessellationPass() {
        return lastTessellationPass;
    }

    // ==================== Tessellation scope (WorldRenderer hook) ====================

    public static void beginTessellating() {
        TESSELLATING.set(Boolean.TRUE);
    }

    public static void endTessellating() {
        TESSELLATING.set(Boolean.FALSE);
        // Leave the pairing stale-safe: a position that never re-pairs must not reuse an old pass.
        lastTessellationPass = -1;
    }

    // ==================== Reload lifecycle ====================

    /**
     * Drops all marks; called from the model re-registration entry
     * ({@code VanillaModelManager.Baking.registerAllModels}) so a hijack can never outlive the
     * model that produced it (pack toggle / resource reload).
     */
    public static void clear() {
        forcedBlocks.clear();
        lastBasePassHijack = null;
        lastTessellationPass = -1;
        rebuildPending = false;
    }

    /** One-shot client-tick handler: rebuild visible renderers after a first-time discovery. */
    private static void scheduleRebuildIfNeeded() {
        if (tickerRegistered) return;
        tickerRegistered = true;
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onClientTick(TickEvent.ClientTickEvent event) {
                if (event.phase != TickEvent.Phase.END || !rebuildPending) return;
                rebuildPending = false;
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.renderGlobal != null && mc.theWorld != null) {
                    mc.renderGlobal.loadRenderers();
                }
            }
        });
    }
}
