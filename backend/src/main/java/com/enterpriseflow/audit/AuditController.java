package com.enterpriseflow.audit;

import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.common.api.Pageables;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Organization-wide audit search, admins only. Per-item history is served by the project and task endpoints. */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @Operation(summary = "Search the organization-wide audit trail (ADMIN)")
    public PageResponse<AuditLogResponse> search(
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) UUID projectId,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageables.restrictSort(pageable, Set.of("occurredAt", "action", "entityType"));
        return auditQueryService.search(
                new AuditQueryService.AuditFilter(entityType, entityId, action, actorId, projectId), pageable);
    }
}
