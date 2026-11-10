package decok.dfcdvadstf.catframe.mixin.middle.resource;

import decok.dfcdvadstf.catframe.ui.util.TextureStretchingMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.data.IMetadataSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the CatFrame {@code stretching} mcmeta section into the vanilla
 * metadata pipeline and drops the parsed values whenever resources are reloaded.
 * <p>
 * {@code refreshResources()} is the natural hook: it runs once during startup
 * (after {@code Minecraft.startGame} built the resource manager and registered
 * the five vanilla sections) and again on every resource pack reload, so the
 * section is always registered before any GUI asks for it and the cache never
 * serves values from a previous pack set.
 * </p>
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftMetadataSection {

    @Shadow
    private IMetadataSerializer metadataSerializer_;

    /** Pre-refresh hook: (re)register the section and invalidate cached values. */
    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void catframe$registerStretchingMetadata(CallbackInfo ci) {
        TextureStretchingMetadata.registerSection(this.metadataSerializer_);
        TextureStretchingMetadata.clearCache();
    }
}
