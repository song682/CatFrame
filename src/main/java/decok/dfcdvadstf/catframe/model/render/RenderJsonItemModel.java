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
 * Forge {@link IItemRenderer}，将 CatFrame 模型系统接入 Forge 物品渲染管线。
 *
 * <h3>核心设计</h3>
 * <ul>
 *   <li><b>物品模型独立于方块模型</b>——物品渲染只通过
 *       {@link ModelRegistry#getRegisteredItemModel} 查找 {@code registeredItemModels}
 *       中显式注册的 {@link IItemStateProvider}（items/ItemState）。
 *       历史上曾对 ItemBlock 自动回退到 {@code registeredBlockModels} 并包装为
 *       {@code BlockStateItemState}，该机制已移除——未实现 ItemState 的方块物品
 *       退回原版物品渲染。</li>
 *   <li><b>{@link #shouldUseRenderHelper} 对 EQUIPPED_BLOCK 返回 true
 *       （所有物品，统一让 Forge 做 translate(-0.5) 前置），对 INVENTORY_BLOCK 始终返回 false</b>——
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
 *   <li>{@link ItemRenderType#EQUIPPED} → {@link RenderPhase#ITEM_HAND_THIRD_PERSON}
 *       （宿主为头部槽时细化为 {@link RenderPhase#ITEM_HEAD}——玩家头盔槽 / 怪物头顶
 *       装备槽 / 雪傀儡南瓜，见 {@code resolveHeadSlotKind}）</li>
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

    /** 单例，所有物品共用（渲染逻辑委托给各 IItemStateProvider）。 */
    public static final RenderJsonItemModel INSTANCE = new RenderJsonItemModel();

    /**
     * 当前渲染线程正在渲染的掉落物实体（仅 ENTITY 阶段，由 Forge {@code renderEntityItem}
     * 经 {@link #renderItem} 的 data[1] 传入）。客户端渲染单线程、提交与写入同栈，
     * 普通静态字段即可；非掉落物渲染（GUI / 手持 / 展示框等）恒为 null。
     */
    @javax.annotation.Nullable
    private static EntityItem currentDroppedEntity;

    /**
     * 供 QuadWriter 在掉落物阶段采样实体位置世界光照的上下文读取口。
     *
     * @return 当前掉落物实体；非掉落物阶段或上下文已清除时为 null
     */
    @javax.annotation.Nullable
    public static EntityItem getCurrentDroppedEntity() {
        return currentDroppedEntity;
    }

    /**
     * 当前渲染线程正在渲染的物品附着实体（仅附着阶段——手持 / 头部槽，由 Forge
     * {@code renderEquippedItem} / {@code ItemRenderer.renderItem} 经 {@link #renderItem}
     * 的 data[1] 传入）。客户端渲染单线程、提交与写入同栈，普通静态字段即可；
     * 非附着渲染（GUI / 掉落物 / 展示框等）恒为 null。
     * <p>
     * 供 {@code RenderPhasePolicy} 在附着阶段按附着实体（怪物 / 其他玩家 / 雪傀儡）位置
     * 采样世界光照（对标原版 {@code RenderManager.renderEntityStatic} 的实体光照贴图语义），
     * 避免按本地玩家位置取光导致的错位高亮。
     * <p>
     * The entity the item is attached to (in hand or on the head slot), visible only
     * inside {@link #renderItem}'s attached-item window; lets the brightness policy
     * sample world light at the carrier's position for mobs / other players.
     */
    @javax.annotation.Nullable
    private static EntityLivingBase currentHolderEntity;

    /**
     * 供 {@code RenderPhasePolicy} 在附着阶段（手持 / 头部槽）采样附着实体光照的上下文读取口。
     *
     * @return 当前附着实体；非附着阶段或上下文已清除时为 null
     */
    @javax.annotation.Nullable
    public static EntityLivingBase getCurrentHolderEntity() {
        return currentHolderEntity;
    }

    private RenderJsonItemModel() {}

    // ==================== handleRenderType ====================

    /**
     * 检查 CatFrame 是否接管该物品在指定阶段的渲染。
     *
     * <p>所有物品（含 {@link ItemBlock}）统一通过
     * {@link ModelRegistry#getRegisteredItemModel} 获取注册的
     * {@link IItemStateProvider}，再将其
     * {@link IItemStateProvider#handles(RenderPhase)} 映射到 Forge 的
     * {@code handleRenderType} 返回值。未显式注册物品模型的方块物品
     * 返回 false，退回原版渲染。</p>
     *
     * <p>这样，实现了 {@code handles()} 的 IItemState 可以精细控制
     * 哪些阶段由 CatFrame 接管、哪些退回原版渲染。
     * 例如 {@code handles(ITEM_GUI) = false} 的模型在 GUI 会走原版 2D，
     * 而 {@code handles(ITEM_HAND_*) = true} 的手持阶段走自定义 3D。</p>
     *
     * <p>FIRST_PERSON_MAP 不接管——地图物品专用路径。</p>
     */
    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        if (item == null || item.getItem() == null) return false;
        if (type == ItemRenderType.FIRST_PERSON_MAP) return false;

        // 统一路径：通过 getRegisteredItemModel 获取显式注册的物品模型
        // （方块物品不再自动 fallback，未注册则退回原版渲染）
        IItemStateProvider model = ModelRegistry.getRegisteredItemModel(item.getItem());
        if (model == null) return false;

        RenderPhase phase = toRenderPhase(type, item);
        if (phase == null) return false;

        return model.handles(phase);
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
     * <p>Model JSON 的 {@code display} 字段在烘焙时存入
     * {@code BlockStateModelPart.partDisplay}，由 {@link DisplayTransformExtension}
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
                // 但 ENTITY 类型除外——Forge 在 BLOCK_3D=true 时走 3D 分支
                // 预应用 scale(0.5 或 0.25)，与 display.ground 叠加导致过小
                return type != ItemRenderType.ENTITY;
            case ENTITY_ROTATION:
                // 掉落物 Y 轴旋转动画（spin）
                // Forge renderEntityItem 会 glRotatef(rotation, 0, 1, 0)，
                // 对齐 26.1.2 GroundItemTransforms 的 spin 旋转语义
                return type == ItemRenderType.ENTITY;
            case ENTITY_BOBBING:
                // 返回 true = 保留 Forge 的 bobbing 浮动效果（世界级别）
                // display.ground.translation 负责模型级别的垂直偏移
                return type == ItemRenderType.ENTITY;
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
     * <p>模型查找通过 {@link ModelRegistry#getRegisteredItemModel}，
     * 只返回显式注册的物品模型；未注册的方块物品退回原版渲染。</p>
     */
    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        if (stack == null || stack.getItem() == null) return;
    
        RenderPhase phase = toRenderPhase(type, stack);
        if (phase == null) return;

        // ---- 提取手持实体 / 掉落物实体（data[0]=renderBlocks, data[1]=entity） ----
        EntityLivingBase entity = null;
        EntityItem droppedEntity = null;
        if (data != null && data.length > 1) {
            if (data[1] instanceof EntityLivingBase) {
                entity = (EntityLivingBase) data[1];
            } else if ((phase == RenderPhase.DROPPED_ITEM_GROUND
                    || phase == RenderPhase.DROPPED_BLOCK_GROUND)
                    && data[1] instanceof EntityItem) {
                // Forge renderEntityItem 传入的 EntityItem（非 EntityLivingBase）：
                // 建立掉落物亮度上下文，供 QuadWriter 写入顶点时采样实体位置世界光照，
                // 使夜间/无光源处掉落物与环境同暗（对标附着阶段 holderBrightness 语义）。
                droppedEntity = (EntityItem) data[1];
            }
        }

        // ---- 头部槽细化（EQUIPPED 且命中头部槽判定时替换为 ITEM_HEAD） ----
        // 1.7.10 三处头部宿主（玩家头盔槽 / 怪物头顶装备槽 / 雪傀儡南瓜）均以
        // EQUIPPED 类型到达；判定依据宿主渲染器与 heldItem 引用（见 resolveHeadSlotKind）。
        if (type == ItemRenderType.EQUIPPED && resolveHeadSlotKind(entity, stack) != null) {
            phase = RenderPhase.ITEM_HEAD;
        }

        // ---- 计算反抵消预变换 ----
        // 将反抵消变换计算为 Matrix4d 矩阵，由管线在顶点提交时统一变换
        Matrix4d preTransform = computePreTransform(type, entity, stack);

        // ---- 附着实体上下文（手持 / 头部槽阶段） ----
        // 与掉落实体同构：供 RenderPhasePolicy 在冲刷期按附着实体（怪物 / 其他玩家 / 雪傀儡）
        // 位置采样光照；固定取本地玩家位置光会让远处实体的附着物品呈现错位高亮。
        EntityLivingBase holderEntity = phase.isAttachedItemPhase() ? entity : null;

        // getRegisteredItemModel 只返回显式注册的物品模型（无方块 fallback）
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

    // ==================== 内部映射 ====================

    /**
     * 计算反抵消预变换矩阵。
     * <p>
     * Forge 在调用 {@code renderItem} 前已经做过一批 GL 变换（见 {@link #shouldUseRenderHelper}），
     * 这些变换与 26.1 的 {@code ItemTransform.apply()} 语义不兼容。
     * 本方法将反抵消操作计算为 {@link Matrix4d} 矩阵，
     * 由管线在顶点提交时应用于顶点坐标。
     * <p>
     * 矩阵构造顺序与 GL 后乘顺序一致：先调用的操作对应最终矩阵左乘。
     *
     * @param type   Forge 物品渲染类型
     * @param entity 附着物品的实体（可能为 null，用于 EQUIPPED 阶段判断头部槽 /
     *               RenderWitch 宿主 / RenderBiped vs RenderPlayer 分流）
     * @param stack  物品栈（用于 EQUIPPED 阶段判断物品类型与头部槽归属）
     * @return 反抵消 Matrix4d 矩阵，若无需要返回 null
     */
    @javax.annotation.Nullable
    private static Matrix4d computePreTransform(ItemRenderType type,
                                                 @javax.annotation.Nullable EntityLivingBase entity,
                                                 @javax.annotation.Nullable ItemStack stack) {
        if (type == ItemRenderType.INVENTORY) {
            // GUI：将模型空间 [0,1]³ 映射到 16×16 像素 GUI 槽位
            // Forge 已经设置了 glTranslatef(x, y, -3+zLevel) 定位到槽位原点
            // 需要将 display-centered 顶点 [-0.5,0.5]³ 映射到 [0,16]×[16,0]×[-8,8] 像素空间
            // 变换: T(8,8,0) × S(16,-16,16)
            // 与 display transform T(-0.5) 组合后:
            //   v' = T(8,8,0) × S(16,-16,16) × T(-0.5) × v
            //   v(0,0,0) → (0, 16, -8) 左上角
            //   v(1,1,1) → (16, 0, 8)  右下角
            Matrix4d m = new Matrix4d();
            m.setIdentity();
            Matrix4d tmp = new Matrix4d();

            // 先平移 (8, 8, 0)
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(8.0, 8.0, 0.0));
            m.mul(tmp);

            // 再缩放 (16, -16, 16) — 翻转 Y 使 GUI Y-down 匹配模型 Y-up
            tmp.setIdentity();
            tmp.m00 = 16.0; tmp.m11 = -16.0; tmp.m22 = 16.0;
            m.mul(tmp);

            return m;
        } else if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
            // Forge 调用链: rotate(45°Y) → swing_rot → scale(0.4) → [FHClient: translate(-0.5)]
            // 反抵消矩阵: T(0.5) × S(2.5) × RY(-45)
            // 构造顺序与 GL 同序（后乘）：先 T 再 S 再 R
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
            // 附着物品：先按宿主渲染器分流头部槽 / 女巫，再按实体 + 物品类型分流手持
            Render hostRenderer = getHostRenderer(entity);

            // ① 头部槽（玩家头盔槽 / 怪物头顶装备槽 / 雪傀儡南瓜）：
            //    1.7.10 三处宿主均以 EQUIPPED 类型到达（见 resolveHeadSlotKind 判定），
            //    变换链与手持完全不同，需独立反抵消 + 26.1 头部层对齐。
            HeadSlotKind headKind = resolveHeadSlotKind(entity, stack);
            if (headKind == HeadSlotKind.SNOWMAN) {
                return computeSnowGolemHeadPreTransform();
            } else if (headKind == HeadSlotKind.NORMAL) {
                return computeHeadPreTransform();
            }

            // ② RenderWitch：女巫手持物品（RenderWitch.renderEquippedItems L52-128）。
            //    必须在 isPlayer / ItemBlock 判定之前分流——女巫是独立宿主链
            //    （extends RenderLiving，非 RenderBiped）：hand offset 不同
            //    （T(-0.0625,0.53125,0.21875)）、ItemBlock 分支无 is3D 保底
            //    且缩放 X 符号相反、尾部额外 RX(-15) × RZ(40)。
            if (hostRenderer instanceof RenderWitch) {
                return computeWitchPreTransform(stack != null ? stack.getItem() : null);
            }

            // 手持物品实体 + 物品类型判断
            // RenderPlayer 对所有物品统一走 BLOCK_3D 路径（因为 is3D=true）
            // RenderBiped 对非 ItemBlock 物品走物品特定路径（弓/Full3D/默认）
            boolean isPlayer = (entity instanceof EntityPlayer);
            boolean isBlockItem = (stack != null && stack.getItem() instanceof ItemBlock);

            if (isPlayer || isBlockItem) {
                // RenderPlayer 路径：所有物品走 BLOCK_3D
                // RenderBiped + ItemBlock：也走 BLOCK_3D
                return computePlayerBlock3DPreTransform();
            } else {
                // RenderBiped 路径：非方块物品走 RenderBiped 专用变换
                Item item = stack != null ? stack.getItem() : null;
                if (item == Items.bow) {
                    // RenderBiped bow 路径 (lines 278-286):
                    //   T(0,0.125,0.3125) × RY(-20) × S(0.625,-0.625,0.625)
                    //   × RX(-100) × RY(45) × FHClient T(-0.5)
                    return computeBipedBowPreTransform();
                } else if (item != null && item.isFull3D()) {
                    // RenderBiped isFull3D 路径 (lines 287-301):
                    //   [rotateAround RZ(180) × T(0,-0.125,0)] × func_82422_c()
                    //   × S(0.625,-0.625,0.625) × RX(-100) × RY(45) × FHClient T(-0.5)
                    // func_82422_c 偏移按宿主渲染器分流：骷髅重写为 T(0.09375,0.1875,0)；
                    // 钓竿 / 胡萝卜钓竿（shouldRotateAroundWhenRendering）额外插入中括号段。
                    return computeBipedFull3DPreTransform(hostRenderer instanceof RenderSkeleton,
                            item.shouldRotateAroundWhenRendering());
                } else {
                    // RenderBiped 默认 2D 路径 (lines 302-310):
                    //   T(0.25,0.1875,-0.1875) × S(0.375) × RZ(60) × RX(-90) × RZ(20)
                    //   × FHClient T(-0.5)
                    return computeBipedDefaultPreTransform();
                }
            }
        } else if (type == ItemRenderType.ENTITY) {
            // Forge renderEntityItem 在 BLOCK_3D=false 时走 else 分支
            // 预应用 scale(0.5, 0.5, 0.5)。反抵消: scale(2.0)
            Matrix4d s = new Matrix4d();
            s.setIdentity();
            s.m00 = 2.0; s.m11 = 2.0; s.m22 = 2.0;
            return s;
        }
        return null;
    }

    // ==================== EQUIPPED 反抵消子方法（按实体/物品类型分流） ====================

    /**
     * 头部槽归属类型（{@link #resolveHeadSlotKind} 判定结果）。
     */
    private enum HeadSlotKind {
        /** 玩家头盔槽 / 怪物头顶装备槽（RenderPlayer L197-211 / RenderBiped L203-223）。 */
        NORMAL,
        /** 雪傀儡南瓜（RenderSnowMan L36-60），尾部缩放 Z 符号与 NORMAL 相反。 */
        SNOWMAN
    }

    /**
     * 获取实体当前使用的宿主渲染器，用于精确判定头部槽 / 女巫 / 骷髅分支
     * （不按实体类型猜测——同一实体类可能被不同渲染器接管）。
     *
     * @param entity 附着物品的实体，可为 null
     * @return 宿主 {@link Render}；实体为 null 或渲染管理器未初始化时返回 null
     */
    @javax.annotation.Nullable
    private static Render getHostRenderer(@javax.annotation.Nullable EntityLivingBase entity) {
        if (entity == null || RenderManager.instance == null) return null;
        return RenderManager.instance.getEntityRenderObject(entity);
    }

    /**
     * 判定 EQUIPPED 渲染是否来自实体头部槽（玩家头盔槽 / 怪物头顶装备槽 / 雪傀儡南瓜）。
     * <p>
     * 1.7.10 三处头部宿主均以 EQUIPPED 类型调用 {@code itemRenderer.renderItem}：
     * 玩家头盔槽（{@code RenderPlayer} L197-211，仅 ItemBlock）、怪物头顶装备槽
     * （{@code RenderBiped} L203-223，仅 ItemBlock）、雪傀儡南瓜（{@code RenderSnowMan}
     * L36-60，恒为南瓜 ItemBlock）。头部槽与手持共用同一入口，只能通过宿主渲染器
     * 与堆引用区分：头部槽传入的 stack 恒为 ItemBlock 且与 {@code getHeldItem()}
     * 不是同一实例（怪物头顶槽传 {@code func_130225_q(3)} 槽位栈、玩家传头盔槽栈、
     * 雪傀儡传新建南瓜栈）。头颅（Items.skull）走 TileEntitySkullRenderer 独立路径、
     * 盔甲走装备模型路径，均不经过物品渲染入口，此处天然排除。
     *
     * @param entity 附着物品的实体，可为 null
     * @param stack  本次渲染的物品栈
     * @return 头部槽类型；非头部槽（含手持）返回 null
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
     * 头部槽反抵消（玩家头盔槽 / 怪物头顶装备槽）+ 26.1 对齐。
     * <p>
     * 反抵消 1.7.10 头部槽变换链（{@code RenderPlayer} L204-207 =
     * {@code RenderBiped} L217-219）：
     *   T(0,-0.25,0) × RY(90) × S(0.625,-0.625,-0.625) × FHClient T(-0.5)
     * <p>
     * 26.1 对齐：{@code CustomHeadLayer} 的头部物品姿态（display 取 head）：
     *   T(0,-0.25,0) × RY(180) × S(0.625,-0.625,-0.625)
     */
    private static Matrix4d computeHeadPreTransform() {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // ① 反抵消 FHClient translate(-0.5)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // ② 反抵消 1.7.10 头部槽链:
        //    S(1.6,-1.6,-1.6) 逆 S(0.625,-0.625,-0.625)
        tmp.setIdentity();
        tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = -1.6;
        m.mul(tmp);

        //    RY(-90) 逆 RY(90)
        tmp.rotY(Math.toRadians(-90));
        m.mul(tmp);

        //    T(0,0.25,0) 逆 T(0,-0.25,0)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0, 0.25, 0));
        m.mul(tmp);

        // ③ 应用 26.1 CustomHeadLayer 头部物品姿态
        //    （中段 T(0,0.25,0) 与 T(0,-0.25,0) 相消，保留完整段以便审计）：
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
     * 雪傀儡头部槽反抵消（南瓜）+ 26.1 对齐。
     * <p>
     * 反抵消 1.7.10 雪傀儡变换链（{@code RenderSnowMan} L49-55，尾部缩放 Z 为正）：
     *   T(0,-0.34375,0) × RY(90) × S(0.625,-0.625,+0.625) × FHClient T(-0.5)
     * <p>
     * 26.1 对齐：{@code SnowGolemHeadLayer} 的南瓜姿态（Z 缩放为负）：
     *   T(0,-0.34375,0) × RY(180) × S(0.625,-0.625,-0.625)
     */
    private static Matrix4d computeSnowGolemHeadPreTransform() {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // ① 反抵消 FHClient translate(-0.5)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // ② 反抵消 1.7.10 雪傀儡链:
        //    S(1.6,-1.6,+1.6) 逆 S(0.625,-0.625,+0.625)
        tmp.setIdentity();
        tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = 1.6;
        m.mul(tmp);

        //    RY(-90) 逆 RY(90)
        tmp.rotY(Math.toRadians(-90));
        m.mul(tmp);

        //    T(0,0.34375,0) 逆 T(0,-0.34375,0)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0, 0.34375, 0));
        m.mul(tmp);

        // ③ 应用 26.1 SnowGolemHeadLayer 南瓜姿态
        //    （中段 T(0,0.34375,0) 与 T(0,-0.34375,0) 相消，保留完整段以便审计）：
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
     * RenderWitch 独立宿主链反抵消 + 26.1 对齐。
     * <p>
     * RenderWitch 继承 {@code RenderLiving}（非 RenderBiped），手持物品链独立
     * （{@code RenderWitch} L52-128）：
     *   T(-0.0625,0.53125,0.21875) × [分支链] × RX(-15) × RZ(40) × FHClient T(-0.5)
     * 分支链按物品类型（方块判定仅 {@code RenderBlocks.renderItemIn3d}、无 is3D 保底，
     * 且 ItemBlock 分支缩放 X 符号与 RenderBiped 相反）：
     *   方块 T(0,0.1875,-0.3125) × RX(20) × RY(45) × S(0.375,-0.375,0.375)；
     *   弓 / Full3D（同 RenderBiped 对应分支）/ 默认 2D。
     * <p>
     * 26.1 对齐：{@code WitchItemLayer} 的鼻端持药姿态静态段
     *   T(0.0625,0.25,0) × RZ(180) × RX(140) × RZ(10) × RX(180)。
     * （非药水物品 26.1 走 CrossedArms 姿态，其枢轴不同于 1.7.10 鼻子枢轴、
     * 无法用静态矩阵反抵消，此处统一采用鼻端姿态。）
     *
     * @param item 女巫手持物品（可为 null，null 时走默认 2D 分支）
     */
    private static Matrix4d computeWitchPreTransform(@javax.annotation.Nullable Item item) {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // ① 反抵消 FHClient translate(-0.5)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // ② 反抵消尾部 RX(-15) × RZ(40)（RZ 先于 RX 作用于顶点）:
        //    RZ(-40) 逆 RZ(40)
        tmp.rotZ(Math.toRadians(-40));
        m.mul(tmp);

        //    RX(15) 逆 RX(-15)
        tmp.rotX(Math.toRadians(15));
        m.mul(tmp);

        // ③ 反抵消分支链（RenderWitch lines 74-115）:
        if (item instanceof ItemBlock
                && RenderBlocks.renderItemIn3d(Block.getBlockFromItem(item).getRenderType())) {
            //    方块路径: T(0,0.1875,-0.3125) × RX(20) × RY(45) × S(0.375,-0.375,0.375)
            //    S(2.667,-2.667,2.667) 逆 S(0.375,-0.375,0.375)
            tmp.setIdentity();
            double invScale = 1.0 / 0.375; // ≈ 2.667
            tmp.m00 = invScale; tmp.m11 = -invScale; tmp.m22 = invScale;
            m.mul(tmp);

            //    RY(-45) 逆 RY(45)
            tmp.rotY(Math.toRadians(-45));
            m.mul(tmp);

            //    RX(-20) 逆 RX(20)
            tmp.rotX(Math.toRadians(-20));
            m.mul(tmp);

            //    T(0,-0.1875,0.3125) 逆 T(0,0.1875,-0.3125)
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(0, -0.1875, 0.3125));
            m.mul(tmp);
        } else if (item == Items.bow) {
            //    弓路径: T(0,0.125,0.3125) × RY(-20) × S(0.625,-0.625,0.625)
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
            //    Full3D 路径: [rotateAround RZ(180) × T(0,-0.125,0)] × func_82410_b()
            //    × S(0.625,-0.625,0.625) × RX(-100) × RY(45)
            tmp.rotY(Math.toRadians(-45));
            m.mul(tmp);

            tmp.rotX(Math.toRadians(100));
            m.mul(tmp);

            tmp.setIdentity();
            tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = 1.6;
            m.mul(tmp);

            //    T(0,-0.1875,0) 逆 func_82410_b T(0,0.1875,0)
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(0, -0.1875, 0));
            m.mul(tmp);

            //    rotateAround 逆（钓竿 / 胡萝卜钓竿）：原版在 func 之前追加
            //    RZ(180) × T(0,-0.125,0)，逆序在 func 逆之后插入 T(0,0.125,0) × RZ(180)
            if (item.shouldRotateAroundWhenRendering()) {
                tmp.setIdentity();
                tmp.setTranslation(new Vector3d(0, 0.125, 0));
                m.mul(tmp);

                tmp.rotZ(Math.toRadians(180));
                m.mul(tmp);
            }
        } else {
            //    默认 2D 路径: T(0.25,0.1875,-0.1875) × S(0.375) × RZ(60) × RX(-90) × RZ(20)
            tmp.rotZ(Math.toRadians(-20));
            m.mul(tmp);

            tmp.rotX(Math.toRadians(90));
            m.mul(tmp);

            tmp.rotZ(Math.toRadians(-60));
            m.mul(tmp);

            //    S(2.667) 逆 S(0.375) — 均匀缩放
            tmp.setIdentity();
            double invScale = 1.0 / 0.375; // ≈ 2.667
            tmp.m00 = invScale; tmp.m11 = invScale; tmp.m22 = invScale;
            m.mul(tmp);

            //    T(-0.25,-0.1875,0.1875) 逆 T(0.25,0.1875,-0.1875)
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(-0.25, -0.1875, 0.1875));
            m.mul(tmp);
        }

        // ④ 反抵消 hand offset T(-0.0625, 0.53125, 0.21875)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, -0.53125, -0.21875));
        m.mul(tmp);

        // ⑤ 应用 26.1 WitchItemLayer 鼻端持药姿态静态段:
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
     * RenderPlayer（或 RenderBiped + ItemBlock）的 BLOCK_3D 路径反抵消 + 26.1 对齐。
     * <p>
     * 反抵消 RenderPlayer BLOCK_3D 变换链：
     *   T(-0.0625,0.4375,0.0625) × T(0,0.1875,-0.3125) × RX(20) × RY(45)
     *   × S(-0.375,-0.375,0.375) × FHClient T(-0.5)
     */
    private static Matrix4d computePlayerBlock3DPreTransform() {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // ① 反抵消 FHClient translate(-0.5)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // ② 反抵消 RenderPlayer BLOCK_3D 路径
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

        // ③ 反抵消 hand offset T(-0.0625, 0.4375, 0.0625)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, -0.4375, -0.0625));
        m.mul(tmp);

        // ④ 应用 26.1.2 ItemInHandLayer 标准对齐变换
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
     * RenderBiped bow 路径反抵消 + 26.1 对齐。
     * <p>
     * RenderBiped 对 {@code Items.bow} 的变换链 (lines 278-286)：
     *   T(-0.0625,0.4375,0.0625) × T(0,0.125,0.3125) × RY(-20)
     *   × S(0.625,-0.625,0.625) × RX(-100) × RY(45) × FHClient T(-0.5)
     * <p>
     * 反抵消矩阵（后乘序）：
     *   T(0.5) × RY(-45) × RX(100) × S(1.6,-1.6,1.6) × RY(20)
     *   × T(0,-0.125,-0.3125) × T(0.0625,-0.4375,-0.0625)
     *   × RX(-90) × RY(180) × T(1/16, 2/16, -10/16)
     */
    private static Matrix4d computeBipedBowPreTransform() {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // ① 反抵消 FHClient translate(-0.5)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // ② 反抵消 bow 路径:
        //    RY(-45) 逆 RY(45)
        tmp.rotY(Math.toRadians(-45));
        m.mul(tmp);

        //    RX(100) 逆 RX(-100)
        tmp.rotX(Math.toRadians(100));
        m.mul(tmp);

        //    S(1.6,-1.6,1.6) 逆 S(0.625,-0.625,0.625)
        tmp.setIdentity();
        tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = 1.6;
        m.mul(tmp);

        //    RY(20) 逆 RY(-20)
        tmp.rotY(Math.toRadians(20));
        m.mul(tmp);

        //    T(0,-0.125,-0.3125) 逆 T(0,0.125,0.3125)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0, -0.125, -0.3125));
        m.mul(tmp);

        // ③ 反抵消 hand offset T(-0.0625, 0.4375, 0.0625)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, -0.4375, -0.0625));
        m.mul(tmp);

        // ④ 26.1.2 对齐
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
     * RenderBiped isFull3D 路径反抵消 + 26.1 对齐。
     * <p>
     * RenderBiped 对 {@code isFull3D()} 物品的变换链 (lines 287-301)：
     *   T(-0.0625,0.4375,0.0625) × [RZ(180) × T(0,-0.125,0)] × func_82422_c()
     *   × S(0.625,-0.625,0.625) × RX(-100) × RY(45) × FHClient T(-0.5)
     * <p>
     * func_82422_c() 被宿主渲染器重写：基类 RenderBiped 为 T(0,0.1875,0)，
     * {@code RenderSkeleton}（骷髅）重写为 T(0.09375,0.1875,0)。
     * 中括号段仅当 {@code shouldRotateAroundWhenRendering()}
     * （钓竿 / 胡萝卜钓竿）时存在。
     *
     * @param skeletonRenderer 宿主是否骷髅渲染器（决定 func_82422_c 的 X 偏移）
     * @param rotateAround     物品是否 shouldRotateAroundWhenRendering（钓竿类）
     */
    private static Matrix4d computeBipedFull3DPreTransform(boolean skeletonRenderer, boolean rotateAround) {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // ① 反抵消 FHClient
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // ② 反抵消 Full3D 路径:
        //    RY(-45) 逆 RY(45)
        tmp.rotY(Math.toRadians(-45));
        m.mul(tmp);

        //    RX(100) 逆 RX(-100)
        tmp.rotX(Math.toRadians(100));
        m.mul(tmp);

        //    S(1.6,-1.6,1.6) 逆 S(0.625,-0.625,0.625)
        tmp.setIdentity();
        tmp.m00 = 1.6; tmp.m11 = -1.6; tmp.m22 = 1.6;
        m.mul(tmp);

        //    func_82422_c 逆：基类 T(0,0.1875,0)；骷髅重写为 T(0.09375,0.1875,0)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(skeletonRenderer ? -0.09375 : 0.0, -0.1875, 0));
        m.mul(tmp);

        //    rotateAround 逆（钓竿 / 胡萝卜钓竿）：原版在 func_82422_c 之前追加
        //    RZ(180) × T(0,-0.125,0)，逆序在 func 逆之后插入 T(0,0.125,0) × RZ(180)
        if (rotateAround) {
            tmp.setIdentity();
            tmp.setTranslation(new Vector3d(0, 0.125, 0));
            m.mul(tmp);

            tmp.rotZ(Math.toRadians(180));
            m.mul(tmp);
        }

        // ③ 反抵消 hand offset
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, -0.4375, -0.0625));
        m.mul(tmp);

        // ④ 26.1.2 对齐
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
     * RenderBiped 默认 2D 路径反抵消 + 26.1 对齐。
     * <p>
     * RenderBiped 默认 2D 物品的变换链 (lines 302-310)：
     *   T(-0.0625,0.4375,0.0625) × T(0.25,0.1875,-0.1875) × S(0.375)
     *   × RZ(60) × RX(-90) × RZ(20) × FHClient T(-0.5)
     */
    private static Matrix4d computeBipedDefaultPreTransform() {
        Matrix4d m = new Matrix4d();
        m.setIdentity();
        Matrix4d tmp = new Matrix4d();

        // ① 反抵消 FHClient
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.5, 0.5, 0.5));
        m.mul(tmp);

        // ② 反抵消默认 2D 路径:
        //    RZ(-20) 逆 RZ(20)
        tmp.rotZ(Math.toRadians(-20));
        m.mul(tmp);

        //    RX(90) 逆 RX(-90)
        tmp.rotX(Math.toRadians(90));
        m.mul(tmp);

        //    RZ(-60) 逆 RZ(60)
        tmp.rotZ(Math.toRadians(-60));
        m.mul(tmp);

        //    S(2.667) 逆 S(0.375) — 均匀缩放
        tmp.setIdentity();
        double invScale = 1.0 / 0.375; // ≈ 2.667
        tmp.m00 = invScale; tmp.m11 = invScale; tmp.m22 = invScale;
        m.mul(tmp);

        //    T(-0.25,-0.1875,0.1875) 逆 T(0.25,0.1875,-0.1875)
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(-0.25, -0.1875, 0.1875));
        m.mul(tmp);

        // ③ 反抵消 hand offset
        tmp.setIdentity();
        tmp.setTranslation(new Vector3d(0.0625, -0.4375, -0.0625));
        m.mul(tmp);

        // ④ 26.1.2 对齐
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
