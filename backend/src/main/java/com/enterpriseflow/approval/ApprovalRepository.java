package com.enterpriseflow.approval;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Data access for approval requests. The entity graphs load the project, task and people in the same query,
 * because every response shows them (avoids N+1 selects on list pages).
 */
public interface ApprovalRepository extends JpaRepository<ApprovalRequest, UUID>,
        JpaSpecificationExecutor<ApprovalRequest> {

    @Override
    @EntityGraph(attributePaths = {"project", "project.manager", "task", "requester", "reviewer"})
    Page<ApprovalRequest> findAll(Specification<ApprovalRequest> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "project.manager", "task", "requester", "reviewer"})
    Optional<ApprovalRequest> findDetailedById(UUID id);

    // Used to allow only one pending request of a given type per project / per task at a time.
    boolean existsByTypeAndProjectIdAndStatus(ApprovalType type, UUID projectId, ApprovalStatus status);

    boolean existsByTypeAndTaskIdAndStatus(ApprovalType type, UUID taskId, ApprovalStatus status);
}
