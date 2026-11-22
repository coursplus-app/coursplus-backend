package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Une filière dans une université (ex. ESPRIT - Génie logiciel). */
@Entity
@Getter @Setter @NoArgsConstructor
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    private University university;

    /** Prix de l'abonnement mensuel à tous les cours de la filière (TND). */
    private BigDecimal monthlyPrice;

    public Track(String name, University university, BigDecimal monthlyPrice) {
        this.name = name;
        this.university = university;
        this.monthlyPrice = monthlyPrice;
    }
}
