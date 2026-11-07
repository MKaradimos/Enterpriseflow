package com.enterpriseflow.approval;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// Entity graphs fetch project, task and people up front; every response needs them.
public interface ApprovalRepository extends JpaRepository<ApprovalRequest, UUID>,
        JpaSpecificationExecutor<ApprovalRequest> {

    @Override
    @EntityGraph(attributePaths = {"project", "project.manager", "task", "requester", "reviewer"})
    Page<ApprovalRequest> findAll(Specification<ApprovalRequest> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "project.manager", "task", "requester", "reviewer"})
    Optional<ApprovalRequest> findDetailedById(UUID id);

    // Only one pending request of each kind per project / task.
    boolean existsByTypeAndProjectIdAndStatus(ApprovalType type, UUID projectId, ApprovalStatus status);

    boolean existsByTypeAndTaskIdAndStatus(ApprovalType type, UUID taskId, ApprovalStatus status);
}
