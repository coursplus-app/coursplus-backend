package tn.coursplus.service.storage;

import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import tn.coursplus.exception.ApiException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Stockage sur le disque du serveur (dossier app.storage.dir) : développement et petits serveurs. */
public class LocalObjectStore implements ObjectStore {

    private final Path root;

    public LocalObjectStore(String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("videos"));
        Files.createDirectories(root.resolve("documents"));
    }

    @Override
    public void put(String key, InputStream in, long length) throws IOException {
        Files.copy(in, resolve(key), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public ObjectChunk open(String key, String rangeHeader) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            long size = Files.size(path);
            long start = 0;
            long end = size - 1;
            boolean partial = false;
            if (rangeHeader != null && !rangeHeader.isBlank()) {
                HttpRange range = firstRange(rangeHeader);
                start = range.getRangeStart(size);
                end = range.getRangeEnd(size);
                if (start >= size || start > end) {
                    throw rangeNotSatisfiable();
                }
                partial = true;
            }
            InputStream in = Files.newInputStream(path);
            try {
                in.skipNBytes(start);
            } catch (IOException | RuntimeException e) {
                in.close();
                throw e;
            }
            return new ObjectChunk(new LimitedInputStream(in, end - start + 1), start, end, size, partial);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de lire le fichier " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            // pas grave si le fichier n'existe plus
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.forbidden("Chemin invalide");
        }
        return p;
    }

    /** Lit la première plage de l'en-tête Range (un lecteur vidéo n'en demande qu'une à la fois). */
    static HttpRange firstRange(String rangeHeader) {
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            if (ranges.isEmpty()) {
                throw rangeNotSatisfiable();
            }
            return ranges.get(0);
        } catch (IllegalArgumentException e) {
            throw rangeNotSatisfiable();
        }
    }

    static ApiException rangeNotSatisfiable() {
        return new ApiException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Plage demandée invalide");
    }

    /** Ne laisse passer que les {@code limit} premiers octets du flux. */
    static class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = super.read();
            if (b >= 0) remaining--;
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int n = super.read(buf, off, (int) Math.min(len, remaining));
            if (n > 0) remaining -= n;
            return n;
        }
    }
}
