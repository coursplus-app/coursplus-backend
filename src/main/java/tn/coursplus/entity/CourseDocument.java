package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un document PDF d'un cours : résumé ou série d'exercices. */
@Entity
@Getter @Setter @NoArgsConstructor
public class CourseDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Course course;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    private DocumentType type;

    private String file;

    private String originalName;
}
