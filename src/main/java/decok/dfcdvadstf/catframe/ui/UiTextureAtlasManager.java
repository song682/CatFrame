package decok.dfcdvadstf.catframe.ui;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.resources.atlas.AtlasDefinitionLoader;
import decok.dfcdvadstf.catframe.resources.atlas.CatAtlas;
import decok.dfcdvadstf.catframe.resources.atlas.CatSprite;
import decok.dfcdvadstf.catframe.resources.atlas.CatSpriteLoader;
import decok.dfcdvadstf.catframe.resources.atlas.source.SpriteRef;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UI atlas manager driving the {@code catframe:gui} stitch lifecycle
 * (Pre collects / On stitches and uploads / Post publishes the lookup).
 * <p>
 * Subscribes to {@link GuiTextureStitchEvent} three phases to drive CatFrame's
 * custom GUI atlas (CatAtlas, pure UI utility, no mipmap):
 * <ol>
 *   <li>{@link GuiTextureStitchEvent.Pre} — Collect: {@link AtlasDefinitionLoader#collectRefs}
 *       retrieves the {@code catframe:gui} definition ({@code assets/catframe/atlases/gui.json},
 *       allows other mods to declare ownership) sources, each decoded by {@link CatSpriteLoader}
 *       into {@link CatSprite}; missing/decode failures get missingno placeholder;</li>
 *   <li>{@link GuiTextureStitchEvent.On} — Stitch: CatAtlas layout (reuses TextureStitcher) +
 *       single CPU assembly + GL upload (<b>no mipmap</b>, hard rule), registered with
 *       TextureManager as {@link #ATLAS_LOCATION} ({@code catframe:atlas/gui}), render layer
 *       {@code bindTexture} zero structural changes;</li>
 *   <li>{@link GuiTextureStitchEvent.Post} — Publish: lookup ready ({@link #isReady()}),
 *       CatFrame UI drawing can now fetch UVs (stage C consumer).</li>
 * </ol>
 * <p>
 * Trigger timing bridged by {@code GuiTextureStitchHandler}: Pre/On on vanilla
 * {@code TextureStitchEvent.Pre} (type 0), Post on {@code Post} (type 1) —
 * UI atlas is an independent GL texture, no conflict with vanilla blocks/items stitching;
 * Post ensures vanilla textures fully loaded before UI lookup ready.
 * <p>
 * UI animation (all assets currently static, mechanism reserved): client tick via
 * {@link #tickAnimations()} advances frames and region-reuploads (CatAtlas
 * glTexSubImage2D region updates).
 */
@SideOnly(Side.CLIENT)
public final class UiTextureAtlasManager {

    /** UI atlas id ({@code assets/catframe/atlases/gui.json} → {@code catframe:gui}). */
    public static final String ATLAS_ID = "catframe:gui";
    /** UI atlas GL texture registration location (TextureManager key, used by render layer bindTexture). */
    public static final ResourceLocation ATLAS_LOCATION = new ResourceLocation("catframe", "atlas/gui");

    /** Current UI atlas (null before stitch). */
    private static CatAtlas atlas;
    /** Post publish complete flag (lookup ready). */
    private static volatile boolean ready = false;
    /** Pre collection output (input to On stitch; rebuilt each stitch cycle). */
    private static List<CatSprite> pending = new ArrayList<>();

    /** Registered to event bus on instantiation (GuiTextureStitchEvent subscriber); static lookup entry directly usable. */
    public UiTextureAtlasManager() {
    }

    // ==================== GuiTextureStitchEvent three phases ====================

    /** Pre — Collect: catframe:gui definition sources → SpriteRef → CatSprite decode (missing → missingno placeholder). */
    @SubscribeEvent
    public void onPre(GuiTextureStitchEvent.Pre event) {
        List<SpriteRef> refs = AtlasDefinitionLoader.collectRefs(ATLAS_ID);
        pending = new ArrayList<>(refs.size());
        int missing = 0;
        for (SpriteRef ref : refs) {
            CatSprite sprite = CatSpriteLoader.load(
                    ref.resource(), ref.spriteId().toString(), ATLAS_ID, ref.transform());
            if (sprite == null) {
                // Texture missing/decode failed → missingno placeholder (preserves publish key integrity, matches vanilla missing semantics)
                sprite = CatSprite.missing(ATLAS_ID, ref.spriteId().toString());
                missing++;
            }
            pending.add(sprite);
        }
        ready = false;
        CatFrame.logger.info("[UiAtlas] '{}' collected {} sprites ({} missing, filled with missingno)",
                ATLAS_ID, pending.size(), missing);
    }

    /** On — Stitch: CatAtlas layout + CPU assembly + GL upload (no mipmap), register to TextureManager. */
    @SubscribeEvent
    public void onOn(GuiTextureStitchEvent.On event) {
        if (atlas == null) {
            atlas = new CatAtlas(ATLAS_ID, false);
        }
        int maxTextureSize = Math.min(GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE), 16384);
        atlas.stitch(pending, maxTextureSize);
        // ITextureObject contract: after registration, render layer bindTexture(ATLAS_LOCATION) binds the UI atlas
        Minecraft.getMinecraft().getTextureManager().loadTexture(ATLAS_LOCATION, atlas);
    }

    /** Post — Publish: lookup ready, CatFrame UI drawing can fetch UVs. */
    @SubscribeEvent
    public void onPost(GuiTextureStitchEvent.Post event) {
        ready = true;
        CatFrame.logger.info("[UiAtlas] '{}' published: {}x{} | sprites={} | location={}",
                ATLAS_ID, atlas.getAtlasWidth(), atlas.getAtlasHeight(),
                atlas.getSprites().size(), ATLAS_LOCATION);
    }

    // ==================== Query (stage C UI consumer entry) ====================

    /** Post publish complete (lookup ready). */
    public static boolean isReady() {
        return ready;
    }

    /**
     * Look up UI sprite by publish key (full texture path, e.g. {@code catframe:gui/widgets/button});
     * returns null if not stitched / not found (caller falls back to {@link #getMissingSprite()}).
     */
    @Nullable
    public static CatSprite findSprite(String texturePath) {
        if (atlas == null || texturePath == null) {
            return null;
        }
        return atlas.getSprite(texturePath);
    }

    /** UI atlas built-in missing sprite (purple-black checkerboard fallback; non-null after stitch). */
    @Nullable
    public static CatSprite getMissingSprite() {
        return atlas != null ? atlas.getMissingSprite() : null;
    }

    /** UI atlas GL texture registration location (render layer bindTexture). */
    public static ResourceLocation getAtlasLocation() {
        return ATLAS_LOCATION;
    }

    /**
     * Texture path → publish key: {@code <ns>:textures/gui/<path>.png} → {@code <ns>:gui/<path>}
     * (inverse of DirectorySource's sprite id generation). Paths without {@code textures/} prefix
     * or not ending in {@code .png} are returned as-is (caller handles fallback).
     *
     * <p>Texture path → publish key: strips the {@code textures/} segment and the
     * {@code .png} suffix so a texture file maps to its atlas sprite id.
     */
    public static String toSpritePath(ResourceLocation texture) {
        if (texture == null) {
            return null;
        }
        String path = texture.getResourcePath();
        if (path.startsWith("textures/") && path.endsWith(".png")) {
            return texture.getResourceDomain() + ":" + path.substring("textures/".length(), path.length() - 4);
        }
        return texture.toString();
    }

    /**
     * Atlas lookup: texture path → UI sprite (stage C consumer entry).
     * Returns sprite (with missing fallback) for textures belonging to {@code catframe:gui} atlas
     * (i.e. publish key resolvable); returns null for textures not in the atlas (vanilla/third-party
     * stay on their own bind path).
     */
    @Nullable
    public static CatSprite resolve(ResourceLocation texture) {
        if (!isReady() || texture == null) {
            return null;
        }
        return findSprite(toSpritePath(texture));
    }

    /**
     * Animation tick: advances UI sprite animation frames each client tick; frame changes
     * trigger CatAtlas glTexSubImage2D region re-upload. All current assets static (zero overhead),
     * mechanism reserved for M3.
     */
    public static void tickAnimations() {
        if (atlas == null || !ready) {
            return;
        }
        for (Map.Entry<String, CatSprite> entry : atlas.getSprites().entrySet()) {
            CatSprite sprite = entry.getValue();
            if (sprite.isAnimated() && sprite.updateAnimationTick()) {
                atlas.updateAnimationRegion(sprite);
            }
        }
    }
}
