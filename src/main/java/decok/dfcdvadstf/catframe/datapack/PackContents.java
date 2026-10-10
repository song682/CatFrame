package decok.dfcdvadstf.catframe.datapack;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Factory plus the two {@link PackContent} implementations: directory-backed
 * and zip/jar-backed.
 * <p>
 * Both implementations are defensive by design: an unreadable file reads as
 * {@code null}, a broken archive lists as empty. The callers turn those into
 * report entries. Archive contents are enumerated once and cached, because the
 * same pack is typically listed for several prefixes during one apply pass
 * (namespace scan, then per-type scans).
 * </p>
 * <p>
 * Text is always decoded as UTF-8 explicitly — never through the platform
 * default encoding, so CJK content survives on non-UTF-8 systems.
 * </p>
 */
public final class PackContents {

    /** Shared UTF-8 charset (Java 6 compatible lookup). */
    public static final Charset UTF_8 = Charset.forName("UTF-8");

    private PackContents() {
    }

    /** Content backed by a directory on disk. */
    public static PackContent directory(File root) {
        return new DirectoryContent(root);
    }

    /**
     * Content backed by a {@code datapacks/<name>/} subtree inside a zip/jar.
     *
     * @param archive    the jar/zip file
     * @param rootPrefix entry-name prefix of the pack root, e.g. {@code "datapacks/mypack/"}
     */
    public static PackContent archive(File archive, String rootPrefix) {
        return new ArchiveContent(archive, rootPrefix);
    }

    /** Reads a whole stream as UTF-8 text. */
    static String readStream(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = stream.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), UTF_8);
    }

    private static final class DirectoryContent implements PackContent {

        private final File root;

        DirectoryContent(File root) {
            this.root = root;
        }

        @Override
        public List<String> list(String prefix) {
            List<String> out = new ArrayList<>();
            File start = prefix.isEmpty() ? root : new File(root, prefix);
            walk(start, prefix, out);
            Collections.sort(out);
            return out;
        }

        private void walk(File dir, String prefix, List<String> out) {
            File[] children = dir.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                if (child.isDirectory()) {
                    walk(child, prefix + child.getName() + "/", out);
                } else {
                    out.add(prefix + child.getName());
                }
            }
        }

        @Override
        public String read(String path) {
            File file = new File(root, path);
            if (!file.isFile()) {
                return null;
            }
            InputStream stream = null;
            try {
                stream = new FileInputStream(file);
                return readStream(stream);
            } catch (IOException e) {
                return null;
            } finally {
                close(stream);
            }
        }

        @Override
        public String describe() {
            return root.getPath();
        }
    }

    private static final class ArchiveContent implements PackContent {

        private final File archive;
        private final String rootPrefix;

        /** Lazily cached full entry listing (root prefix stripped, sorted). */
        private List<String> entries;

        ArchiveContent(File archive, String rootPrefix) {
            this.archive = archive;
            this.rootPrefix = rootPrefix;
        }

        @Override
        public List<String> list(String prefix) {
            List<String> out = new ArrayList<>();
            for (String entry : allEntries()) {
                if (entry.startsWith(prefix)) {
                    out.add(entry);
                }
            }
            return out;
        }

        private synchronized List<String> allEntries() {
            if (entries != null) {
                return entries;
            }
            List<String> out = new ArrayList<>();
            ZipFile zip = null;
            try {
                zip = new ZipFile(archive);
                Enumeration<? extends ZipEntry> enumeration = zip.entries();
                while (enumeration.hasMoreElements()) {
                    ZipEntry entry = enumeration.nextElement();
                    String name = entry.getName();
                    if (!entry.isDirectory() && name.startsWith(rootPrefix)) {
                        out.add(name.substring(rootPrefix.length()));
                    }
                }
            } catch (IOException ignored) {
                // broken archive -> empty listing; the metadata read reports the problem
            } finally {
                close(zip);
            }
            Collections.sort(out);
            entries = out;
            return entries;
        }

        @Override
        public String read(String path) {
            ZipFile zip = null;
            InputStream stream = null;
            try {
                zip = new ZipFile(archive);
                ZipEntry entry = zip.getEntry(rootPrefix + path);
                if (entry == null) {
                    return null;
                }
                stream = zip.getInputStream(entry);
                return readStream(stream);
            } catch (IOException e) {
                return null;
            } finally {
                close(stream);
                close(zip);
            }
        }

        @Override
        public String describe() {
            return archive.getPath() + "!" + rootPrefix;
        }
    }

    private static void close(ZipFile zip) {
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException ignored) {
                // best-effort close
            }
        }
    }

    private static void close(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
                // best-effort close
            }
        }
    }
}
