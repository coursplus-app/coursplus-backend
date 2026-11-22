package tn.coursplus.service.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tn.coursplus.exception.ApiException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalObjectStoreTest {

    private static final String CONTENT = "0123456789"; // 10 octets

    @TempDir
    Path dir;

    private LocalObjectStore store;

    @BeforeEach
    void setUp() throws IOException {
        store = new LocalObjectStore(dir.toString());
        store.put("videos/a.mp4", new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8)), 10);
    }

    private static String read(ObjectChunk chunk) throws IOException {
        try (var in = chunk.stream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void sansRangeRenvoieToutLeFichier() throws IOException {
        ObjectChunk c = store.open("videos/a.mp4", null);
        assertFalse(c.partial());
        assertEquals(10, c.total());
        assertEquals(10, c.length());
        assertEquals(CONTENT, read(c));
    }

    @Test
    void plageAvecDebutEtFin() throws IOException {
        ObjectChunk c = store.open("videos/a.mp4", "bytes=2-5");
        assertTrue(c.partial());
        assertEquals(2, c.start());
        assertEquals(5, c.end());
        assertEquals(10, c.total());
        assertEquals("2345", read(c));
    }

    @Test
    void plageOuverteJusquALaFin() throws IOException {
        ObjectChunk c = store.open("videos/a.mp4", "bytes=7-");
        assertEquals(7, c.start());
        assertEquals(9, c.end());
        assertEquals("789", read(c));
    }

    @Test
    void plageDesDerniersOctets() throws IOException {
        ObjectChunk c = store.open("videos/a.mp4", "bytes=-4");
        assertEquals(6, c.start());
        assertEquals("6789", read(c));
    }

    @Test
    void finDepasseLaTailleEstRamenee() throws IOException {
        ObjectChunk c = store.open("videos/a.mp4", "bytes=8-100");
        assertEquals(9, c.end());
        assertEquals("89", read(c));
    }

    @Test
    void plageHorsFichierRenvoie416() {
        ApiException e = assertThrows(ApiException.class, () -> store.open("videos/a.mp4", "bytes=50-60"));
        assertEquals(416, e.getStatus().value());
    }

    @Test
    void plageIllisibleRenvoie416() {
        ApiException e = assertThrows(ApiException.class, () -> store.open("videos/a.mp4", "n'importe quoi"));
        assertEquals(416, e.getStatus().value());
    }

    @Test
    void fichierAbsentRenvoieNull() {
        assertNull(store.open("videos/absent.mp4", null));
    }

    @Test
    void supprimeEtTolereUnFichierDejaAbsent() {
        store.delete("videos/a.mp4");
        assertNull(store.open("videos/a.mp4", null));
        assertDoesNotThrow(() -> store.delete("videos/a.mp4"));
    }

    @Test
    void refuseDeSortirDuDossier() {
        assertThrows(ApiException.class, () -> store.open("../secret.txt", null));
    }
}
