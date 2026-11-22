package tn.coursplus.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coursplus.dto.CatalogDtos.*;
import tn.coursplus.entity.*;
import tn.coursplus.exception.ApiException;
import tn.coursplus.repository.*;
import tn.coursplus.security.AuthUser;
import tn.coursplus.security.CurrentUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Catalogue public : universités, filières, enseignants, cours. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final UniversityRepository universityRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final AccessService accessService;

    public CatalogService(UniversityRepository universityRepository, TrackRepository trackRepository,
                          UserRepository userRepository, CourseRepository courseRepository,
                          AccessService accessService) {
        this.universityRepository = universityRepository;
        this.trackRepository = trackRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.accessService = accessService;
    }

    public List<UniversityDto> universities() {
        return universityRepository.findAllByOrderByCodeAsc().stream()
                .map(u -> new UniversityDto(u.getId(), u.getCode(), u.getName(),
                        trackRepository.findByUniversityIdOrderByNameAsc(u.getId()).stream()
                                .map(TrackDto::from).toList()))
                .toList();
    }

    public List<TeacherDto> teachers() {
        return userRepository.findByRoleOrderByFullNameAsc(Role.TEACHER).stream()
                .filter(User::isActive)
                .filter(u -> u.getApprovalStatus() == ApprovalStatus.APPROVED)
                .map(this::toTeacherDto)
                .toList();
    }

    public TeacherDto toTeacherDto(User t) {
        return new TeacherDto(t.getId(), t.getFullName(), t.getSpecialty(), t.getBio(), t.getMonthlyPrice(),
                courseRepository.findByTeacherIdAndPublishedTrue(t.getId()).size());
    }

    /** Recherche dans le catalogue. Les filtres sont facultatifs. */
    public List<CourseCardDto> search(Long universityId, Long trackId, Long teacherId, String q) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return courseRepository.findByPublishedTrueOrderByCreatedAtDesc().stream()
                .filter(c -> universityId == null || (c.getUniversity() != null && c.getUniversity().getId().equals(universityId)))
                .filter(c -> trackId == null || (c.getTrack() != null && c.getTrack().getId().equals(trackId)))
                .filter(c -> teacherId == null || c.getTeacher().getId().equals(teacherId))
                .filter(c -> query.isEmpty()
                        || c.getTitle().toLowerCase(Locale.ROOT).contains(query)
                        || (c.getDescription() != null && c.getDescription().toLowerCase(Locale.ROOT).contains(query))
                        || c.getTeacher().getFullName().toLowerCase(Locale.ROOT).contains(query))
                .map(CourseCardDto::from)
                .toList();
    }

    public CourseDetailDto detail(Long courseId) {
        Course c = courseRepository.findById(courseId).orElseThrow(() -> ApiException.notFound("Cours"));
        Optional<AuthUser> me = CurrentUser.get();
        boolean isOwnerOrAdmin = me.isPresent()
                && (me.get().role() == Role.ADMIN || c.getTeacher().getId().equals(me.get().id()));
        if (!c.isPublished() && !isOwnerOrAdmin) {
            throw ApiException.notFound("Cours");
        }
        boolean hasAccess = me.isPresent() && accessService.canWatch(me.get().id(), me.get().role(), c);
        return new CourseDetailDto(c.getId(), c.getTitle(), c.getDescription(), c.getPrice(),
                toTeacherDto(c.getTeacher()),
                c.getUniversity() == null ? null : c.getUniversity().getId(),
                c.getUniversity() == null ? null : c.getUniversity().getCode(),
                c.getTrack() == null ? null : c.getTrack().getId(),
                c.getTrack() == null ? null : c.getTrack().getName(),
                c.isPublished(),
                c.getLessons().stream().map(LessonDto::from).toList(),
                c.getDocuments().stream().map(DocumentDto::from).toList(),
                hasAccess, offersFor(c));
    }

    /** Les 3 formules du cahier des charges, affichées sur la page du cours. */
    private List<OfferDto> offersFor(Course c) {
        List<OfferDto> offers = new ArrayList<>();
        offers.add(new OfferDto(OfferType.COURSE.name(), c.getId(), "Ce cours",
                "Accès à vie à toutes les vidéos et documents de ce cours", c.getPrice()));
        User t = c.getTeacher();
        if (t.getMonthlyPrice() != null) {
            int n = courseRepository.findByTeacherIdAndPublishedTrue(t.getId()).size();
            offers.add(new OfferDto(OfferType.TEACHER_SUB.name(), t.getId(), "Abonnement " + t.getFullName(),
                    "1 mois d'accès à ses " + n + " cours", t.getMonthlyPrice()));
        }
        Track tr = c.getTrack();
        if (tr != null && tr.getMonthlyPrice() != null) {
            int n = courseRepository.findByTrackIdAndPublishedTrue(tr.getId()).size();
            offers.add(new OfferDto(OfferType.TRACK_SUB.name(), tr.getId(),
                    "Abonnement filière " + tr.getUniversity().getCode() + " - " + tr.getName(),
                    "1 mois d'accès aux " + n + " cours de la filière", tr.getMonthlyPrice()));
        }
        return offers;
    }
}
