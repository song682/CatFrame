package decok.dfcdvadstf.catframe.model;

import decok.dfcdvadstf.catframe.CatFrame;
import decok.dfcdvadstf.catframe.core.Direction;
import decok.dfcdvadstf.catframe.model.core.AtlasPixelCache;
import decok.dfcdvadstf.catframe.model.core.baking.JsonModelBake;
import decok.dfcdvadstf.catframe.resources.atlas.Sprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.IIcon;

import javax.vecmath.Vector3d;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Item model side face generator — mirrors 26.1.2 {@code ItemModelGenerator.bakeSideFaces}.
 * <p>
 * Scans texture pixel-by-pixel, generates 1px-wide side quads at opaque→transparent
 * boundaries, giving 2D items extruded thickness sides matching edge pixel colors.
 * <p>
 * Aligned with ModelResolver's builtin/generated from/to (7.5→8.5):
 * side face Z range is {@code MIN_Z/16 ~ MAX_Z/16} (block space).
 * </p>
 */
public class ItemModelGenerator {

    /** Z extrusion range (pixel space), matches ModelResolver's builtin/generated from/to */
    static final float MIN_Z = 7.5F;
    static final float MAX_Z = 8.5F;

    /**
     * UV inset amount (pixel space), mirrors 26.1.2 ItemModelGenerator.UV_SHRINK.
     * Side face UVs inset 0.1px from pixel boundaries to avoid atlas seam shimmer.
     */
    private static final float UV_SHRINK = 0.1F;

    /**
     * Generate side faces for a single layer texture.
     * <p>
     * Grid/scale are driven by the UV-region content slice (AtlasPixelCache), not
     * {@code getIconWidth()}: under anisotropic filtering, 1.7.10 vanilla expands
     * physical storage to (content+16)x(content+16) and reports that padded size.
     *
     * @param icon      layer texture sprite
     * @param tintIndex tint index (for layered models), -1 for none
     * @return list of side BakedQuads
     */
    public static List<JsonModelBake.BakedQuad> bakeSideFaces(IIcon icon, int tintIndex) {
        List<JsonModelBake.BakedQuad> quads = new ArrayList<>();

        // Pixel source: CatSprite reads its CPU-owned pixels directly; vanilla
        // sprites use the AtlasPixelCache UV-region content slice. Both grids
        // are content-sized.
        // Pixel source: CatSprite reads its CPU-owned pixels directly; vanilla
        // sprites use the AtlasPixelCache UV-region content slice. Both grids
        // are content-sized.
        int[] flatPixels;
        int width, height;
        if (icon instanceof Sprite) {
            Sprite catSprite = (Sprite) icon;
            flatPixels = catSprite.getPixels();
            width = catSprite.getIconWidth();
            height = catSprite.getIconHeight();
        } else if (icon instanceof TextureAtlasSprite) {
            TextureAtlasSprite sprite = (TextureAtlasSprite) icon;

            // Grid/scale are driven by the UV-region content slice, not by
        // getIconWidth(), which reports the padded storage size under
        // anisotropic filtering.
            // Grid/scale are driven by the UV-region content slice, not by
            // getIconWidth(), which reports the padded storage size under
            // anisotropic filtering.
            AtlasPixelCache.CachedSprite cached = AtlasPixelCache.getSprite(sprite.getIconName());
            if (cached == null || cached.pixels.length < cached.width * cached.height) {
                CatFrame.logger.debug("[ItemModelGenerator] no pixel data for sprite '{}'", sprite.getIconName());
                return quads;
            }
            flatPixels = cached.pixels;
            width = cached.width;
            height = cached.height;
        } else {
            return quads;
        }

        float xScale = 16.0F / width;
        float yScale = 16.0F / height;

        // Use Set to dedup: same pixel + same direction only emitted once (multi-pass scan dedup)
        Set<String> emitted = new HashSet<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isTransparent(flatPixels, x, y, width)) {
                    continue;
                }

                // Read current opaque pixel's ARGB color for side solid fill
                int pixelARGB = flatPixels[y * width + x];

