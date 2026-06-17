package decok.dfcdvadstf.catframe.model.render;

import decok.dfcdvadstf.catframe.model.ItemModel;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

/**
 * Forge IItemRenderer 实现，将 CatFrame 的物品模型系统接入 Forge 原生渲染管线。
 * <p>
 * 替代原有 MixinItemRenderer 的全局 @WrapOperation 拦截方案，
 * 通过 {@link net.minecraftforge.client.MinecraftForgeClient#registerItemRenderer}
 * 为每个拥有 CatFrame 模型的物品逐一注册。
 * <p>
 * <b>ItemRenderType → RenderPhase 映射：</b>
 * <ul>
 *   <li>{@link ItemRenderType#ENTITY} → 不接管（返回 false），保留原版掉落物渲染</li>
 *   <li>{@link ItemRenderType#EQUIPPED} → {@link RenderPhase#ITEM_HAND_THIRD_PERSON}</li>
 *   <li>{@link ItemRenderType#EQUIPPED_FIRST_PERSON} → {@link RenderPhase#ITEM_HAND_FIRST_PERSON}</li>
 *   <li>{@link ItemRenderType#INVENTORY} → {@link RenderPhase#ITEM_GUI}</li>
 *   <li>{@link ItemRenderType#FIRST_PERSON_MAP} → 不接管（返回 false）</li>
 * </ul>
 * <p>
 * <b>ItemRendererHelper 策略：</b>
 * 返回 true 让 Forge 应用标准的 3D 方块辅助变换，与
 * {@link net.minecraft.client.renderer.RenderBlocks#renderBlockAsItem} 语义一致。
 * CatFrame 的 display transforms 在此基础上做微调。
 */
public class RenderJsonItemRenderer implements IItemRenderer {

    /** 单例，所有物品共用同一实例（实际渲染逻辑委托给各物品的 ItemModel）。 */
    public static final RenderJsonItemRenderer INSTANCE = new RenderJsonItemRenderer();

    private RenderJsonItemRenderer() {}

    // ==================== handleRenderType ====================

    /**
     * 判断 CatFrame 是否接管该物品在指定渲染类型下的渲染。
     * <p>
     * 委托给物品的 {@link ItemModel#handles(RenderPhase)}；
     * 若物品无 ItemModel 或 RenderPhase 无对应映射，返回 false（走原版路径）。
     */
    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        if (item == null || item.getItem() == null) return false;

        RenderPhase phase = toRenderPhase(type);
        if (phase == null) return false;

        ItemModel model = VanillaModelManager.ModelRegistration.getRegisteredItemModel(item.getItem());
        if (model == null) return false;

