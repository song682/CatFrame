package decok.dfcdvadstf.catframe.mixin.early;

import decok.dfcdvadstf.catframe.resources.builtin.BuiltinPackEntry;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.data.PackMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.image.BufferedImage;

/**
 * Turns {@code ResourcePackRepository.Entry} into a {@link BuiltinPackEntry}:
 * adds the {@code catframe$builtinPackId} marker plus the three private field
 * wirings the injector needs, since the entry's fields cannot be filled through
 * its package-private constructor.
 * <p>
 * A built-in entry's backing {@code File} is a fake path that is never opened,
 * so {@code updateResourcePack()} (which would try to open it as a folder or
 * zip pack and replace the injected fields) is cancelled for such entries.
 * <p>
 * 使 {@code ResourcePackRepository.Entry} 成为 {@link BuiltinPackEntry}：加入
 * {@code catframe$builtinPackId} 标记，并打通注入器所需的三个私有字段——条目
 * 的字段无法经其包级构造器填充。内置条目背后的 {@code File} 是永不打开的空路径，
 * 因此对这类条目取消 {@code updateResourcePack()}（它会尝试把该路径当作文件夹
 * 或 zip 包打开并覆盖已注入的字段）。
 */
@Mixin(ResourcePackRepository.Entry.class)
public abstract class MixinResourcePackRepositoryEntry implements BuiltinPackEntry {

    @Shadow
    private IResourcePack reResourcePack;

    @Shadow
    private PackMetadataSection rePackMetadataSection;

    @Shadow
    private BufferedImage texturePackIcon;

    /** Built-in pack id; null for regular file packs. */
    @Unique
    private String catframe$builtinPackId;

    @Override
    public boolean catframe$isBuiltin() {
        return this.catframe$builtinPackId != null;
    }

    @Override
    public String catframe$getBuiltinId() {
        return this.catframe$builtinPackId;
    }

    @Override
    public void catframe$setupBuiltin(String id, IResourcePack pack, PackMetadataSection metadata,
            BufferedImage icon) {
        this.catframe$builtinPackId = id;
        this.reResourcePack = pack;
        this.rePackMetadataSection = metadata;
        this.texturePackIcon = icon;
    }

    /** Keeps the injected fields of a built-in entry; regular entries are untouched. */
    @Inject(method = "updateResourcePack", at = @At("HEAD"), cancellable = true)
    private void catframe$skipFileUpdate(CallbackInfo ci) {
        if (this.catframe$builtinPackId != null) {
            ci.cancel();
        }
    }
}
