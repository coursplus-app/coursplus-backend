package tn.coursplus.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coursplus.dto.AdminDtos.*;
import tn.coursplus.dto.CatalogDtos.*;
import tn.coursplus.dto.PaymentDtos.PaymentDto;
import tn.coursplus.dto.UserDto;
import tn.coursplus.entity.*;
import tn.coursplus.exception.ApiException;
import tn.coursplus.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final TrackRepository trackRepository;
    private final CourseRepository courseRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;
    private final BigDecimal teacherShare;
    private final NotificationService notifications;

    public AdminService(UserRepository userRepository, UniversityRepository universityRepository,
                        TrackRepository trackRepository, CourseRepository courseRepository,
                        PaymentRepository paymentRepository, PasswordEncoder passwordEncoder,
                        @Value("${app.teacher-share}") BigDecimal teacherShare,
                        NotificationService notifications) {
        this.notifications = notifications;
        this.userRepository = userRepository;
        this.universityRepository = universityRepository;
        this.trackRepository = trackRepository;
        this.courseRepository = courseRepository;
        this.paymentRepository = paymentRepository;
        this.passwordEncoder = passwordEncoder;
        this.teacherShare = teacherShare;
    }

    @Transactional(readOnly = true)
    public OverviewDto overview() {
        List<Payment> all = paymentRepository.findAll();
        BigDecimal gross = all.stream().filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Part plateforme : 30 % des ventes attribuées à un enseignant + 100 % des abonnements filière
        // (à redistribuer ensuite manuellement aux enseignants de la filière)
        BigDecimal teachers = all.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID && p.getTeacher() != null)
                .map(p -> p.getAmount().multiply(teacherShare))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OverviewDto(
                userRepository.countByRole(Role.STUDENT),
                userRepository.countByRole(Role.TEACHER),
                courseRepository.countByPublishedTrue(),
                all.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).count(),
                all.stream().filter(p -> p.getStatus() == PaymentStatus.PAID).count(),
                gross,
                gross.subtract(teachers).setScale(3, RoundingMode.HALF_UP),
                userRepository.countByRoleAndApprovalStatus(Role.TEACHER, ApprovalStatus.PENDING));
    }

    // ---------- Utilisateurs ----------

    public List<UserDto> users(Role role) {
        List<User> list = role == null ? userRepository.findAll() : userRepository.findByRoleOrderByFullNameAsc(role);
        return list.stream().map(UserDto::from).toList();
    }

    @Transactional
    public UserDto createTeacher(CreateTeacherRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.badRequest("Cet email est déjà utilisé");
        }
        User t = new User();
        t.setFullName(req.fullName().trim());
        t.setEmail(email);
        t.setPasswordHash(passwordEncoder.encode(req.password()));
        t.setRole(Role.TEACHER);
        t.setApprovalStatus(ApprovalStatus.APPROVED); // créé par l'admin : déjà validé
        t.setSpecialty(req.specialty());
        t.setBio(req.bio());
        t.setMonthlyPrice(req.monthlyPrice());
        t.setSessionId(UUID.randomUUID().toString());
        return UserDto.from(userRepository.save(t));
    }

    /** Valide ou refuse une candidature enseignant, puis prévient l'enseignant. */
    @Transactional
    public UserDto reviewTeacher(Long teacherId, boolean approve, String note) {
        User t = userRepository.findById(teacherId)
                .filter(u -> u.getRole() == Role.TEACHER)
                .orElseThrow(() -> ApiException.notFound("Enseignant"));
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 500) cleanNote = cleanNote.substring(0, 500);

        t.setApprovalStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        t.setReviewNote(approve ? null : cleanNote);
        userRepository.save(t);

        if (approve) {
            notifications.notifyUser(t.getId(), "TEACHER_APPROVED", "Compte enseignant validé",
                    "Bienvenue ! Tu peux maintenant créer et publier tes cours.", "/enseignant");
        } else {
            notifications.notifyUser(t.getId(), "TEACHER_REJECTED", "Candidature refusée",
                    cleanNote != null ? cleanNote : "Ta candidature n'a pas été retenue pour le moment.",
                    "/enseignant");
        }
        return UserDto.from(t);
    }

    @Transactional
    public UserDto setActive(Long userId, boolean active) {
        User u = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Utilisateur"));
        if (u.getRole() == Role.ADMIN && !active) {
            throw ApiException.badRequest("On ne peut pas désactiver un administrateur");
        }
        u.setActive(active);
        u.setSessionId(UUID.randomUUID().toString()); // déconnecte l'utilisateur
        return UserDto.from(userRepository.save(u));
    }

    // ---------- Universités et filières ----------

    @Transactional
    public UniversityDto createUniversity(UniversityRequest req) {
        if (universityRepository.findByCodeIgnoreCase(req.code().trim()).isPresent()) {
            throw ApiException.badRequest("Ce code existe déjà");
        }
        University u = universityRepository.save(new University(req.code().trim().toUpperCase(), req.name().trim()));
        return new UniversityDto(u.getId(), u.getCode(), u.getName(), List.of());
    }

    @Transactional
    public TrackDto createTrack(TrackRequest req) {
        University u = universityRepository.findById(req.universityId())
                .orElseThrow(() -> ApiException.notFound("Université"));
        return TrackDto.from(trackRepository.save(new Track(req.name().trim(), u, req.monthlyPrice())));
    }

    @Transactional
    public TrackDto updateTrackPrice(Long trackId, BigDecimal price) {
        Track t = trackRepository.findById(trackId).orElseThrow(() -> ApiException.notFound("Filière"));
        t.setMonthlyPrice(price);
        return TrackDto.from(trackRepository.save(t));
    }

    // ---------- Cours et paiements ----------

    @Transactional(readOnly = true)
    public List<CourseCardDto> allCourses() {
        return courseRepository.findAll().stream().map(CourseCardDto::from).toList();
    }

    @Transactional
    public CourseCardDto setCoursePublished(Long courseId, boolean published) {
        Course c = courseRepository.findById(courseId).orElseThrow(() -> ApiException.notFound("Cours"));
        c.setPublished(published);
        return CourseCardDto.from(courseRepository.save(c));
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> payments(PaymentStatus status) {
        List<Payment> list = status == null ? paymentRepository.findAllByOrderByCreatedAtDesc()
                : paymentRepository.findByStatusOrderByCreatedAtDesc(status);
        return list.stream().map(PaymentDto::from).toList();
    }
}
