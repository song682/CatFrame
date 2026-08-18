package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.model.VanillaTextureTracker;
import decok.dfcdvadstf.catframe.resources.atlas.source.SpriteRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import javax.annotation.Nullable;

import java.util.List;

/**
 * CatFrame 纹理收集管理器 —— 原版输出端与缺失兜底（渲染三域架构定案：物品/方块纹理
 * 缝合归原版 TextureMap 管理，CatAtlas blocks/items 自研缝合链已退役，见
 * 《渲染三域架构-收集分流方案.md》阶段 A）。
 * <p>
 * 职责收敛为两项：
 * <ol>
 *   <li>{@link #registerDefinedSprites(TextureMap)} —— 在 {@code TextureStitchEvent.Pre}
 *       把数据驱动收集（{@code atlases/<id>.json} 的 sources 产物）经键变换后
 *       {@code registerIcon} 进原版 blocks / items 图集，由原版缝合器完成布局 + 上传。
 *       仅可表达<b>素引用</b>（spriteId == resource 且无像素变换）；带 unstitch /
 *       paletted_permutations 等像素变换的定义产物原版无法表达，记日志挂起；</li>
 *   <li>{@link #getMissingIcon(String)} —— 缺失查找最终兜底：
 *       直接返回原版 missingImage（紫黑格，与原版 {@code TextureMap.getAtlasSprite}
 *       缺失语义一致）。</li>
 * </ol>
 * <p>
 * UI 域素材（{@code catframe:gui} 图集定义）不经过本类 —— 由
 * {@code GuiTextureStitchEvent}（Pre / On / Post）独立事件链驱动，见
 * {@link decok.dfcdvadstf.catframe.adapter.vanilla.model.GuiTextureStitchHandler}。
 *
 * <p>Texture collection manager: definition-driven output into the vanilla
 * TextureMap plus missing-icon fallback. The CatAtlas self-stitch chain is
 * retired; UI-domain sprites are handled by the independent GuiTextureStitchEvent.
 */
@SideOnly(Side.CLIENT)
public final class CatAtlasManager {

    /** blocks 图集 id（IAtlas.getAtlasName 语义；对齐 Wiki：原版定义文件
     * {@code assets/minecraft/atlases/blocks.json} → 图集 id {@code minecraft:blocks}）。 */
    public static final String BLOCK_ATLAS_ID = "minecraft:blocks";
    /** items 图集 id（对齐 Wiki：{@code atlases/items.json} → {@code minecraft:items}）。 */
    public static final String ITEM_ATLAS_ID = "minecraft:items";

    private CatAtlasManager() {
    }

    /**
     * [渲染三域架构] 原版输出端：把图集定义（{@code atlases/<id>.json} sources）的
     * 产物 sprite id 经键变换后 {@code registerIcon} 进原版 {@link TextureMap}，
     * 由原版缝合器完成布局 + 上传。在 {@code TextureStitchEvent.Pre}
     * （type 0 → blocks 图集定义，type 1 → items 图集定义）调用，早于原版
     * {@code loadTextureAtlas} 的 sprite 加载循环。
     * <p>
     * 仅可表达<b>素引用</b>（spriteId == resource 且无像素变换）；带 unstitch /
     * paletted_permutations 等像素变换或多图集目标的定义产物原版无法表达，
     * 记日志挂起（UI 域素材走 {@code GuiTextureStitchEvent} 独立链，不在此列）。
     * <p>
     * Vanilla-backend output: definition-driven sprite ids are key-transformed
     * and registered into the vanilla TextureMap at stitch Pre; refs carrying
     * pixel transforms cannot be expressed by vanilla stitching and are parked
     * with a log entry.
     *
     * @param map 当前缝合中的原版图集（type 0 blocks / type 1 items）
     */
    public static void registerDefinedSprites(TextureMap map) {
        boolean itemAtlas = map.getTextureType() == 1;
        String atlasId = itemAtlas ? ITEM_ATLAS_ID : BLOCK_ATLAS_ID;
        // [渲染三域架构] 定义驱动收集已提取为共用件（AtlasDefinitionLoader.collectRefs）
        List<SpriteRef> refs = AtlasDefinitionLoader.collectRefs(atlasId);
        int registered = 0, parked = 0;
        for (SpriteRef ref : refs) {
            // 像素变换 / 重命名引用原版缝合无法表达 → 记日志挂起
            // Transform/rename refs are parked: vanilla stitching cannot express them
            if (ref.transform() != null || !ref.resource().equals(ref.spriteId())) {
                parked++;
                CatFrame.logger.info("[CatAtlas] vanilla backend: '{}' parked (requires CatAtlas backend: transform={})",
                        ref.spriteId(), ref.transform() != null);
                continue;
            }
            String key = VanillaTextureTracker.toVanillaKey(ref.spriteId().toString(), itemAtlas);
            if (key == null || key.isEmpty()) {
                continue;
            }
            map.registerIcon(key);
            VanillaTextureTracker.trackRegisteredKey(key, itemAtlas);
            registered++;
        }
        CatFrame.logger.info("[CatAtlas] vanilla backend feed: atlas='{}' registered={} parked={}",
                atlasId, registered, parked);
    }

    /**
     * 缺失查找最终兜底：返回原版 missingImage（紫黑格，missingno）—— 与原版
     * {@code TextureMap.getAtlasSprite} 的缺失语义一致：<b>纹理找不着 → missingno</b>。
     *
     * <p>Final fallback for unresolved textures: the vanilla missing image
     * (purple-black square), matching vanilla missing semantics.
     *
     * @return missingno icon（原版 missingImage）；极端情况下可为 null
     */
    @Nullable
    public static IIcon getMissingIcon(String texturePath) {
        try {
            return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("missingno");
        } catch (Exception e) {
            return null;
        }
    }
}
