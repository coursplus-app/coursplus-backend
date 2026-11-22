package tn.coursplus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class AdminDtos {

    /** Ligne du tableau « Visionnage » : activité d'un compte sur 24 h / 7 jours. */
    public record WatchRow(Long userId, String fullName, String email, long plays24h, long ips24h,
                           long seconds24h, long seconds7d, boolean suspicious, String reason) {
    }

    /** Détail par leçon pour un compte. */
    public record WatchLessonRow(Long lessonId, String lessonTitle, String courseTitle, long seconds, int maxPosition,
                                 java.time.Instant lastWatchedAt) {
    }

    private AdminDtos() {
    }

    public record CreateTeacherRequest(
            @NotBlank(message = "obligatoire") String fullName,
            @NotBlank(message = "obligatoire") @Email(message = "email invalide") String email,
            @NotBlank(message = "obligatoire") @Size(min = 6, message = "6 caractères minimum") String password,
            String specialty,
            String bio,
            BigDecimal monthlyPrice) {
    }

    public record UniversityRequest(@NotBlank(message = "obligatoire") String code,
                                    @NotBlank(message = "obligatoire") String name) {
    }

    public record TrackRequest(@NotBlank(message = "obligatoire") String name,
                               @NotNull(message = "obligatoire") Long universityId,
                               BigDecimal monthlyPrice) {
    }

    public record OverviewDto(long students, long teachers, long publishedCourses,
                              long pendingPayments, long paidPayments, BigDecimal grossRevenue,
                              BigDecimal platformRevenue, long pendingTeachers) {
    }
}
