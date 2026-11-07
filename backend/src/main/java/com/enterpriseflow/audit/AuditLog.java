package com.enterpriseflow.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only record of a business action. {@code @Immutable} stops Hibernate from ever issuing an UPDATE;
 * the database trigger in V6 enforces the same rule for any other client.
 */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant occurredAt;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 60)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 40)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    // Stored as PostgreSQL jsonb; Hibernate serializes the map with Jackson.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    protected AuditLog() {
        // Required by JPA
    }

    AuditLog(Instant occurredAt, UUID actorId, AuditAction action, AuditEntityType entityType,
             UUID entityId, UUID projectId, Map<String, Object> metadata) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.projectId = projectId;
        this.metadata = metadata;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorId() {
        return actorId;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
