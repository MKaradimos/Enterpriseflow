package com.enterpriseflow.notification;

import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.common.error.ResourceNotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed in-app notifications. Clients poll the unread count; a message broker or WebSockets
 * would only be justified once real-time delivery matters more than simplicity.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Joins the caller's transaction, so a notification exists only if the triggering change commits. */
    @Transactional
    public void notify(UUID recipientId, NotificationType type, String title, String message,
                       String referenceType, UUID referenceId) {
        repository.save(new Notification(recipientId, type, title, message, referenceType, referenceId, clock.instant()));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID recipientId, boolean unreadOnly, Pageable pageable) {
        var page = unreadOnly
                ? repository.findByRecipientIdAndReadAtIsNull(recipientId, pageable)
                : repository.findByRecipientId(recipientId, pageable);
        return PageResponse.from(page, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return repository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Transactional
    public NotificationResponse markRead(UUID recipientId, UUID notificationId) {
        Notification notification = repository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(UUID recipientId) {
        return repository.markAllRead(recipientId, clock.instant());
    }
}
