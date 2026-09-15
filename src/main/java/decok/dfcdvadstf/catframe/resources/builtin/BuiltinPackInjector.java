package decok.dfcdvadstf.catframe.resources.builtin;

import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.data.PackMetadataSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges {@link BuiltinPackRegistry} into the vanilla repository. Invoked from
 * the repository mixins on the client thread while
 * {@code ResourcePackRepository.updateRepositoryEntriesAll()} rebuilds its
 * entry list, including the very first rebuild inside the repository
 * constructor. Restoring the enabled state of the packs registered by ordinary
 * mods at startup is {@link BuiltinPackBootstrap}'s job, on the first vanilla
 * resource refresh after preInit.
 * <p>
 * Everything vanilla needed here (the metadata serializer, the default pack)
 * is read from the repository instance that is being rebuilt: the rebuild
 * window includes the repository constructor, during which the
 * {@code Minecraft} field holding the repository is not assigned yet.
 * <p>
 * 将 {@link BuiltinPackRegistry} 桥接进原版仓库。由仓库 mixin 在客户端线程、
 * {@code ResourcePackRepository.updateRepositoryEntriesAll()} 重建条目列表时调用——
 * 包括仓库构造器内的首次重建。普通模组注册的包的启动启用状态恢复由
 * {@link BuiltinPackBootstrap} 在 preInit 之后的那次原版资源刷新上完成。
 * 这里需要的一切原版状态（metadata serializer、默认包）均取自正在重建的仓库
 * 实例：重建窗口包含仓库构造器本身，此时 {@code Minecraft} 中持有仓库的字段
 * 尚未赋值。
 */
public final class BuiltinPackInjector {

    private static final Logger LOGGER = LogManager.getLogger("CatFrame/BuiltinPacks");

    /**
     * Built-in entries of the previous entry list, captured at the HEAD of
     * {@code updateRepositoryEntriesAll} and consumed by
     * {@link #injectBuiltinEntries}. Reusing the same entry objects keeps their
     * bound texture icons (dynamic textures registered with the texture
     * manager) instead of leaking one per rebuild.
     */
    private static Map<String, ResourcePackRepository.Entry> previousEntries;

    private BuiltinPackInjector() {}

    /**
     * Captures the built-in entries of the current list. Called at the HEAD of
     * {@code updateRepositoryEntriesAll}, before the list is replaced.
     */
    public static void captureBuiltinEntries(List entriesAll) {
        Map<String, ResourcePackRepository.Entry> captured = new HashMap<>();
        if (entriesAll != null) {
            for (Object candidate : entriesAll) {
                if (candidate instanceof BuiltinPackEntry) {
                    BuiltinPackEntry builtin = (BuiltinPackEntry) candidate;
                    if (builtin.catframe$isBuiltin()) {
                        captured.put(builtin.catframe$getBuiltinId(), (ResourcePackRepository.Entry) candidate);
                    }
                }
            }
        }
        previousEntries = captured;
    }

    /**
     * Adds the registered built-in packs to the rebuilt list, reusing entries
     * from {@link #captureBuiltinEntries} where possible. Called at the RETURN
     * of {@code updateRepositoryEntriesAll}. Built-in packs are listed before
     * file packs, in registration order.
     */
    public static void injectBuiltinEntries(ResourcePackRepository repository, List entriesAll) {
        Map<String, ResourcePackRepository.Entry> previous = previousEntries;
        previousEntries = null;
        if (entriesAll == null) {
            return;
        }
        List<BuiltinPackDescriptor> descriptors = new ArrayList<>(BuiltinPackRegistry.getDescriptors());
        if (descriptors.isEmpty()) {
            return;
        }
        List<ResourcePackRepository.Entry> builtinEntries = new ArrayList<>(descriptors.size());
        for (BuiltinPackDescriptor descriptor : descriptors) {
            ResourcePackRepository.Entry entry = previous == null ? null : previous.get(descriptor.getId());
            if (entry == null) {
                entry = createEntry(repository, descriptor);
            }
            if (entry != null) {
                builtinEntries.add(entry);
            }
        }
        if (builtinEntries.isEmpty()) {
            return;
        }
        entriesAll.removeAll(builtinEntries);
        entriesAll.addAll(0, builtinEntries);
    }

    /**
     * Constructs a repository entry for a built-in pack through the vanilla
     * {@code Entry(ResourcePackRepository, File)} constructor, made public by an
     * access transformer (see {@code META-INF/catframe_at.cfg}). The fake file is
     * never opened: the entry's fields are filled by
     * {@link BuiltinPackEntry#catframe$setupBuiltin}.
     */
    private static ResourcePackRepository.Entry createEntry(ResourcePackRepository repository,
            BuiltinPackDescriptor descriptor) {
        try {
            ResourcePackRepository.Entry entry = repository.new Entry(
                    new File(BuiltinResourcePack.ROOT_DIR, descriptor.getId()));
            if (!(entry instanceof BuiltinPackEntry)) {
                LOGGER.error("Repository mixins are not applied — cannot create a repository entry for built-in pack '{}'",
                        descriptor.getId());
                return null;
            }
            BuiltinResourcePack pack = new BuiltinResourcePack(descriptor);
            PackMetadataSection metadata = (PackMetadataSection) pack.getPackMetadata(repository.rprMetadataSerializer,
                    "pack");
            ((BuiltinPackEntry) entry).catframe$setupBuiltin(descriptor.getId(), pack, metadata,
                    loadIcon(repository, pack));
            return entry;
        } catch (Throwable throwable) {
            LOGGER.error("Failed to create the repository entry for built-in pack '{}'", descriptor.getId(), throwable);
            return null;
        }
    }

    /**
     * Icon fallback chain: the pack's own {@code pack.png}, then the vanilla
     * default pack icon, then a transparent placeholder.
     */
    private static BufferedImage loadIcon(ResourcePackRepository repository, BuiltinResourcePack pack) {
        try {
            BufferedImage icon = pack.getPackImage();
            if (icon != null) {
                return icon;
            }
        } catch (IOException ignored) {
            // this built-in pack ships no pack.png
        }
        try {
            BufferedImage fallback = repository.rprDefaultResourcePack.getPackImage();
            if (fallback != null) {
                return fallback;
            }
        } catch (IOException ignored) {
            // default pack icon unavailable — use the placeholder below
        }
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }
}