                // Top edge: pixel above transparent → emit UP face quad
                if (isTransparent(flatPixels, x, y - 1, width)) {
                    String key = "U" + x + "," + y;
                    if (emitted.add(key)) {
                        bakeHorizontalEdge(quads, icon, x, y, true, xScale, yScale, tintIndex, pixelARGB);
                    }
                }
                // Bottom edge: pixel below transparent → emit DOWN face quad
                if (isTransparent(flatPixels, x, y + 1, width)) {
                    String key = "D" + x + "," + y;
                    if (emitted.add(key)) {
                        bakeHorizontalEdge(quads, icon, x, y, false, xScale, yScale, tintIndex, pixelARGB);
                    }
                }
                // Left edge: pixel to left transparent → emit WEST face quad (outward winding, see bakeVerticalEdge notes)
                if (isTransparent(flatPixels, x - 1, y, width)) {
                    String key = "L" + x + "," + y;
                    if (emitted.add(key)) {
                        bakeVerticalEdge(quads, icon, x, y, true, xScale, yScale, tintIndex, pixelARGB);
                    }
                }
                // Right edge: pixel to right transparent → emit EAST face quad (outward winding, see bakeVerticalEdge notes)
                if (isTransparent(flatPixels, x + 1, y, width)) {
                    String key = "R" + x + "," + y;
                    if (emitted.add(key)) {
                        bakeVerticalEdge(quads, icon, x, y, false, xScale, yScale, tintIndex, pixelARGB);
                    }
                }
            }
        }

        CatFrame.logger.info(
                "[ItemModelGenerator] sprite '{}' content {}x{} (phys {}x{}): {} edge pixels → {} side quads",
                icon.getIconName(), width, height, icon.getIconWidth(), icon.getIconHeight(), emitted.size(),
                quads.size());

        return quads;
    }

