package tn.coursplus.dto;

import tn.coursplus.entity.Notification;

import java.time.Instant;
import java.util.List;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationDto(Long id, String type, String title, String message, String link,
                                  boolean seen, Instant createdAt) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                    n.isSeen(), n.getCreatedAt());
        }
    }

    public record NotificationList(long unread, List<NotificationDto> items) {
    }
}
