package tn.coursplus.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter @NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @ManyToOne(optional = false)
    private User teacher;

    @ManyToOne
    private University university;

    @ManyToOne
    private Track track;

    /** Prix d'achat du cours à l'unité (TND). */
    @Column(nullable = false)
    private BigDecimal price;

    /** Un cours non publié n'apparaît pas dans le catalogue. */
    private boolean published = false;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Lesson> lessons = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourseDocument> documents = new ArrayList<>();
}
