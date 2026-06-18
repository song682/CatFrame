package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.ItemModel;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import net.minecraft.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Forge {@link IItemRenderer}，将 CatFrame 模型系统接入 Forge 物品渲染管线。
 *
 * <h3>核心设计</h3>
 * <ul>
 *   <li><b>Block 和 ItemBlock 共用同一个模型</b>——方块物品不创建独立 ItemModel，
 *       而是通过 {@link VanillaModelManager.ModelRegistration#getRegisteredItemModel}
 *       的 fallback 逻辑直接从 {@code registeredBlockModels} / {@code bakedBlockModels}
 *       取 BlockStateModel，包装为 ItemModelWrapper。</li>
 *   <li><b>{@link #shouldUseRenderHelper} 对 EQUIPPED_BLOCK 返回 true
 *       （仅 ItemBlock），对 INVENTORY_BLOCK 始终返回 false</b>——
 *       手持路径让 Forge 做 translate(-0.5) 前置，GUI 路径不依赖 Forge，
 *       等距旋转完全由 model JSON 的 {@code display.gui} 字段控制，
 *       由 {@link DisplayTransformExtension} 在
 *       {@link UniformRenderPipeline#renderItemQuads} 中消费。</li>
 * </ul>
 *
 * <h3>ItemRenderType → RenderPhase 映射</h3>
 * <ul>
 *   <li>{@link ItemRenderType#ENTITY} → {@link RenderPhase#DROPPED_ITEM_GROUND}（普通物品）/
 *       {@link RenderPhase#DROPPED_BLOCK_GROUND}（方块物品）</li>
 *   <li>{@link ItemRenderType#EQUIPPED} → {@link RenderPhase#ITEM_HAND_THIRD_PERSON}</li>
 *   <li>{@link ItemRenderType#EQUIPPED_FIRST_PERSON} → {@link RenderPhase#ITEM_HAND_FIRST_PERSON}</li>
 *   <li>{@link ItemRenderType#INVENTORY} → {@link RenderPhase#ITEM_GUI}</li>
 *   <li>{@link ItemRenderType#FIRST_PERSON_MAP} → 不接管</li>
 * </ul>
 *
 * <h3>Forge 前置变换与反抵消</h3>
 * Forge 在 {@code ForgeHooksClient.renderEquippedItem} 中：
 * <ul>
 *   <li>{@code EQUIPPED_BLOCK=true}：{@code translate(-0.5, -0.5, -0.5)}</li>
 *   <li>{@code EQUIPPED_BLOCK=false}：{@code scale(1.5) + rotate(50°Y, 335°Z)}</li>
 * </ul>
 * Forge 在 {@code ForgeHooksClient.renderInventoryItem} 中：
 * <ul>
 *   <li>{@code INVENTORY_BLOCK=true}：{@code scale(10) → translate(1, 0.5, 1)
 *       → scale(1,1,-1) → rotate(210°X, 45°Y, -90°Y)}（3D 等距）</li>
 *   <li>{@code INVENTORY_BLOCK=false}：简单 translate</li>
 * </ul>
 *
 * <p>{@link #renderItem} 中对 EQUIPPED/EQUIPPED_FIRST_PERSON 路径：
 * 第一人称额外反抵消 Forge 的 rotate(45°Y)+scale(0.4)，
 * 所有物品统一反抵消 ForgeHooksClient 的 translate(-0.5) 偏移。
 * INVENTORY 路径的 Forge 3D 变换靠模型自身的 display transform 配合，
 * 不需要额外处理。</p>
 */
public class RenderJsonItemModel implements IItemRenderer {

    /** 单例，所有物品共用（渲染逻辑委托给各 ItemModel）。 */
    public static final RenderJsonItemModel INSTANCE = new RenderJsonItemModel();

    private RenderJsonItemModel() {}

    // ====== 诊断日志：状态驱动（按视角/物品切换触发，不逐帧打印） ======
    private static final Logger LOGGER = LogManager.getLogger(RenderJsonItemModel.class);
    private static ItemRenderType lastDebugType = null;
    private static int lastDebugItemId = -1;
    private static int lastDebugItemDamage = -1;

    // ==================== handleRenderType ====================

    /**
     * 只要 CatFrame 有该物品的模型就接管渲染。
     *
     * <p>对于 {@link ItemBlock}：检查方块是否有 CatFrame 模型
     * （{@link VanillaModelManager.ModelRegistration#hasModel(Block)}）。
     * 对于非方块物品：检查是否有注册的 {@link ItemModel}。</p>
     *
     * <p>FIRST_PERSON_MAP 不接管——地图物品专用路径。</p>
     */
    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        if (item == null || item.getItem() == null) return false;
        if (type == ItemRenderType.FIRST_PERSON_MAP) return false;

        // Block items → check if block has a CatFrame model
        if (item.getItem() instanceof ItemBlock) {
            Block block = Block.getBlockFromItem(item.getItem());
            return block != null && VanillaModelManager.ModelRegistration.hasModel(block);
        }

        // Non-block items → check if there's a registered ItemModel
        return VanillaModelManager.ModelRegistration.getRegisteredItemModel(item.getItem()) != null;
    }

    // ==================== shouldUseRenderHelper ====================

    /**
     * EQUIPPED_BLOCK 对所有物品返回 true，让 Forge 统一应用
     * {@code translate(-0.5, -0.5, -0.5)} 前置变换。
     * 原先非方块物品走 EQUIPPED_BLOCK=false 路径，会触发 Forge 的 legacy 2D
     * 变换链 ({@code translate + scale(1.5) + rotate(50°Y) + rotate(335°Z) + translate})，
     * 这些变换与 26.1 {@code ItemTransform.apply()} 语义不兼容。
     * 统一走 EQUIPPED_BLOCK=true 路径后，只需在 {@link #renderItem} 中
     * 反抵消 {@code translate(-0.5)} 即可回归干净的模型空间。
     *
     * <p>INVENTORY_BLOCK 始终返回 false——所有物品在 GUI 走同一条路径：
     * 等距旋转由 model JSON 的 {@code display.gui} 字段（rotation + scale）
     * + 管线的 {@code scale(16, -16, 16)} 投影构成，
     * 不再依赖 Forge 的 legacy 等距变换（{@code scale(10) + rotate}）。
     *
     * <p>Model JSON 的 {@code display} 字段在烘焙时写入
     * {@code BakedQuad.modelDisplay}，由 {@link DisplayTransformExtension}
     * 在扩展链中消费。</p>
     */
    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        switch (helper) {
            case EQUIPPED_BLOCK:
                return true;
            case BLOCK_3D:
                // 统一所有自定义渲染物品走 RenderPlayer 方块路径，
                // 避免非方块物品落入 isFull3D() / else 分支（变换不一致）
                return true;
            case INVENTORY_BLOCK:
                return false;
            default:
                return false;
        }
    }

    // ==================== renderItem ====================

    /**
     * 统一渲染入口。
     *
     * <h3>Forge 前置变换与反抵消</h3>
     * <p>Forge 在调用 {@code renderItem} 前已经做过一批 GL 变换，
     * 这些变换与 26.1 的 {@code ItemTransform.apply()} 语义不兼容。
     * 为了让后续的 {@link DisplayTransformExtension} 在干净的模型空间干活，
     * 需要先反抵消掉 Forge 的遗留变换：</p>
     *
     * <pre>{@code
     * EQUIPPED_FIRST_PERSON (第一人称，所有物品):
     *   Forge renderItemInFirstPerson: rotate(45°Y) + scale(0.4)
     *   反抵消: scale(2.5) + rotate(-45°Y) → 净效果 = I
     *   ForgeHooksClient (EQUIPPED_BLOCK=true): translate(-0.5, -0.5, -0.5)
     *   反抵消: translate(+0.5, +0.5, +0.5) → 净效果 = I
     *
     * EQUIPPED (第三人称，所有物品):
     *   ForgeHooksClient (EQUIPPED_BLOCK=true): translate(-0.5, -0.5, -0.5)
     *   反抵消: translate(+0.5, +0.5, +0.5) → 净效果 = I
     *
     * INVENTORY (所有物品 GUI):
     *   Forge: 简单 2D translate → 无 Forge 等距旋转
     *   → DisplayTransformExtension 应用 display.gui (rotate → scale)
     *   → 管线 scale(16, -16, 16) 投影到 16×16 GUI 槽位
     *   等距变换完全由 model JSON 的 display.gui 字段控制，对齐 26.1 语义
     * }</pre>
     *
     * <p>反抵消后进入干净的模型空间 [0,1]³，
     * {@code model.render(stack, phase)} → {@link UniformRenderPipeline#renderItemQuads}
     * → {@link DisplayTransformExtension} 在扩展链中应用 JSON model 的
     * {@code display} 变换（translate(-0.5) 中心偏移 → scale → rotate → translate）。</p>
     *
     * <p>模型查找通过 {@link VanillaModelManager.ModelRegistration#getRegisteredItemModel}，
     * 对方块物品会自动 fallback 到 BlockStateModel（不经独立注册表）。</p>
     */
    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        if (stack == null || stack.getItem() == null) return;
    
        // ---- 反抵消 Forge 的遗留前置变换 ----
        // ====== 状态驱动诊断日志：仅视角/物品变更时触发 ======
        int equipItemId = Item.getIdFromItem(stack.getItem());
        int equipItemDamage = stack.getItemDamage();
        boolean typeChanged = (type != lastDebugType);
        boolean itemChanged = (equipItemId != lastDebugItemId || equipItemDamage != lastDebugItemDamage);
        boolean debugLog = (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON)
                && (typeChanged || itemChanged);
        if (debugLog) {
            lastDebugType = type;
            lastDebugItemId = equipItemId;
            lastDebugItemDamage = equipItemDamage;
        }
        // ====== 打印当前模型视图矩阵 (仅状态变更时) ======
        if (debugLog) {
            FloatBuffer beforeBuf = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, beforeBuf);
            float b0 = beforeBuf.get(0), b1 = beforeBuf.get(1), b2 = beforeBuf.get(2), b3 = beforeBuf.get(3);
            float b4 = beforeBuf.get(4), b5 = beforeBuf.get(5), b6 = beforeBuf.get(6), b7 = beforeBuf.get(7);
            float b8 = beforeBuf.get(8), b9 = beforeBuf.get(9), b10 = beforeBuf.get(10), b11 = beforeBuf.get(11);
            float b12 = beforeBuf.get(12), b13 = beforeBuf.get(13), b14 = beforeBuf.get(14), b15 = beforeBuf.get(15);
            LOGGER.info(String.format("[DFXDBG] %s BEFORE counter | item=%s", type, stack.getDisplayName()));
            LOGGER.info(String.format("  M = [%+.4f %+.4f %+.4f %+.4f]", b0, b4, b8, b12));
            LOGGER.info(String.format("      [%+.4f %+.4f %+.4f %+.4f]", b1, b5, b9, b13));
            LOGGER.info(String.format("      [%+.4f %+.4f %+.4f %+.4f]", b2, b6, b10, b14));
            LOGGER.info(String.format("      [%+.4f %+.4f %+.4f %+.4f]", b3, b7, b11, b15));
        }
        // Forge 调用链 (GL 后乘, 先调用者在左):
        //   第一人称: rotate(45°Y) → swing_rot → scale(0.4) → [FHClient: translate(-0.5)]
        //   第三人称: [RenderPlayer] T_hand(-0.0625, 0.4375, 0.0625)
        //             → translate(0,0.1875,-0.3125) → rotate(20°X) → rotate(45°Y)
        //             → scale(-0.375,-0.375,0.375) → [FHClient: translate(-0.5)]
        // 反抵消 (GL 后乘逆序): 先抵消 FHClient, 再抵消上游额外变换
        if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
            // ① 反抵消 FHClient translate(-0.5)
            GL11.glTranslatef(0.5F, 0.5F, 0.5F);
            // ② 反抵消 Forge renderItemInFirstPerson (26.1 中不存在):
            //    scale(0.4) → scale(2.5)
            GL11.glScalef(2.5F, 2.5F, 2.5F);
            //    rotate(45°Y) → rotate(-45°Y)
            GL11.glRotatef(-45.0F, 0.0F, 1.0F, 0.0F);
        } else if (type == ItemRenderType.EQUIPPED) {
            // ① 反抵消 FHClient translate(-0.5)
            GL11.glTranslatef(0.5F, 0.5F, 0.5F);
            // ② 反抵消 RenderPlayer BLOCK_3D 路径 (所有物品统一, BLOCK_3D=true):
            //    scale(-0.375, -0.375, 0.375) → scale(-2.667, -2.667, 2.667)
            GL11.glScalef(-2.667F, -2.667F, 2.667F);
            //    rotate(45°Y) → rotate(-45°Y)
            GL11.glRotatef(-45.0F, 0.0F, 1.0F, 0.0F);
            //    rotate(20°X) → rotate(-20°X)
            GL11.glRotatef(-20.0F, 1.0F, 0.0F, 0.0F);
            //    translate(0,0.1875,-0.3125) → translate(0,-0.1875,0.3125)
            GL11.glTranslatef(0.0F, -0.1875F, 0.3125F);
            // ③ 反抵消 RenderPlayer line319 translate(-0.0625, 0.4375, 0.0625)
            //    该偏移从手臂骨骼旋转点移动向手部区域，但 26.1.2 的 translateToHand
            //    仅做 arm.postRender()，不包含此偏移，必须抵消才能对齐
            GL11.glTranslatef(0.0625F, -0.4375F, -0.0625F);

            // ④ 应用 26.1.2 ItemInHandLayer 标准对齐变换
            //    反抵消后矩阵空间 = ArmBone (与 26.1.2 translateToHand 对齐)
            //    补充 R(-90°X) × R(180°Y) × T(1/16, 2/16, -10/16) 对齐高版本
            //    所有物品统一走此路径，thirdperson_righthand display 数据负责具体旋转/缩放/位移
            GL11.glRotatef(-90.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
            GL11.glTranslatef(1.0F / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
        }
        if (debugLog) {
            FloatBuffer afterBuf = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, afterBuf);
            float a0 = afterBuf.get(0), a1 = afterBuf.get(1), a2 = afterBuf.get(2), a3 = afterBuf.get(3);
            float a4 = afterBuf.get(4), a5 = afterBuf.get(5), a6 = afterBuf.get(6), a7 = afterBuf.get(7);
            float a8 = afterBuf.get(8), a9 = afterBuf.get(9), a10 = afterBuf.get(10), a11 = afterBuf.get(11);
            float a12 = afterBuf.get(12), a13 = afterBuf.get(13), a14 = afterBuf.get(14), a15 = afterBuf.get(15);
            LOGGER.info(String.format("[DFXDBG] %s AFTER  counter | item=%s", type, stack.getDisplayName()));
            LOGGER.info(String.format("  M = [%+.4f %+.4f %+.4f %+.4f]", a0, a4, a8, a12));
            LOGGER.info(String.format("      [%+.4f %+.4f %+.4f %+.4f]", a1, a5, a9, a13));
            LOGGER.info(String.format("      [%+.4f %+.4f %+.4f %+.4f]", a2, a6, a10, a14));
            LOGGER.info(String.format("      [%+.4f %+.4f %+.4f %+.4f]", a3, a7, a11, a15));
        }
        
        RenderPhase phase = toRenderPhase(type, stack);
        if (phase == null) return;
    
        // ====== 诊断：phase 和 model 查找（仅状态变更时） ======
        if (debugLog) {
            LOGGER.info(String.format("[DFXDBG] Phase=%s displayKey=%s",
                    phase.name(),
                    decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension.phaseToDisplayKey(phase.name())));
        }
        // ====== 诊断结束 =======

        // getRegisteredItemModel 对方块物品有 BlockStateModel fallback
        ItemModel model = VanillaModelManager.ModelRegistration.getRegisteredItemModel(stack.getItem());
        if (model == null) {
            if (debugLog) LOGGER.info(String.format("[DFXDBG] NULL model for item=%s", stack.getDisplayName()));
            return;
        }
    
        model.render(stack, phase);
    }

    // ==================== 内部映射 ====================

    /**
     * Forge ItemRenderType → CatFrame RenderPhase。
     * ENTITY 根据是否为方块物品分别映射到 DROPPED_BLOCK_GROUND / DROPPED_ITEM_GROUND。
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
