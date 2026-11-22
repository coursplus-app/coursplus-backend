package tn.coursplus.dto;

import tn.coursplus.entity.*;

import java.math.BigDecimal;
import java.util.List;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record TrackDto(Long id, String name, Long universityId, BigDecimal monthlyPrice) {
        public static TrackDto from(Track t) {
            return new TrackDto(t.getId(), t.getName(), t.getUniversity().getId(), t.getMonthlyPrice());
        }
    }

    public record UniversityDto(Long id, String code, String name, List<TrackDto> tracks) {
    }

    public record TeacherDto(Long id, String fullName, String specialty, String bio,
                             BigDecimal monthlyPrice, long courseCount) {
    }

    /** Carte de cours dans le catalogue. */
    public record CourseCardDto(Long id, String title, String shortDescription, BigDecimal price,
                                Long teacherId, String teacherName, String teacherSpecialty,
                                String universityCode, String trackName,
                                int lessonCount, int documentCount, boolean published) {

        public static CourseCardDto from(Course c) {
            String d = c.getDescription() == null ? "" : c.getDescription();
            return new CourseCardDto(c.getId(), c.getTitle(),
                    d.length() > 160 ? d.substring(0, 157) + "..." : d,
                    c.getPrice(), c.getTeacher().getId(), c.getTeacher().getFullName(),
                    c.getTeacher().getSpecialty(),
                    c.getUniversity() == null ? null : c.getUniversity().getCode(),
                    c.getTrack() == null ? null : c.getTrack().getName(),
                    c.getLessons().size(), c.getDocuments().size(), c.isPublished());
        }
    }

    public record LessonDto(Long id, String title, int position, boolean freePreview, boolean hasVideo) {
        public static LessonDto from(Lesson l) {
            return new LessonDto(l.getId(), l.getTitle(), l.getPosition(), l.isFreePreview(), l.getVideoFile() != null);
        }
    }

    public record DocumentDto(Long id, String title, String type, String originalName) {
        public static DocumentDto from(CourseDocument d) {
            return new DocumentDto(d.getId(), d.getTitle(), d.getType().name(), d.getOriginalName());
        }
    }

    /** Une offre d'achat proposée sur la page d'un cours. */
    public record OfferDto(String offerType, Long targetId, String label, String description, BigDecimal price) {
    }

    public record CourseDetailDto(Long id, String title, String description, BigDecimal price,
                                  TeacherDto teacher, Long universityId, String universityCode,
                                  Long trackId, String trackName, boolean published,
                                  List<LessonDto> lessons, List<DocumentDto> documents,
                                  boolean hasAccess, List<OfferDto> offers) {
    }
}
