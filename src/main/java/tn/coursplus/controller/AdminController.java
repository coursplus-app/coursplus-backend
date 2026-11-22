package tn.coursplus.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import tn.coursplus.dto.AdminDtos.*;
import tn.coursplus.dto.CatalogDtos.*;
import tn.coursplus.dto.PaymentDtos.PaymentDto;
import tn.coursplus.dto.UserDto;
import tn.coursplus.entity.PaymentStatus;
import tn.coursplus.entity.Role;
import tn.coursplus.service.AdminService;
import tn.coursplus.service.PaymentService;
import tn.coursplus.service.WatchService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Espace administrateur (rôle ADMIN obligatoire). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final PaymentService paymentService;
    private final WatchService watchService;

    public AdminController(AdminService adminService, PaymentService paymentService, WatchService watchService) {
        this.watchService = watchService;
        this.adminService = adminService;
        this.paymentService = paymentService;
    }

    /** Visionnage des 7 derniers jours par compte (suspects d'abord). */
    @GetMapping("/watch")
    public List<WatchRow> watch() {
        return watchService.summary();
    }

    /** Détail par leçon d'un compte. */
    @GetMapping("/watch/{userId}")
    public List<WatchLessonRow> watchDetail(@PathVariable Long userId) {
        return watchService.detail(userId);
    }

    @GetMapping("/overview")
    public OverviewDto overview() {
        return adminService.overview();
    }

    // Utilisateurs
    @GetMapping("/users")
    public List<UserDto> users(@RequestParam(required = false) Role role) {
        return adminService.users(role);
    }

    @PostMapping("/teachers")
    public UserDto createTeacher(@Valid @RequestBody CreateTeacherRequest req) {
        return adminService.createTeacher(req);
    }

    /** Candidature enseignant : { "approve": true|false, "note": "motif du refus (facultatif)" } */
    @PostMapping("/teachers/{id}/review")
    public UserDto reviewTeacher(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean approve = Boolean.TRUE.equals(body.get("approve"));
        Object note = body.get("note");
        return adminService.reviewTeacher(id, approve, note == null ? null : note.toString());
    }

    @PatchMapping("/users/{id}/active")
    public UserDto setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return adminService.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }

    // Universités et filières
    @PostMapping("/universities")
    public UniversityDto createUniversity(@Valid @RequestBody UniversityRequest req) {
        return adminService.createUniversity(req);
    }

    @PostMapping("/tracks")
    public TrackDto createTrack(@Valid @RequestBody TrackRequest req) {
        return adminService.createTrack(req);
    }

    @PatchMapping("/tracks/{id}/price")
    public TrackDto trackPrice(@PathVariable Long id, @RequestBody Map<String, BigDecimal> body) {
        return adminService.updateTrackPrice(id, body.get("monthlyPrice"));
    }

    // Cours
    @GetMapping("/courses")
    public List<CourseCardDto> courses() {
        return adminService.allCourses();
    }

    @PatchMapping("/courses/{id}/published")
    public CourseCardDto publish(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return adminService.setCoursePublished(id, Boolean.TRUE.equals(body.get("published")));
    }

    // Paiements
    @GetMapping("/payments")
    public List<PaymentDto> payments(@RequestParam(required = false) PaymentStatus status) {
        return adminService.payments(status);
    }

    @PostMapping("/payments/{id}/confirm")
    public PaymentDto confirm(@PathVariable Long id) {
        return paymentService.adminConfirm(id);
    }

    @PostMapping("/payments/{id}/reject")
    public PaymentDto reject(@PathVariable Long id) {
        return paymentService.adminReject(id);
    }
}
