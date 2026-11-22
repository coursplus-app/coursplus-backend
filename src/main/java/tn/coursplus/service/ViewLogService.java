package tn.coursplus.service;

import org.springframework.stereotype.Service;
import tn.coursplus.entity.Lesson;
import tn.coursplus.entity.User;
import tn.coursplus.entity.ViewLog;
import tn.coursplus.repository.ViewLogRepository;

import java.time.Duration;
import java.time.Instant;

/**
 * Enregistre les lectures et alerte les admins quand un compte semble partagé :
 *  - trop de visionnage sur 24 h (≈ 40 liens × 13 min ≈ 9 h) ;
 *  - ou plusieurs adresses IP différentes sur 24 h.
 * Chaque alerte n'est envoyée qu'une fois par compte et par seuil (égalité stricte sur le compteur).
 */
@Service
public class ViewLogService {

    static final int MAX_PLAYS_PER_DAY = 40;
    static final int MAX_IPS_PER_DAY = 4;

    private final ViewLogRepository repository;
    private final NotificationService notifications;

    public ViewLogService(ViewLogRepository repository, NotificationService notifications) {
        this.repository = repository;
        this.notifications = notifications;
    }

    public void record(User user, Lesson lesson, String ip) {
        ViewLog log = new ViewLog();
        log.setUserId(user.getId());
        log.setLessonId(lesson.getId());
        log.setCourseId(lesson.getCourse().getId());
        log.setIp(ip);
        repository.save(log);

        Instant since = Instant.now().minus(Duration.ofHours(24));
        long plays = repository.countByUserIdAndCreatedAtAfter(user.getId(), since);
        if (plays == MAX_PLAYS_PER_DAY) {
            notifications.notifyAdmins("ACCOUNT_SHARING", "Compte suspect : visionnage excessif",
                    user.getEmail() + " a demandé " + plays + " lectures vidéo en 24 h (≈ " + (plays * 13 / 60)
                            + " h). Le compte est peut-être partagé.", "/admin");
        }
        if (plays > 1 && repository.countDistinctIp(user.getId(), since) == MAX_IPS_PER_DAY) {
            notifications.notifyAdmins("ACCOUNT_SHARING", "Compte suspect : plusieurs réseaux",
                    user.getEmail() + " a regardé des vidéos depuis " + MAX_IPS_PER_DAY
                            + " adresses IP différentes en 24 h. Le compte est peut-être partagé.", "/admin");
        }
    }
}
