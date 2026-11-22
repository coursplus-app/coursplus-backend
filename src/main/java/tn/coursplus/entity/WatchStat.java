package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Temps total regardé par un utilisateur sur une leçon, et position la plus avancée atteinte. */
@Entity
@Table(name = "watch_stats", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "lessonId"}))
@Getter @Setter @NoArgsConstructor
public class WatchStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long lessonId;

    private long seconds = 0;

    /** Position la plus avancée (en secondes) atteinte dans la vidéo. */
    private int maxPosition = 0;

    private Instant updatedAt = Instant.now();
}
