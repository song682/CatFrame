package decok.dfcdvadstf.catframe.datagen;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe output cache: skips writes when the target already holds the
 * exact same content (SHA-1 based), providing incremental regeneration.
 * <p>
 * Mirrors the modern Minecraft {@code CachedOutput} idea without the on-disk
 * cache file: within one run the in-memory map deduplicates identical writes,
 * and against the filesystem an existing file with the same hash is left
 * untouched. Repeated full runs therefore cost about as much as an incremental
 * one, which matters at million-scale resource counts.
 * <p>
 * 线程安全的输出缓存：当目标文件已持有完全相同内容（基于 SHA-1）时跳过写入，
 * 实现增量再生成。仿照高版本 {@code CachedOutput} 思路但无需磁盘缓存文件：
 * 单次运行内内存 Map 去重相同写入，对文件系统则保留哈希相同的既有文件不动。
 * 因此重复全量运行的代价趋近增量运行——这对百万级资源规模至关重要。
 */
public class CachedOutput {

    /** in-run path → SHA-1, deduplicates concurrent identical writes */
    private final ConcurrentHashMap<String, String> written = new ConcurrentHashMap<>();

    private final AtomicInteger writtenCount = new AtomicInteger();
    private final AtomicInteger skippedCount = new AtomicInteger();

    /**
     * Write bytes to a file unless it already contains the exact same content.
     * Thread-safe: concurrent calls for the same path are deduplicated.
     *
     * @param path target file path
     * @param data file content
     * @return {@code true} if the file was (re)written, {@code false} if skipped
     */
    public boolean writeIfNeeded(Path path, byte[] data) {
        String key = path.toString();
        String hash = Hashing.sha1().hashBytes(data).toString();

        String seen = written.putIfAbsent(key, hash);
        if (seen != null) {
            if (seen.equals(hash)) {
                skippedCount.incrementAndGet();
                return false;
            }
            // Same path written twice with different content within one run —
            // last writer wins; recompute and overwrite.
            written.put(key, hash);
        } else if (fileAlreadyMatches(path, hash)) {
            skippedCount.incrementAndGet();
            return false;
        }

        try {
            Files.createDirectories(path.getParent());
            Files.write(path, data);
            writtenCount.incrementAndGet();
            return true;
        } catch (IOException e) {
            throw new DatagenException("Failed to write " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience overload: hash a plain string payload.
     *
     * @param path target file path
     * @param text UTF-8 file content
     * @return {@code true} if written, {@code false} if skipped
     */
    public boolean writeIfNeeded(Path path, String text) {
        return writeIfNeeded(path, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Hash a payload while streaming, without buffering the full byte array twice.
     *
     * @param path   target file path
     * @param writer callback that writes the payload to the provided stream
     * @return {@code true} if written, {@code false} if skipped
     */
    public boolean writeHashed(Path path, IoWriter writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HashingOutputStream hashed = new HashingOutputStream(Hashing.sha1(), bytes);
        try {
            writer.write(hashed);
            hashed.close();
        } catch (IOException e) {
            throw new DatagenException("Failed to hash payload for " + path + ": " + e.getMessage(), e);
        }
        return writeIfNeeded(path, bytes.toByteArray());
    }

    private boolean fileAlreadyMatches(Path path, String hash) {
        if (!Files.isRegularFile(path)) return false;
        try {
            return Hashing.sha1().hashBytes(Files.readAllBytes(path)).toString().equals(hash);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Total number of files actually written in this run.
     *
     * @return written count
     */
    public int writtenCount() {
        return writtenCount.get();
    }

    /**
     * Total number of files skipped because content was unchanged.
     *
     * @return skipped count
     */
    public int skippedCount() {
        return skippedCount.get();
    }

    /** Callback that streams payload bytes. */
    @FunctionalInterface
    public interface IoWriter {
        void write(OutputStream out) throws IOException;
    }

    /**
     * Unchecked exception for datagen I/O failures.
     */
    public static class DatagenException extends RuntimeException {
        public DatagenException(String message) {
            super(message);
        }

        public DatagenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
