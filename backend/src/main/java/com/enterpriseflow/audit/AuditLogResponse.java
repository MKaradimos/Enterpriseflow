package com.enterpriseflow.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * @param actorName resolved display name, or null for system actions and deleted users
 */
public record AuditLogResponse(UUID id, Instant occurredAt, UUID actorId, String actorName, AuditAction action,
                               AuditEntityType entityType, UUID entityId, UUID projectId,
                               Map<String, Object> metadata) {

    public static AuditLogResponse from(AuditLog log, String actorName) {
        return new AuditLogResponse(log.getId(), log.getOccurredAt(), log.getActorId(), actorName, log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getProjectId(), log.getMetadata());
    }
}
