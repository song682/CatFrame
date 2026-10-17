package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;

/**
 * Render-phase to default-brightness computation utility (pipeline-internal, used
 * together with the extension chain).
 * <p>
 * Absorbs the per-{@link RenderPhase} brightness policy that was previously inlined
 * in {@link QuadWriter}: GUI is always 255, attached-item phases (hand / head slot)
 * use the attached entity's position light, dropped items use the entity's position
 * light, and blocks use the world's mixed brightness — semantics replicated bit for
 * bit (a stateless pure function).
 * <b>Consumers (form B')</b>: the light policy lives uniformly in the extension
 * chain — the built-in {@code LightPolicyExtension} (extension package) computes the
 * item submission-level brightness in beforePart and computes block brightness on
 * demand for AO-degraded quads in apply, both via this utility, and writes
 * {@code ctx.brightnessOverride}; QuadWriter only falls back to calling this utility
 * when {@link RenderSubmit#baselineBrightness} = -1 and the extension chain has not
 * written an override (chain-missing safety net). The GL-lighting mode judgment
 * ({@link #isItemGlLit}) is called directly on this class by FeatureRenderDispatcher
 * / QuadWriter, sharing the same source as the brightness policy.
 * <p>
 * Timing premise (the basis for write-time sampling semantics): item render paths
 * always submit within RenderDispatcher's beginScope/endScope, endScope triggers a
 * flush on the same call stack, and this occurs within the context window of
 * RenderJsonItemModel's attached (hand / head slot) / dropped entity context — the
 * entity/player context read during the flush (extension write phase) is the same as
 * during submission (GUI scene brightness is always 255, entity positions are frozen
 * within a frame).
 */
public final class RenderPhasePolicy {

    private RenderPhasePolicy() {
    }

    /** Full-bright packed brightness (sky=15, block=15). */
    private static final int FULL_BRIGHT = 15728880;

    /**
     * Resolves the base brightness for a render phase (packed sky<<20 | block<<4
     * format; the GUI phase uses 255 screen-light semantics).
     * <p>
     * Per-phase semantics (migrated from QuadWriter's original branches, bit for bit):
     * <ul>
     *   <li>{@code ITEM_GUI} → 255 (no ambient light in screen space, constant full-bright);</li>
     *   <li>attached phases ({@link RenderPhase#isAttachedItemPhase()}, hand / head slot) →
     *       world light at the attached entity's position: for the local player, the
     *       player-position light (matching 1.7.10 {@code ItemRenderer} semantics); for
     *       mobs / other players / snow golems, the attached entity's own light (matching
     *       the entity lightmap semantics of {@code RenderManager.renderEntityStatic});
     *       fully black when there is no external light, falling back to full-bright
     *       when context is missing;</li>
     *   <li>{@code BLOCK_WORLD} / {@code BLOCK_DESTROY} → the block position's world
     *       mixed brightness (falls back to 0 when there is no world context);</li>
     *   <li>remaining item phases (dropped items / item frames) → world light at the
     *       dropped-item entity's position (falls back to full-bright when there is no
     *       entity context, preserving the existing fallback behavior).</li>
     * </ul>
     * Stateless pure function: block phases are computed from the incoming
     * world/x/y/z/block arguments; item phases internally read the Minecraft player
     * singleton and the {@link RenderJsonItemModel} attached (hand / head slot) /
     * dropped entity context.
     *
     * @param phase the render phase
     * @param world world access (used by block phases; ignored by item phases)
     * @param x     block X (ignored by item phases)
     * @param y     block Y
     * @param z     block Z
     * @param block block instance (used by block phases)
     * @return packed brightness (15728880 = full-bright, 0 = fully black)
     */
    public static int baselineBrightness(RenderPhase phase,
                                         @Nullable IBlockAccess world, int x, int y, int z,
                                         @Nullable Block block) {
        // GUI: no ambient light in screen space, constant full-bright (matching 1.7.10 RenderItem GUI semantics)
        if (phase == RenderPhase.ITEM_GUI) {
            return 255;
        }
        if (phase != null && phase.isAttachedItemPhase()) {
            return holderBrightness();
        }
        if (phase == RenderPhase.BLOCK_WORLD || phase == RenderPhase.BLOCK_DESTROY) {
            // Block phase: original writeBlockQuads semantics — use mixed brightness
            // when there is a world context, otherwise 0. (Now computed on demand by
            // LightPolicyExtension for BLOCK_WORLD AO-degraded quads in apply;
            // BLOCK_DESTROY is superseded by the BlockDestroyExtension override, and
            // this path is only a fallback.)
            return (world != null && block != null)
                    ? block.getMixedBrightnessForBlock(world, x, y, z)
                    : 0;
        }
        // DROPPED_ITEM_GROUND / DROPPED_BLOCK_GROUND / ITEM_FIXED
        return droppedItemBrightness();
    }

