package com.enterpriseflow.approval.dto;

import com.enterpriseflow.approval.ApprovalStatus;
import com.enterpriseflow.approval.ApprovalType;
import com.enterpriseflow.user.dto.UserSummary;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Request / response bodies for the approvals API. */
public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    /**
     * TASK_COMPLETION needs {@code taskId}; PROJECT_BUDGET_CHANGE needs {@code projectId} and
     * {@code requestedBudget}. The service validates the combination.
     */
    public record SubmitApprovalRequest(
            @NotNull ApprovalType type,
            UUID projectId,
            UUID taskId,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal requestedBudget,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record DecisionRequest(@Size(max = 1000) String comment) {
    }

    public record Ref(UUID id, String name) {
    }

    public record ApprovalResponse(UUID id, ApprovalType type, ApprovalStatus status, Ref project, Ref task,
                                   UserSummary requester, UserSummary reviewer, String reason,
                                   BigDecimal requestedBudget, BigDecimal currentBudget, String reviewComment,
                                   Instant decidedAt, Instant createdAt, boolean canDecide, boolean canCancel) {
    }

    public enum Scope {
        /** Requests the caller submitted */
        MINE,
        /** Pending requests the caller is allowed to decide */
        TO_REVIEW,
        /** Everything the caller can see: all for ADMIN, their projects otherwise */
        ALL
    }
}
