package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
public class University {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;   // ex. ESPRIT

    @Column(nullable = false)
    private String name;   // ex. École Supérieure Privée d'Ingénierie et de Technologies

    public University(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
