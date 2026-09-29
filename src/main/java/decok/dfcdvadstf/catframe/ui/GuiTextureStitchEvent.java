package decok.dfcdvadstf.catframe.ui;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * GUI atlas stitch lifecycle event (Pre/On/Post), driven independently of the
 * vanilla blocks/items stitch chain. UI-domain sprites are managed by the
 * CatFrame-built GUI atlas; Pre collects, On stitches and uploads, Post publishes
 * the sprite lookup table.
 * <p>
 * Three-phase lifecycle (driven by {@link decok.dfcdvadstf.catframe.adapter.vanilla.model.GuiTextureStitchHandler}
 * at vanilla {@code TextureStitchEvent} resource sync points):
 * <ol>
 *   <li>{@link Pre} — Collect: consumes {@code catframe:gui} atlas definition ({@code atlases/gui.json}
 *       sources) produces sprite refs; other mods may subscribe to add/remove assets;</li>
 *   <li>{@link On} — Stitch: layout (reuses TextureStitcher, no mipmap) + GL upload;</li>
 *   <li>{@link Post} — Publish: UI-side sprite lookup ready, CatFrame UI drawing can batch-fetch UVs.</li>
 * </ol>
 * <p>
 * Phase A skeleton: event defined and fired; collect/stitch/publish consumers filled by phase B.
 */
public class GuiTextureStitchEvent extends Event {

    /** Collect phase: sources from catframe:gui atlas definition produce sprite refs (subscribable add/remove). */
    public static class Pre extends GuiTextureStitchEvent {
    }

    /** Stitch phase: layout + GL upload (no mipmap). */
    public static class On extends GuiTextureStitchEvent {
    }

    /** Publish phase: UI-side sprite lookup ready. */
    public static class Post extends GuiTextureStitchEvent {
    }
}
