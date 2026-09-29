package decok.dfcdvadstf.catframe.resources.atlas.source;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.resources.IResourceManager;

import java.util.List;

/**
 * Definition-driven source SPI (mirrors 26.1.2 {@code SpriteSource}).
 * <p>
 * A source turns "scan / clip / dye" results into a list of {@link SpriteRef};
 * sources run in the order declared in the definition JSON, and on duplicate
 * sprite ids the later one wins (the collector warns). Sources for which
 * {@link #removesCollected()} is true (filters) produce nothing; instead they
 * apply {@link #shouldRemove(String)} removal semantics to the collector's
 * already-collected ids.
 */
@SideOnly(Side.CLIENT)
public interface AtlasSource {

    /** Produces this source's sprite refs (filter sources return an empty list). */
    List<SpriteRef> list(IResourceManager manager);

    /** Whether this is a filter source (when true, list produces nothing and the collector applies shouldRemove to its collected set). */
    boolean removesCollected();

    /** Filter predicate (only called for sources with removesCollected() == true): whether spriteId should be removed. */
    boolean shouldRemove(String spriteId);

    /** Source type name (for diagnostic logs, e.g. {@code minecraft:directory}). */
    String type();
}
