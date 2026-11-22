package tn.coursplus.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.coursplus.dto.NotificationDtos.NotificationList;
import tn.coursplus.security.CurrentUser;
import tn.coursplus.service.NotificationService;

/** Notifications de l'utilisateur connecté (tous les rôles). */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private Long me() {
        return CurrentUser.require().id();
    }

    @GetMapping
    public NotificationList list() {
        return notificationService.list(me());
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id) {
        notificationService.markSeen(me(), id);
    }

    @PostMapping("/read-all")
    public void readAll() {
        notificationService.markAllSeen(me());
    }

    /** Flux temps réel (Server-Sent Events). Le frontend l'ouvre avec fetch + en-tête Authorization. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no"); // Nginx : ne pas mettre le flux en mémoire tampon
        return notificationService.subscribe(me());
    }
}
