package decok.dfcdvadstf.catframe.model.render.pipeline;

import decok.dfcdvadstf.catframe.model.render.RenderJsonItemModel;
import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;

/**
 * 渲染阶段 → 默认亮度计算工具（pipeline 内部，配合扩展链使用）。
 * <p>
 * 收编原 {@link QuadWriter} 中按 {@link RenderPhase} 分支内联的亮度政策计算：
 * GUI 恒 255 / 手持取玩家位置光 / 掉落取实体位置光 / 方块取世界混合亮度，
 * 语义逐位复刻（无状态纯函数）。
 * <b>消费方（B' 形态）</b>：光政策统一居住于扩展链 —— 内建
 * {@code LightPolicyExtension}（extension 包）在 beforePart 计算物品提交级亮度、
 * 在 apply 对 AO 退化 quad 现算方块亮度，均经本工具取值后写
 * {@code ctx.brightnessOverride}；QuadWriter 仅在
 * {@link RenderSubmit#baselineBrightness} = -1 且扩展链未写 override 时回退调用
 * 本工具（链缺失安全网）。GL 光照模式判定（{@link #isItemGlLit}）由
 * FeatureRenderDispatcher / QuadWriter 直接调用本类，与亮度政策同源。
 * <p>
 * 时点前提（write 期采样语义的依据）：物品渲染路径一律在 RenderDispatcher 的
 * beginScope/endScope 内提交，endScope 触发 flush 于同一调用栈，且处于
 * RenderJsonItemModel 掉落实体 thread-local 窗口内 —— 冲刷期（扩展 write 期）
 * 读取的实体/玩家上下文与提交期相同（GUI 场景亮度恒 255，实体同帧位置冻结）。
 * <p>
 * Single mapping from {@link RenderPhase} to the default-brightness computation,
 * consumed by the built-in LightPolicyExtension and by QuadWriter as the
 * chain-missing fallback; the GL-lighting mode judgment ({@link #isItemGlLit})
 * lives here as the single source for FeatureRenderDispatcher and QuadWriter.
 */
public final class RenderPhasePolicy {

    private RenderPhasePolicy() {
    }

    /** 全亮 packed brightness（sky=15, block=15）。Full-bright packed light. */
    private static final int FULL_BRIGHT = 15728880;

    /**
     * 解析渲染阶段的基础亮度（packed sky<<20 | block<<4 格式；GUI 阶段为 255 屏幕光语义）。
     * <p>
     * 逐 phase 语义（迁移自 QuadWriter 原分支，逐位一致）：
     * <ul>
     *   <li>{@code ITEM_GUI} → 255（屏幕空间无环境光，恒定全亮）；</li>
     *   <li>手持阶段（{@link RenderPhase#isHandPhase()}）→ 玩家位置世界光
     *       （完全无外部光照时全黑；玩家/世界缺失回退全亮）；</li>
     *   <li>{@code BLOCK_WORLD} / {@code BLOCK_DESTROY} → 方块位置世界混合亮度
     *       （无世界上下文回退 0）；</li>
     *   <li>其余物品阶段（掉落物 / 展示框）→ 掉落物实体位置世界光
     *       （无实体上下文回退全亮，保持既有兜底行为）。</li>
     * </ul>
     * 无状态纯函数：方块相位经入参 world/x/y/z/block 计算；物品相位内部经 Minecraft
     * 玩家单例与 {@link RenderJsonItemModel} 掉落实体 thread-local 读取。
     *
     * @param phase 渲染阶段
     * @param world 世界访问（方块相位使用；物品相位忽略）
     * @param x     方块 X（物品相位忽略）
     * @param y     方块 Y
     * @param z     方块 Z
     * @param block 方块实例（方块相位使用）
     * @return packed brightness（15728880 = 全亮，0 = 全黑）
     */
    public static int baselineBrightness(RenderPhase phase,
                                         @Nullable IBlockAccess world, int x, int y, int z,
                                         @Nullable Block block) {
        // GUI：屏幕空间无环境光，恒定全亮（对标 1.7.10 RenderItem GUI 语义）
        if (phase == RenderPhase.ITEM_GUI) {
            return 255;
        }
        if (phase != null && phase.isHandPhase()) {
            return handBrightness();
        }
        if (phase == RenderPhase.BLOCK_WORLD || phase == RenderPhase.BLOCK_DESTROY) {
            // 方块相位：原 writeBlockQuads 语义 —— 有世界上下文取混合亮度，否则 0。
            // （现由 LightPolicyExtension 对 BLOCK_WORLD AO 退化 quad 在 apply 现算；
            // BLOCK_DESTROY 被 BlockDestroyExtension override 取代，此处仅兜底。）
            return (world != null && block != null)
                    ? block.getMixedBrightnessForBlock(world, x, y, z)
                    : 0;
        }
        // DROPPED_ITEM_GROUND / DROPPED_BLOCK_GROUND / ITEM_FIXED
        return droppedItemBrightness();
    }

