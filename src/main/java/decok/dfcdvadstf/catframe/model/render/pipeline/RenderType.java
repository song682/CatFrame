package decok.dfcdvadstf.catframe.model.render.pipeline;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

/**
 * 渲染分组类型，对标原版 26w+ 延迟渲染管线中的 render layer / render type。
 * <p>
 * 编码"纹理图集 + 混合（solid/translucent）"分组，作为 {@link SubmitNodeStorage}
 * 命令缓冲的分组键，同时用于 {@link FeatureRenderDispatcher} flush 时的状态设置与排序。
 * <p>
 * <b>flush 顺序</b>：枚举声明顺序即 flush 顺序 —— 所有 SOLID 先于所有 TRANSLUCENT
 * （对齐原版 order(0) → solid → translucent），保证半透明正确叠加在不透明之上。
 * <p>
 * <b>关于剔除（cull）</b>：面剔除开关不编码进 RenderType，而是随每个
 * {@link RenderSubmit#disableCull} 携带（物品路径关剔除、方块路径保持），因为
 * 同一图集 + 混合分组下方块 GUI 与方块物品的剔除策略不同。flush 时按分组内提交项的
 * 剔除标志统一设置（作用域内提交项同质）。
 */
public enum RenderType {
    /** 方块图集 · 不透明（方块世界内联、方块物品手持等）。 */
    BLOCK_ATLAS_SOLID(TextureMap.locationBlocksTexture, false),
    /** 物品图集 · 不透明（普通物品手持/展示等无混合场景）。 */
    ITEM_ATLAS_SOLID(TextureMap.locationItemsTexture, false),
    /** 方块图集 · 半透明（方块 GUI、方块物品 GUI/掉落/展示框）。 */
    BLOCK_ATLAS_TRANSLUCENT(TextureMap.locationBlocksTexture, true),
    /** 物品图集 · 半透明（物品 GUI/掉落/展示框）。 */
    ITEM_ATLAS_TRANSLUCENT(TextureMap.locationItemsTexture, true);

    private final ResourceLocation atlas;
    private final boolean blend;

    RenderType(ResourceLocation atlas, boolean blend) {
        this.atlas = atlas;
        this.blend = blend;
    }

    /** 该分组绑定的纹理图集（blocks / items atlas）。 */
    public ResourceLocation atlas() {
        return atlas;
    }

    /** 该分组是否需要开启 alpha 混合（半透明）。 */
    public boolean blend() {
        return blend;
    }

    /**
     * 依据图集选择与混合需求解析 RenderType。
     *
     * @param blockAtlas true=使用 blocks atlas，false=使用 items atlas
     * @param translucent true=需要混合（半透明），false=不透明
     */
    public static RenderType of(boolean blockAtlas, boolean translucent) {
        if (blockAtlas) {
            return translucent ? BLOCK_ATLAS_TRANSLUCENT : BLOCK_ATLAS_SOLID;
        }
        return translucent ? ITEM_ATLAS_TRANSLUCENT : ITEM_ATLAS_SOLID;
    }
}
