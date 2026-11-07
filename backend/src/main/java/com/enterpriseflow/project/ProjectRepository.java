package com.enterpriseflow.project;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Project queries. Besides plain lookups it provides aggregated counts (tasks, members) per project, so a
 * list page needs a fixed number of queries regardless of how many projects it shows.
 */
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    // Fetching the manager (a to-one association) together with the page is safe with pagination.
    @Override
    @EntityGraph(attributePaths = "manager")
    Page<Project> findAll(Specification<Project> spec, Pageable pageable);

    @EntityGraph(attributePaths = "manager")
    Optional<Project> findWithManagerById(UUID id);

    /** Task totals per project for a whole page in one grouped query, instead of one query per project. */
    @Query("""
            select t.project.id as projectId,
                   count(t) as totalTasks,
                   sum(case when t.status = com.enterpriseflow.task.TaskStatus.DONE then 1 else 0 end) as doneTasks
            from Task t
            where t.project.id in :projectIds and t.archived = false
            group by t.project.id
            """)
    List<TaskCounts> countTasksByProject(@Param("projectIds") Collection<UUID> projectIds);

    @Query("""
            select m.project.id as projectId, count(m) as memberCount
            from ProjectMember m
            where m.project.id in :projectIds
            group by m.project.id
            """)
    /** Member count per project for a whole page in one grouped query. */
    List<MemberCount> countMembersByProject(@Param("projectIds") Collection<UUID> projectIds);

    /** Interface projections: Spring Data maps the query aliases onto these getters. */
    interface TaskCounts {
        UUID getProjectId();

        long getTotalTasks();

        long getDoneTasks();
    }

    interface MemberCount {
        UUID getProjectId();

        long getMemberCount();
    }
}
