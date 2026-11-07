package com.enterpriseflow.task;

import com.enterpriseflow.common.persistence.SpecificationUtils;
import com.enterpriseflow.project.ProjectMember;
import com.enterpriseflow.project.ProjectStatus;
import com.enterpriseflow.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Translates the task list filters into a JPA Specification (dynamic WHERE clause). */
final class TaskSpecifications {

    private TaskSpecifications() {
    }

    /** Every filter is optional; the membership restriction for non-admins is always applied. */
    static Specification<Task> matching(TaskFilter filter, CurrentUser user, LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Authorization scope first: non-admins only ever see tasks of projects they belong to.
            if (!user.isAdmin()) {
                Subquery<UUID> membership = query.subquery(UUID.class);
                Root<ProjectMember> member = membership.from(ProjectMember.class);
                membership.select(member.get("id")).where(
                        cb.equal(member.get("project"), root.get("project")),
                        cb.equal(member.get("user").get("id"), user.id()));
                predicates.add(cb.exists(membership));
            }

            if (!filter.includeArchived()) {
                predicates.add(cb.isFalse(root.get("archived")));
                predicates.add(cb.notEqual(root.get("project").get("status"), ProjectStatus.ARCHIVED));
            }
            if (filter.projectId() != null) {
                predicates.add(cb.equal(root.get("project").get("id"), filter.projectId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.priority()));
            }
            if (filter.assignedToMe()) {
                predicates.add(cb.equal(root.get("assignee").get("id"), user.id()));
            } else if (filter.assigneeId() != null) {
                predicates.add(cb.equal(root.get("assignee").get("id"), filter.assigneeId()));
            }
            if (filter.dueBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), filter.dueBefore()));
            }
            if (filter.overdue()) {
                predicates.add(cb.lessThan(root.get("dueDate"), today));
                predicates.add(cb.notEqual(root.get("status"), TaskStatus.DONE));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                predicates.add(SpecificationUtils.containsIgnoreCase(cb, root.get("title"), filter.q()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
