package tn.coursplus.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coursplus.dto.AuthDtos.*;
import tn.coursplus.dto.UserDto;
import tn.coursplus.entity.ApprovalStatus;
import tn.coursplus.entity.Role;
import tn.coursplus.entity.User;
import tn.coursplus.exception.ApiException;
import tn.coursplus.repository.UniversityRepository;
import tn.coursplus.repository.UserRepository;
import tn.coursplus.security.JwtService;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NotificationService notifications;

    public AuthService(UserRepository userRepository, UniversityRepository universityRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       NotificationService notifications) {
        this.userRepository = userRepository;
        this.universityRepository = universityRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.notifications = notifications;
    }

    /** Inscription publique : uniquement des étudiants. Les enseignants sont créés par l'admin. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.badRequest("Cet email est déjà utilisé");
        }
        User u = new User();
        u.setFullName(req.fullName().trim());
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setRole(Role.STUDENT);
        if (req.universityId() != null) {
            u.setUniversity(universityRepository.findById(req.universityId()).orElse(null));
        }
        u.setSessionId(UUID.randomUUID().toString());
        userRepository.save(u);
        return new AuthResponse(jwtService.createApiToken(u), UserDto.from(u));
    }

    /**
     * Candidature enseignant : compte créé en attente (PENDING). L'admin est notifié
     * et vérifie la compétence avant d'autoriser la création de cours.
     */
    @Transactional
    public AuthResponse registerTeacher(TeacherRegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.badRequest("Cet email est déjà utilisé");
        }
        User t = new User();
        t.setFullName(req.fullName().trim());
        t.setEmail(email);
        t.setPasswordHash(passwordEncoder.encode(req.password()));
        t.setRole(Role.TEACHER);
        t.setApprovalStatus(ApprovalStatus.PENDING);
        t.setSpecialty(req.specialty().trim());
        t.setBio(req.bio().trim());
        String link = req.credentialsLink() == null ? "" : req.credentialsLink().trim();
        t.setCredentialsLink(link.isEmpty() ? null : link);
        if (req.universityId() != null) {
            t.setUniversity(universityRepository.findById(req.universityId()).orElse(null));
        }
        t.setSessionId(UUID.randomUUID().toString());
        userRepository.save(t);

        notifications.notifyAdmins("TEACHER_PENDING", "Nouvelle candidature enseignant",
                t.getFullName() + " (" + t.getSpecialty() + ") demande à devenir enseignant.",
                "/admin?tab=teachers");
        return new AuthResponse(jwtService.createApiToken(t), UserDto.from(t));
    }

    /**
     * Connexion. Un nouveau sessionId est généré : l'appareil connecté avant
     * est automatiquement déconnecté (un compte = un appareil à la fois).
     */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        User u = userRepository.findByEmailIgnoreCase(req.email().trim())
                .filter(x -> passwordEncoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect"));
        if (!u.isActive()) {
            throw ApiException.forbidden("Ce compte est désactivé");
        }
        u.setSessionId(UUID.randomUUID().toString());
        userRepository.save(u);
        return new AuthResponse(jwtService.createApiToken(u), UserDto.from(u));
    }

    public UserDto me(Long userId) {
        return userRepository.findById(userId).map(UserDto::from)
                .orElseThrow(() -> ApiException.notFound("Utilisateur"));
    }

    @Transactional
    public void logout(Long userId) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setSessionId(UUID.randomUUID().toString());
            userRepository.save(u);
        });
    }
}
