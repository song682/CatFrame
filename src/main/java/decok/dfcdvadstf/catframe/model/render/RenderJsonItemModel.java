package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderSkeleton;
import net.minecraft.client.renderer.entity.RenderSnowMan;
import net.minecraft.client.renderer.entity.RenderWitch;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;

/**
 * Forge {@link IItemRenderer} that wires the CatFrame model system into the Forge item render pipeline.
 *
 * <h3>Core design</h3>
 * <ul>
 *   <li><b>Item models are independent of block models</b> - item rendering only looks up the
 *       {@link IItemStateProvider} explicitly registered in {@code registeredItemModels}
 *       (items/ItemState) via {@link ModelRegistry#getRegisteredItemModel}.
 *       Historically ItemBlock auto-fell back to {@code registeredBlockModels} and was wrapped as
 *       {@code BlockStateItemState}; that mechanism has been removed - block items that do not
 *       implement ItemState fall back to vanilla item rendering.</li>
 *   <li><b>{@link #shouldUseRenderHelper} returns true for EQUIPPED_BLOCK
 *       (all items, uniformly letting Forge apply the translate(-0.5) pre-transform), and always returns false for INVENTORY_BLOCK</b> -
 *       the hand path lets Forge apply the translate(-0.5) pre-transform, while the GUI path does not depend on Forge;
 *       the isometric rotation is entirely controlled by the model JSON's {@code display.gui} field,
 *       consumed by {@link DisplayTransformExtension} in
 *       {@link UniformRenderPipeline#renderItemQuads}.</li>
 * </ul>
 *
 * <h3>ItemRenderType → RenderPhase mapping</h3>
 * <ul>
 *   <li>{@link ItemRenderType#ENTITY} → {@link RenderPhase#DROPPED_ITEM_GROUND} (regular items) /
 *       {@link RenderPhase#DROPPED_BLOCK_GROUND} (block items)</li>
 *   <li>{@link ItemRenderType#EQUIPPED} → {@link RenderPhase#ITEM_HAND_THIRD_PERSON}
 *       (refined to {@link RenderPhase#ITEM_HEAD} when the host is a head slot - player helmet slot / mob head
 *       equipment slot / snow golem pumpkin, see {@code resolveHeadSlotKind})</li>
 *   <li>{@link ItemRenderType#EQUIPPED_FIRST_PERSON} → {@link RenderPhase#ITEM_HAND_FIRST_PERSON}</li>
 *   <li>{@link ItemRenderType#INVENTORY} → {@link RenderPhase#ITEM_GUI}</li>
 *   <li>{@link ItemRenderType#FIRST_PERSON_MAP} → not handled</li>
 * </ul>
 *
 * <h3>Forge pre-transforms and their inverse cancellation</h3>
 * In {@code ForgeHooksClient.renderEquippedItem}, Forge applies:
 * <ul>
 *   <li>{@code EQUIPPED_BLOCK=true}: {@code translate(-0.5, -0.5, -0.5)}</li>
 *   <li>{@code EQUIPPED_BLOCK=false}: {@code scale(1.5) + rotate(50°Y, 335°Z)}</li>
 * </ul>
 * In {@code ForgeHooksClient.renderInventoryItem}, Forge applies:
 * <ul>
 *   <li>{@code INVENTORY_BLOCK=true}: {@code scale(10) → translate(1, 0.5, 1)
 *       → scale(1,1,-1) → rotate(210°X, 45°Y, -90°Y)} (3D isometric)</li>
 *   <li>{@code INVENTORY_BLOCK=false}: a simple translate</li>
 * </ul>
 *
 * <p>For the EQUIPPED/EQUIPPED_FIRST_PERSON paths in {@link #renderItem}:
 * first person additionally cancels Forge's rotate(45°Y)+scale(0.4),
 * and all items uniformly cancel ForgeHooksClient's translate(-0.5) offset.
 * The Forge 3D transform on the INVENTORY path is handled in tandem with the model's own display transform,
 * requiring no extra processing.</p>
 */
public class RenderJsonItemModel implements IItemRenderer {

    /** Singleton shared by all items (render logic is delegated to each IItemStateProvider). */
    public static final RenderJsonItemModel INSTANCE = new RenderJsonItemModel();

    /**
     * The dropped-item entity currently being rendered by the render thread (ENTITY phase only, passed in by Forge's
     * {@code renderEntityItem} via data[1] of {@link #renderItem}). Client rendering is single-threaded and submission
     * and writing share the same stack, so a plain static field suffices; it is always null for non-dropped rendering
     * (GUI / hand / item frame, etc.).
     */
    @javax.annotation.Nullable
    private static EntityItem currentDroppedEntity;

    /**
     * Context accessor for QuadWriter to sample world light at the entity position during the dropped phase.
     *
     * @return the current dropped entity; null outside the dropped phase or after the context has been cleared
     */
    @javax.annotation.Nullable
    public static EntityItem getCurrentDroppedEntity() {
        return currentDroppedEntity;
    }

    /**
     * The item-attached entity currently being rendered by the render thread (attached phases only - hand / head slot,
     * passed in by Forge's {@code renderEquippedItem} / {@code ItemRenderer.renderItem} via data[1] of
     * {@link #renderItem}). Client rendering is single-threaded and submission and writing share the same stack, so a
     * plain static field suffices; it is always null for non-attached rendering (GUI / dropped item / item frame, etc.).
     * <p>
     * Lets {@code RenderPhasePolicy} sample world light at the attached entity's position (mob / other player / snow golem)
     * during attached phases (mirroring vanilla {@code RenderManager.renderEntityStatic}'s entity lightmap semantics),
     * avoiding misaligned highlighting caused by sampling light at the local player's position.
     * <p>
     * The entity the item is attached to (in hand or on the head slot), visible only
     * inside {@link #renderItem}'s attached-item window; lets the brightness policy
     * sample world light at the carrier's position for mobs / other players.
     */
    @javax.annotation.Nullable
    private static EntityLivingBase currentHolderEntity;

