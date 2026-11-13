package com.groupthree.Facilitybook.dto.notification;

import com.groupthree.Facilitybook.entity.Notification;
import com.groupthree.Facilitybook.entity.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long reservationId,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReservation() != null ? notification.getReservation().getId() : null,
                notification.isRead(),
                notification.getCreatedAt());
    }
}
