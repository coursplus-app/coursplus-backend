package tn.coursplus.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.coursplus.exception.ApiException;

import java.util.Optional;

/** Petit utilitaire pour récupérer l'utilisateur connecté depuis n'importe quel service. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser u) {
            return Optional.of(u);
        }
        return Optional.empty();
    }

    public static AuthUser require() {
        return get().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Connecte-toi d'abord"));
    }
}
