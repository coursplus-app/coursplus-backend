package tn.coursplus.controller;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tn.coursplus.dto.MeDtos.PlayResponse;
import tn.coursplus.entity.*;
import tn.coursplus.exception.ApiException;
import tn.coursplus.repository.CourseDocumentRepository;
import tn.coursplus.repository.LessonRepository;
import tn.coursplus.repository.UserRepository;
import tn.coursplus.security.AuthUser;
import tn.coursplus.security.CurrentUser;
import tn.coursplus.security.JwtService;
import tn.coursplus.service.AccessService;
import tn.coursplus.service.StorageService;
import tn.coursplus.service.ViewLogService;
import tn.coursplus.service.WatchService;
import tn.coursplus.service.storage.ObjectChunk;
import tn.coursplus.security.RateLimiter;

import java.util.Objects;

/**
 * Lecture protégée des vidéos et PDF.
 *
 * 1. Le frontend (connecté) demande /api/lessons/{id}/play
 *    -> on vérifie l'accès et on renvoie une URL avec un jeton "media" de courte durée.
 * 2. La balise <video> charge /api/media/lessons/{id}?t=...
 *    -> on revérifie le jeton, la session (1 appareil à la fois) et l'accès, puis on envoie la vidéo.
 *    Les requêtes "Range" (avancer / reculer dans la vidéo) sont gérées dans file().
 */
@RestController
@RequestMapping("/api")
public class MediaController {

    private final LessonRepository lessonRepository;
    private final CourseDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AccessService accessService;
    private final StorageService storage;
    private final JwtService jwtService;
    private final ViewLogService viewLogs;
    private final WatchService watch;
    private final RateLimiter limiter;

    public MediaController(LessonRepository lessonRepository, CourseDocumentRepository documentRepository,
                           UserRepository userRepository, AccessService accessService,
                           StorageService storage, JwtService jwtService, ViewLogService viewLogs,
                           WatchService watch, RateLimiter limiter) {
        this.watch = watch;
        this.limiter = limiter;
        this.lessonRepository = lessonRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.storage = storage;
        this.jwtService = jwtService;
        this.viewLogs = viewLogs;
    }

    // ---------- 1. Demande d'un lien (utilisateur connecté) ----------

    @GetMapping("/lessons/{id}/play")
    public PlayResponse play(@PathVariable Long id, HttpServletRequest request) {
        AuthUser me = CurrentUser.require();
        Lesson lesson = lessonRepository.findById(id).orElseThrow(() -> ApiException.notFound("Leçon"));
        if (!lesson.isFreePreview()) {
            checkAccess(me.id(), me.role(), lesson.getCourse());
        }
        if (lesson.getVideoFile() == null) {
            throw ApiException.notFound("Vidéo");
        }
        User user = userRepository.findById(me.id()).orElseThrow();
        viewLogs.record(user, lesson, RateLimiter.clientIp(request)); // historique des lectures (détection de compte partagé)
        String token = jwtService.createMediaToken(user, "lesson:" + id, JwtService.fingerprint(request));
        return new PlayResponse("/api/media/lessons/" + id + "?t=" + token, watermarkOf(user), jwtService.mediaTtlSeconds());
    }

    /** Battement du lecteur (~toutes les 15 s pendant la lecture) : secondes regardées + position. */
    public record HeartbeatRequest(int seconds, int position) {
    }

    @PostMapping("/lessons/{id}/heartbeat")
    public void heartbeat(@PathVariable Long id, @RequestBody HeartbeatRequest body) {
        AuthUser me = CurrentUser.require();
        String key = "beat:" + me.id();
        limiter.assertAllowed(key, 12, java.time.Duration.ofMinutes(1)); // anti-abus
        limiter.record(key, java.time.Duration.ofMinutes(1));
        Lesson lesson = lessonRepository.findById(id).orElseThrow(() -> ApiException.notFound("Leçon"));
        User user = userRepository.findById(me.id()).orElseThrow();
        watch.heartbeat(user, lesson, body.seconds(), body.position());
    }

