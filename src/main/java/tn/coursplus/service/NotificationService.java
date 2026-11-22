package tn.coursplus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.coursplus.dto.NotificationDtos.NotificationDto;
import tn.coursplus.dto.NotificationDtos.NotificationList;
import tn.coursplus.entity.Notification;
import tn.coursplus.entity.Role;
import tn.coursplus.entity.User;
import tn.coursplus.exception.ApiException;
import tn.coursplus.repository.NotificationRepository;
import tn.coursplus.repository.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notifications : enregistrées en base (historique + compteur de non lus)
 * et poussées en temps réel vers le navigateur par SSE (Server-Sent Events).
 *
 * ⚠️ Les connexions SSE sont gardées en mémoire : avec plusieurs instances du backend,
 * il faudrait un broker (Redis, RabbitMQ) pour diffuser entre elles. Une seule instance : aucun souci.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    /** Une connexion est renouvelée côté navigateur toutes les 30 min (le client se reconnecte seul). */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    // ---------- Envoi ----------

    /** Crée la notification et la pousse au navigateur une fois la transaction en cours validée. */
    @Transactional
    public void notifyUser(Long userId, String type, String title, String message, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        repository.save(n);
        NotificationDto dto = NotificationDto.from(n);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push(userId, dto);
                }
            });
        } else {
            push(userId, dto);
        }
    }

    /** Notifie tous les administrateurs actifs. */
    @Transactional
    public void notifyAdmins(String type, String title, String message, String link) {
        for (User admin : userRepository.findByRoleOrderByFullNameAsc(Role.ADMIN)) {
            if (admin.isActive()) notifyUser(admin.getId(), type, title, message, link);
        }
    }

    // ---------- Lecture ----------

    @Transactional(readOnly = true)
    public NotificationList list(Long userId) {
        return new NotificationList(repository.countByUserIdAndSeenFalse(userId),
                repository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream().map(NotificationDto::from).toList());
    }

    @Transactional
    public void markSeen(Long userId, Long notificationId) {
        Notification n = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.setSeen(true);
    }

    @Transactional
    public void markAllSeen(Long userId) {
        repository.markAllSeen(userId);
    }

    // ---------- SSE ----------

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        try {
            // Envoie les en-têtes tout de suite : le navigateur sait que le flux est ouvert.
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    private void push(Long userId, NotificationDto dto) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(dto));
            } catch (Exception e) {
                remove(userId, emitter);
            }
        }
    }

    /** Un signe de vie régulier évite que les proxys coupent les connexions silencieuses. */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) emitters.remove(userId, list);
        log.debug("Flux SSE fermé pour l'utilisateur {}", userId);
    }
}
