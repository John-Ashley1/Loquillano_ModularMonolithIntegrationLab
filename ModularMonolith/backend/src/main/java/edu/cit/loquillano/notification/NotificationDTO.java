package edu.cit.loquillano.notification;

import java.time.LocalDateTime;

public class NotificationDTO {

    private Long notificationId;
    private String message;
    private LocalDateTime createdAt;

    public NotificationDTO(Long notificationId, String message, LocalDateTime createdAt) {
        this.notificationId = notificationId;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