    @GetMapping("/documents/{id}/link")
    public PlayResponse documentLink(@PathVariable Long id, HttpServletRequest request) {
        AuthUser me = CurrentUser.require();
        CourseDocument doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        checkAccess(me.id(), me.role(), doc.getCourse());
        User user = userRepository.findById(me.id()).orElseThrow();
        String token = jwtService.createMediaToken(user, "document:" + id, JwtService.fingerprint(request));
        return new PlayResponse("/api/media/documents/" + id + "?t=" + token, watermarkOf(user), jwtService.mediaTtlSeconds());
    }

    // ---------- 2. Envoi du fichier (jeton dans l'URL) ----------

    @GetMapping("/media/lessons/{id}")
    public ResponseEntity<Resource> streamLesson(@PathVariable Long id, @RequestParam("t") String token, HttpServletRequest request) {
        Lesson lesson = lessonRepository.findById(id).orElseThrow(() -> ApiException.notFound("Leçon"));
        User user = verifyMediaToken(token, "lesson:" + id, request);
        if (!lesson.isFreePreview()) {
            checkAccess(user.getId(), user.getRole(), lesson.getCourse());
        }
        return file(lesson.getVideoFile(), "video.mp4", request);
    }

    @GetMapping("/media/documents/{id}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id, @RequestParam("t") String token, HttpServletRequest request) {
        CourseDocument doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        User user = verifyMediaToken(token, "document:" + id, request);
        checkAccess(user.getId(), user.getRole(), doc.getCourse());
        return file(doc.getFile(), doc.getOriginalName() == null ? "document.pdf" : doc.getOriginalName(), request);
    }

    private User verifyMediaToken(String token, String resource, HttpServletRequest request) {
        Claims c = jwtService.parse(token)
                .filter(x -> JwtService.TYPE_MEDIA.equals(x.get("typ", String.class)))
                .filter(x -> resource.equals(x.get("res", String.class)))
                .orElseThrow(() -> ApiException.forbidden("Lien expiré, recharge la page"));
        if (!Objects.equals(c.get("fp", String.class), JwtService.fingerprint(request))) {
            throw ApiException.forbidden("Lien invalide pour ce navigateur, recharge la page");
        }
        User user = userRepository.findById(Long.valueOf(c.getSubject()))
                .orElseThrow(() -> ApiException.forbidden("Lien invalide"));
        if (!user.isActive() || !Objects.equals(user.getSessionId(), c.get("sid", String.class))) {
            throw ApiException.forbidden("Ton compte est connecté sur un autre appareil");
        }
        return user;
    }

    /** Texte du filigrane : e-mail + identifiant, pour retrouver la source d'une fuite. */
    private static String watermarkOf(User user) {
        return user.getEmail() + " · ID " + user.getId();
    }

    private void checkAccess(Long userId, Role role, Course course) {
        if (!accessService.canWatch(userId, role, course)) {
            throw ApiException.forbidden("Achète ce cours ou un abonnement pour y accéder");
        }
    }

    /**
     * Envoie le fichier, en entier (200) ou seulement la plage demandée (206) : c'est ce qui permet
     * d'avancer et de reculer dans la vidéo. La plage est calculée ici, pas par Spring, car le fichier
     * peut venir d'un stockage distant (R2).
     */
    private ResponseEntity<Resource> file(String storedName, String downloadName, HttpServletRequest request) {
        ObjectChunk chunk = storage.open(storedName, request.getHeader(HttpHeaders.RANGE));
        if (chunk == null) {
            throw ApiException.notFound("Fichier");
        }
        MediaType type = MediaTypeFactory.getMediaType(storedName).orElse(MediaType.APPLICATION_OCTET_STREAM);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(chunk.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .contentType(type)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(chunk.length()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(downloadName, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store");
        if (chunk.partial()) {
            response.header(HttpHeaders.CONTENT_RANGE, "bytes " + chunk.start() + "-" + chunk.end() + "/" + chunk.total());
        }
        return response.body(new InputStreamResource(chunk.stream()));
    }
}