    /**
     * Context accessor for {@code RenderPhasePolicy} to sample attached-entity light during attached phases
     * (hand / head slot).
     *
     * @return the current attached entity; null outside attached phases or after the context has been cleared
     */
    @javax.annotation.Nullable
    public static EntityLivingBase getCurrentHolderEntity() {
        return currentHolderEntity;
    }

    private RenderJsonItemModel() {}

    // ==================== handleRenderType ====================

    /**
     * Checks whether CatFrame takes over rendering of this item in the given phase.
     *
     * <p>All items (including {@link ItemBlock}) uniformly obtain the registered
     * {@link IItemStateProvider} via
     * {@link ModelRegistry#getRegisteredItemModel}, then map its
     * {@link IItemStateProvider#handles(RenderPhase)} to Forge's
     * {@code handleRenderType} return value. Block items that do not explicitly register
     * an item model return false and fall back to vanilla rendering.</p>
     *
     * <p>This lets an IItemState that implements {@code handles()} finely control
     * which phases CatFrame takes over and which fall back to vanilla rendering.
     * For example, a model with {@code handles(ITEM_GUI) = false} uses vanilla 2D in the GUI,
     * while its hand phases with {@code handles(ITEM_HAND_*) = true} use the custom 3D path.</p>
     *
     * <p>FIRST_PERSON_MAP is not handled - the map item has a dedicated path.</p>
     */
    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        if (item == null || item.getItem() == null) return false;
        if (type == ItemRenderType.FIRST_PERSON_MAP) return false;

        // Unified path: obtain the explicitly registered item model via getRegisteredItemModel
        // (block items no longer auto-fallback; unregistered ones fall back to vanilla rendering)
        IItemStateProvider model = ModelRegistry.getRegisteredItemModel(item.getItem());
        if (model == null) return false;

        RenderPhase phase = toRenderPhase(type, item);
        if (phase == null) return false;

