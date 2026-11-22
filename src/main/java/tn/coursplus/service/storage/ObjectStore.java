package tn.coursplus.service.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Où sont physiquement rangés les fichiers (vidéos, PDF) : sur le disque ({@link LocalObjectStore})
 * ou dans un stockage compatible S3 comme Cloudflare R2 ({@link S3ObjectStore}).
 * Les clés sont de la forme {@code videos/<uuid>.mp4} et sont enregistrées telles quelles en base.
 */
public interface ObjectStore {

    /** Enregistre le contenu sous cette clé. {@code length} vaut -1 si la taille est inconnue. */
    void put(String key, InputStream in, long length) throws IOException;

    /**
     * Ouvre le fichier, en entier ou seulement la partie demandée par l'en-tête HTTP {@code Range}
     * (ex. {@code bytes=1000-}). Renvoie {@code null} si le fichier n'existe pas.
     *
     * @throws tn.coursplus.exception.ApiException (416) si la plage demandée est invalide
     */
    ObjectChunk open(String key, String rangeHeader);

    /** Supprime le fichier. Sans erreur s'il n'existe plus. */
    void delete(String key);
}
