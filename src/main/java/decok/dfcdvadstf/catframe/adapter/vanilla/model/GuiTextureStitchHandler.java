package decok.dfcdvadstf.catframe.adapter.vanilla.model;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import decok.dfcdvadstf.catframe.ui.GuiTextureStitchEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * UI atlas stitching driver (render three-domain architecture): bridges the vanilla
 * {@code TextureStitchEvent} resource sync points (two full reloads: startGame + refreshResources)
 * into the UI domain's <b>independent</b> event chain {@link GuiTextureStitchEvent} (Pre / On / Post).
 * <p>
 * Firing timing:
 * <ul>
 *   <li>{@code TextureStitchEvent.Pre} (type 0 = blocks atlas, start of the FML resource reload) →
 *       post {@code GuiTextureStitchEvent.Pre} (collect) + {@code GuiTextureStitchEvent.On}
 *       (stitch + upload) — the UI atlas is a standalone GL texture, so it can run in parallel
 *       with the blocks stitch without conflict;</li>
 *   <li>{@code TextureStitchEvent.Post} (type 1 = items atlas, the last vanilla atlas to finish) →
 *       post {@code GuiTextureStitchEvent.Post} (publish lookups) — this guarantees the UI lookup
 *       table only becomes ready after every vanilla texture has been loaded.</li>
 * </ul>
 * <p>
 * The UI atlas does not take part in the vanilla blocks/items stitch (no {@code registerIcon} into
 * {@code TextureMap}); consumers only serve CatFrame's own UI (theme / component tree / tooltip),
 * while vanilla GUIs and third-party GUIs keep binding textures one by one — zero impact.
 * <p>
 * Stage A skeleton: only drives the event chain (with logging); the collect / stitch / publish
 * consumers are filled in at stage B.
 *
 * <p>Bridges the vanilla TextureStitchEvent sync points into the independent
 * GuiTextureStitchEvent chain (Pre/On/Post) that drives the CatFrame GUI atlas.
 */
@SideOnly(Side.CLIENT)
public class GuiTextureStitchHandler {

    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != 0) {
            return;
        }
        MinecraftForge.EVENT_BUS.post(new GuiTextureStitchEvent.Pre());
        MinecraftForge.EVENT_BUS.post(new GuiTextureStitchEvent.On());
    }

    @SubscribeEvent
    public void onTextureStitchPost(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() != 1) {
            return;
        }
        MinecraftForge.EVENT_BUS.post(new GuiTextureStitchEvent.Post());
    }
}
