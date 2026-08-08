package decok.dfcdvadstf.catframe.model.state.item.tint;

import decok.dfcdvadstf.catframe.model.render.api.RenderPhase;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemStack;

/**
 * 刷怪蛋颜色 tint（CatFrame 扩展类型）。
 * <p>
 * 依据刷怪蛋的 damage 值（实体 ID）查 {@link EntityList.EntityEggInfo}，
 * 按 {@code index} 返回主色/副色：
 * <ul>
 *   <li>{@code index == 0}（layer0 — 蛋底纹）：primaryColor（实体主色）</li>
 *   <li>{@code index != 0}（layer1 — 斑点覆盖）：secondaryColor（实体副色）</li>
 * </ul>
 * 对齐 1.7.10 原版 {@code ItemMonsterPlacer#getColorFromItemStack}：
 * pass 0 = primaryColor，pass &gt; 0 = secondaryColor。
 * <p>
 * <b>注意</b>：Minecraft wiki 列出的 tint 类型不含 {@code spawn_egg}
 * （高版本原版通过 {@code minecraft:special} 模型渲染刷怪蛋）。此类型是 CatFrame
 * 针对 1.7.10 逐 pass 染色行为的声明式扩展，用于替代旧的硬编码 {@code SpawnEggTintProvider}。
 *
 * <p>Spawn egg color tint (CatFrame extension). Not a vanilla wiki tint type;
 * high-version vanilla renders spawn eggs via a {@code minecraft:special} model.
 * This mirrors 1.7.10 per-pass tinting (primary/secondary) declaratively.
 *
 * <p>JSON: {@code {"type": "minecraft:spawn_egg", "index": 0}}
 */
public class SpawnEggTint implements ItemTint {

    /** 颜色分量下标：0 = 主色（primaryColor），非 0 = 副色（secondaryColor）。 */
    private final int index;

    public SpawnEggTint(int index) {
        this.index = index;
    }

    @Override
    public int compute(ItemStack stack, RenderPhase phase) {
        if (stack == null) return 0xFFFFFF;

        EntityList.EntityEggInfo eggInfo =
                (EntityList.EntityEggInfo) EntityList.entityEggs.get(stack.getItemDamage());
        if (eggInfo == null) return 0xFFFFFF;

        // index 0 = layer0 (底纹) → primaryColor；否则 layer1 (斑点) → secondaryColor
        return (index == 0 ? eggInfo.primaryColor : eggInfo.secondaryColor) & 0xFFFFFF;
    }
}
