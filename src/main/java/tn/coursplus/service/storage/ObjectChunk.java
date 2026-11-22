package tn.coursplus.service.storage;

import java.io.InputStream;

/**
 * Un fichier (ou un morceau de fichier) prêt à être envoyé au navigateur.
 *
 * @param stream  les octets de {@code start} à {@code end} inclus (à fermer par l'appelant)
 * @param start   premier octet envoyé
 * @param end     dernier octet envoyé (inclus)
 * @param total   taille totale du fichier
 * @param partial vrai si seule une plage a été demandée (réponse HTTP 206)
 */
public record ObjectChunk(InputStream stream, long start, long end, long total, boolean partial) {

    public long length() {
        return end - start + 1;
    }
}