/**
     * Generate horizontal edge (top/bottom) side quads — mirrors BlockJsonModelBake.emitFaceFromCorners winding spec.
     * <p>
     * Quad is a flat slice on XZ plane: width = 1px, depth = 1px (Z: 7.5→8.5).
     * UP and DOWN use different vertex winding for correct normal direction.
     */
    private static void bakeHorizontalEdge(
            List<JsonModelBake.BakedQuad> quads,
            IIcon sprite,
            int x, int y, boolean isTop,
            float xScale, float yScale, int tintIndex, int pixelARGB) {

        // World coords (block space 0-1)
        float worldX0 = (x * xScale) / 16.0F;
        float worldX1 = ((x + 1.0F) * xScale) / 16.0F;

        // Y: per-pixel placement
        // Texture y=0 is top → world Y=1; y=height is bottom → world Y=0
        float worldY;
        if (isTop) {
            // UP = pixel top edge
            worldY = (16.0F - y * yScale) / 16.0F;
        } else {
            // DOWN = pixel bottom edge
            worldY = (16.0F - (y + 1.0F) * yScale) / 16.0F;
        }

        // UV (pixel space) — mirrors 26.1.2: u0/u1 = pixel left/right edges + UV_SHRINK,
        // v0/v1 differ by face: horizontal (UP/DOWN) V axis flipped (v0=bottom, v1=top),
        // vertical (EAST/WEST) V axis normal (v0=top, v1=bottom).
        float u0 = (x + UV_SHRINK) * xScale;
        float u1 = (x + 1.0F - UV_SHRINK) * xScale;
        float v0, v1;
        if (isTop) {
            // UP: V flip — v0 maps to pixel bottom (worldY side), v1 to pixel top
            v0 = (y + 1.0F - UV_SHRINK) * yScale;
            v1 = (y + UV_SHRINK) * yScale;
        } else {
            // DOWN: V normal — v0=pixel top, v1=pixel bottom
            v0 = (y + UV_SHRINK) * yScale;
            v1 = (y + 1.0F - UV_SHRINK) * yScale;
        }

        Direction face = isTop ? Direction.UP : Direction.DOWN;

        JsonModelBake.BakedQuad q = new JsonModelBake.BakedQuad();
        q.icon = sprite;
        q.face = face;
        q.tintIndex = tintIndex;
        q.guiLight = "front";
        // Force opaque alpha: side faces are solid-color filled in a second untextured
        // pass
        q.solidColor = pixelARGB | 0xFF000000;

        // Vertex winding: mirrors BlockJsonModelBake.emitFaceFromCorners
        // UP: v0(idx=010)=MIN_X,MIN_Z v1(idx=011)=MIN_X,MAX_Z v2(idx=111)=MAX_X,MAX_Z
        // v3(idx=110)=MAX_X,MIN_Z
        // DOWN:v0(idx=001)=MIN_X,MAX_Z v1(idx=000)=MIN_X,MIN_Z v2(idx=100)=MAX_X,MIN_Z
        // v3(idx=101)=MAX_X,MAX_Z
        float minZ = MIN_Z / 16.0F, maxZ = MAX_Z / 16.0F;
        if (isTop) {
            q.vertices[0] = new Vector3d(worldX0, worldY, minZ);
            q.vertices[1] = new Vector3d(worldX0, worldY, maxZ);
            q.vertices[2] = new Vector3d(worldX1, worldY, maxZ);
            q.vertices[3] = new Vector3d(worldX1, worldY, minZ);
        } else {
            q.vertices[0] = new Vector3d(worldX0, worldY, maxZ);
            q.vertices[1] = new Vector3d(worldX0, worldY, minZ);
            q.vertices[2] = new Vector3d(worldX1, worldY, minZ);
            q.vertices[3] = new Vector3d(worldX1, worldY, maxZ);
        }
        // UVs assigned per vertex: v0→MIN_Z vertex, v1→MAX_Z vertex; u0→MIN_X, u1→MAX_X
        q.up[0] = u0;
        q.vp[0] = v0;
        q.up[1] = u0;
        q.vp[1] = v1;
        q.up[2] = u1;
        q.vp[2] = v1;
        q.up[3] = u1;
        q.vp[3] = v0;

        q.faceNormal = faceNormal(face);
        quads.add(q);
    }

    /**
     * Generates the vertical edge (left/right) side quads — mirrors the winding spec of
     * BlockJsonModelBake.emitFaceFromCorners.
     * <p>
     * The quad is a flat slice on the YZ plane: height = 1px, depth = 1px (Z: 7.5→8.5).
     * <p>
     * Face mapping (deliberately deviating from 26.1.2's LEFT→EAST / RIGHT→WEST):
     * LEFT → Direction.WEST (normal -X, faces the transparent pixel on the left, visible from the left)
     * RIGHT → Direction.EAST (normal +X, faces the transparent pixel on the right, visible from the right)
     * <p>
     * Vanilla 26.1.2 lets the east/west side faces point towards the pixel's <b>inside</b>
     * (LEFT→EAST); visibility relies on the mechanism where cutout discards transparent
     * texels and the far strip is seen through from the opposite side. CatFrame's fixed
     * pipeline enables backface culling and lacks that cutout depth semantics, so an inward
     * winding would be culled wholesale (manifesting as the east/west extruded faces turning
     * transparent). This is therefore changed to an <b>outward</b> winding, aligning with the
     * UP/DOWN outward convention, with culling semantics equivalent to solid geometry.
     */
    private static void bakeVerticalEdge(
            List<JsonModelBake.BakedQuad> quads,
            IIcon sprite,
            int x, int y, boolean isLeft,
            float xScale, float yScale, int tintIndex, int pixelARGB) {

        // World coordinates (block space 0-1) — mirrors 26.1.2:
        // the LEFT edge is at the pixel's left boundary (x * xScale), the RIGHT edge at the right boundary ((x+1) * xScale)
        float worldX = isLeft
                ? (x * xScale) / 16.0F
                : ((x + 1.0F) * xScale) / 16.0F;

        // Y: pixel y → block space (Y flipped: texture top → world top)
        float worldY0 = (16.0F - (y + 1.0F) * yScale) / 16.0F;
        float worldY1 = (16.0F - y * yScale) / 16.0F;

        // UV (pixel space) — mirrors 26.1.2: u0/u1 are the pixel left/right boundaries + UV_SHRINK (spanning the extrusion depth along Z),
        // v0/v1 are the pixel top/bottom boundaries + UV_SHRINK (assigned along the world Y axis: v0=top, v1=bottom)
        float u0 = (x + UV_SHRINK) * xScale;
        float u1 = (x + 1.0F - UV_SHRINK) * xScale;
        float v0 = (y + UV_SHRINK) * yScale;
        float v1 = (y + 1.0F - UV_SHRINK) * yScale;

        // Outward-facing: LEFT→WEST, RIGHT→EAST (see method JavaDoc for rationale)
        Direction face = isLeft ? Direction.WEST : Direction.EAST;

        JsonModelBake.BakedQuad q = new JsonModelBake.BakedQuad();
        q.icon = sprite;
        q.face = face;
        q.tintIndex = tintIndex;
        q.guiLight = "front";
        // Force opaque alpha: side faces are solid-color filled in a second untextured
        // pass
        q.solidColor = pixelARGB | 0xFF000000;

        // Vertex winding: mirrors BlockJsonModelBake.emitFaceFromCorners
        // EAST: v0(idx=111)=MAX_Y,MAX_Z v1(idx=101)=MIN_Y,MAX_Z v2(idx=100)=MIN_Y,MIN_Z
        // v3(idx=110)=MAX_Y,MIN_Z
        // WEST: v0(idx=010)=MAX_Y,MIN_Z v1(idx=000)=MIN_Y,MIN_Z v2(idx=001)=MIN_Y,MAX_Z
        // v3(idx=011)=MAX_Y,MAX_Z
        float minZ = MIN_Z / 16.0F, maxZ = MAX_Z / 16.0F;
        if (isLeft) {
            // WEST face (LEFT edge, outward -X)
            q.vertices[0] = new Vector3d(worldX, worldY1, minZ);
            q.vertices[1] = new Vector3d(worldX, worldY0, minZ);
            q.vertices[2] = new Vector3d(worldX, worldY0, maxZ);
            q.vertices[3] = new Vector3d(worldX, worldY1, maxZ);
        } else {
            // EAST face (RIGHT edge, outward +X)
            q.vertices[0] = new Vector3d(worldX, worldY1, maxZ);
            q.vertices[1] = new Vector3d(worldX, worldY0, maxZ);
            q.vertices[2] = new Vector3d(worldX, worldY0, minZ);
            q.vertices[3] = new Vector3d(worldX, worldY1, minZ);
        }
        // UVs assigned per vertex: V along the Y axis (v0→MAX_Y(top) vertex, v1→MIN_Y(bottom) vertex);
        // U along the Z axis spans the pixel width (u0→MIN_Z, u1→MAX_Z, isomorphic to bakeHorizontalEdge's v0→MIN_Z convention;
        // mirrors 26.1.2 bakeSideFaces, whose vertical edge likewise gives the full u0→u1 span).
        // Full u0→u1 span across the Z-depth axis, matching vanilla 26.1.2 side-face
        // UVs.
        if (isLeft) {
            // WEST: vertices 0/1 are at MIN_Z, vertices 2/3 at MAX_Z
            q.up[0] = u0;
            q.vp[0] = v0;
            q.up[1] = u0;
            q.vp[1] = v1;
            q.up[2] = u1;
            q.vp[2] = v1;
            q.up[3] = u1;
            q.vp[3] = v0;
        } else {
            // EAST: vertices 0/1 are at MAX_Z, vertices 2/3 at MIN_Z
            q.up[0] = u1;
            q.vp[0] = v0;
            q.up[1] = u1;
            q.vp[1] = v1;
            q.up[2] = u0;
            q.vp[2] = v1;
            q.up[3] = u0;
            q.vp[3] = v0;
        }

        q.faceNormal = faceNormal(face);
        quads.add(q);
    }

    /**
     * Checks whether (x,y) in the flat pixel array is transparent (out-of-bounds is
     * treated as transparent). getFrameTextureData(0) returns the mipmap level array;
     * level 0 is a width*height flat array.
     */
    private static boolean isTransparent(int[] flatPixels, int x, int y, int width) {
        if (x < 0 || y < 0 || x >= width) {
            return true;
        }
        int idx = y * width + x;
        if (idx < 0 || idx >= flatPixels.length) {
            return true;
        }
        return (flatPixels[idx] >> 24 & 0xFF) == 0;
    }

    private static Vector3d faceNormal(Direction face) {
        return face.getNormalVec3d();
    }
}
