package tn.coursplus.service.storage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.coursplus.exception.ApiException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste S3ObjectStore contre un faux serveur S3 en mémoire (PUT, GET avec Range, DELETE, erreurs XML).
 * Vérifie nos requêtes et l'analyse des réponses ; ne remplace pas un essai réel sur R2.
 */
class S3ObjectStoreTest {

    private static final String BUCKET = "coursplus-media";
    private static final Pattern RANGE = Pattern.compile("bytes=(\\d*)-(\\d*)");

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private volatile boolean ignoreRange = false;
    private HttpServer server;
    private S3ObjectStore store;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        store = new S3ObjectStore("http://127.0.0.1:" + server.getAddress().getPort(), "auto", BUCKET, "cle", "secret");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String prefix = "/" + BUCKET + "/";
        String path = ex.getRequestURI().getPath();
        if (!path.startsWith(prefix)) {
            reply(ex, 400, "text/plain", new byte[0]);
            return;
        }
        String key = path.substring(prefix.length());
        switch (ex.getRequestMethod()) {
            case "PUT" -> {
                byte[] body = ex.getRequestBody().readAllBytes();
                String sha = ex.getRequestHeaders().getFirst("x-amz-content-sha256");
                objects.put(key, sha != null && sha.startsWith("STREAMING") ? decodeAwsChunked(body) : body);
                reply(ex, 200, "text/plain", new byte[0]);
            }
            case "DELETE" -> {
                objects.remove(key);
                reply(ex, 204, "text/plain", new byte[0]);
            }
            case "GET" -> {
                byte[] data = objects.get(key);
                if (data == null) {
                    error(ex, 404, "NoSuchKey");
                    return;
                }
                String range = ex.getRequestHeaders().getFirst("Range");
                if (range == null || ignoreRange) {
                    reply(ex, 200, "video/mp4", data);
                    return;
                }
                Matcher m = RANGE.matcher(range);
                if (!m.matches()) {
                    error(ex, 416, "InvalidRange");
                    return;
                }
                long size = data.length;
                long start;
                long end;
                if (m.group(1).isEmpty()) { // bytes=-N : les N derniers octets
                    start = Math.max(0, size - Long.parseLong(m.group(2)));
                    end = size - 1;
                } else {
                    start = Long.parseLong(m.group(1));
                    end = m.group(2).isEmpty() ? size - 1 : Math.min(Long.parseLong(m.group(2)), size - 1);
                }
                if (start >= size) {
                    error(ex, 416, "InvalidRange");
                    return;
                }
                byte[] part = java.util.Arrays.copyOfRange(data, (int) start, (int) end + 1);
                ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + size);
                reply(ex, 206, "video/mp4", part);
            }
            default -> reply(ex, 405, "text/plain", new byte[0]);
        }
    }

    /** Le SDK envoie le corps en morceaux signés : "<taille hexa>;chunk-signature=...\r\n<données>\r\n" ... "0;...". */
    private static byte[] decodeAwsChunked(byte[] raw) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int pos = 0;
        while (pos < raw.length) {
            int eol = pos;
            while (raw[eol] != '\r') eol++;
            String header = new String(raw, pos, eol - pos, StandardCharsets.US_ASCII);
            int size = Integer.parseInt(header.substring(0, header.indexOf(';')), 16);
            if (size == 0) break;
            out.write(raw, eol + 2, size);
            pos = eol + 2 + size + 2;
        }
        return out.toByteArray();
    }

    private static void error(HttpExchange ex, int status, String code) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>" + code + "</Code><Message>" + code + "</Message></Error>";
        reply(ex, status, "application/xml", xml.getBytes(StandardCharsets.UTF_8));
    }

    private static void reply(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, status == 204 ? -1 : body.length == 0 ? -1 : body.length);
        if (body.length > 0 && status != 204) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
        ex.close();
    }

    private static String read(ObjectChunk chunk) throws IOException {
        try (var in = chunk.stream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void put(String key, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        store.put(key, new ByteArrayInputStream(bytes), bytes.length);
    }

    @Test
    void envoieEtRelitUnFichier() throws IOException {
        put("videos/a.mp4", "0123456789");
        assertArrayEquals("0123456789".getBytes(StandardCharsets.UTF_8), objects.get("videos/a.mp4"));

        ObjectChunk c = store.open("videos/a.mp4", null);
        assertFalse(c.partial());
        assertEquals(10, c.total());
        assertEquals(10, c.length());
        assertEquals("0123456789", read(c));
    }

    @Test
    void envoiSansTailleConnue() throws IOException {
        store.put("documents/b.pdf", new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), -1);
        assertEquals("abc", read(store.open("documents/b.pdf", null)));
    }

    @Test
    void lirePlageAvecDebutEtFin() throws IOException {
        put("videos/a.mp4", "0123456789");
        ObjectChunk c = store.open("videos/a.mp4", "bytes=2-5");
        assertTrue(c.partial());
        assertEquals(2, c.start());
        assertEquals(5, c.end());
        assertEquals(10, c.total());
        assertEquals("2345", read(c));
    }

    @Test
    void lirePlageOuverte() throws IOException {
        put("videos/a.mp4", "0123456789");
        ObjectChunk c = store.open("videos/a.mp4", "bytes=7-");
        assertEquals(7, c.start());
        assertEquals(9, c.end());
        assertEquals("789", read(c));
    }

    @Test
    void lireLesDerniersOctets() throws IOException {
        put("videos/a.mp4", "0123456789");
        ObjectChunk c = store.open("videos/a.mp4", "bytes=-4");
        assertEquals(6, c.start());
        assertEquals("6789", read(c));
    }

    @Test
    void decoupeLuiMemeSiLeStockageIgnoreLaPlage() throws IOException {
        put("videos/a.mp4", "0123456789");
        ignoreRange = true;
        ObjectChunk c = store.open("videos/a.mp4", "bytes=3-6");
        assertTrue(c.partial());
        assertEquals(3, c.start());
        assertEquals(6, c.end());
        assertEquals(10, c.total());
        assertEquals("3456", read(c));
    }

    @Test
    void plageHorsFichierRenvoie416() throws IOException {
        put("videos/a.mp4", "0123456789");
        ApiException e = assertThrows(ApiException.class, () -> store.open("videos/a.mp4", "bytes=50-60"));
        assertEquals(416, e.getStatus().value());
    }

    @Test
    void fichierAbsentRenvoieNull() {
        assertNull(store.open("videos/absent.mp4", null));
        assertNull(store.open("videos/absent.mp4", "bytes=0-1"));
    }

    @Test
    void supprimeUnFichier() throws IOException {
        put("videos/a.mp4", "0123456789");
        store.delete("videos/a.mp4");
        assertNull(store.open("videos/a.mp4", null));
        assertDoesNotThrow(() -> store.delete("videos/a.mp4"));
    }
}
