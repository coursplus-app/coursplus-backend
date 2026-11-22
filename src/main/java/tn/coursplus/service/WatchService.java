package tn.coursplus.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coursplus.dto.AdminDtos.WatchLessonRow;
import tn.coursplus.dto.AdminDtos.WatchRow;
import tn.coursplus.entity.Lesson;
import tn.coursplus.entity.User;
import tn.coursplus.entity.WatchHour;
import tn.coursplus.entity.WatchStat;
import tn.coursplus.repository.LessonRepository;
import tn.coursplus.repository.UserRepository;
import tn.coursplus.repository.ViewLogRepository;
import tn.coursplus.repository.WatchHourRepository;
import tn.coursplus.repository.WatchStatRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Temps de visionnage réel. Le lecteur envoie un « battement » toutes les ~15 s pendant la lecture.
 * On agrège par heure d'horloge (anti-partage) et par leçon (suivi admin).
 */
@Service
public class WatchService {

    /** Un seul flux ne peut pas dépasser 3600 s/heure ; au-delà de 4200 s, plusieurs lectures simultanées. */
    static final int HOUR_LIMIT_SECONDS = 4200;
    /** Plus de 10 h de visionnage en 24 h : très improbable pour un seul étudiant. */
    static final long DAY_LIMIT_SECONDS = 10 * 3600L;
    /** Un battement ne peut pas représenter plus de 20 s (le lecteur en envoie ~15). */
    static final int MAX_BEAT_SECONDS = 20;

    private final WatchHourRepository hours;
    private final WatchStatRepository stats;
    private final ViewLogRepository viewLogs;
    private final UserRepository users;
    private final LessonRepository lessons;
    private final NotificationService notifications;

    public WatchService(WatchHourRepository hours, WatchStatRepository stats, ViewLogRepository viewLogs,
                        UserRepository users, LessonRepository lessons, NotificationService notifications) {
        this.hours = hours;
        this.stats = stats;
        this.viewLogs = viewLogs;
        this.users = users;
        this.lessons = lessons;
        this.notifications = notifications;
    }

    @Transactional
    public void heartbeat(User user, Lesson lesson, int seconds, int position) {
        int s = Math.max(0, Math.min(seconds, MAX_BEAT_SECONDS));
        Instant now = Instant.now();
        Instant hourStart = now.truncatedTo(ChronoUnit.HOURS);

        WatchHour h = hours.findByUserIdAndHourStart(user.getId(), hourStart).orElseGet(() -> {
            WatchHour fresh = new WatchHour();
            fresh.setUserId(user.getId());
            fresh.setHourStart(hourStart);
            return fresh;
        });
        int before = h.getSeconds();
        h.setSeconds(before + s);
        hours.save(h);

        WatchStat st = stats.findByUserIdAndLessonId(user.getId(), lesson.getId()).orElseGet(() -> {
            WatchStat fresh = new WatchStat();
            fresh.setUserId(user.getId());
            fresh.setLessonId(lesson.getId());
            return fresh;
        });
        st.setSeconds(st.getSeconds() + s);
        st.setMaxPosition(Math.max(st.getMaxPosition(), Math.max(0, position)));
        st.setUpdatedAt(now);
        stats.save(st);

        // Alertes (une seule fois, au franchissement du seuil)
        if (before <= HOUR_LIMIT_SECONDS && h.getSeconds() > HOUR_LIMIT_SECONDS) {
            notifications.notifyAdmins("ACCOUNT_SHARING", "Compte suspect : lectures simultanées",
                    user.getEmail() + " a cumulé plus de " + (HOUR_LIMIT_SECONDS / 60)
                            + " minutes de visionnage en une seule heure : plusieurs personnes utilisent peut-être ce compte.",
                    "/admin?tab=watch");
        }
        long day = hours.totalSince(user.getId(), now.minus(Duration.ofHours(24)));
        if (day - s < DAY_LIMIT_SECONDS && day >= DAY_LIMIT_SECONDS) {
            notifications.notifyAdmins("ACCOUNT_SHARING", "Compte suspect : visionnage excessif",
                    user.getEmail() + " a regardé plus de " + (DAY_LIMIT_SECONDS / 3600) + " h de vidéo en 24 h.",
                    "/admin?tab=watch");
        }
    }

    // ---------- Vue admin ----------

    @Transactional(readOnly = true)
    public List<WatchRow> summary() {
        Instant now = Instant.now();
        Instant since24 = now.minus(Duration.ofHours(24));
        Instant since7 = now.minus(Duration.ofDays(7));

        Map<Long, long[]> activity = new HashMap<>(); // userId -> [lectures, ips]
        for (Object[] r : viewLogs.activitySince(since24)) {
            activity.put((Long) r[0], new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
        }
        Map<Long, Long> secs24 = toMap(hours.totalsSince(since24));
        Map<Long, Long> secs7 = toMap(hours.totalsSince(since7));
        Set<Long> parallel = new HashSet<>(hours.usersOverHourLimit(since24, HOUR_LIMIT_SECONDS));

        Set<Long> ids = new HashSet<>(secs7.keySet());
        ids.addAll(activity.keySet());
        Map<Long, User> byId = new HashMap<>();
        users.findAllById(ids).forEach(u -> byId.put(u.getId(), u));

        List<WatchRow> rows = new ArrayList<>();
        for (Long id : ids) {
            User u = byId.get(id);
            if (u == null) continue;
            long[] a = activity.getOrDefault(id, new long[]{0, 0});
            long d = secs24.getOrDefault(id, 0L);
            List<String> reasons = new ArrayList<>();
            if (parallel.contains(id)) reasons.add("lectures simultanées");
            if (d >= DAY_LIMIT_SECONDS) reasons.add("plus de 10 h en 24 h");
            if (a[0] >= ViewLogService.MAX_PLAYS_PER_DAY) reasons.add(a[0] + " lectures en 24 h");
            if (a[1] >= ViewLogService.MAX_IPS_PER_DAY) reasons.add(a[1] + " adresses IP");
            rows.add(new WatchRow(id, u.getFullName(), u.getEmail(), a[0], a[1], d, secs7.getOrDefault(id, 0L),
                    !reasons.isEmpty(), String.join(", ", reasons)));
        }
        // Suspects d'abord, puis par temps de visionnage décroissant ; 100 lignes maximum
        rows.sort(Comparator.comparing(WatchRow::suspicious).reversed()
                .thenComparing(Comparator.comparingLong(WatchRow::seconds24h).reversed()));
        return rows.size() > 100 ? rows.subList(0, 100) : rows;
    }

    @Transactional(readOnly = true)
    public List<WatchLessonRow> detail(Long userId) {
        List<WatchStat> list = stats.findByUserIdOrderByUpdatedAtDesc(userId);
        Map<Long, Lesson> byId = new HashMap<>();
        lessons.findAllById(list.stream().map(WatchStat::getLessonId).toList()).forEach(l -> byId.put(l.getId(), l));
        List<WatchLessonRow> rows = new ArrayList<>();
        for (WatchStat s : list) {
            Lesson l = byId.get(s.getLessonId());
            if (l == null) continue;
            rows.add(new WatchLessonRow(l.getId(), l.getTitle(), l.getCourse().getTitle(), s.getSeconds(),
                    s.getMaxPosition(), s.getUpdatedAt()));
        }
        return rows;
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) m.put((Long) r[0], ((Number) r[1]).longValue());
        return m;
    }
}
