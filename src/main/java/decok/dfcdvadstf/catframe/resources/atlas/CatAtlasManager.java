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
 * CatFrame texture collection manager — vanilla output end and missing-icon
 * fallback (per the final three-domain rendering decision: item/block texture
 * stitching is managed by the vanilla TextureMap, and the in-house CatAtlas
 * blocks/items stitching chain is retired — see
 * "渲染三域架构-收集分流方案.md" stage A).
 * <p>
 * Responsibilities are narrowed to two:
 * <ol>
 *   <li>{@link #registerDefinedSprites(TextureMap)} — during
 *       {@code TextureStitchEvent.Pre}, key-transform data-driven collections
 *       (the source output of {@code atlases/<id>.json}) and {@code registerIcon}
 *       them into the vanilla blocks / items atlas; the vanilla stitcher performs
 *       layout + upload. Only <b>plain refs</b> are expressible (spriteId == resource
 *       and no pixel transform); definition output carrying unstitch /
 *       paletted_permutations pixel transforms cannot be expressed by vanilla
 *       and is parked with a log entry;</li>
 *   <li>{@link #getMissingIcon(String)} — the final fallback for missing lookups:
 *       returns the vanilla missingImage directly (purple-black square, matching
 *       the missing semantics of vanilla {@code TextureMap.getAtlasSprite}).</li>
 * </ol>
 * <p>
 * UI-domain assets (the {@code catframe:gui} atlas definition) do not pass through
 * this class — they are driven by the independent
 * {@code GuiTextureStitchEvent} (Pre / On / Post) chain, see
 * {@link decok.dfcdvadstf.catframe.adapter.vanilla.model.GuiTextureStitchHandler}.
 */
@SideOnly(Side.CLIENT)
public final class CatAtlasManager {

    /** blocks atlas id (IAtlas.getAtlasName semantics; aligned with the Wiki: the vanilla
     * definition file {@code assets/minecraft/atlases/blocks.json} → atlas id {@code minecraft:blocks}). */
    public static final String BLOCK_ATLAS_ID = "minecraft:blocks";
    /** items atlas id (aligned with the Wiki: {@code atlases/items.json} → {@code minecraft:items}). */
    public static final String ITEM_ATLAS_ID = "minecraft:items";

    private CatAtlasManager() {
    }

    /**
     * [Render three-domain architecture] Vanilla output end: key-transform the
     * sprite ids produced by atlas definitions ({@code atlases/<id>.json} sources)
     * and {@code registerIcon} them into the vanilla {@link TextureMap}; the
     * vanilla stitcher performs layout + upload. Called during
     * {@code TextureStitchEvent.Pre} (type 0 → blocks atlas definition, type 1 →
     * items atlas definition), before the sprite loading loop of the vanilla
     * {@code loadTextureAtlas}.
     * <p>
     * Only <b>plain refs</b> are expressible (spriteId == resource and no pixel
     * transform); definition output carrying unstitch / paletted_permutations
     * pixel transforms or multi-atlas targets cannot be expressed by vanilla and
     * is parked with a log entry (UI-domain assets take the independent
     * {@code GuiTextureStitchEvent} chain and are not covered here).
     *
     * @param map the vanilla atlas currently being stitched (type 0 blocks / type 1 items)
     */
    public static void registerDefinedSprites(TextureMap map) {
        boolean itemAtlas = map.getTextureType() == 1;
        String atlasId = itemAtlas ? ITEM_ATLAS_ID : BLOCK_ATLAS_ID;
        // [Render three-domain architecture] Definition-driven collection is extracted as a shared component (AtlasDefinitionLoader.collectRefs)
        List<SpriteRef> refs = AtlasDefinitionLoader.collectRefs(atlasId);
        int registered = 0, parked = 0;
        for (SpriteRef ref : refs) {
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
     * Final fallback for unresolved lookups: returns the vanilla missingImage
     * (purple-black square, missingno) — matching the missing semantics of vanilla
     * {@code TextureMap.getAtlasSprite}: <b>texture not found → missingno</b>.
     *
     * @return the missingno icon (vanilla missingImage); may be null in extreme cases
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
