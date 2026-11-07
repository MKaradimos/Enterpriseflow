package com.enterpriseflow.notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Notification queries, always filtered by recipient. */
interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadAtIsNull(UUID recipientId, Pageable pageable);

    /** Feeds the unread badge in the header. */
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    // Scoped by recipient: looking up someone else's notification id yields "not found".
    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    // Flush before the bulk update so pending changes in the same transaction are not lost by the clear.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);
}
