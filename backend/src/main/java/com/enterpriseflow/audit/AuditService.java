package com.enterpriseflow.audit;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit entries inside the caller's transaction. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * MANDATORY: an audit entry must be written in the same transaction as the change it describes.
     * If the business change rolls back, so does its audit entry, and the reverse.
     *
     * <p>Metadata must contain only safe, non-secret values (ids, names, old/new statuses) — never passwords
     * or tokens. Null values are dropped.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, AuditAction action, AuditEntityType entityType, UUID entityId,
                       UUID projectId, Map<String, ?> metadata) {
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (value != null) {
                safe.put(key, value instanceof Enum<?> e ? e.name() : value.toString());
            }
        });
        repository.save(new AuditLog(clock.instant(), actorId, action, entityType, entityId, projectId, safe));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, AuditAction action, AuditEntityType entityType, UUID entityId, UUID projectId) {
        record(actorId, action, entityType, entityId, projectId, Map.of());
    }
}
