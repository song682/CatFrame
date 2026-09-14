package decok.dfcdvadstf.catframe.resources.builtin;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.PackMetadataSection;

import java.awt.image.BufferedImage;

/**
 * Duck interface added to {@code ResourcePackRepository.Entry} by the early
 * mixin, marking an entry as backed by a built-in (classpath-provided)
 * resource pack and exposing the internals the injector must fill.
 * <p>
 * 由 early mixin 混入 {@code ResourcePackRepository.Entry} 的鸭子接口，用于标记
 * “该条目由内置（随 jar 提供的）资源包支撑”，并暴露注入器需要填充的内部字段。
 */
public interface BuiltinPackEntry {

    /** @return true when this entry belongs to a built-in pack */
    boolean catframe$isBuiltin();

    /** @return the built-in pack id, or null for regular file packs */
    String catframe$getBuiltinId();

    /**
     * Fills the entry's private fields without touching the file system. Called
     * by {@link BuiltinPackInjector} right after the reflective construction of
     * a built-in entry.
     */
    void catframe$setupBuiltin(String id, IResourcePack pack, PackMetadataSection metadata, BufferedImage icon);
}
