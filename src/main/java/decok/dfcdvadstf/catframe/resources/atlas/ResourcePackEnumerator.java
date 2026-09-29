package decok.dfcdvadstf.catframe.resources.atlas;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resource-pack content enumerator — infrastructure for M2 definition-driven
 * sources.
 * <p>
 * The 1.7.10 {@code IResourceManager} cannot list directory contents (design-doc
 * constraint table), so DirectorySource / definition-file discovery is
 * implemented by directly enumerating reachable archives:
 * <ol>
 *   <li>all {@code java.class.path} entries (gradle runClient / IDE scenarios,
 *       including the CatFrame jar itself, the vanilla minecraft.jar, dependency
 *       jars and resource output directories);</li>
 *   <li>the CodeSource of {@code Minecraft.class} (launcher dynamic-classloader
 *       scenario, a fallback for locating the vanilla jar);</li>
 *   <li>the {@code .minecraft/resourcepacks/} directory (zip resource packs and
 *       folder packs with pack.mcmeta, enabled or not; priority is decided solely
 *       by getResource).</li>
 * </ol>
 * When nothing is reachable an empty list is returned — definition-driven
 * sources degrade naturally and model-driven refs serve as fallback (design doc:
 * "fall back to model-driven refs when pack traversal is unavailable").
 * <p>
 * Implemented with plain public APIs + JDK file enumeration; no reflection and
 * no Forge-internal API dependency.
 */
@SideOnly(Side.CLIENT)
public final class ResourcePackEnumerator {

    private ResourcePackEnumerator() {
    }

    /**
     * Enumerates resource paths across all reachable archives (deduplicated,
     * stable order).
     *
     * @param prefix path prefix (e.g. {@code "assets/"} or {@code "assets/minecraft/textures/items/"})
     * @return full relative paths matching the prefix (e.g. {@code "assets/minecraft/textures/items/apple.png"})
     */
    public static List<String> listAssets(String prefix) {
        Set<String> paths = new LinkedHashSet<>();
        for (File f : archives()) {
            if (f.isFile()) {
                listJar(f, prefix, paths);
            } else if (f.isDirectory()) {
                walk(new File(f, prefix), prefix, paths);
            }
        }
        return new ArrayList<>(paths);
    }

    /** Collects candidate archives: classpath entries + Minecraft.class CodeSource + the resourcepacks directory. */
    static List<File> archives() {
        Set<File> files = new LinkedHashSet<>();
        // 1. java.class.path (the full gradle runClient classpath; directory entries are walked directly)
        String cp = System.getProperty("java.class.path");
        if (cp != null) {
            for (String p : cp.split(Pattern.quote(File.pathSeparator))) {
                if (!p.isEmpty()) {
                    File f = new File(p);
                    if (f.exists()) {
                        files.add(f);
                    }
                }
            }
        }
        // 2. Minecraft.class CodeSource (in the launcher scenario java.class.path does not contain the game jar)
        addCodeSource(net.minecraft.client.Minecraft.class, files);
        // 3. resourcepacks directory (zip and folder packs; disabled packs are enumerated too, getResource decides priority)
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.mcDataDir != null) {
                File rp = new File(mc.mcDataDir, "resourcepacks");
                if (rp.isDirectory()) {
                    File[] list = rp.listFiles();
                    if (list != null) {
                        for (File f : list) {
                            if (f.isFile() && f.getName().toLowerCase().endsWith(".zip")) {
                                files.add(f);
                            } else if (f.isDirectory() && new File(f, "pack.mcmeta").isFile()) {
                                files.add(f);
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // resourcepacks directory unavailable → degrade silently (the classpath scan already covers most scenarios)
        }
        return new ArrayList<>(files);
    }

    private static void addCodeSource(Class<?> cls, Set<File> files) {
        try {
            URL url = cls.getProtectionDomain().getCodeSource().getLocation();
            if (url != null && "file".equals(url.getProtocol())) {
                File f = new File(url.toURI());
                if (f.exists()) {
                    files.add(f);
                }
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // CodeSource unresolvable → skip (non-fatal)
        }
    }

    /** jar/zip entry enumeration (prefix match, non-directories only, deduplicated). */
    private static void listJar(File jar, String prefix, Set<String> out) {
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory() && e.getName().startsWith(prefix)) {
                    out.add(e.getName());
                }
            }
        } catch (IOException ignored) {
            // Non-zip file (e.g. .pom/.txt mixed into the classpath) → skip
        }
    }

    /** Recursive directory enumeration (relative path = prefix + file path relative to root). */
    private static void walk(File root, String prefix, Set<String> out) {
        File[] list = root.listFiles();
        if (list == null) {
            return;
        }
        for (File f : list) {
            if (f.isDirectory()) {
                walk(f, prefix, out);
            } else {
                out.add(prefix + f.getName());
            }
        }
    }
}
