package tn.coursplus.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TeacherDtos {

    private TeacherDtos() {
    }

    public record CourseRequest(
            @NotBlank(message = "obligatoire") String title,
            String description,
            @NotNull(message = "obligatoire") @DecimalMin(value = "0", message = "doit être positif") BigDecimal price,
            Long universityId,
            Long trackId) {
    }

    public record LessonUpdateRequest(String title, Boolean freePreview, Integer position) {
    }

    public record ProfileRequest(String specialty, String bio, BigDecimal monthlyPrice) {
    }

    public record CourseSales(Long courseId, String title, long sales, BigDecimal revenue) {
    }

    public record SaleDto(String reference, String label, String studentName, BigDecimal amount,
                          BigDecimal teacherShare, Instant paidAt) {
    }

    public record StatsDto(long totalSales, BigDecimal grossRevenue, BigDecimal teacherRevenue,
                           double teacherSharePercent, long studentCount, long activeSubscribers,
                           List<CourseSales> perCourse, List<SaleDto> recentSales) {
    }

    public record StudentDto(Long id, String fullName, String email, String universityCode,
                             String accessType, String accessLabel, Instant since, Instant until, boolean active) {
    }
}