    /**
     * 该渲染阶段是否使用 GL_LIGHTING + 逐面法线的物品光照模式（方案B）。
     * <p>
     * 语义基准 = FeatureRenderDispatcher 原私有 isItemGlLitPhase：非 BLOCK_WORLD、
     * 非 ITEM_GUI、非手持阶段（即掉落物 / 展示框）—— 这些阶段启用 GL_NORMALIZE 并
     * 由发射器按面写入法线，方向光照交予 GL 计算而非烘焙进顶点色；
     * GUI 与手持阶段维持烘焙阴影（手持对标 1.7.10 ItemRenderer glDisable(GL_LIGHTING)）。
     * <p>
     * Whether the phase renders items lit by GL_LIGHTING with per-face normals
     * (dropped / item-frame); GUI and hand phases keep baked shading instead.
     */
    public static boolean isItemGlLit(RenderPhase phase) {
        return phase != null && phase != RenderPhase.BLOCK_WORLD
                && phase != RenderPhase.ITEM_GUI && !phase.isHandPhase();
    }

    /**
     * 手持阶段的基础亮度：取玩家所在位置的世界光照（天空光 + 方块光），
     * 对标 1.7.10 {@code ItemRenderer.renderItemInFirstPerson} 的
     * {@code getLightBrightnessForSkyBlocks(玩家坐标, 0)} 语义——完全无外部
     * 光照时返回 0，手持物品渲染为全黑；玩家或世界上下文缺失时回退全亮兜底。
     * 自 QuadWriter 迁移（DBF 化：亮度政策外移至提交期）。
     * <p>
     * Base brightness for hand-held phases: world light sampled at the player's
     * position (same semantics as 1.7.10 {@code ItemRenderer}), so held items
     * render fully black when no external light is available.
     *
     * @return packed brightness（15728880 = 全亮，0 = 全黑）
     */
    private static int handBrightness() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = (mc != null) ? mc.thePlayer : null;
        if (player == null || player.worldObj == null) {
            return FULL_BRIGHT;
        }
        return player.worldObj.getLightBrightnessForSkyBlocks(
                MathHelper.floor_double(player.posX),
                MathHelper.floor_double(player.posY),
                MathHelper.floor_double(player.posZ), 0);
    }

    /**
     * 掉落物阶段的基础亮度：取掉落物实体所在位置的世界光照（天空光 + 方块光），
     * 与手持阶段 {@link #handBrightness()} 同构——夜间 / 无光源处掉落物与环境同暗，
     * 不再恒定全亮（修复"掉落物在黑暗环境相对环境高亮、注意力被吸引"）。
     * 实体上下文由 {@link RenderJsonItemModel} 在 Forge ENTITY 渲染时建立
     * （ForgeHooksClient.renderEntityItem 将 EntityItem 作为 renderItem 的 data[1] 传入）；
     * 无实体上下文（非 Forge 掉落路径的自定义调用）回退全亮，保持既有兜底行为。
     * 自 QuadWriter 迁移（DBF 化：亮度政策外移至提交期）。
     * <p>
     * Base brightness for dropped-item phases: world light sampled at the entity's
     * position, mirroring the hand-held {@link #handBrightness()} semantics, so
     * dropped items darken together with the environment instead of staying
     * full-bright; falls back to full-bright when no entity context is present.
     *
     * @return packed brightness（15728880 = 全亮，0 = 全黑）
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
