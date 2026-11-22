package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Une vidéo d'un cours. */
@Entity
@Getter @Setter @NoArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Course course;

    @Column(nullable = false)
    private String title;

    private int position;

    /** Nom du fichier vidéo dans le dossier de stockage (jamais exposé au public). */
    private String videoFile;

    /** Une leçon "aperçu gratuit" est visible sans achat (pour donner envie). */
    private boolean freePreview = false;
}
