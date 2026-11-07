package com.enterpriseflow.dashboard;

import com.enterpriseflow.dashboard.DashboardResponse.Kpis;
import com.enterpriseflow.dashboard.DashboardResponse.ProjectProgress;
import com.enterpriseflow.project.ProjectStatus;
import com.enterpriseflow.project.dto.ProjectDtos;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.task.TaskPriority;
import com.enterpriseflow.task.TaskStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Aggregate queries for the dashboard. Every number is computed by the database (COUNT/SUM/GROUP BY);
 * no entity is loaded just to be counted in Java.
 *
 * <p>Each query embeds the same authorization scope: admins see everything, other users only projects
 * they are members of.
 */
@Component
class DashboardQueries {

    /** JPQL fragment, parameterized by :admin and :userId, applied to a Project alias named {@code p}. */
    private static final String PROJECT_SCOPE = """
            (:admin = true or exists (
                select m.id from ProjectMember m where m.project = p and m.user.id = :userId))
            """;

    private final EntityManager em;

    DashboardQueries(EntityManager em) {
        this.em = em;
    }

    Kpis kpis(CurrentUser user, LocalDate today) {
        Object[] projects = scoped(em.createQuery("""
                select count(p),
                       coalesce(sum(case when p.status = com.enterpriseflow.project.ProjectStatus.ACTIVE then 1 else 0 end), 0)
                from Project p
                where p.status <> com.enterpriseflow.project.ProjectStatus.ARCHIVED and """ + PROJECT_SCOPE,
                Object[].class), user).getSingleResult();

        Object[] tasks = scoped(em.createQuery("""
                select count(t),
                       coalesce(sum(case when t.status = com.enterpriseflow.task.TaskStatus.DONE then 1 else 0 end), 0),
                       coalesce(sum(case when t.dueDate < :today
                                          and t.status <> com.enterpriseflow.task.TaskStatus.DONE then 1 else 0 end), 0)
                from Task t join t.project p
                where t.archived = false
                  and p.status <> com.enterpriseflow.project.ProjectStatus.ARCHIVED and """ + PROJECT_SCOPE,
                Object[].class), user).setParameter("today", today).getSingleResult();

        Long pendingApprovals = scoped(em.createQuery("""
                select count(a) from ApprovalRequest a join a.project p
                where a.status = com.enterpriseflow.approval.ApprovalStatus.PENDING and """ + PROJECT_SCOPE,
                Long.class), user).getSingleResult();

        return new Kpis(number(projects[0]), number(projects[1]), number(tasks[0]), number(tasks[1]),
                number(tasks[2]), pendingApprovals);
    }

    Map<TaskStatus, Long> tasksByStatus(CurrentUser user) {
        Map<TaskStatus, Long> result = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            result.put(status, 0L);
        }
        scoped(em.createQuery("""
                select t.status, count(t) from Task t join t.project p
                where t.archived = false
                  and p.status <> com.enterpriseflow.project.ProjectStatus.ARCHIVED and """ + PROJECT_SCOPE + """
                group by t.status
                """, Object[].class), user)
                .getResultList()
                .forEach(row -> result.put((TaskStatus) row[0], number(row[1])));
        return result;
    }

    Map<TaskPriority, Long> openTasksByPriority(CurrentUser user) {
        Map<TaskPriority, Long> result = new EnumMap<>(TaskPriority.class);
        for (TaskPriority priority : TaskPriority.values()) {
            result.put(priority, 0L);
        }
        scoped(em.createQuery("""
                select t.priority, count(t) from Task t join t.project p
                where t.archived = false and t.status <> com.enterpriseflow.task.TaskStatus.DONE
                  and p.status <> com.enterpriseflow.project.ProjectStatus.ARCHIVED and """ + PROJECT_SCOPE + """
                group by t.priority
                """, Object[].class), user)
                .getResultList()
                .forEach(row -> result.put((TaskPriority) row[0], number(row[1])));
        return result;
    }

    /** Most recently updated non-archived projects with task totals, via an entity join and GROUP BY. */
    List<ProjectProgress> projectProgress(CurrentUser user, int limit) {
        return scoped(em.createQuery("""
                select p.id, p.name, p.status, count(t),
                       coalesce(sum(case when t.status = com.enterpriseflow.task.TaskStatus.DONE then 1 else 0 end), 0)
                from Project p
                left join Task t on t.project = p and t.archived = false
                where p.status <> com.enterpriseflow.project.ProjectStatus.ARCHIVED and """ + PROJECT_SCOPE + """
                group by p.id, p.name, p.status, p.updatedAt
                order by p.updatedAt desc
                """, Object[].class), user)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(row -> {
                    long total = number(row[3]);
                    long done = number(row[4]);
                    return new ProjectProgress((UUID) row[0], (String) row[1], (ProjectStatus) row[2], total, done,
                            ProjectDtos.progressPercent(total, done));
                })
                .toList();
    }

    private static <T> TypedQuery<T> scoped(TypedQuery<T> query, CurrentUser user) {
        return query.setParameter("admin", user.isAdmin()).setParameter("userId", user.id());
    }

    /** COUNT returns Long and SUM of integers may return Long or Integer depending on the dialect. */
    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
