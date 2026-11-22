package tn.coursplus.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tn.coursplus.dto.AuthDtos.*;
import tn.coursplus.dto.UserDto;
import tn.coursplus.exception.ApiException;
import tn.coursplus.security.CurrentUser;
import tn.coursplus.security.RateLimiter;
import tn.coursplus.service.AuthService;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Limites anti « brute force » (voir RateLimiter)
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int LOGIN_MAX_PER_ACCOUNT = 5;   // échecs par (adresse IP + e-mail)
    private static final int LOGIN_MAX_PER_IP = 20;       // échecs par adresse IP, tous e-mails confondus
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final int REGISTER_MAX_PER_IP = 10;    // inscriptions par adresse IP et par heure

    private final AuthService authService;
    private final RateLimiter limiter;

    public AuthController(AuthService authService, RateLimiter limiter) {
        this.authService = authService;
        this.limiter = limiter;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        limitRegistrations(http);
        return authService.register(req);
    }

    @PostMapping("/register-teacher")
    public AuthResponse registerTeacher(@Valid @RequestBody TeacherRegisterRequest req, HttpServletRequest http) {
        limitRegistrations(http);
        return authService.registerTeacher(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String ip = RateLimiter.clientIp(http);
        String accountKey = "login:" + ip + "|" + req.email().trim().toLowerCase();
        String ipKey = "login-ip:" + ip;
        limiter.assertAllowed(accountKey, LOGIN_MAX_PER_ACCOUNT, LOGIN_WINDOW);
        limiter.assertAllowed(ipKey, LOGIN_MAX_PER_IP, LOGIN_WINDOW);
        try {
            AuthResponse res = authService.login(req);
            limiter.reset(accountKey); // connexion réussie : on repart de zéro
            return res;
        } catch (ApiException e) {
            if (e.getStatus() == HttpStatus.UNAUTHORIZED) { // seulement les mauvais identifiants
                limiter.record(accountKey, LOGIN_WINDOW);
                limiter.record(ipKey, LOGIN_WINDOW);
            }
            throw e;
        }
    }

    private void limitRegistrations(HttpServletRequest http) {
        String key = "register:" + RateLimiter.clientIp(http);
        limiter.assertAllowed(key, REGISTER_MAX_PER_IP, REGISTER_WINDOW);
        limiter.record(key, REGISTER_WINDOW);
    }

    @GetMapping("/me")
    public UserDto me() {
        return authService.me(CurrentUser.require().id());
    }

    @PostMapping("/logout")
    public void logout() {
        authService.logout(CurrentUser.require().id());
    }
}
