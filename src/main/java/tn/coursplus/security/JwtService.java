package tn.coursplus.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.coursplus.entity.User;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Crée et vérifie les jetons JWT.
 * - jeton "api"   : envoyé dans l'en-tête Authorization pour toutes les requêtes
 * - jeton "media" : court, mis dans l'URL d'une vidéo (la balise <video> ne peut pas envoyer d'en-tête)
 */
@Service
public class JwtService {

    public static final String TYPE_API = "api";
    public static final String TYPE_MEDIA = "media";

    private final SecretKey key;
    private final Duration apiTtl;
    private final Duration mediaTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-hours}") long hours,
                      @Value("${app.jwt.media-expiration-minutes}") long mediaMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.apiTtl = Duration.ofHours(hours);
        this.mediaTtl = Duration.ofMinutes(mediaMinutes);
    }

    public String createApiToken(User user) {
        return build(user, TYPE_API, apiTtl, null, null);
    }

    /** Jeton valable uniquement pour une ressource précise (ex. "lesson:12"). */
    public String createMediaToken(User user, String resource, String fingerprint) {
        return build(user, TYPE_MEDIA, mediaTtl, resource, fingerprint);
    }

    private String build(User user, String type, Duration ttl, String resource, String fingerprint) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .claim("sid", user.getSessionId())
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (resource != null) {
            builder.claim("res", resource);
            // Empreinte du navigateur : le lien copié dans un autre navigateur est refusé
            builder.claim("fp", fingerprint);
        }
        return builder.signWith(key).compact();
    }

    /** Durée de validité d'un jeton media, en secondes (le lecteur le renouvelle avant l'expiration). */
    public long mediaTtlSeconds() {
        return mediaTtl.toSeconds();
    }

    /** Empreinte stable du navigateur (hash du User-Agent). Dissuade le partage du lien, sans casser le changement de réseau wifi/4G. */
    public static String fingerprint(jakarta.servlet.http.HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((ua == null ? "" : ua).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h, 0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Renvoie les "claims" si le jeton est valide (signature + date), sinon vide. */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
