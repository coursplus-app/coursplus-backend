package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Secondes réellement regardées par un utilisateur pendant une heure d'horloge (agrégé).
 * Un seul flux vidéo ne peut pas dépasser 3600 s par heure : au-delà, plusieurs lectures simultanées
 * ont eu lieu avec le même compte, signe d'un compte partagé.
 */
@Entity
@Table(name = "watch_hours", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "hourStart"}))
@Getter @Setter @NoArgsConstructor
public class WatchHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Début de l'heure d'horloge (UTC). */
    @Column(nullable = false)
    private Instant hourStart;

    @Column(nullable = false)
    private int seconds;
}
