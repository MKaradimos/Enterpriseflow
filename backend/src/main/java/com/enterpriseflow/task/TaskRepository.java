package com.enterpriseflow.task;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Task queries. List queries go through Specifications built by {@code TaskSpecifications}. */
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    /** Everything a task row displays, loaded in the same query (all to-one, so pagination stays in SQL). */
    @Override
    @EntityGraph(attributePaths = {"project", "project.manager", "assignee", "reporter"})
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "project.manager", "assignee", "reporter"})
    Optional<Task> findDetailedById(UUID id);

    /** A removed member keeps no open assignments in that project; completed work keeps its history. */
    // Flush before the bulk update so pending changes in the same transaction are not lost by the clear.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Task t set t.assignee = null
            where t.project.id = :projectId and t.assignee.id = :userId and t.status <> com.enterpriseflow.task.TaskStatus.DONE
            """)
    int unassignOpenTasks(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}
