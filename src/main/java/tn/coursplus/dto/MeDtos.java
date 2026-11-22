package tn.coursplus.dto;

import java.time.Instant;
import java.util.List;

public final class MeDtos {

    private MeDtos() {
    }

    public record AccessDto(Long id, String type, String label, Instant startsAt, Instant endsAt, boolean active) {
    }

    public record MyLearningDto(List<CatalogDtos.CourseCardDto> courses, List<AccessDto> accesses) {
    }

    public record PlayResponse(String url, String watermark, long expiresInSeconds) {
    }
}
