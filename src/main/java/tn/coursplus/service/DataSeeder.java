package tn.coursplus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.coursplus.entity.*;
import tn.coursplus.repository.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Données de démonstration (profil dev uniquement, et seulement si la base est vide).
 *
 * Comptes créés :
 *   admin@coursplus.tn      / admin123
 *   finance@coursplus.tn    / prof123   (Ingénieur financier)
 *   medecine@coursplus.tn   / prof123   (Médecin)
 *   info@coursplus.tn       / prof123   (Ingénieur informatique)
 *   etudiant@esprit.tn      / etudiant123
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final boolean enabled;
    private final UserRepository users;
    private final UniversityRepository universities;
    private final TrackRepository tracks;
    private final CourseRepository courses;
    private final PasswordEncoder encoder;
    private final StorageService storage;

    public DataSeeder(@Value("${app.seed.enabled}") boolean enabled, UserRepository users,
                      UniversityRepository universities, TrackRepository tracks, CourseRepository courses,
                      PasswordEncoder encoder, StorageService storage) {
        this.enabled = enabled;
        this.users = users;
        this.universities = universities;
        this.tracks = tracks;
        this.courses = courses;
        this.encoder = encoder;
        this.storage = storage;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!enabled || users.count() > 0) return;
        log.info("Base vide : chargement des données de démonstration...");

        University esprit = universities.save(new University("ESPRIT", "École Supérieure Privée d'Ingénierie et de Technologies"));
        University esc = universities.save(new University("ESC", "École Supérieure de Commerce de Tunis"));
        University fmt = universities.save(new University("FMT", "Faculté de Médecine de Tunis"));

        Track espritInfo = tracks.save(new Track("Génie logiciel", esprit, new BigDecimal("59")));
        Track espritData = tracks.save(new Track("Data Science & IA", esprit, new BigDecimal("59")));
        Track escFinance = tracks.save(new Track("Finance", esc, new BigDecimal("49")));
        tracks.save(new Track("Comptabilité", esc, new BigDecimal("49")));
        Track fmtPcem = tracks.save(new Track("PCEM 1", fmt, new BigDecimal("69")));

        user("Administrateur", "admin@coursplus.tn", "admin123", Role.ADMIN, null, null);
        User finance = user("Sami Ben Ali", "finance@coursplus.tn", "prof123", Role.TEACHER,
                "Ingénieur financier", new BigDecimal("39"));
        finance.setBio("Ingénieur financier, 6 ans en salle des marchés. J'explique la finance simplement, avec des exercices types examen.");
        User medecin = user("Dr. Amira Trabelsi", "medecine@coursplus.tn", "prof123", Role.TEACHER,
                "Médecin", new BigDecimal("45"));
        medecin.setBio("Médecin généraliste. Cours de PCEM avec schémas et fiches de synthèse.");
        User info = user("Youssef Gharbi", "info@coursplus.tn", "prof123", Role.TEACHER,
                "Ingénieur informatique", new BigDecimal("39"));
        info.setBio("Ingénieur logiciel. Spring Boot, Angular, bases de données : des cours orientés projets ESPRIT.");
        User etudiant = user("Étudiant Démo", "etudiant@esprit.tn", "etudiant123", Role.STUDENT, null, null);
        etudiant.setUniversity(esprit);

        course(info, espritInfo, "Spring Boot de A à Z",
                "Construire une API REST complète avec Spring Boot, JPA et Spring Security (JWT). Parfait pour préparer le module Architecture des SI à ESPRIT.",
                "29", new String[]{"Introduction et installation", "Entités et repositories JPA", "Sécurité avec JWT"});
        course(info, espritData, "Machine Learning pour débutants",
                "Régression, classification et évaluation des modèles avec Python et scikit-learn, avec des exercices corrigés.",
                "35", new String[]{"Qu'est-ce que le Machine Learning ?", "Régression linéaire"});
        course(finance, escFinance, "Mathématiques financières",
                "Intérêts simples et composés, actualisation, annuités et emprunts indivis. Méthode + exercices types examen ESC.",
                "25", new String[]{"Intérêts simples", "Intérêts composés", "Emprunts indivis"});
        course(finance, escFinance, "Finance de marché",
                "Actions, obligations, produits dérivés et gestion de portefeuille expliqués avec des exemples tunisiens (BVMT).",
                "30", new String[]{"Le fonctionnement de la bourse", "Les obligations"});
        course(medecin, fmtPcem, "Anatomie : le membre supérieur",
                "Ostéologie, myologie et innervation du membre supérieur, avec schémas et fiches de révision.",
                "32", new String[]{"Ostéologie", "Myologie"});

        log.info("Données de démonstration prêtes ✔");
    }

    private User user(String name, String email, String pwd, Role role, String specialty, BigDecimal monthly) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(pwd));
        u.setRole(role);
        u.setSpecialty(specialty);
        u.setMonthlyPrice(monthly);
        u.setSessionId(UUID.randomUUID().toString());
        return users.save(u);
    }

    private void course(User teacher, Track track, String title, String description, String price,
                        String[] lessons) throws IOException {
        Course c = new Course();
        c.setTeacher(teacher);
        c.setTrack(track);
        c.setUniversity(track.getUniversity());
        c.setTitle(title);
        c.setDescription(description);
        c.setPrice(new BigDecimal(price));
        c.setPublished(true);
        for (int i = 0; i < lessons.length; i++) {
            Lesson l = new Lesson();
            l.setCourse(c);
            l.setTitle(lessons[i]);
            l.setPosition(i + 1);
            l.setFreePreview(i == 0); // la 1re leçon est un aperçu gratuit
            l.setVideoFile(copySeed("seed/sample.mp4", "videos", "mp4"));
            c.getLessons().add(l);
        }
        CourseDocument summary = new CourseDocument();
        summary.setCourse(c);
        summary.setTitle("Résumé du cours");
        summary.setType(DocumentType.SUMMARY);
        summary.setOriginalName("resume.pdf");
        summary.setFile(copySeed("seed/sample.pdf", "documents", "pdf"));
        c.getDocuments().add(summary);
        CourseDocument exercises = new CourseDocument();
        exercises.setCourse(c);
        exercises.setTitle("Série d'exercices");
        exercises.setType(DocumentType.EXERCISE);
        exercises.setOriginalName("exercices.pdf");
        exercises.setFile(copySeed("seed/sample.pdf", "documents", "pdf"));
        c.getDocuments().add(exercises);
        courses.save(c);
    }

    private String copySeed(String resource, String folder, String ext) throws IOException {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            return storage.store(in, folder, ext);
        }
    }
}
