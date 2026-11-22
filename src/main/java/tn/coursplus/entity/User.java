package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Université de l'étudiant (facultatif). */
    @ManyToOne
    private University university;

    /** Pour les enseignants : spécialité affichée (ex. "Ingénieur financier"). */
    private String specialty;

    @Column(length = 2000)
    private String bio;

    /** Pour les enseignants : prix de l'abonnement mensuel à tous ses cours (TND). */
    private BigDecimal monthlyPrice;

    /**
     * Identifiant de la session active. Il change à chaque connexion :
     * si le compte se connecte sur un autre appareil, l'ancien est déconnecté
     * (protection contre le partage de compte).
     */
    private String sessionId;

    private boolean active = true;

    /**
     * Enseignants : validation par l'admin. Null (comptes créés avant cette fonction) = APPROVED.
     * Un enseignant PENDING peut se connecter mais ne peut ni créer ni publier de cours.
     */
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus;

    /** Candidature enseignant : lien vers un CV / LinkedIn / diplôme. */
    @Column(length = 500)
    private String credentialsLink;

    /** Message de l'admin lors du refus. */
    @Column(length = 500)
    private String reviewNote;

    private Instant createdAt = Instant.now();

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus == null ? ApprovalStatus.APPROVED : approvalStatus;
    }
}
