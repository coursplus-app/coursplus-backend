package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Notification affichée dans la cloche d'un utilisateur. */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notif_user", columnList = "userId, createdAt"))
@Getter @Setter @NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Code court : PAYMENT_PAID, PAYMENT_PROOF, TEACHER_PENDING, ... */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String message;

    /** Lien interne du frontend (ex. /admin?tab=teachers). */
    private String link;

    private boolean seen = false;

    private Instant createdAt = Instant.now();
}
