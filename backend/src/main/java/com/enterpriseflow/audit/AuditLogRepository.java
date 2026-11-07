package com.enterpriseflow.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Insert and read only; a DB trigger blocks updates and deletes.
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(AuditEntityType entityType, UUID entityId);

    /** Recent activity limited to the projects the caller can see. */
    @Query("""
            select a from AuditLog a
            where a.projectId in (select m.project.id from ProjectMember m where m.user.id = :userId)
            order by a.occurredAt desc
            """)
    Page<AuditLog> findRecentForMember(@Param("userId") UUID userId, Pageable pageable);

    @Query("select a from AuditLog a where a.projectId is not null order by a.occurredAt desc")
    Page<AuditLog> findRecentProjectActivity(Pageable pageable);
}
