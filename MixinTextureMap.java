package decok.dfcdvadstf.catframe.mixin;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * Mixin into TextureMap to allow item models to use texture paths exactly as specified in JSON.
 * 
 * <h3>Problem</h3>
 * <p>
 * By default, 1.7.10's {@code TextureMap} forces all item textures to be loaded from
 * {@code textures/items/} directory, regardless of what path is written in the model JSON.
 * For example, if your model specifies:
 * <pre>
 * {
 *   "textures": {
 *     "layer0": "mymod:item/custom_path"
 *   }
 * }
 * </pre>
 * The game will actually try to load from:
 * {@code assets/mymod/textures/items/item/custom_path.png}
 * 
 * This happens because {@code completeResourceLocation()} always prepends the {@code basePath}
 * (which is {@code "textures/items"} for item atlas).
 * </p>
 * 
 * <h3>Solution</h3>
 * <p>
 * This mixin intercepts the texture path resolution for item textures (textureType == 1).
 * When the texture path already contains a directory structure (e.g., {@code "item/custom_path"}
 * or {@code "blocks/stone"}), it bypasses the automatic {@code basePath} prefix injection
 * and uses the path exactly as specified.
 * </p>
 * 
 * <h3>Usage for Mod Developers</h3>
 * <p>
 * Now you can write texture paths in your model JSON exactly as you want them:
 * </p>
 * 
 * <h4>Example 1: Custom item subdirectory</h4>
 * <pre>
 * {
 *   "textures": {
 *     "layer0": "mymod:item/bluey_inventory"
 *   }
 * }
 * </pre>
 * This will load from: {@code assets/mymod/textures/item/bluey_inventory.png}
 * 
 * <h4>Example 2: Shared textures folder</h4>
 * <pre>
 * {
 *   "textures": {
 *     "layer0": "mymod:textures/custom/my_texture"
 *   }
 * }
 * </pre>
 * This will load from: {@code assets/mymod/textures/custom/my_texture.png}
 * 
 * <h4>Example 3: Cross-mod texture reference</h4>
 * <pre>
 * {
 *   "textures": {
 *     "layer0": "othermod:items/some_item"
 *   }
 * }
 * </pre>
 * This will load from: {@code assets/othermod/textures/items/some_item.png}
 * 
 * <h3>Backward Compatibility</h3>
 * <p>
 * Vanilla-style paths still work as before:
 * <pre>
 * {
 *   "textures": {
 *     "layer0": "mymod:my_texture"
 *   }
 * }
 * </pre>
 * This will still load from: {@code assets/mymod/textures/items/my_texture.png}
 * (because there's no directory separator in {@code "my_texture"})
 * </p>
 * 
 * @author CatFrame
 * @since 0.3.1
 */
@Mixin(TextureMap.class)
public class MixinTextureMap {

    @Shadow
    @Final
    private int textureType;

    /**
     * Redirects the ResourceLocation constructor call in completeResourceLocation()
     * to allow item textures to use their specified paths without forced basePath prefix.
     * 
     * <p>
     * For item textures (textureType == 1), if the texture path already contains
     * a directory structure (has '/' or starts with 'textures/'), we construct
     * the ResourceLocation with the path as-is, bypassing the basePath injection.
     * </p>
     * 
     * @param domain   The resource domain (modid)
     * @param path     The resource path (may include directory structure)
     * @return ResourceLocation with faithful path preservation for item textures
     */
    @WrapOperation(
        method = "completeResourceLocation",
        at = @At(
            value = "NEW",
            target = "net/minecraft/util/ResourceLocation"
        )
    )
    private ResourceLocation catframe$preserveItemTexturePath(
            String domain, String path) {
        
        // Only intercept item texture atlas (textureType == 1)
        if (this.textureType == 1) {
            // If path already contains directory structure, use it as-is
            // This allows modders to specify exact paths like "item/bluey" or "custom/texture"
            if (path.contains("/") || path.startsWith("textures/")) {
                // Path already has structure, use it faithfully
                return new ResourceLocation(domain, path);
            }
            // No directory structure, let vanilla behavior apply (basePath will be prepended)
        }
        
        // For block textures or simple filenames, use original behavior
        return new ResourceLocation(domain, path);
    }
}
