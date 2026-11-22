package tn.coursplus.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.coursplus.dto.CatalogDtos.*;
import tn.coursplus.dto.TeacherDtos.*;
import tn.coursplus.dto.UserDto;
import tn.coursplus.entity.*;
import tn.coursplus.exception.ApiException;
import tn.coursplus.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Espace enseignant : ses cours, ses vidéos/PDF, ses ventes, ses étudiants. */
@Service
public class TeacherService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final CourseDocumentRepository documentRepository;
    private final UniversityRepository universityRepository;
    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final AccessRepository accessRepository;
    private final StorageService storage;
    private final CatalogService catalogService;
    private final BigDecimal teacherShare;

    public TeacherService(CourseRepository courseRepository, LessonRepository lessonRepository,
                          CourseDocumentRepository documentRepository, UniversityRepository universityRepository,
                          TrackRepository trackRepository, UserRepository userRepository,
                          PaymentRepository paymentRepository, AccessRepository accessRepository,
                          StorageService storage, CatalogService catalogService,
                          @Value("${app.teacher-share}") BigDecimal teacherShare) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.documentRepository = documentRepository;
        this.universityRepository = universityRepository;
        this.trackRepository = trackRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.accessRepository = accessRepository;
        this.storage = storage;
        this.catalogService = catalogService;
        this.teacherShare = teacherShare;
    }

    // ---------- Cours ----------

    @Transactional(readOnly = true)
    public List<CourseCardDto> myCourses(Long teacherId) {
        return courseRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId).stream()
                .map(CourseCardDto::from).toList();
    }

    @Transactional
    public CourseDetailDto create(Long teacherId, CourseRequest req) {
        requireApproved(teacherId);
        Course c = new Course();
        c.setTeacher(userRepository.getReferenceById(teacherId));
        apply(c, req);
        courseRepository.save(c);
        return catalogService.detail(c.getId());
    }

    @Transactional
    public CourseDetailDto update(Long teacherId, Long courseId, CourseRequest req) {
        Course c = own(teacherId, courseId);
        apply(c, req);
        courseRepository.save(c);
        return catalogService.detail(c.getId());
    }

    private void apply(Course c, CourseRequest req) {
        c.setTitle(req.title().trim());
        c.setDescription(req.description());
        c.setPrice(req.price());
        c.setUniversity(req.universityId() == null ? null : universityRepository.findById(req.universityId())
                .orElseThrow(() -> ApiException.notFound("Université")));
        if (req.trackId() == null) {
            c.setTrack(null);
        } else {
            Track t = trackRepository.findById(req.trackId()).orElseThrow(() -> ApiException.notFound("Filière"));
            c.setTrack(t);
            c.setUniversity(t.getUniversity()); // la filière impose l'université
        }
    }

    @Transactional
    public CourseDetailDto setPublished(Long teacherId, Long courseId, boolean published) {
        if (published) requireApproved(teacherId);
        Course c = own(teacherId, courseId);
        if (published && c.getLessons().isEmpty()) {
            throw ApiException.badRequest("Ajoute au moins une vidéo avant de publier le cours");
        }
        c.setPublished(published);
        courseRepository.save(c);
        return catalogService.detail(c.getId());
    }

    @Transactional
    public void deleteCourse(Long teacherId, Long courseId) {
        Course c = own(teacherId, courseId);
        if (paymentRepository.findAll().stream().anyMatch(p -> p.getOfferType() == OfferType.COURSE
                && p.getTargetId().equals(courseId) && p.getStatus() == PaymentStatus.PAID)) {
            throw ApiException.badRequest("Ce cours a déjà été acheté : dépublie-le plutôt que de le supprimer");
        }
        c.getLessons().forEach(l -> storage.delete(l.getVideoFile()));
        c.getDocuments().forEach(d -> storage.delete(d.getFile()));
        courseRepository.delete(c);
    }

    // ---------- Vidéos ----------

    @Transactional
    public LessonDto addLesson(Long teacherId, Long courseId, String title, boolean freePreview, MultipartFile video) {
        Course c = own(teacherId, courseId);
        Lesson l = new Lesson();
        l.setCourse(c);
        l.setTitle(title == null || title.isBlank() ? "Leçon " + (c.getLessons().size() + 1) : title.trim());
        l.setFreePreview(freePreview);
        l.setPosition(c.getLessons().size() + 1);
        l.setVideoFile(storage.saveVideo(video));
        c.getLessons().add(l);
        lessonRepository.save(l);
        return LessonDto.from(l);
    }

    @Transactional
    public LessonDto updateLesson(Long teacherId, Long lessonId, LessonUpdateRequest req) {
        Lesson l = lessonRepository.findById(lessonId).orElseThrow(() -> ApiException.notFound("Leçon"));
        own(teacherId, l.getCourse().getId());
        if (req.title() != null && !req.title().isBlank()) l.setTitle(req.title().trim());
        if (req.freePreview() != null) l.setFreePreview(req.freePreview());
        if (req.position() != null) l.setPosition(req.position());
        return LessonDto.from(lessonRepository.save(l));
    }

    @Transactional
    public void deleteLesson(Long teacherId, Long lessonId) {
        Lesson l = lessonRepository.findById(lessonId).orElseThrow(() -> ApiException.notFound("Leçon"));
        Course c = own(teacherId, l.getCourse().getId());
        storage.delete(l.getVideoFile());
        c.getLessons().remove(l);
        int i = 1;
        for (Lesson other : c.getLessons()) other.setPosition(i++);
    }

    // ---------- Documents PDF ----------

    @Transactional
    public DocumentDto addDocument(Long teacherId, Long courseId, String title, DocumentType type, MultipartFile file) {
        Course c = own(teacherId, courseId);
        CourseDocument d = new CourseDocument();
        d.setCourse(c);
        d.setTitle(title == null || title.isBlank() ? file.getOriginalFilename() : title.trim());
        d.setType(type == null ? DocumentType.SUMMARY : type);
        d.setOriginalName(file.getOriginalFilename());
        d.setFile(storage.saveDocument(file));
        c.getDocuments().add(d);
        documentRepository.save(d);
        return DocumentDto.from(d);
    }

    @Transactional
    public void deleteDocument(Long teacherId, Long documentId) {
        CourseDocument d = documentRepository.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
        Course c = own(teacherId, d.getCourse().getId());
        storage.delete(d.getFile());
        c.getDocuments().remove(d);
    }

    // ---------- Profil ----------

    @Transactional
    public UserDto updateProfile(Long teacherId, ProfileRequest req) {
        User t = userRepository.findById(teacherId).orElseThrow();
        t.setSpecialty(req.specialty());
        t.setBio(req.bio());
        t.setMonthlyPrice(req.monthlyPrice());
        return UserDto.from(userRepository.save(t));
    }

    // ---------- Ventes et étudiants ----------

    @Transactional(readOnly = true)
    public StatsDto stats(Long teacherId) {
        List<Payment> paid = paymentRepository.findByTeacherIdAndStatusOrderByCreatedAtDesc(teacherId, PaymentStatus.PAID);
        BigDecimal gross = paid.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, CourseSales> perCourse = new LinkedHashMap<>();
        for (Course c : courseRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId)) {
            perCourse.put(c.getId(), new CourseSales(c.getId(), c.getTitle(), 0, BigDecimal.ZERO));
        }
        for (Payment p : paid) {
            if (p.getOfferType() == OfferType.COURSE && perCourse.containsKey(p.getTargetId())) {
                CourseSales s = perCourse.get(p.getTargetId());
                perCourse.put(p.getTargetId(), new CourseSales(s.courseId(), s.title(), s.sales() + 1,
                        s.revenue().add(share(p.getAmount()))));
            }
        }

        List<SaleDto> recent = paid.stream()
                .sorted(Comparator.comparing(Payment::getPaidAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .map(p -> new SaleDto(p.getReference(), p.getLabel(), p.getStudent().getFullName(),
                        p.getAmount(), share(p.getAmount()), p.getPaidAt()))
                .toList();

        List<StudentDto> students = students(teacherId);
        long distinct = students.stream().map(StudentDto::id).distinct().count();
        long activeSubs = students.stream()
                .filter(s -> s.active() && OfferType.TEACHER_SUB.name().equals(s.accessType()))
                .map(StudentDto::id).distinct().count();

        return new StatsDto(paid.size(), gross, share(gross),
                teacherShare.multiply(BigDecimal.valueOf(100)).doubleValue(),
                distinct, activeSubs, new ArrayList<>(perCourse.values()), recent);
    }

    @Transactional(readOnly = true)
    public List<StudentDto> students(Long teacherId) {
        return accessRepository.findAllForTeacher(teacherId).stream()
                .sorted(Comparator.comparing(Access::getStartsAt).reversed())
                .map(a -> new StudentDto(a.getStudent().getId(), a.getStudent().getFullName(), a.getStudent().getEmail(),
                        a.getStudent().getUniversity() == null ? null : a.getStudent().getUniversity().getCode(),
                        a.getType().name(), AccessService.label(a), a.getStartsAt(), a.getEndsAt(), a.isActive()))
                .toList();
    }

    private BigDecimal share(BigDecimal amount) {
        return amount.multiply(teacherShare).setScale(3, RoundingMode.HALF_UP);
    }

    /** Un enseignant dont la candidature n'est pas validée ne peut pas créer ni publier de cours. */
    private void requireApproved(Long teacherId) {
        User t = userRepository.findById(teacherId).orElseThrow(() -> ApiException.notFound("Enseignant"));
        switch (t.getApprovalStatus()) {
            case PENDING -> throw ApiException.forbidden("Ton compte est en cours de vérification par l'équipe CoursPlus");
            case REJECTED -> throw ApiException.forbidden("Ta candidature n'a pas été retenue");
            default -> { }
        }
    }

    private Course own(Long teacherId, Long courseId) {
        Course c = courseRepository.findById(courseId).orElseThrow(() -> ApiException.notFound("Cours"));
        if (!c.getTeacher().getId().equals(teacherId)) {
            throw ApiException.forbidden("Ce cours ne t'appartient pas");
        }
        return c;
    }
}
