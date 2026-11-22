package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Un droit d'accès d'un étudiant. Il est créé automatiquement quand un paiement passe à PAID.
 * - COURSE      : accès à vie à un cours (endsAt = null)
 * - TEACHER_SUB : accès à tous les cours d'un enseignant jusqu'à endsAt
 * - TRACK_SUB   : accès à tous les cours d'une filière jusqu'à endsAt
 */
@Entity
@Table(name = "accesses")
@Getter @Setter @NoArgsConstructor
public class Access {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferType type;

    @ManyToOne
    private Course course;

    @ManyToOne
    private User teacher;

    @ManyToOne
    private Track track;

    private Instant startsAt = Instant.now();

    private Instant endsAt;

    @ManyToOne
    private Payment payment;

    public boolean isActive() {
        return endsAt == null || endsAt.isAfter(Instant.now());
    }
}
