package tn.coursplus.security;

import org.junit.jupiter.api.Test;
import tn.coursplus.exception.ApiException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private final Duration window = Duration.ofMinutes(15);

    @Test
    void bloqueApresLeMaximumDEchecs() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.assertAllowed("k", 5, window); // autorisé tant que < 5 échecs
            limiter.record("k", window);
        }
        ApiException e = assertThrows(ApiException.class, () -> limiter.assertAllowed("k", 5, window));
        assertEquals(429, e.getStatus().value());
    }

    @Test
    void reinitialiseApresUneConnexionReussie() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 5; i++) limiter.record("k", window);
        limiter.reset("k");
        assertDoesNotThrow(() -> limiter.assertAllowed("k", 5, window));
    }

    @Test
    void lesClesSontIndependantes() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 5; i++) limiter.record("a", window);
        assertDoesNotThrow(() -> limiter.assertAllowed("b", 5, window));
    }

    @Test
    void empreinteDuNavigateurEstStableEtDependDuUserAgent() {
        var r1 = new org.springframework.mock.web.MockHttpServletRequest();
        r1.addHeader("User-Agent", "Chrome");
        var r2 = new org.springframework.mock.web.MockHttpServletRequest();
        r2.addHeader("User-Agent", "Chrome");
        var r3 = new org.springframework.mock.web.MockHttpServletRequest();
        r3.addHeader("User-Agent", "Firefox");
        assertEquals(JwtService.fingerprint(r1), JwtService.fingerprint(r2));
        assertNotEquals(JwtService.fingerprint(r1), JwtService.fingerprint(r3));
    }
}
