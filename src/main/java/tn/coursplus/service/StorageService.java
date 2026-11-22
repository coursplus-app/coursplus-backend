package tn.coursplus.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tn.coursplus.exception.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Enregistre les vidéos et PDF sur le disque (dossier app.storage.dir).
 * Les fichiers ne sont JAMAIS servis directement : ils passent par MediaController
 * qui vérifie les droits d'accès.
 * Plus tard on pourra remplacer ce service par un stockage cloud (S3, Bunny Stream...).
 */
@Service
public class StorageService {

    private static final Set<String> VIDEO_EXT = Set.of("mp4", "webm", "mov", "m4v");
    private static final Set<String> DOC_EXT = Set.of("pdf");

    private final Path root;

    public StorageService(@Value("${app.storage.dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("videos"));
        Files.createDirectories(root.resolve("documents"));
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
            return store(in, folder, ext);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'enregistrer le fichier", e);
        }
    }

    /** Utilisé aussi par le chargement des données de démonstration. */
    public String store(InputStream in, String folder, String ext) throws IOException {
        String name = folder + "/" + UUID.randomUUID() + "." + ext;
        Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    public Path resolve(String storedName) {
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.forbidden("Chemin invalide");
        }
        return p;
    }

    public void delete(String storedName) {
        if (storedName == null) return;
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException ignored) {
            // pas grave si le fichier n'existe plus
        }
    }
}
