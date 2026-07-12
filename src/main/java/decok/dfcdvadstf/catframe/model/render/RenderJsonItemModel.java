package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.IItemStateProvider;
import decok.dfcdvadstf.catframe.model.ModelRegistry;
import decok.dfcdvadstf.catframe.model.render.extension.DisplayTransformExtension;
import net.minecraft.block.Block;
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
 *   <li><b>Block 和 ItemBlock 共用同一个模型</b>——方块物品不创建独立 IItemState，
 *       而是通过 {@link ModelRegistry#getRegisteredItemModel}
 *       的 fallback 逻辑直接从 {@code registeredBlockModels}
 *       取 BlockStateModel，包装为 BlockStateItemState。</li>
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

    /** 单例，所有物品共用（渲染逻辑委托给各 IItemStateProvider）。 */
    public static final RenderJsonItemModel INSTANCE = new RenderJsonItemModel();

    private RenderJsonItemModel() {}

    // ==================== handleRenderType ====================

    /**
     * 检查 CatFrame 是否接管该物品在指定阶段的渲染。
     *
     * <p>对于 {@link ItemBlock}：检查方块是否有 CatFrame 模型
     * （{@link ModelRegistry#hasModel(Block)}）。
     * 对于非方块物品：先获取注册的 {@link IItemStateProvider}，再将其
     * {@link IItemStateProvider#handles(RenderPhase)} 映射到 Forge 的
     * {@code handleRenderType} 返回值。</p>
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

        // 统一路径：通过 getRegisteredItemModel 获取物品模型
        // （对 ItemBlock 自动 fallback 到 BlockStateModel）
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
     * 对方块物品会自动 fallback 到 BlockStateModel（不经独立注册表）。</p>
     */
    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        if (stack == null || stack.getItem() == null) return;
    
        RenderPhase phase = toRenderPhase(type, stack);
        if (phase == null) return;

        // ---- 计算反抵消预变换 ----
        // 将反抵消变换计算为 Matrix4d 矩阵，由管线在顶点提交时统一变换
        Matrix4d preTransform = computePreTransform(type);

        // getRegisteredItemModel 对方块物品有 BlockStateModel fallback
        IItemStateProvider model = ModelRegistry.getRegisteredItemModel(stack.getItem());
        if (model == null) return;
    
        model.render(stack, phase, preTransform);
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
     * @param type  Forge 物品渲染类型
     * @return 反抵消 Matrix4d 矩阵，若无需要返回 null
     */
    @javax.annotation.Nullable
    private static Matrix4d computePreTransform(ItemRenderType type) {
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
            // Forge 调用链:
            //   [RenderPlayer] T_hand(-0.0625, 0.4375, 0.0625)
            //     → translate(0,0.1875,-0.3125) → rotate(20°X) → rotate(45°Y)
            //     → scale(-0.375,-0.375,0.375) → [FHClient: translate(-0.5)]
            // 反抵消矩阵 (同序):
            //   T(0.5) × S(-2.667,-2.667,2.667) × RY(-45) × RX(-20)
            //   × T(0,-0.1875,0.3125) × T(0.0625,-0.4375,-0.0625)
            //   × RX(-90) × RY(180) × T(1/16, 2/16, -10/16)
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

            // ③ 反抵消 RenderPlayer line319 T(-0.0625, 0.4375, 0.0625)
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
