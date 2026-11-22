package tn.coursplus.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coursplus.entity.*;
import tn.coursplus.exception.ApiException;
import tn.coursplus.repository.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Tout ce qui concerne "qui a le droit de voir quoi". */
@Service
public class AccessService {

    private final AccessRepository accessRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    private final int subscriptionDays;

    public AccessService(AccessRepository accessRepository, CourseRepository courseRepository,
                         UserRepository userRepository, TrackRepository trackRepository,
                         @Value("${app.subscription-days}") int subscriptionDays) {
        this.accessRepository = accessRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.trackRepository = trackRepository;
        this.subscriptionDays = subscriptionDays;
    }

    /** Est-ce que cet utilisateur peut regarder ce cours ? */
    public boolean canWatch(Long userId, Role role, Course course) {
        if (role == Role.ADMIN) return true;
        if (course.getTeacher().getId().equals(userId)) return true;
        for (Access a : accessRepository.findByStudentId(userId)) {
            if (!a.isActive()) continue;
            switch (a.getType()) {
                case COURSE -> {
                    if (a.getCourse() != null && a.getCourse().getId().equals(course.getId())) return true;
                }
                case TEACHER_SUB -> {
                    if (a.getTeacher() != null && a.getTeacher().getId().equals(course.getTeacher().getId())) return true;
                }
                case TRACK_SUB -> {
                    if (a.getTrack() != null && course.getTrack() != null
                            && a.getTrack().getId().equals(course.getTrack().getId())) return true;
                }
            }
        }
        return false;
    }

    /** Appelé quand un paiement est confirmé : on crée (ou prolonge) l'accès. */
    @Transactional
    public Access grant(Payment payment) {
        Access access = new Access();
        access.setStudent(payment.getStudent());
        access.setType(payment.getOfferType());
        access.setPayment(payment);
        Instant now = Instant.now();
        switch (payment.getOfferType()) {
            case COURSE -> access.setCourse(courseRepository.findById(payment.getTargetId())
                    .orElseThrow(() -> ApiException.notFound("Cours")));
            case TEACHER_SUB -> {
                access.setTeacher(userRepository.findById(payment.getTargetId())
                        .orElseThrow(() -> ApiException.notFound("Enseignant")));
                access.setEndsAt(currentEnd(payment).plus(Duration.ofDays(subscriptionDays)));
            }
            case TRACK_SUB -> {
                access.setTrack(trackRepository.findById(payment.getTargetId())
                        .orElseThrow(() -> ApiException.notFound("Filière")));
                access.setEndsAt(currentEnd(payment).plus(Duration.ofDays(subscriptionDays)));
            }
        }
        access.setStartsAt(now);
        return accessRepository.save(access);
    }

    /**
     * Si l'étudiant a déjà un abonnement actif au même enseignant / à la même filière,
     * le nouveau mois commence à la fin de l'ancien (il ne perd pas de jours).
     */
    private Instant currentEnd(Payment payment) {
        Instant end = Instant.now();
        for (Access a : accessRepository.findByStudentIdAndType(payment.getStudent().getId(), payment.getOfferType())) {
            Long target = a.getTeacher() != null ? a.getTeacher().getId() : (a.getTrack() != null ? a.getTrack().getId() : null);
            if (payment.getTargetId().equals(target) && a.getEndsAt() != null && a.getEndsAt().isAfter(end)) {
                end = a.getEndsAt();
            }
        }
        return end;
    }

    /** Tous les cours publiés accessibles à l'étudiant (achats + abonnements actifs). */
    public List<Course> accessibleCourses(Long userId, Role role) {
        return courseRepository.findByPublishedTrueOrderByCreatedAtDesc().stream()
                .filter(c -> canWatch(userId, role, c))
                .toList();
    }

    public static String label(Access a) {
        return switch (a.getType()) {
            case COURSE -> "Cours : " + a.getCourse().getTitle();
            case TEACHER_SUB -> "Abonnement enseignant : " + a.getTeacher().getFullName();
            case TRACK_SUB -> "Abonnement filière : " + a.getTrack().getUniversity().getCode() + " - " + a.getTrack().getName();
        };
    }
}
