package com.enterpriseflow.notification;

import java.time.Instant;
import java.util.UUID;

/** A notification as returned to its recipient. {@code referenceType/Id} let the UI link to the related item. */
public record NotificationResponse(UUID id, NotificationType type, String title, String message,
                                   String referenceType, UUID referenceId, boolean read, Instant createdAt) {

    static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getReferenceType(), n.getReferenceId(), n.getReadAt() != null, n.getCreatedAt());
    }
}
