package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Journal des lectures vidéo : une ligne à chaque demande de lien de lecture (play).
 * Le lecteur renouvelle son lien toutes les ~13 minutes, donc 1 ligne ≈ 13 minutes de visionnage :
 * le nombre de lignes par jour sert d'indicateur pour repérer un compte partagé.
 */
@Entity
@Table(name = "view_logs", indexes = @Index(name = "idx_viewlog_user", columnList = "userId, createdAt"))
@Getter @Setter @NoArgsConstructor
public class ViewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long lessonId;

    @Column(nullable = false)
    private Long courseId;

    /** Adresse IP au moment de la lecture (utile pour repérer un compte utilisé depuis plusieurs réseaux). */
    private String ip;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
