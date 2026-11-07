package com.enterpriseflow.approval;

import com.enterpriseflow.approval.dto.ApprovalDtos.ApprovalResponse;
import com.enterpriseflow.approval.dto.ApprovalDtos.DecisionRequest;
import com.enterpriseflow.approval.dto.ApprovalDtos.Scope;
import com.enterpriseflow.approval.dto.ApprovalDtos.SubmitApprovalRequest;
import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.common.api.Pageables;
import com.enterpriseflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for approval requests. The controller only maps HTTP to service calls; who may submit,
 * decide or cancel a request is decided in {@link ApprovalService}.
 */
@RestController
@RequestMapping("/api/v1/approvals")
@Tag(name = "Approvals")
public class ApprovalController {

    // Sort properties accepted from the client; anything else is rejected instead of reaching the query.
    private static final Set<String> SORTABLE = Set.of("createdAt", "decidedAt", "status", "type");

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    @Operation(summary = "List approval requests: MINE, TO_REVIEW (pending for the caller) or ALL visible")
    public PageResponse<ApprovalResponse> list(CurrentUser user,
                                               @RequestParam(defaultValue = "ALL") Scope scope,
                                               @RequestParam(required = false) ApprovalStatus status,
                                               @PageableDefault(size = 20, sort = "createdAt",
                                                       direction = Sort.Direction.DESC) Pageable pageable) {
        return approvalService.list(user, scope, status, Pageables.restrictSort(pageable, SORTABLE));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Approval request details")
    public ApprovalResponse get(CurrentUser user, @PathVariable UUID id) {
        return approvalService.get(id, user);
    }

    @PostMapping
    @Operation(summary = "Submit an approval request")
    public ResponseEntity<ApprovalResponse> submit(CurrentUser user, @Valid @RequestBody SubmitApprovalRequest request) {
        ApprovalResponse created = approvalService.submit(user, request);
        return ResponseEntity.created(URI.create("/api/v1/approvals/" + created.id())).body(created);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending request and apply its effect")
    public ApprovalResponse approve(CurrentUser user, @PathVariable UUID id,
        // The body is optional when approving: a comment is only mandatory for rejections.
                                    @Valid @RequestBody(required = false) DecisionRequest request) {
        return approvalService.approve(id, user, request == null ? null : request.comment());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending request (comment required)")
    public ApprovalResponse reject(CurrentUser user, @PathVariable UUID id,
                                   @Valid @RequestBody DecisionRequest request) {
        return approvalService.reject(id, user, request.comment());
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel your own pending request")
    public ApprovalResponse cancel(CurrentUser user, @PathVariable UUID id) {
        return approvalService.cancel(id, user);
    }
}