        return model.handles(phase);
    }

    // ==================== shouldUseRenderHelper ====================

    /**
     * 根据物品类型决定是否让 Forge 应用 3D 方块辅助变换。
     * <p>
     * <b>只有 ItemBlock 才返回 true</b>，非方块物品（工具、食物等）返回 false，
     * 让 Forge 走 2D 路径，避免所有物品都被渲染为类方块形式。
     * <p>
     * Forge 的变换矩阵：
     * <ul>
     *   <li><b>EQUIPPED_BLOCK = true</b>（{@code ForgeHooksClient.renderEquippedItem}）：
     *       {@code translate(-0.5, -0.5, -0.5)} — 将模型从 [0,1]³ 推到以原点为中心</li>
     *   <li><b>INVENTORY_BLOCK = true</b>（{@code ForgeHooksClient.renderInventoryItem}）：
     *       {@code translate(x-2, y+3, zLevel-3) → scale(10) → translate(1, 0.5, 1) →
     *       scale(1, 1, -1) → rotate(210° X) → rotate(45° Y) → rotate(-90° Y)}
     *       — 标准的 GUI 3D 方块定位</li>
     * </ul>
     * <p>
     * 在 {@link #renderItem} 中，EQUIPPED 路径通过 {@code glTranslatef(0.5, 0.5, 0.5)}
     * 反抵消 Forge 的 {@code translate(-0.5)} 偏移，使模型回到 [0,1]³ 原点，
     * 与 GUI display transform 的坐标系一致（参考 NEI SpawnerRenderer 模式）。
     */
    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        boolean isBlock = (item != null && item.getItem() instanceof ItemBlock);
        switch (helper) {
            case EQUIPPED_BLOCK:
            case INVENTORY_BLOCK:
                return isBlock;
            // ENTITY_ROTATION / ENTITY_BOBBING / BLOCK_3D：
            // CatFrame 不接管 ENTITY，handleRenderType 已返回 false，不会触发。
            default:
                return false;
        }
    }

    // ==================== renderItem ====================

    /**
     * 执行实际渲染。由 Forge 在 {@code handleRenderType} 返回 true 后调用。
     * <p>
     * 方块物品（ItemBlock）采用 SpawnerRenderer 的 fall-through 模式：
     * <pre>{@code
     * Forge renderEquippedItem (EQUIPPED_BLOCK=true):
     *   glPushMatrix → translate(-0.5, -0.5, -0.5) → renderItem → glPopMatrix
     *
     * 我们在 renderItem 中:
     *   EQUIPPED / EQUIPPED_FIRST_PERSON:
     *     glTranslatef(+0.5, +0.5, +0.5)  // 反抵消 Forge 的 -0.5
     *     → fall through →
     *   INVENTORY:
     *     CatFrame rendering pipeline (display transform + centered vertices)
     * }</pre>
     * <p>
     * 非方块物品（工具、食物等）EQUIPPED_BLOCK=false，Forge 走 2D 变换路径，
     * 不需要反抵消，直接渲染。
     */
    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        if (item == null || item.getItem() == null) return;

        boolean isBlock = (item.getItem() instanceof ItemBlock);

        switch (type) {
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON:
                if (isBlock) {
                    // 反抵消 Forge EQUIPPED_BLOCK=true 的 translate(-0.5, -0.5, -0.5)
                    GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                }
                this.renderCatFrameItem(item, type);
                break;
            case INVENTORY:
                if (isBlock) {
                    // 方块物品：Forge INVENTORY_BLOCK=true 已含 scale(10)+rotate+Z-flip
                    this.renderCatFrameItem(item, type);
                } else {
                    // 非方块物品：Forge 2D 路径仅做 translate，需额外补偿
                    // scale(16) 将 [0,1]³ 模型填满 16×16 GUI 槽位
                    // scale(1,1,-1) Z 轴翻转使模型正面朝向观察者
                    GL11.glScalef(16F, 16F, 16F);
                    GL11.glScalef(1F, 1F, -1F);
                    this.renderCatFrameItem(item, type);
                }
                break;
            default:
        }
    }

    /**
     * 统一的 CatFrame 渲染调度。
     * 根据渲染类型选择对应的 PublicRenderAPI 方法。
     */
    private void renderCatFrameItem(ItemStack item, ItemRenderType type) {
        switch (type) {
            case EQUIPPED:
                VanillaModelManager.PublicRenderAPI.renderItemInHand(item, false);
                break;
            case EQUIPPED_FIRST_PERSON:
                VanillaModelManager.PublicRenderAPI.renderItemInHand(item, true);
                break;
            case INVENTORY:
                VanillaModelManager.PublicRenderAPI.renderItem(item);
                break;
            default:
                break;
        }
    }

    // ==================== 内部映射 ====================

    /**
     * 将 Forge 的 {@link ItemRenderType} 映射到 CatFrame 的 {@link RenderPhase}。
     *
     * @return 对应的 RenderPhase，若无映射返回 null
     */
    private static RenderPhase toRenderPhase(ItemRenderType type) {
        if (type == null) return null;
        switch (type) {
            case EQUIPPED:
                return RenderPhase.ITEM_HAND_THIRD_PERSON;
            case EQUIPPED_FIRST_PERSON:
                return RenderPhase.ITEM_HAND_FIRST_PERSON;
            case INVENTORY:
                return RenderPhase.ITEM_GUI;
            // ENTITY 和 FIRST_PERSON_MAP 不映射
            default:
                return null;
        }
    }
}
