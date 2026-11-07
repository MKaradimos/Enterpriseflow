package com.enterpriseflow.audit;

import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.user.User;
import com.enterpriseflow.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the audit trail: admin search, item history, dashboard activity. */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private final AuditLogRepository auditRepository;
    private final UserRepository userRepository;

    public AuditQueryService(AuditLogRepository auditRepository, UserRepository userRepository) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
    }

    public PageResponse<AuditLogResponse> search(AuditFilter filter, Pageable pageable) {
        Page<AuditLog> page = auditRepository.findAll(toSpecification(filter), pageable);
        Map<UUID, String> names = actorNames(page.getContent());
        return PageResponse.from(page, log -> AuditLogResponse.from(log, names.get(log.getActorId())));
    }

    /** Caller is responsible for having checked access to the entity. */
    public List<AuditLogResponse> forEntity(AuditEntityType type, UUID entityId) {
        return toResponses(auditRepository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(type, entityId));
    }

    public List<AuditLogResponse> recentActivity(CurrentUser user, int limit) {
        Pageable top = PageRequest.of(0, limit);
        Page<AuditLog> page = user.isAdmin()
                ? auditRepository.findRecentProjectActivity(top)
                : auditRepository.findRecentForMember(user.id(), top);
        return toResponses(page.getContent());
    }

    private List<AuditLogResponse> toResponses(List<AuditLog> logs) {
        Map<UUID, String> names = actorNames(logs);
        return logs.stream().map(log -> AuditLogResponse.from(log, names.get(log.getActorId()))).toList();
    }

    /** One query for all actors on the page instead of one per row. */
    private Map<UUID, String> actorNames(List<AuditLog> logs) {
        Set<UUID> ids = logs.stream().map(AuditLog::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
    }

    private static Specification<AuditLog> toSpecification(AuditFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addIfPresent(predicates, filter.entityType(), v -> cb.equal(root.get("entityType"), v));
            addIfPresent(predicates, filter.entityId(), v -> cb.equal(root.get("entityId"), v));
            addIfPresent(predicates, filter.action(), v -> cb.equal(root.get("action"), v));
            addIfPresent(predicates, filter.actorId(), v -> cb.equal(root.get("actorId"), v));
            addIfPresent(predicates, filter.projectId(), v -> cb.equal(root.get("projectId"), v));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static <T> void addIfPresent(List<Predicate> predicates, T value, Function<T, Predicate> predicate) {
        if (value != null) {
            predicates.add(predicate.apply(value));
        }
    }

    public record AuditFilter(AuditEntityType entityType, UUID entityId, AuditAction action, UUID actorId,
                              UUID projectId) {
    }
}
