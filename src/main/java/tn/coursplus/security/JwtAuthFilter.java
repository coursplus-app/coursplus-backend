package tn.coursplus.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.coursplus.entity.User;
import tn.coursplus.repository.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lit le jeton "Authorization: Bearer ..." et connecte l'utilisateur pour la requête.
 * Si le compte s'est connecté ailleurs entre-temps (sessionId différent), on renvoie 401
 * avec le code SESSION_REPLACED pour que le frontend affiche un message clair.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /**
     * Le flux SSE des notifications est terminé par un "async dispatch" : Spring Security
     * ré-autorise alors la requête, il faut donc relire le jeton (sinon faux 401 en fin de flux).
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional<Claims> claims = jwtService.parse(header.substring(7));
            if (claims.isPresent() && JwtService.TYPE_API.equals(claims.get().get("typ", String.class))) {
                Long userId = Long.valueOf(claims.get().getSubject());
                Optional<User> user = userRepository.findById(userId);
                if (user.isEmpty() || !user.get().isActive()) {
                    unauthorized(response, "ACCOUNT_DISABLED", "Compte introuvable ou désactivé");
                    return;
                }
                if (!Objects.equals(user.get().getSessionId(), claims.get().get("sid", String.class))) {
                    unauthorized(response, "SESSION_REPLACED",
                            "Ton compte a été connecté sur un autre appareil. Reconnecte-toi.");
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(
                        new AuthUser(user.get().getId(), user.get().getRole()), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.get().getRole().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":401,\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