        return model.handles(phase);
    }

    // ==================== shouldUseRenderHelper ====================

    /**
     * Returns true for EQUIPPED_BLOCK for all items, so Forge uniformly applies the
     * {@code translate(-0.5, -0.5, -0.5)} pre-transform.
     * Previously non-block items took the EQUIPPED_BLOCK=false path, which triggered Forge's legacy 2D
     * transform chain ({@code translate + scale(1.5) + rotate(50°Y) + rotate(335°Z) + translate});
     * those transforms are incompatible with the 26.1 {@code ItemTransform.apply()} semantics.
     * After uniformly taking the EQUIPPED_BLOCK=true path, it suffices to cancel
     * {@code translate(-0.5)} in {@link #renderItem} to return to a clean model space.
     *
     * <p>INVENTORY_BLOCK always returns false - all items take the same path in the GUI:
     * the isometric rotation is formed by the model JSON's {@code display.gui} field (rotation + scale)
     * plus the pipeline's {@code scale(16, -16, 16)} projection,
     * no longer relying on Forge's legacy isometric transform ({@code scale(10) + rotate}).
     *
     * <p>The model JSON's {@code display} field is stored into
     * {@code BlockStateModelPart.partDisplay} at bake time and consumed by {@link DisplayTransformExtension}
     * in the extension chain.</p>
     */
    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        switch (helper) {
            case EQUIPPED_BLOCK:
                return true;
            case BLOCK_3D:
                // Route all custom-rendered items through the RenderPlayer block path,
                // so non-block items do not fall into the isFull3D() / else branch (inconsistent transforms)
                // except for the ENTITY type - when BLOCK_3D=true Forge takes the 3D branch and
                // pre-applies scale(0.5 or 0.25), which stacks with display.ground and makes the item too small
                return type != ItemRenderType.ENTITY;
            case ENTITY_ROTATION:
                // Dropped-item Y-axis spin animation
                // Forge's renderEntityItem calls glRotatef(rotation, 0, 1, 0),
                // aligning with the 26.1.2 GroundItemTransforms spin rotation semantics
                return type == ItemRenderType.ENTITY;
            case ENTITY_BOBBING:
                // Returning true = keep Forge's bobbing float effect (world-level)
                // display.ground.translation handles the model-level vertical offset
                return type == ItemRenderType.ENTITY;
            case INVENTORY_BLOCK:
                return false;
            default:
                return false;
        }
    }

    // ==================== renderItem ====================

    /**
     * Unified render entry point.
     *
     * <h3>Forge pre-transforms and their inverse cancellation</h3>
     * <p>Before calling {@code renderItem}, Forge has already applied a batch of GL transforms
     * that are incompatible with the 26.1 {@code ItemTransform.apply()} semantics.
     * To let the subsequent {@link DisplayTransformExtension} work in a clean model space,
     * Forge's legacy transforms must first be cancelled:</p>
     *
     * <pre>{@code
     * EQUIPPED_FIRST_PERSON (first person, all items):
     *   Forge renderItemInFirstPerson: rotate(45°Y) + scale(0.4)
     *   cancel: scale(2.5) + rotate(-45°Y) → net effect = I
     *   ForgeHooksClient (EQUIPPED_BLOCK=true): translate(-0.5, -0.5, -0.5)
     *   cancel: translate(+0.5, +0.5, +0.5) → net effect = I
     *
     * EQUIPPED (third person, all items):
     *   ForgeHooksClient (EQUIPPED_BLOCK=true): translate(-0.5, -0.5, -0.5)
     *   cancel: translate(+0.5, +0.5, +0.5) → net effect = I
     *
     * INVENTORY (all items in the GUI):
     *   Forge: simple 2D translate → no Forge isometric rotation
     *   → DisplayTransformExtension applies display.gui (rotate → scale)
     *   → pipeline scale(16, -16, 16) projects into the 16×16 GUI slot
     *   the isometric transform is entirely controlled by the model JSON's display.gui field, aligned with 26.1 semantics
     * }</pre>
     *
     * <p>After cancellation it enters a clean model space [0,1]³,
     * {@code model.render(stack, phase)} → {@link UniformRenderPipeline#renderItemQuads}
     * → {@link DisplayTransformExtension} applies the JSON model's
     * {@code display} transform in the extension chain (translate(-0.5) center offset → scale → rotate → translate).</p>
     *
     * <p>Model lookup goes through {@link ModelRegistry#getRegisteredItemModel}
     * and returns only explicitly registered item models; unregistered block items fall back to vanilla rendering.</p>
     */
    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        if (stack == null || stack.getItem() == null) return;
    
        RenderPhase phase = toRenderPhase(type, stack);
        if (phase == null) return;

        // ---- Extract the holder entity / dropped entity (data[0]=renderBlocks, data[1]=entity) ----
        EntityLivingBase entity = null;
        EntityItem droppedEntity = null;
        if (data != null && data.length > 1) {
            if (data[1] instanceof EntityLivingBase) {
                entity = (EntityLivingBase) data[1];
            } else if ((phase == RenderPhase.DROPPED_ITEM_GROUND
                    || phase == RenderPhase.DROPPED_BLOCK_GROUND)
                    && data[1] instanceof EntityItem) {
                // The EntityItem (not an EntityLivingBase) passed in by Forge's renderEntityItem:
                // establish the dropped-item brightness context so QuadWriter can sample world light at the entity
                // position when writing vertices, making dropped items as dark as their surroundings at night / with no light source
                // (mirroring the attached-phase holderBrightness semantics).
                droppedEntity = (EntityItem) data[1];
            }
        }

        // ---- Head slot refinement (replace with ITEM_HEAD when EQUIPPED and the head slot test matches) ----
        // In 1.7.10, the three head hosts (player helmet slot / mob head equipment slot / snow golem pumpkin) all
        // arrive as EQUIPPED; the test uses the host renderer and the heldItem reference (see resolveHeadSlotKind).
        if (type == ItemRenderType.EQUIPPED && resolveHeadSlotKind(entity, stack) != null) {
            phase = RenderPhase.ITEM_HEAD;
        }

        // ---- Compute the inverse-cancellation pre-transform ----
        // Compute the inverse-cancellation transform as a Matrix4d matrix, applied uniformly by the pipeline when submitting vertices
        Matrix4d preTransform = computePreTransform(type, entity, stack);

        // ---- Attached-entity context (hand / head slot phases) ----
        // Isomorphic to the dropped entity: lets RenderPhasePolicy sample light at the attached entity's position
        // (mob / other player / snow golem) during the flush; always sampling light at the local player's position
        // would render attached items of distant entities with misaligned highlighting.
        EntityLivingBase holderEntity = phase.isAttachedItemPhase() ? entity : null;

        // getRegisteredItemModel returns only explicitly registered item models (no block fallback)
        IItemStateProvider model = ModelRegistry.getRegisteredItemModel(stack.getItem());
        if (model == null) return;

        if (droppedEntity != null) {
            currentDroppedEntity = droppedEntity;
        }
        if (holderEntity != null) {
            currentHolderEntity = holderEntity;
        }
        try {
            model.render(stack, phase, preTransform);
        } finally {
            if (droppedEntity != null) {
                currentDroppedEntity = null;
            }
            if (holderEntity != null) {
                currentHolderEntity = null;
            }
        }
    }

    // ==================== Internal mappings ====================

    /**
     * Computes the inverse-cancellation pre-transform matrix.
     * <p>
     * Before calling {@code renderItem}, Forge has already applied a batch of GL transforms (see {@link #shouldUseRenderHelper}),
     * which are incompatible with the 26.1 {@code ItemTransform.apply()} semantics.
     * This method computes the inverse-cancellation operation as a {@link Matrix4d} matrix,
     * applied by the pipeline to vertex coordinates when submitting vertices.
     * <p>
     * The matrix construction order matches GL's post-multiply order: an operation called earlier corresponds to a left multiplication of the final matrix.
     *
     * @param type   Forge item render type
     * @param entity the entity the item is attached to (may be null; used during EQUIPPED to determine head slot /
     *               RenderWitch host / RenderBiped vs RenderPlayer routing)
     * @param stack  the item stack (used during EQUIPPED to determine item type and head slot ownership)
     * @return the inverse-cancellation Matrix4d matrix, or null if none is needed
     */
    @javax.annotation.Nullable
    private static Matrix4d computePreTransform(ItemRenderType type,
                                                 @javax.annotation.Nullable EntityLivingBase entity,
                                                 @javax.annotation.Nullable ItemStack stack) {
        if (type == ItemRenderType.INVENTORY) {
            // GUI: map model space [0,1]³ to a 16×16 pixel GUI slot
            // Forge has already set glTranslatef(x, y, -3+zLevel) to position at the slot origin
            // display-centered vertices [-0.5,0.5]³ must be mapped to the [0,16]×[16,0]×[-8,8] pixel space
            // transform: T(8,8,0) × S(16,-16,16)
            // combined with the display transform T(-0.5):
            //   v' = T(8,8,0) × S(16,-16,16) × T(-0.5) × v
            //   v(0,0,0) → (0, 16, -8) top-left corner
            //   v(1,1,1) → (16, 0, 8)  bottom-right corner
            Matrix4d m = new Matrix4d();
            m.setIdentity();
            Matrix4d tmp = new Matrix4d();

            // first translate (8, 8, 0)
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(8.0, 8.0, 0.0));
            m.mul(tmp);

            // then scale (16, -16, 16) — flip Y so GUI Y-down matches model Y-up
            tmp.setIdentity();
            tmp.m00 = 16.0; tmp.m11 = -16.0; tmp.m22 = 16.0;
            m.mul(tmp);

            return m;
        } else if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
            // Forge call chain: rotate(45°Y) → swing_rot → scale(0.4) → [FHClient: translate(-0.5)]
            // inverse-cancellation matrix: T(0.5) × S(2.5) × RY(-45)
            // construction order matches GL (post-multiply): T first, then S, then R
            Matrix4d m = new Matrix4d();
            m.setIdentity();
            Matrix4d tmp = new Matrix4d();
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
            m.mul(tmp);
            tmp.setIdentity();
            tmp.m00 = 2.5; tmp.m11 = 2.5; tmp.m22 = 2.5;
            m.mul(tmp);
            tmp.rotY(Math.toRadians(-45));
            m.mul(tmp);
            return m;
        } else if (type == ItemRenderType.EQUIPPED) {
            // Attached item: first route to head slot / witch by host renderer, then route the hand by entity + item type
            Render hostRenderer = getHostRenderer(entity);

            // (1) Head slot (player helmet slot / mob head equipment slot / snow golem pumpkin):
            //    In 1.7.10 all three hosts arrive as EQUIPPED (see resolveHeadSlotKind),
            //    and their transform chain is entirely different from the hand, needing independent inverse cancellation + 26.1 head-layer alignment.
            HeadSlotKind headKind = resolveHeadSlotKind(entity, stack);
            if (headKind == HeadSlotKind.SNOWMAN) {
                return computeSnowGolemHeadPreTransform();
            } else if (headKind == HeadSlotKind.NORMAL) {
                return computeHeadPreTransform();
            }

            // (2) RenderWitch: witch's held item (RenderWitch.renderEquippedItems L52-128).
            //    Must be routed before the isPlayer / ItemBlock test - the witch is an independent host chain
            //    (extends RenderLiving, not RenderBiped): different hand offset
            //    (T(-0.0625,0.53125,0.21875)), the ItemBlock branch has no is3D guarantee
            //    and has the opposite X scale sign, plus the extra RX(-15) × RZ(40) at the tail.
            if (hostRenderer instanceof RenderWitch) {
                return computeWitchPreTransform(stack != null ? stack.getItem() : null);
            }

            // Held item entity + item type test
            // RenderPlayer uniformly takes the BLOCK_3D path for all items (since is3D=true)
            // RenderBiped takes item-specific paths for non-ItemBlock items (bow / Full3D / default)
            boolean isPlayer = (entity instanceof EntityPlayer);
            boolean isBlockItem = (stack != null && stack.getItem() instanceof ItemBlock);

            if (isPlayer || isBlockItem) {
                // RenderPlayer path: all items take BLOCK_3D
                // RenderBiped + ItemBlock: also takes BLOCK_3D
                return computePlayerBlock3DPreTransform();
            } else {
                // RenderBiped path: non-block items take the RenderBiped-specific transform
                Item item = stack != null ? stack.getItem() : null;
                if (item == Items.bow) {
                    // RenderBiped bow path (lines 278-286):
                    //   T(0,0.125,0.3125) × RY(-20) × S(0.625,-0.625,0.625)
                    //   × RX(-100) × RY(45) × FHClient T(-0.5)
                    return computeBipedBowPreTransform();
                } else if (item != null && item.isFull3D()) {
                    // RenderBiped isFull3D path (lines 287-301):
                    //   [rotateAround RZ(180) × T(0,-0.125,0)] × func_82422_c()
                    //   × S(0.625,-0.625,0.625) × RX(-100) × RY(45) × FHClient T(-0.5)
                    // the func_82422_c offset is routed by host renderer: skeleton overrides it to T(0.09375,0.1875,0);
                    // fishing rod / carrot on a stick (shouldRotateAroundWhenRendering) additionally insert the bracketed segment.
                    return computeBipedFull3DPreTransform(hostRenderer instanceof RenderSkeleton,
                            item.shouldRotateAroundWhenRendering());
                } else {
                    // RenderBiped default 2D path (lines 302-310):
                    //   T(0.25,0.1875,-0.1875) × S(0.375) × RZ(60) × RX(-90) × RZ(20)
                    //   × FHClient T(-0.5)
                    return computeBipedDefaultPreTransform();
                }
            }
        } else if (type == ItemRenderType.ENTITY) {
            // Forge's renderEntityItem takes the else branch when BLOCK_3D=false and
            // pre-applies scale(0.5, 0.5, 0.5). Inverse cancellation: scale(2.0)
            Matrix4d s = new Matrix4d();
            s.setIdentity();
            s.m00 = 2.0; s.m11 = 2.0; s.m22 = 2.0;
            return s;
        }
        return null;
    }

    // ==================== EQUIPPED inverse-cancellation sub-methods (routed by entity / item type) ====================

    /**
     * Head slot ownership kind (the result of {@link #resolveHeadSlotKind}).
     */
    private enum HeadSlotKind {
        /** Player helmet slot / mob head equipment slot (RenderPlayer L197-211 / RenderBiped L203-223). */
        NORMAL,
        /** Snow golem pumpkin (RenderSnowMan L36-60); its tail scale Z sign is opposite to NORMAL. */
        SNOWMAN
    }

    /**
     * Gets the host renderer currently used by the entity, for precise head slot / witch / skeleton routing
     * (without guessing by entity type - the same entity class may be handled by different renderers).
     *
     * @param entity the entity the item is attached to; may be null
     * @return the host {@link Render}; null when the entity is null or the render manager is uninitialized
     */
    @javax.annotation.Nullable
    private static Render getHostRenderer(@javax.annotation.Nullable EntityLivingBase entity) {
        if (entity == null || RenderManager.instance == null) return null;
        return RenderManager.instance.getEntityRenderObject(entity);
    }

    /**
     * Determines whether an EQUIPPED render comes from an entity head slot (player helmet slot / mob head equipment slot / snow golem pumpkin).
     * <p>
     * In 1.7.10 all three head hosts call {@code itemRenderer.renderItem} with the EQUIPPED type:
     * player helmet slot ({@code RenderPlayer} L197-211, ItemBlock only), mob head equipment slot
     * ({@code RenderBiped} L203-223, ItemBlock only), snow golem pumpkin ({@code RenderSnowMan}
     * L36-60, always a pumpkin ItemBlock). Head slots and the hand share the same entry point, so they can only be
     * distinguished by the host renderer and the stack reference: a head slot's stack is always an ItemBlock and is
     * not the same instance as {@code getHeldItem()} (the mob head slot passes the {@code func_130225_q(3)} slot stack,
     * the player passes the helmet slot stack, and the snow golem passes a freshly created pumpkin stack). Skulls
     * (Items.skull) use the separate TileEntitySkullRenderer path and armor uses the equipment model path, neither going
     * through the item render entry, so they are naturally excluded here.
     *
     * @param entity the entity the item is attached to; may be null
     * @param stack  the item stack being rendered
     * @return the head slot kind; null for non-head slots (including the hand)
     */
    @javax.annotation.Nullable
    private static HeadSlotKind resolveHeadSlotKind(@javax.annotation.Nullable EntityLivingBase entity,
                                                    @javax.annotation.Nullable ItemStack stack) {
        if (entity == null || stack == null || stack.getItem() == null) return null;
        if (getHostRenderer(entity) instanceof RenderSnowMan) return HeadSlotKind.SNOWMAN;
        if (stack.getItem() instanceof ItemBlock && stack != entity.getHeldItem()) return HeadSlotKind.NORMAL;
        return null;
    }

    /**
     * Head slot inverse cancellation (player helmet slot / mob head equipment slot) + 26.1 alignment.
     * <p>
     * Cancel the 1.7.10 head slot transform chain ({@code RenderPlayer} L204-207 =
     * {@code RenderBiped} L217-219):
     *   T(0,-0.25,0) × RY(90) × S(0.625,-0.625,-0.625) × FHClient T(-0.5)
     * <p>
     * 26.1 alignment: {@code CustomHeadLayer}'s head item pose (display takes head):
     *   T(0,-0.25,0) × RY(180) × S(0.625,-0.625,-0.625)
     */
    private static Matrix4d computeHeadPreTransform() {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // (1) cancel FHClient translate(-0.5)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // (2) cancel the 1.7.10 head slot chain:
        //    S(1.6,-1.6,-1.6) inverse of S(0.625,-0.625,-0.625)
        tmp.setIdentity();
        tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = -1.6;
        m.mul(tmp);

        //    RY(-90) inverse of RY(90)
        tmp.rotY(Math.toRadians(-90));
        m.mul(tmp);

        //    T(0,0.25,0) inverse of T(0,-0.25,0)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0, 0.25, 0));
        m.mul(tmp);

        // (3) apply the 26.1 CustomHeadLayer head item pose
        //    (the middle T(0,0.25,0) and T(0,-0.25,0) cancel out; the full segment is kept for auditability):
        //    T(0,-0.25,0)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0, -0.25, 0));
        m.mul(tmp);

        //    RY(180)
        tmp.rotY(Math.toRadians(180));
        m.mul(tmp);

        //    S(0.625,-0.625,-0.625)
        tmp.setIdentity();
        tmp.m00 = 0.625; tmp.m11 = -0.625; tmp.m22 = -0.625;
        m.mul(tmp);

        return m;
    }

    /**
     * Snow golem head slot inverse cancellation (pumpkin) + 26.1 alignment.
     * <p>
     * Cancel the 1.7.10 snow golem transform chain ({@code RenderSnowMan} L49-55, tail scale Z is positive):
     *   T(0,-0.34375,0) × RY(90) × S(0.625,-0.625,+0.625) × FHClient T(-0.5)
     * <p>
     * 26.1 alignment: {@code SnowGolemHeadLayer}'s pumpkin pose (Z scale is negative):
     *   T(0,-0.34375,0) × RY(180) × S(0.625,-0.625,-0.625)
     */
    private static Matrix4d computeSnowGolemHeadPreTransform() {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // (1) cancel FHClient translate(-0.5)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // (2) cancel the 1.7.10 snow golem chain:
        //    S(1.6,-1.6,+1.6) inverse of S(0.625,-0.625,+0.625)
        tmp.setIdentity();
        tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = 1.6;
        m.mul(tmp);

        //    RY(-90) inverse of RY(90)
        tmp.rotY(Math.toRadians(-90));
        m.mul(tmp);

        //    T(0,0.34375,0) inverse of T(0,-0.34375,0)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0, 0.34375, 0));
        m.mul(tmp);

        // (3) apply the 26.1 SnowGolemHeadLayer pumpkin pose
        //    (the middle T(0,0.34375,0) and T(0,-0.34375,0) cancel out; the full segment is kept for auditability):
        //    T(0,-0.34375,0)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0, -0.34375, 0));
        m.mul(tmp);

        //    RY(180)
        tmp.rotY(Math.toRadians(180));
        m.mul(tmp);

        //    S(0.625,-0.625,-0.625)
        tmp.setIdentity();
        tmp.m00 = 0.625; tmp.m11 = -0.625; tmp.m22 = -0.625;
        m.mul(tmp);

        return m;
    }

    /**
     * RenderWitch independent host chain inverse cancellation + 26.1 alignment.
     * <p>
     * RenderWitch extends {@code RenderLiving} (not RenderBiped); its held-item chain is independent
     * ({@code RenderWitch} L52-128):
     *   T(-0.0625,0.53125,0.21875) × [branch chain] × RX(-15) × RZ(40) × FHClient T(-0.5)
     * The branch chain depends on item type (the block test uses only {@code RenderBlocks.renderItemIn3d}, with no is3D guarantee,
     * and the ItemBlock branch's X scale sign is opposite to RenderBiped):
     *   block: T(0,0.1875,-0.3125) × RX(20) × RY(45) × S(0.375,-0.375,0.375);
     *   bow / Full3D (same as the corresponding RenderBiped branches) / default 2D.
     * <p>
     * 26.1 alignment: {@code WitchItemLayer}'s static nose-potion pose segment
     *   T(0.0625,0.25,0) × RZ(180) × RX(140) × RZ(10) × RX(180).
     * (For non-potion items, 26.1 uses the CrossedArms pose whose pivot differs from the 1.7.10 nose pivot and
     * cannot be cancelled with a static matrix, so the nose pose is used uniformly here.)
     *
     * @param item the witch's held item (may be null; the default 2D branch is used when null)
     */
    private static Matrix4d computeWitchPreTransform(@javax.annotation.Nullable Item item) {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // (1) cancel FHClient translate(-0.5)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // (2) cancel the tail RX(-15) × RZ(40) (RZ applies to vertices before RX):
        //    RZ(-40) inverse of RZ(40)
        tmp.rotZ(Math.toRadians(-40));
        m.mul(tmp);

        //    RX(15) inverse of RX(-15)
        tmp.rotX(Math.toRadians(15));
        m.mul(tmp);

        // (3) cancel the branch chain (RenderWitch lines 74-115):
        if (item instanceof ItemBlock
                && RenderBlocks.renderItemIn3d(Block.getBlockFromItem(item).getRenderType())) {
            //    block path: T(0,0.1875,-0.3125) × RX(20) × RY(45) × S(0.375,-0.375,0.375)
            //    S(2.667,-2.667,2.667) inverse of S(0.375,-0.375,0.375)
            tmp.setIdentity();
            double invScale = 1.0 / 0.375; // ≈ 2.667
            tmp.m00 = invScale; tmp.m11 = -invScale; tmp.m22 = invScale;
            m.mul(tmp);

            //    RY(-45) inverse of RY(45)
            tmp.rotY(Math.toRadians(-45));
            m.mul(tmp);

            //    RX(-20) inverse of RX(20)
            tmp.rotX(Math.toRadians(-20));
            m.mul(tmp);

            //    T(0,-0.1875,0.3125) inverse of T(0,0.1875,-0.3125)
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(0, -0.1875, 0.3125));
            m.mul(tmp);
        } else if (item == Items.bow) {
            //    bow path: T(0,0.125,0.3125) × RY(-20) × S(0.625,-0.625,0.625)
            //    × RX(-100) × RY(45)
            tmp.rotY(Math.toRadians(-45));
            m.mul(tmp);

            tmp.rotX(Math.toRadians(100));
            m.mul(tmp);

            tmp.setIdentity();
            tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = 1.6;
            m.mul(tmp);

            tmp.rotY(Math.toRadians(20));
            m.mul(tmp);

            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(0, -0.125, -0.3125));
            m.mul(tmp);
        } else if (item != null && item.isFull3D()) {
            //    Full3D path: [rotateAround RZ(180) × T(0,-0.125,0)] × func_82410_b()
            //    × S(0.625,-0.625,0.625) × RX(-100) × RY(45)
            tmp.rotY(Math.toRadians(-45));
            m.mul(tmp);

            tmp.rotX(Math.toRadians(100));
            m.mul(tmp);

            tmp.setIdentity();
            tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = 1.6;
            m.mul(tmp);

            //    T(0,-0.1875,0) inverse of func_82410_b T(0,0.1875,0)
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(0, -0.1875, 0));
            m.mul(tmp);

            //    rotateAround inverse (fishing rod / carrot on a stick): vanilla appends
            //    RZ(180) × T(0,-0.125,0) before func; in reverse order, insert T(0,0.125,0) × RZ(180) after the func inverse
            if (item.shouldRotateAroundWhenRendering()) {
                tmp.setIdentity();
                tmp.setTranslation(new Vector3d(0, 0.125, 0));
                m.mul(tmp);

                tmp.rotZ(Math.toRadians(180));
                m.mul(tmp);
            }
        } else {
            //    default 2D path: T(0.25,0.1875,-0.1875) × S(0.375) × RZ(60) × RX(-90) × RZ(20)
            tmp.rotZ(Math.toRadians(-20));
            m.mul(tmp);

            tmp.rotX(Math.toRadians(90));
            m.mul(tmp);

            tmp.rotZ(Math.toRadians(-60));
            m.mul(tmp);

            //    S(2.667) inverse of S(0.375) — uniform scale
            tmp.setIdentity();
            double invScale = 1.0 / 0.375; // ≈ 2.667
            tmp.m00 = invScale; tmp.m11 = invScale; tmp.m22 = invScale;
            m.mul(tmp);

            //    T(-0.25,-0.1875,0.1875) inverse of T(0.25,0.1875,-0.1875)
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(-0.25, -0.1875, 0.1875));
            m.mul(tmp);
        }

        // (4) cancel the hand offset T(-0.0625, 0.53125, 0.21875)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, -0.53125, -0.21875));
        m.mul(tmp);

        // (5) apply the 26.1 WitchItemLayer static nose-potion pose segment:
        //    T(0.0625,0.25,0)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, 0.25, 0));
        m.mul(tmp);

        //    RZ(180)
        tmp.rotZ(Math.toRadians(180));
        m.mul(tmp);

        //    RX(140)
        tmp.rotX(Math.toRadians(140));
        m.mul(tmp);

        //    RZ(10)
        tmp.rotZ(Math.toRadians(10));
        m.mul(tmp);

        //    RX(180)
        tmp.rotX(Math.toRadians(180));
        m.mul(tmp);

        return m;
    }

    /**
     * Inverse cancellation for RenderPlayer's (or RenderBiped + ItemBlock's) BLOCK_3D path + 26.1 alignment.
     * <p>
     * Cancel the RenderPlayer BLOCK_3D transform chain:
     *   T(-0.0625,0.4375,0.0625) × T(0,0.1875,-0.3125) × RX(20) × RY(45)
     *   × S(-0.375,-0.375,0.375) × FHClient T(-0.5)
     */
    private static Matrix4d computePlayerBlock3DPreTransform() {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // (1) cancel FHClient translate(-0.5)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // (2) cancel the RenderPlayer BLOCK_3D path
        //    scale(-2.667, -2.667, 2.667)
        tmp.setIdentity();
        tmp.m00 = -2.667; tmp.m11 = -2.667; tmp.m22 = 2.667;
        m.mul(tmp);

        //    RY(-45)
        tmp.rotY(Math.toRadians(-45));
        m.mul(tmp);

        //    RX(-20)
        tmp.rotX(Math.toRadians(-20));
        m.mul(tmp);

        //    T(0, -0.1875, 0.3125)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0, -0.1875, 0.3125));
        m.mul(tmp);

        // (3) cancel the hand offset T(-0.0625, 0.4375, 0.0625)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, -0.4375, -0.0625));
        m.mul(tmp);

        // (4) apply the 26.1.2 ItemInHandLayer standard alignment transform
        //    RX(-90)
        tmp.rotX(Math.toRadians(-90));
        m.mul(tmp);

        //    RY(180)
        tmp.rotY(Math.toRadians(180));
        m.mul(tmp);

        //    T(1/16, 2/16, -10/16)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(1.0 / 16.0, 2.0 / 16.0, -10.0 / 16.0));
        m.mul(tmp);

        return m;
    }

    /**
     * RenderBiped bow path inverse cancellation + 26.1 alignment.
     * <p>
     * RenderBiped's transform chain for {@code Items.bow} (lines 278-286):
     *   T(-0.0625,0.4375,0.0625) × T(0,0.125,0.3125) × RY(-20)
     *   × S(0.625,-0.625,0.625) × RX(-100) × RY(45) × FHClient T(-0.5)
     * <p>
     * Inverse-cancellation matrix (post-multiply order):
     *   T(0.5) × RY(-45) × RX(100) × S(1.6,-1.6,1.6) × RY(20)
     *   × T(0,-0.125,-0.3125) × T(0.0625,-0.4375,-0.0625)
     *   × RX(-90) × RY(180) × T(1/16, 2/16, -10/16)
     */
    private static Matrix4d computeBipedBowPreTransform() {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // (1) cancel FHClient translate(-0.5)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // (2) cancel the bow path:
        //    RY(-45) inverse of RY(45)
        tmp.rotY(Math.toRadians(-45));
        m.mul(tmp);

        //    RX(100) inverse of RX(-100)
        tmp.rotX(Math.toRadians(100));
        m.mul(tmp);

        //    S(1.6,-1.6,1.6) inverse of S(0.625,-0.625,0.625)
        tmp.setIdentity();
        tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = 1.6;
        m.mul(tmp);

        //    RY(20) inverse of RY(-20)
        tmp.rotY(Math.toRadians(20));
        m.mul(tmp);

        //    T(0,-0.125,-0.3125) inverse of T(0,0.125,0.3125)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0, -0.125, -0.3125));
        m.mul(tmp);

        // (3) cancel the hand offset T(-0.0625, 0.4375, 0.0625)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, -0.4375, -0.0625));
        m.mul(tmp);

        // (4) 26.1.2 alignment
        tmp.rotX(Math.toRadians(-90));
        m.mul(tmp);
        tmp.rotY(Math.toRadians(180));
        m.mul(tmp);
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(1.0 / 16.0, 2.0 / 16.0, -10.0 / 16.0));
        m.mul(tmp);

        return m;
    }

    /**
     * RenderBiped isFull3D path inverse cancellation + 26.1 alignment.
     * <p>
     * RenderBiped's transform chain for {@code isFull3D()} items (lines 287-301):
     *   T(-0.0625,0.4375,0.0625) × [RZ(180) × T(0,-0.125,0)] × func_82422_c()
     *   × S(0.625,-0.625,0.625) × RX(-100) × RY(45) × FHClient T(-0.5)
     * <p>
     * func_82422_c() is overridden by the host renderer: the base RenderBiped uses T(0,0.1875,0),
     * while {@code RenderSkeleton} (skeleton) overrides it to T(0.09375,0.1875,0).
     * The bracketed segment exists only when {@code shouldRotateAroundWhenRendering()}
     * (fishing rod / carrot on a stick).
     *
     * @param skeletonRenderer whether the host is the skeleton renderer (determines func_82422_c's X offset)
     * @param rotateAround     whether the item is shouldRotateAroundWhenRendering (fishing-rod-like)
     */
    private static Matrix4d computeBipedFull3DPreTransform(boolean skeletonRenderer, boolean rotateAround) {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // (1) cancel FHClient
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // (2) cancel the Full3D path:
        //    RY(-45) inverse of RY(45)
        tmp.rotY(Math.toRadians(-45));
        m.mul(tmp);

        //    RX(100) inverse of RX(-100)
        tmp.rotX(Math.toRadians(100));
        m.mul(tmp);

        //    S(1.6,-1.6,1.6) inverse of S(0.625,-0.625,0.625)
        tmp.setIdentity();
        tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = 1.6;
        m.mul(tmp);

        //    func_82422_c inverse: base is T(0,0.1875,0); skeleton overrides it to T(0.09375,0.1875,0)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(skeletonRenderer ? -0.09375 : 0.0, -0.1875, 0));
        m.mul(tmp);

        //    rotateAround inverse (fishing rod / carrot on a stick): vanilla appends
        //    RZ(180) × T(0,-0.125,0) before func_82422_c; in reverse order, insert T(0,0.125,0) × RZ(180) after the func inverse
        if (rotateAround) {
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(0, 0.125, 0));
            m.mul(tmp);

            tmp.rotZ(Math.toRadians(180));
            m.mul(tmp);
        }

        // (3) cancel the hand offset
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, -0.4375, -0.0625));
        m.mul(tmp);

        // (4) 26.1.2 alignment
        tmp.rotX(Math.toRadians(-90));
        m.mul(tmp);
        tmp.rotY(Math.toRadians(180));
        m.mul(tmp);
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(1.0 / 16.0, 2.0 / 16.0, -10.0 / 16.0));
        m.mul(tmp);

        return m;
    }

    /**
     * RenderBiped default 2D path inverse cancellation + 26.1 alignment.
     * <p>
     * RenderBiped's transform chain for default 2D items (lines 302-310):
     *   T(-0.0625,0.4375,0.0625) × T(0.25,0.1875,-0.1875) × S(0.375)
     *   × RZ(60) × RX(-90) × RZ(20) × FHClient T(-0.5)
     */
    private static Matrix4d computeBipedDefaultPreTransform() {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // (1) cancel FHClient
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // (2) cancel the default 2D path:
        //    RZ(-20) inverse of RZ(20)
        tmp.rotZ(Math.toRadians(-20));
        m.mul(tmp);

        //    RX(90) inverse of RX(-90)
        tmp.rotX(Math.toRadians(90));
        m.mul(tmp);

        //    RZ(-60) inverse of RZ(60)
        tmp.rotZ(Math.toRadians(-60));
        m.mul(tmp);

        //    S(2.667) inverse of S(0.375) — uniform scale
        tmp.setIdentity();
        double invScale = 1.0 / 0.375; // ≈ 2.667
        tmp.m00 = invScale; tmp.m11 = invScale; tmp.m22 = invScale;
        m.mul(tmp);

        //    T(-0.25,-0.1875,0.1875) inverse of T(0.25,0.1875,-0.1875)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(-0.25, -0.1875, 0.1875));
        m.mul(tmp);

        // (3) cancel the hand offset
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, -0.4375, -0.0625));
        m.mul(tmp);

        // (4) 26.1.2 alignment
        tmp.rotX(Math.toRadians(-90));
        m.mul(tmp);
        tmp.rotY(Math.toRadians(180));
        m.mul(tmp);
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(1.0 / 16.0, 2.0 / 16.0, -10.0 / 16.0));
        m.mul(tmp);

        return m;
    }

    /**
     * Forge ItemRenderType → CatFrame RenderPhase.
     * ENTITY maps to DROPPED_BLOCK_GROUND / DROPPED_ITEM_GROUND depending on whether the item is a block item.
     */
    private static RenderPhase toRenderPhase(ItemRenderType type, ItemStack stack) {
        if (type == null) return null;
        switch (type) {
            case EQUIPPED:
                return RenderPhase.ITEM_HAND_THIRD_PERSON;
            case EQUIPPED_FIRST_PERSON:
                return RenderPhase.ITEM_HAND_FIRST_PERSON;
            case INVENTORY:
                return RenderPhase.ITEM_GUI;
            case ENTITY:
                return (stack != null && stack.getItem() instanceof net.minecraft.item.ItemBlock)
                        ? RenderPhase.DROPPED_BLOCK_GROUND
                        : RenderPhase.DROPPED_ITEM_GROUND;
            default:
                return null;
        }
    }
}
