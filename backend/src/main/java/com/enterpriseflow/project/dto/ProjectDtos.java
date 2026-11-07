package com.enterpriseflow.project.dto;

import com.enterpriseflow.project.ProjectMember;
import com.enterpriseflow.project.ProjectStatus;
import com.enterpriseflow.user.dto.UserSummary;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request and response bodies of the projects API. */
public final class ProjectDtos {

    private ProjectDtos() {
    }

    /**
     * @param managerId optional; defaults to the creator. Must be a user with the MANAGER or ADMIN role.
     */
    public record CreateProjectRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 5000) String description,
            UUID managerId,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal budget,
            LocalDate startDate,
            LocalDate dueDate) {
    }

    /**
     * Budget is deliberately absent: changing it requires an approved PROJECT_BUDGET_CHANGE request.
     * Status cannot be set to ARCHIVED here; archiving has its own endpoint.
     */
    public record UpdateProjectRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 5000) String description,
            @NotNull ProjectStatus status,
            LocalDate startDate,
            LocalDate dueDate) {
    }

    /** Hands a project over to another manager (ADMIN only). */
    public record AssignManagerRequest(@NotNull UUID managerId) {
    }

    /** Adds an existing, active user to the project team. */
    public record AddMemberRequest(@NotNull UUID userId) {
    }

    /** @param progressPercent share of non-archived tasks that are DONE, 0 when the project has no tasks */
    public record ProjectResponse(UUID id, String name, String description, ProjectStatus status,
                                  UserSummary manager, BigDecimal budget, LocalDate startDate, LocalDate dueDate,
                                  long totalTasks, long doneTasks, int progressPercent, long memberCount,
                                  boolean canManage, Instant createdAt, Instant updatedAt) {
    }

    /** One team member; {@code manager} marks the project's manager in the member list. */
    public record ProjectMemberResponse(UUID userId, String fullName, String email, boolean manager,
                                        Instant joinedAt) {

        public static ProjectMemberResponse from(ProjectMember member, UUID managerId) {
            var user = member.getUser();
            return new ProjectMemberResponse(user.getId(), user.getFullName(), user.getEmail(),
                    user.getId().equals(managerId), member.getJoinedAt());
        }
    }

    /** Rounded completion percentage; 0 for a project without tasks. */
    public static int progressPercent(long total, long done) {
        return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
    }
}