    /**
     * Whether the render phase uses the GL_LIGHTING + per-face-normal item lighting
     * mode (plan B).
     * <p>
     * Semantic baseline = FeatureRenderDispatcher's original private
     * isItemGlLitPhase: not BLOCK_WORLD, not ITEM_GUI, not an attached phase (i.e.
     * dropped items / item frames) — these phases enable GL_NORMALIZE and the emitters
     * write normals per face, handing directional lighting to GL instead of baking it
     * into vertex colors; GUI and attached-item phases keep baked shading (attached
     * matching 1.7.10 ItemRenderer glDisable(GL_LIGHTING)).
     */
    public static boolean isItemGlLit(RenderPhase phase) {
        return phase != null && phase != RenderPhase.BLOCK_WORLD
                && phase != RenderPhase.ITEM_GUI && !phase.isAttachedItemPhase();
    }

    /**
     * Base brightness for attached-item phases (hand / head slot): the world light
     * (sky light + block light) at the position of the entity the item is attached to.
     * <p>
     * Two branches (matching 1.7.10's independent lighting semantics for the two kinds
     * of attached items):
     * <ul>
     *   <li><b>non-player attached entities</b> (mobs / other players / snow golems):
     *       the entity light at the attached entity's own position, matching the entity
     *       lightmap semantics of {@code RenderManager.renderEntityStatic}
     *       ({@code getBrightnessForRender}; burning entities are forced to full-bright)
     *       — the attached-entity context is provided by
     *       {@link RenderJsonItemModel#getCurrentHolderEntity()} within the attached
     *       render window. Fixes the mismatch where a mob's held item was rendered with
     *       the player's position light and looked oddly bright when viewed from afar:
     *       the item appeared highlighted while the mob was in shadow and the player in
     *       light, and only returned to "normal" after approaching;</li>
     *   <li><b>the local player</b> (first / third person / player helmet slot): the
     *       world light at the player's position, matching the
     *       {@code getLightBrightnessForSkyBlocks(player coords, 0)} semantics of
     *       {@code ItemRenderer.renderItemInFirstPerson} — returns 0 when there is no
     *       external light and the held item renders fully black; falls back to
     *       full-bright when the player or world context is missing.</li>
     * </ul>
     * Migrated from QuadWriter (DBF refactor: brightness policy moved out to submission
     * time).
     *
     * @return packed brightness (15728880 = full-bright, 0 = fully black)
     */
    private static int holderBrightness() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = (mc != null) ? mc.thePlayer : null;

        // Non-player attached entities (mobs / other players / snow golems): the
        // entity's own lightmap semantics (attached-entity context within the render
        // window, see RenderJsonItemModel's attached entity window)
        EntityLivingBase holderEntity = RenderJsonItemModel.getCurrentHolderEntity();
        if (holderEntity != null && holderEntity != player) {
            if (holderEntity.worldObj == null) {
                // Abnormal window (entity has left the world): keep the full-bright fallback
                return FULL_BRIGHT;
            }
            // Matching RenderManager.renderEntityStatic: burning entities are forced to full-bright in the lightmap
            if (holderEntity.isBurning()) {
                return FULL_BRIGHT;
            }
            // partialTicks passed as 0: the vanilla implementation samples light by current tick coordinates and does not consume this parameter
            return holderEntity.getBrightnessForRender(0.0F);
        }

        // Local player (first / third person / player helmet slot): keep 1.7.10 ItemRenderer player-position light semantics
        if (player == null || player.worldObj == null) {
            return FULL_BRIGHT;
        }
        return player.worldObj.getLightBrightnessForSkyBlocks(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY),
                MathHelper.floor_double(player.posZ), 0);
    }

    /**
     * Base brightness for dropped-item phases: the world light (sky light + block
     * light) at the dropped-item entity's position, isomorphic to the attached-item
     * {@link #holderBrightness()} — dropped items darken together with the environment
     * at night or in unlit places instead of staying constantly full-bright (fixing
     * "dropped items are highlighted relative to a dark environment and draw
     * attention"). The entity context is established by {@link RenderJsonItemModel}
     * during Forge ENTITY rendering (ForgeHooksClient.renderEntityItem passes the
     * EntityItem as data[1] to renderItem); with no entity context (custom calls not
     * going through the Forge dropped path) it falls back to full-bright, preserving
     * the existing fallback behavior. Migrated from QuadWriter (DBF refactor:
     * brightness policy moved out to submission time).
     *
     * @return packed brightness (15728880 = full-bright, 0 = fully black)
     */
    private static int droppedItemBrightness() {
        EntityItem e = RenderJsonItemModel.getCurrentDroppedEntity();
        if (e == null || e.worldObj == null) {
            return FULL_BRIGHT;
        }
        return e.worldObj.getLightBrightnessForSkyBlocks(
                MathHelper.floor_double(e.posX),
                MathHelper.floor_double(e.posY),
                MathHelper.floor_double(e.posZ), 0);
    }
}
