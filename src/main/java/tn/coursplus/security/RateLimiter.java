package tn.coursplus.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tn.coursplus.exception.ApiException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limiteur de tentatives en mémoire (fenêtre fixe), sans dépendance externe.
 * Sert à freiner le « brute force » sur la connexion et le spam d'inscriptions.
 * Limite : l'état est local à ce serveur (réinitialisé au redémarrage). Pour plusieurs
 * instances, passer à Redis ou à un filtre au niveau du reverse proxy.
 */
@Component
public class RateLimiter {

    private record Bucket(long windowStart, int count) {
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Lève une 429 si la clé a déjà atteint {@code max} tentatives dans la fenêtre. */
    public void assertAllowed(String key, int max, Duration window) {
        Bucket b = buckets.get(key);
        long now = System.currentTimeMillis();
        if (b != null && now - b.windowStart() < window.toMillis() && b.count() >= max) {
            long minutes = Math.max(1, (window.toMillis() - (now - b.windowStart()) + 59_999) / 60_000);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives. Réessaie dans " + minutes + " minute" + (minutes > 1 ? "s" : "") + ".");
        }
    }

    /** Compte une tentative (échec de connexion, inscription…). */
    public void record(String key, Duration window) {
        long now = System.currentTimeMillis();
        if (buckets.size() > 10_000) {
            buckets.entrySet().removeIf(e -> now - e.getValue().windowStart() > window.toMillis());
        }
        buckets.merge(key, new Bucket(now, 1),
                (old, fresh) -> now - old.windowStart() >= window.toMillis() ? fresh : new Bucket(old.windowStart(), old.count() + 1));
    }

    public void reset(String key) {
        buckets.remove(key);
    }

    /**
     * Adresse du client. On n'utilise PAS l'en-tête X-Forwarded-For directement (falsifiable) :
     * derrière un reverse proxy, activer server.forward-headers-strategy=framework pour que Spring l'applique.
     */
    public static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
