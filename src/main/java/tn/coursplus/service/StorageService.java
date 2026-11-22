package tn.coursplus.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tn.coursplus.exception.ApiException;
import tn.coursplus.service.storage.ObjectChunk;
import tn.coursplus.service.storage.ObjectStore;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Enregistre et relit les vidéos et PDF. Où ils sont rangés (disque local ou Cloudflare R2) dépend de
 * app.storage.type, voir {@link ObjectStore}.
 * Les fichiers ne sont JAMAIS servis directement : ils passent par MediaController
 * qui vérifie les droits d'accès.
 */
@Service
public class StorageService {

    private static final Set<String> VIDEO_EXT = Set.of("mp4", "webm", "mov", "m4v");
    private static final Set<String> DOC_EXT = Set.of("pdf");

    private final ObjectStore store;

    public StorageService(ObjectStore store) {
        this.store = store;
    }

    public String saveVideo(MultipartFile file) {
        return save(file, "videos", VIDEO_EXT);
    }

    public String saveDocument(MultipartFile file) {
        return save(file, "documents", DOC_EXT);
    }

    private String save(MultipartFile file, String folder, Set<String> allowed) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Aucun fichier reçu");
        }
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        ext = ext == null ? "" : ext.toLowerCase();
        if (!allowed.contains(ext)) {
            throw ApiException.badRequest("Format non accepté. Formats autorisés : " + allowed);
        }
        try (InputStream in = file.getInputStream()) {
            return put(in, file.getSize(), folder, ext);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'enregistrer le fichier", e);
        }
    }

    /** Utilisé aussi par le chargement des données de démonstration. */
    public String store(InputStream in, String folder, String ext) throws IOException {
        return put(in, -1, folder, ext);
    }

    private String put(InputStream in, long length, String folder, String ext) throws IOException {
        String name = folder + "/" + UUID.randomUUID() + "." + ext;
        store.put(name, in, length);
        return name;
    }

    /**
     * Ouvre le fichier (en entier ou la plage demandée par l'en-tête Range).
     * Renvoie null s'il n'existe pas.
     */
    public ObjectChunk open(String storedName, String rangeHeader) {
        if (storedName == null) return null;
        return store.open(storedName, rangeHeader);
    }

    public void delete(String storedName) {
        if (storedName == null) return;
        store.delete(storedName);
    }
}
