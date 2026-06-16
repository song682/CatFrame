package decok.dfcdvadstf.catframe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.CatFrameConfig;
import decok.dfcdvadstf.catframe.model.ItemModel;
import decok.dfcdvadstf.catframe.model.VanillaModelManager;
import decok.dfcdvadstf.catframe.model.render.RenderPhase;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    /**
     * 标记本次 renderItem(4-param) 调用是否已由 CatFrame 接管渲染。
     * 用于 glint 分支（renderItemIn2D 被调用三次），避免重复渲染。
     * 每次方法调用最前方（getItemRenderer 包裹处）重置。
     */
    private boolean catframe$renderedInHand = false;

    // ==================== 入口：重置 flag ====================

    /**
     * 包裹方法第一个调用点 {@code MinecraftForgeClient.getItemRenderer()}，
     * 在此重置渲染 flag，同时保留原始 IItemRenderer 查询结果。
     */
    @WrapOperation(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraftforge/client/MinecraftForgeClient;getItemRenderer(Lnet/minecraft/item/ItemStack;Lnet/minecraftforge/client/IItemRenderer$ItemRenderType;)Lnet/minecraftforge/client/IItemRenderer;",
                 remap = false),
        remap = false)
    private IItemRenderer catframe$resetFlagThenGetRenderer(ItemStack stack, ItemRenderType type,
                                                             Operation<IItemRenderer> original) {
        this.catframe$renderedInHand = false;
        return original.call(stack, type);
    }

    // ==================== 分支 A：IItemRenderer 路径 ====================

    /**
     * 包裹 {@code ForgeHooksClient.renderEquippedItem()}。
     * CatFrame 不注册 IItemRenderer，此路径通常不触发，但保留以防其它 mod 共存。
     */
    @WrapOperation(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraftforge/client/ForgeHooksClient;renderEquippedItem(Lnet/minecraftforge/client/IItemRenderer$ItemRenderType;Lnet/minecraftforge/client/IItemRenderer;Lnet/minecraft/client/renderer/RenderBlocks;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V",
                 remap = false),
        remap = false)
    private void catframe$wrapEquipped(ItemRenderType type, IItemRenderer customRenderer,
                                       RenderBlocks renderBlocks, EntityLivingBase entity,
                                       ItemStack stack, Operation<Void> original) {
        if (catframe$tryRenderHand(stack, entity)) return;
        original.call(type, customRenderer, renderBlocks, entity, stack);
    }

    // ==================== 分支 B：ItemBlock 3D 路径 ====================

    /**
     * 包裹 {@code RenderBlocks.renderBlockAsItem()}。
     * 此路径无额外矩阵变换，直接替换为 CatFrame 渲染即可。
     */
    @WrapOperation(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V"))
    private void catframe$wrapBlockAsItem(Block block, int meta, float brightness,
                                          Operation<Void> original,
                                          @Local(argsOnly = true) ItemStack stack,
                                          @Local(argsOnly = true) EntityLivingBase entity) {
        if (catframe$tryRenderHand(stack, entity)) return;
        original.call(block, meta, brightness);
    }

    // ==================== 分支 C：2D 图标路径（含 glint） ====================

    /**
     * 包裹 {@code renderItemIn2D()}。
     * <p>
     * 原版代码在此前已应用 2D 专属矩阵变换（translate/scale/rotate），
     * CatFrame 渲染 3D 模型前需 {@link GL11#glPopMatrix()} 清理，
     * 渲染后再 {@link GL11#glPushMatrix()} 保持栈平衡（对应 line 173 pop）。
     * <p>
     * {@code renderItemIn2D} 在同一方法中被调用三次：
     * <ol>
     *   <li>line 130 — 主渲染</li>
     *   <li>line 148 — glint pass 1</li>
     *   <li>line 155 — glint pass 2</li>
     * </ol>
     * 后两次通过 {@link #catframe$renderedInHand} flag 跳过。
     */
    @WrapOperation(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V"))
    private void catframe$wrapRenderItemIn2D(Tessellator tess, float u1, float u2, float v1, float v2,
                                             int w, int h, float depth, Operation<Void> original,
                                             @Local(argsOnly = true) ItemStack stack,
                                             @Local(argsOnly = true) EntityLivingBase entity) {
        // glint 后续 pass → 已由 CatFrame 渲染，跳过原始调用
        if (this.catframe$renderedInHand) return;

        // 主渲染（line 130）
        if (catframe$shouldRenderHand(stack, entity)) {
            this.catframe$renderedInHand = true;
            // pop line 71 push + 2D transforms，恢复干净矩阵
            GL11.glPopMatrix();
            VanillaModelManager.PublicRenderAPI.renderItemInHand(stack,
                entity == Minecraft.getMinecraft().thePlayer
                    && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0);
            GL11.glPushMatrix(); // 重新 push 供 line 173 pop
            return;
        }

        original.call(tess, u1, u2, v1, v2, w, h, depth);
    }

    // ==================== 统一帮助方法 ====================

    private static RenderPhase catframe$getPhase(EntityLivingBase entity) {
        return (entity == Minecraft.getMinecraft().thePlayer
                && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0)
            ? RenderPhase.ITEM_HAND_FIRST_PERSON
            : RenderPhase.ITEM_HAND_THIRD_PERSON;
    }

    /**
     * 判断 CatFrame 是否应接管渲染（不设置 flag，不处理 GL）。
     */
    private static boolean catframe$shouldRenderHand(ItemStack stack, EntityLivingBase entity) {
        if (stack == null || entity == null) return false;
        Item item = stack.getItem();
        if (item == null) return false;
        ItemModel model = VanillaModelManager.ModelRegistration.getRegisteredItemModel(item);
        if (model == null) return false;
        return model.handles(catframe$getPhase(entity));
    }

    /**
     * 尝试用 CatFrame 渲染手持物品。设置 flag，不处理 GL 矩阵。
     *
     * @return true=已由 CatFrame 接管
     */
    private boolean catframe$tryRenderHand(ItemStack stack, EntityLivingBase entity) {
        if (!catframe$shouldRenderHand(stack, entity) || this.catframe$renderedInHand) return false;
        this.catframe$renderedInHand = true;
        if (CatFrameConfig.shouldLogDebug()) {
            CatFrame.logger.info("[MixinItemRenderer] CatFrame rendering {} | phase={}",
                Item.itemRegistry.getNameForObject(stack.getItem()),
                catframe$getPhase(entity));
        }
        VanillaModelManager.PublicRenderAPI.renderItemInHand(stack,
            entity == Minecraft.getMinecraft().thePlayer
                && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0);
        return true;
    }
}
