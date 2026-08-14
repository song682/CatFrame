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
 * 资源包内容枚举器 —— M2 定义驱动源的基础设施。
 * <p>
 * 1.7.10 的 {@code IResourceManager} 无法列出目录内容（设计文档约束表），
 * 故 DirectorySource / 定义文件发现通过直接枚举可达归档实现：
 * <ol>
 *   <li>{@code java.class.path} 全部条目（gradle runClient / IDE 场景，含 CatFrame
 *       自身 jar、原版 minecraft.jar、各依赖 jar、资源输出目录）；</li>
 *   <li>{@code Minecraft.class} 的 CodeSource（launcher 动态 classloader 场景，
 *       兜底定位原版 jar）；</li>
 *   <li>{@code .minecraft/resourcepacks/} 目录（zip 资源包与带 pack.mcmeta 的
 *       文件夹资源包，含启用/未启用全部，优先级由 getResource 统一裁决）。</li>
 * </ol>
 * 全部不可达时返回空列表 —— 定义驱动源自然降级，模型驱动引用兜底（设计文档
 * "fall back to model-driven refs when pack traversal is unavailable"）。
 * <p>
 * 纯公共 API + JDK 文件枚举实现，无反射、无 Forge 内部 API 依赖。
 *
 * <p>Enumerates reachable archives/classpath entries to list resource paths —
 * the 1.7.10 substitute for the modern {@code ResourceManager.listResources}.
 */
@SideOnly(Side.CLIENT)
public final class ResourcePackEnumerator {

    private ResourcePackEnumerator() {
    }

    /**
     * 枚举所有可达归档中的资源路径（去重、稳定顺序）。
     *
     * @param prefix 路径前缀（形如 {@code "assets/"} 或 {@code "assets/minecraft/textures/items/"}）
     * @return 匹配前缀的完整相对路径列表（如 {@code "assets/minecraft/textures/items/apple.png"}）
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

    /** 收集候选归档：classpath 条目 + Minecraft.class CodeSource + resourcepacks 目录。 */
    static List<File> archives() {
        Set<File> files = new LinkedHashSet<>();
        // 1. java.class.path（gradle runClient 的 classpath 全量；目录条目直接遍历）
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
        // 2. Minecraft.class CodeSource（launcher 场景 java.class.path 不含游戏 jar）
        addCodeSource(net.minecraft.client.Minecraft.class, files);
        // 3. resourcepacks 目录（zip 与文件夹资源包；未启用包也枚举，优先级由 getResource 裁决）
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
            // resourcepacks 目录不可用 → 静默降级（classpath 扫描已覆盖大部分场景）
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
            // CodeSource 不可解析 → 跳过（不致命）
        }
    }

    /** jar/zip 条目枚举（前缀匹配、非目录、去重）。 */
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
            // 非 zip 文件（如 .pom/.txt 混入 classpath）→ 跳过
        }
    }

    /** 目录递归枚举（相对路径 = prefix + 文件相对 root 的路径）。 */
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
