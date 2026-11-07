package com.enterpriseflow.project;

import com.enterpriseflow.common.domain.BaseEntity;
import com.enterpriseflow.common.error.ConflictException;
import com.enterpriseflow.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root for a project and its memberships. Members are only added or removed through this class,
 * so invariants (the manager is always a member, archived projects are read-only) live in one place.
 */
@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @Column(precision = 14, scale = 2)
    private BigDecimal budget;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Version
    private long version;

    // cascade + orphanRemoval: a membership has no meaning outside its project. Removing it from this set
    // deletes the row; saving the project inserts new members.
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProjectMember> members = new HashSet<>();

    protected Project() {
        // Required by JPA
    }

    public Project(String name, String description, User manager, BigDecimal budget,
                   LocalDate startDate, LocalDate dueDate, UUID createdBy, Instant now) {
        this.status = ProjectStatus.PLANNED;
        this.createdBy = createdBy;
        update(name, description, startDate, dueDate);
        this.budget = budget;
        assignManager(manager, now);
    }

    public void update(String name, String description, LocalDate startDate, LocalDate dueDate) {
        ensureNotArchived();
        if (startDate != null && dueDate != null && dueDate.isBefore(startDate)) {
            throw new ConflictException("The due date cannot be before the start date.");
        }
        this.name = name.trim();
        this.description = description;
        this.startDate = startDate;
        this.dueDate = dueDate;
    }

    public void changeStatus(ProjectStatus newStatus) {
        ensureNotArchived();
        if (newStatus == ProjectStatus.ARCHIVED) {
            throw new ConflictException("Use the archive operation to archive a project.");
        }
        this.status = newStatus;
    }

    /** The manager is always a member, so membership checks never need a special case for them. */
    public void assignManager(User newManager, Instant now) {
        ensureNotArchived();
        this.manager = newManager;
        addMember(newManager, now);
    }

    public void changeBudget(BigDecimal newBudget) {
        ensureNotArchived();
        this.budget = newBudget;
    }

    /** @return the new membership, or empty if the user was already a member */
    public Optional<ProjectMember> addMember(User user, Instant now) {
        ensureNotArchived();
        if (findMember(user.getId()).isPresent()) {
            return Optional.empty();
        }
        ProjectMember member = new ProjectMember(this, user, now);
        members.add(member);
        return Optional.of(member);
    }

    public void removeMember(UUID userId) {
        ensureNotArchived();
        if (manager.getId().equals(userId)) {
            throw new ConflictException("The project manager cannot be removed. Assign another manager first.");
        }
        ProjectMember member = findMember(userId)
                .orElseThrow(() -> new ConflictException("The user is not a member of this project."));
        members.remove(member);
    }

    public void archive() {
        ensureNotArchived();
        this.status = ProjectStatus.ARCHIVED;
    }

    /** Administrative operation: archived projects come back as ON_HOLD for a deliberate re-activation. */
    public void restore() {
        if (status != ProjectStatus.ARCHIVED) {
            throw new ConflictException("Only archived projects can be restored.");
        }
        this.status = ProjectStatus.ON_HOLD;
    }

    public void ensureNotArchived() {
        if (status == ProjectStatus.ARCHIVED) {
            throw new ConflictException("Archived projects are read-only.");
        }
    }

    public boolean isArchived() {
        return status == ProjectStatus.ARCHIVED;
    }

    public boolean isManagedBy(UUID userId) {
        return manager.getId().equals(userId);
    }

    private Optional<ProjectMember> findMember(UUID userId) {
        return members.stream().filter(m -> m.getUser().getId().equals(userId)).findFirst();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public User getManager() {
        return manager;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Set<ProjectMember> getMembers() {
        return Set.copyOf(members);
    }
}
