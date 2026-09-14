package decok.dfcdvadstf.catframe.resources.builtin;

import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.util.ChatComponentTranslation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * {@code IResourcePack} implementation serving one built-in pack out of the
 * classpath folder {@code builtin_packs/<id>} — inside the mod jar in
 * production, inside the resources output during development.
 * <p>
 * The pack is intentionally not {@code Closeable}: the resource manager may
 * close packs it no longer needs, and a classpath pack holds no file handle to
 * release.
 * <p>
 * {@code IResourcePack} 实现：从 classpath 目录 {@code builtin_packs/<id>} 提供单个内置包——
 * 生产环境位于模组 jar 内，开发环境位于 resources 输出目录。本包刻意不实现
 * {@code Closeable}：资源管理器会关闭不再需要的包，而 classpath 包本就没有需要释放的
 * 文件句柄。
 */
public class BuiltinResourcePack extends AbstractResourcePack {

    /** Classpath folder that contains all built-in packs. */
    public static final String ROOT_DIR = "builtin_packs";

    private final BuiltinPackDescriptor descriptor;
    private final String root;

    public BuiltinResourcePack(BuiltinPackDescriptor descriptor) {
        // The inherited file field is never read from disk; it only gives the
        // pack a name in vanilla log output (getPackName is overridden below).
        super(new File(ROOT_DIR, descriptor.getId()));
        this.descriptor = descriptor;
        this.root = ROOT_DIR + "/" + descriptor.getId();
    }

    @Override
    protected InputStream getInputStreamByName(String name) throws IOException {
        InputStream stream = BuiltinResourcePack.class.getClassLoader().getResourceAsStream(this.root + "/" + name);
        if (stream == null) {
            throw new FileNotFoundException(
                    "Resource '" + name + "' not found in built-in resource pack '" + this.descriptor.getId() + "'");
        }
        return stream;
    }

    @Override
    protected boolean hasResourceName(String name) {
        return BuiltinResourcePack.class.getClassLoader().getResource(this.root + "/" + name) != null;
    }

    /**
     * Stable, never-localized name — the persistence key of the pack, see
     * {@link BuiltinPackDescriptor#getPackName()}.
     */
    @Override
    public String getPackName() {
        return this.descriptor.getPackName();
    }

    /**
     * Reads {@code pack.mcmeta}; when the pack ships none (or an unreadable
     * one), metadata is synthesized from the descriptor description key so the
     * GUI still shows a localized description instead of the vanilla
     * "Invalid pack.mcmeta" message.
     */
    @Override
    public IMetadataSection getPackMetadata(IMetadataSerializer serializer, String section) throws IOException {
        try {
            IMetadataSection metadata = super.getPackMetadata(serializer, section);
            if (metadata != null) {
                return metadata;
            }
        } catch (IOException ignored) {
            // missing or unreadable pack.mcmeta — fall through to the synthesized metadata
        } catch (RuntimeException ignored) {
            // malformed pack.mcmeta (JsonParseException) — same fallback
        }
        return new PackMetadataSection(new ChatComponentTranslation(this.descriptor.getDescriptionKey()), 1);
    }

    @Override
    public Set getResourceDomains() {
        Set<String> domains = new LinkedHashSet<>();
        collectDomains(domains);
        return domains;
    }

    /**
     * Collects the domains under {@code builtin_packs/<id>/assets}. Two anchors
     * are tried because classpath layouts differ: jars built by Gradle keep
     * directory entries, but some tooling strips them — in that case a real file
     * entry ({@code pack.mcmeta}) is used to locate the pack root instead.
     * <p>
     * 扫描 {@code builtin_packs/<id>/assets} 下的域（domain）。由于 classpath 布局可能不同，
     * 这里尝试两个锚点：Gradle 构建的 jar 保留目录条目；若目录条目被剥离，则改用真实文件
     * 条目（{@code pack.mcmeta}）定位包根。
     */
    private void collectDomains(Set<String> domains) {
        for (URL anchor : resources(this.root + "/assets")) {
            collectDomainsFromAnchor(anchor, true, domains);
        }
        if (!domains.isEmpty()) {
            return;
        }
        for (URL anchor : resources(this.root + "/pack.mcmeta")) {
            collectDomainsFromAnchor(anchor, false, domains);
        }
    }

    private void collectDomainsFromAnchor(URL anchor, boolean anchorIsAssets, Set<String> domains) {
        try {
            if ("file".equals(anchor.getProtocol())) {
                File anchorFile = new File(anchor.toURI());
                File assetsDir = anchorIsAssets ? anchorFile : new File(anchorFile.getParentFile(), "assets");
                addDirectoryDomains(assetsDir, domains);
            } else if ("jar".equals(anchor.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) anchor.openConnection();
                connection.setUseCaches(false);
                JarFile jar = connection.getJarFile();
                try {
                    addJarDomains(jar, domains);
                } finally {
                    jar.close();
                }
            }
        } catch (Exception ignored) {
            // Best effort: a pack whose enumeration fails simply contributes no domains
        }
    }

    private void addJarDomains(JarFile jar, Set<String> domains) {
        String prefix = this.root + "/assets/";
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (!name.startsWith(prefix)) {
                continue;
            }
            String rest = name.substring(prefix.length());
            int slash = rest.indexOf('/');
            addDomain(slash < 0 ? rest : rest.substring(0, slash), domains);
        }
    }

    private static void addDirectoryDomains(File assetsDir, Set<String> domains) {
        if (assetsDir == null || !assetsDir.isDirectory()) {
            return;
        }
        File[] children = assetsDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                addDomain(child.getName(), domains);
            }
        }
    }

    private static void addDomain(String domain, Set<String> domains) {
        if (!domain.isEmpty() && domain.equals(domain.toLowerCase(Locale.ROOT))) {
            domains.add(domain);
        }
    }

    private static Iterable<URL> resources(String path) {
        try {
            return Collections.list(BuiltinResourcePack.class.getClassLoader().getResources(path));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
