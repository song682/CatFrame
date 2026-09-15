package decok.dfcdvadstf.catframe.resources.builtin;

import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.data.PackMetadataSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges {@link BuiltinPackRegistry} into the vanilla repository. Invoked from
 * the early mixins on the client thread while
 * {@code ResourcePackRepository.updateRepositoryEntriesAll()} rebuilds its
 * entry list — including the very first build inside the {@code Minecraft}
 * constructor, which is what lets the vanilla constructor's own name-matching
 * loop restore the enabled state of built-in packs from {@code options.txt}.
 * <p>
 * The mixin cannot pass the vanilla-private serializer around, and the
 * repository field of {@code Minecraft} is still null during its construction,
 * so the metadata serializer is always taken from the repository instance that
 * is being rebuilt.
 * <p>
 * 将 {@link BuiltinPackRegistry} 桥接进原版仓库。由 early mixin 在客户端线程、
 * {@code ResourcePackRepository.updateRepositoryEntriesAll()} 重建条目列表时调用——包括
 * {@code Minecraft} 构造器中的首次重建，这就是原版构造器自身的按名匹配循环能够从
 * {@code options.txt} 恢复内置包启用状态的原因。metadata serializer 始终取自正在重建的
 * 仓库实例（构造期间 {@code Minecraft.getMinecraft().getResourcePackRepository()} 尚为 null，
 * 不能使用）。
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
     * constructor located by {@link #findEntryConstructor} (the fake file is
     * never opened: the entry's fields are filled by
     * {@link BuiltinPackEntry#catframe$setupBuiltin}).
     */
    private static ResourcePackRepository.Entry createEntry(ResourcePackRepository repository,
            BuiltinPackDescriptor descriptor) {
        try {
            Constructor<ResourcePackRepository.Entry> constructor = findEntryConstructor();
            if (constructor == null) {
                return null;
            }
            constructor.setAccessible(true);
            ResourcePackRepository.Entry entry = constructor.newInstance(repository,
                    new File(BuiltinResourcePack.ROOT_DIR, descriptor.getId()));
            if (!(entry instanceof BuiltinPackEntry)) {
                LOGGER.error("Early mixins are not applied — cannot create a repository entry for built-in pack '{}'",
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
     * Looks up the vanilla-private {@code Entry(ResourcePackRepository, File)}
     * constructor — the real constructor behind the source-level
     * {@code private Entry(File)}. It is the only entry constructor present in
     * both layouts: the MCP recompile behind the development classes and the
     * obfuscated production jar (checked with javap against the production
     * jar). The javac access bridge next to it is deliberately ignored: its
     * synthetic dummy parameter is typed {@code Object} in the MCP recompile
     * but {@code ResourcePackRepository$1} in the obfuscated jar, and
     * depending on that synthetic member is exactly what broke the previous
     * exact-signature lookup. Returns {@code null} (logged with the actual
     * signatures) when the constructor is missing.
     */
    private static Constructor<ResourcePackRepository.Entry> findEntryConstructor() {
        try {
            return ResourcePackRepository.Entry.class
                    .getDeclaredConstructor(ResourcePackRepository.class, File.class);
        } catch (NoSuchMethodException missing) {
            LOGGER.error("No (ResourcePackRepository, File) constructor on {} — found {}",
                    ResourcePackRepository.Entry.class, describeConstructors());
            return null;
        }
    }

    /** Signature dump for diagnostics; only read when no constructor matched. */
    private static String describeConstructors() {
        StringBuilder signatures = new StringBuilder();
        for (Constructor<?> candidate : ResourcePackRepository.Entry.class.getDeclaredConstructors()) {
            if (signatures.length() > 0) {
                signatures.append(", ");
            }
            signatures.append('(');
            Class<?>[] parameterTypes = candidate.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i > 0) {
                    signatures.append(", ");
                }
                signatures.append(parameterTypes[i].getName());
            }
            signatures.append(')');
        }
        return signatures.toString();
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
