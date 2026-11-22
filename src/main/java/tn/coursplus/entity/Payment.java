package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter @Setter @NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Référence lisible donnée à l'étudiant, ex. CP-7K2M9Q (à mettre dans la note du D17 / virement). */
    @Column(nullable = false, unique = true)
    private String reference;

    @ManyToOne(optional = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferType offerType;

    /** id du cours, de l'enseignant ou de la filière selon offerType. */
    @Column(nullable = false)
    private Long targetId;

    /** Libellé de l'offre au moment de l'achat (ex. "Cours : Finance de marché"). */
    private String label;

    /** Enseignant qui touche sa part (null pour un abonnement filière). */
    @ManyToOne
    private User teacher;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Référence côté Konnect (paymentRef). */
    private String providerRef;

    /** Note de l'étudiant pour un paiement manuel (ex. numéro de transaction D17). */
    @Column(length = 500)
    private String studentNote;

    private Instant createdAt = Instant.now();
    private Instant paidAt;
}
